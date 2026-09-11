package org.example.data;

import org.example.model.ElementType;
import org.example.model.HeldItem;
import org.example.model.HeldItemEffect;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.StatusCondition;
import org.example.util.LogUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 游戏数据注册表。
 * <p>统一的数据来源：先加载代码内建精灵/技能/道具，再从 classpath 的 {@code /data/*.csv}
 * 读取“统一格式”的外部数据并覆盖（便于导入与扩展）。</p>
 *
 * <p>CSV 统一格式（首行为表头，自动跳过，# 开头视为注释）：</p>
 * <ul>
 *     <li>moves.csv：id,name,type,category,power,accuracy,maxPp,priority,inflicts,inflictionChance[,effect]
 *     —— type 用属性英文名，category 取 PHYSICAL/SPECIAL/STATUS（变化类 power 为 0）；
 *     accuracy 为命中率（-1 表示必中）；priority 为先制度；inflicts 为命中后可能施加的异常状态
 *     （POISON/BADLY_POISON/PARALYSIS/BURN/SLEEP/FREEZE/CONFUSION，留空表示无），
 *     inflictionChance 为触发概率百分比（0~100）；
 *     effect 为可选的技能效果英文名（如 SUNNY_DAY/GRASSY_TERRAIN），不填为无效果</li>
 *     <li>species.csv：id,name,type1,type2,hp,atk,def,spatk,spdef,speed,catchRate,wild,evolvesTo,evolveLevel,moves,learns
 *     —— type2 可为空；wild 1/0 决定是否进野怪池；evolvesTo 为进化目标 id（可空），
 *     evolveLevel 为进化等级（0/空 = 不进化）；moves 为出生即会的技能（“;”分隔、最多 4 个）；
 *     learns 为按等级习得技能，格式 “等级:技能id;等级:技能id”（如 8:m_flame_wheel）</li>
 *     <li>items.csv：id,name,category,effect,alwaysCatch[,curesSpec]
 *     —— category 取 HEAL/BALL/CURE，HEAL 的 effect 为回复量、BALL 的 effect 为捕捉倍率；
 *     CURE 的第 6 列 curesSpec 为可解除的异常状态（ALL 表示全部主要异常，或
 *     POISON/PARALYSIS/BURN/FREEZE/SLEEP/BADLY_POISON 之一）</li>
 * </ul>
 */
public final class GameData {

    /** 技能 id -> 技能。 */
    private final Map<String, Move> moveMap = new LinkedHashMap<>();
    /** 物种 id -> 精灵种族。 */
    private final Map<String, Species> speciesMap = new LinkedHashMap<>();
    /** 道具 id -> 道具。 */
    private final Map<String, Item> itemMap = new LinkedHashMap<>();
    /** 装备 id -> 可携带装备。 */
    private final Map<String, HeldItem> equipmentMap = new LinkedHashMap<>();
    /** 可随机遭遇的野生精灵物种 id。 */
    private final List<String> wildPool = new ArrayList<>();

    private static volatile GameData instance;

    private GameData() {
        registerBuiltin();
        loadCsvOverrides();
    }

    /** 单例（懒加载，首次调用触发注册）。 */
    public static GameData instance() {
        GameData local = instance;
        if (local == null) {
            synchronized (GameData.class) {
                local = instance;
                if (local == null) {
                    local = new GameData();
                    instance = local;
                }
            }
        }
        return local;
    }

    // ------------------------------------------------------------------
    // 对外查询
    // ------------------------------------------------------------------

    public Move move(String id) {
        return moveMap.get(id);
    }

    public Species species(String id) {
        return speciesMap.get(id);
    }

    public Item item(String id) {
        return itemMap.get(id);
    }

    /** 按 id 查询可携带装备（未注册返回 {@code null}）。 */
    public HeldItem equipment(String id) {
        return equipmentMap.get(id);
    }

    /** 全部可携带装备（不可变快照，肉鸽 REWARD 事件随机抽取用）。 */
    public List<HeldItem> allEquipment() {
        return List.copyOf(equipmentMap.values());
    }

    /** 内建全部精灵种族（不可变快照）。 */
    public List<Species> allSpecies() {
        return List.copyOf(speciesMap.values());
    }

    /** 内建全部技能（不可变快照）。 */
    public List<Move> allMoves() {
        return List.copyOf(moveMap.values());
    }

    /** 内建全部道具（不可变快照）。 */
    public List<Item> allItems() {
        return List.copyOf(itemMap.values());
    }

    /** 可随机遭遇的野生精灵 id 池。 */
    public List<String> wildPool() {
        return List.copyOf(wildPool);
    }

    /** 初始可选队伍：游戏启动时让玩家从这几个入口精灵中组队。 */
    public List<String> starterPool() {
        List<String> starterIds = new ArrayList<>();
        for (String id : List.of("s_fire_cat", "s_water_tad", "s_leaf_chick", "s_spark_rat")) {
            if (speciesMap.containsKey(id) && !starterIds.contains(id)) {
                starterIds.add(id);
            }
        }
        return List.copyOf(starterIds);
    }

    /**
     * 按物种 id 创建一只满血个体（等级任意）。技能按成长解锁：先给「出生即会」的技能，
     * 再按等级升序补入该等级已经习得的技能（最多 4 招），因此低等级个体技能较少。
     */
    public Optional<Pokemon> createPokemon(String speciesId, int level) {
        Species sp = species(speciesId);
        if (sp == null) {
            return Optional.empty();
        }
        List<Move> pool = new ArrayList<>();
        for (String moveId : sp.getMoveIds()) {
            Move mv = moveMap.get(moveId);
            if (mv != null && !pool.contains(mv)) {
                pool.add(mv);
            }
        }
        for (Map.Entry<Integer, String> e : sp.getLearnSchedule()) {
            if (e.getKey() > level || pool.size() >= Pokemon.MAX_MOVES) {
                break;
            }
            Move mv = moveMap.get(e.getValue());
            if (mv != null && !pool.contains(mv)) {
                pool.add(mv);
            }
        }
        return Optional.of(Pokemon.create(sp, level, pool));
    }

    // ------------------------------------------------------------------
    // 内建注册
    // ------------------------------------------------------------------

    private void registerBuiltin() {
        registerBuiltinMoves();
        registerBuiltinSpecies();
        registerBuiltinItems();
        registerBuiltinEquipment();
    }

    private void registerBuiltinMoves() {
        putMove("m_tackle", "撞击", ElementType.NORMAL, MoveCategory.PHYSICAL, 40, 35);
        putMove("m_quick", "电光一闪", ElementType.NORMAL, MoveCategory.PHYSICAL, 40, 30);
        putMove("m_ember", "火花", ElementType.FIRE, MoveCategory.SPECIAL, 40, 25);
        putMove("m_flamethrower", "喷射火焰", ElementType.FIRE, MoveCategory.SPECIAL, 90, 15);
        putMove("m_flame_wheel", "火焰轮", ElementType.FIRE, MoveCategory.PHYSICAL, 60, 25);
        putMove("m_bubble", "水枪", ElementType.WATER, MoveCategory.SPECIAL, 40, 25);
        putMove("m_hydro_pump", "水炮", ElementType.WATER, MoveCategory.SPECIAL, 110, 5);
        putMove("m_aqua_jet", "水流喷射", ElementType.WATER, MoveCategory.PHYSICAL, 40, 20);
        putMove("m_vine_whip", "藤鞭", ElementType.GRASS, MoveCategory.PHYSICAL, 45, 25);
        putMove("m_razor_leaf", "飞叶快刀", ElementType.GRASS, MoveCategory.PHYSICAL, 55, 25);
        putMove("m_giga_drain", "终极吸取", ElementType.GRASS, MoveCategory.SPECIAL, 75, 10);
        putMove("m_thunder_shock", "电击", ElementType.ELECTRIC, MoveCategory.SPECIAL, 40, 30);
        putMove("m_thunderbolt", "十万伏特", ElementType.ELECTRIC, MoveCategory.SPECIAL, 90, 15);
        putMove("m_thunder", "打雷", ElementType.ELECTRIC, MoveCategory.SPECIAL, 110, 10);
        putMove("m_icy_wind", "冰冻之风", ElementType.ICE, MoveCategory.SPECIAL, 55, 15);
        putMove("m_rock_slide", "岩崩", ElementType.ROCK, MoveCategory.PHYSICAL, 75, 10);
        putMove("m_rock_throw", "落石", ElementType.ROCK, MoveCategory.PHYSICAL, 50, 15);
        putMove("m_mud_slap", "掷泥", ElementType.GROUND, MoveCategory.SPECIAL, 20, 10);
        putMove("m_earthquake", "地震", ElementType.GROUND, MoveCategory.PHYSICAL, 100, 10);
        putMove("m_wing_attack", "翅膀攻击", ElementType.FLYING, MoveCategory.PHYSICAL, 60, 35);
        putMove("m_cross_poison", "十字毒刃", ElementType.POISON, MoveCategory.PHYSICAL, 70, 20);
        // 进化形态的招牌技能
        putMove("m_fire_blast", "大字爆炎", ElementType.FIRE, MoveCategory.SPECIAL, 110, 5);
        putMove("m_muddy_water", "浊流", ElementType.WATER, MoveCategory.SPECIAL, 90, 10);
        putMove("m_leaf_blade", "叶刃", ElementType.GRASS, MoveCategory.PHYSICAL, 90, 15);
        putMove("m_volt_tackle", "伏特冲击", ElementType.ELECTRIC, MoveCategory.PHYSICAL, 120, 15);

        // ---- 天气/场地变化技能：power=0、STATUS，效果开启对应天气/场地 ----
        putMove("m_sunny_day", "大晴天", ElementType.FIRE, MoveCategory.STATUS, 0, 5, MoveEffect.SUNNY_DAY);
        putMove("m_rain_dance", "求雨", ElementType.WATER, MoveCategory.STATUS, 0, 5, MoveEffect.RAIN_DANCE);
        putMove("m_sandstorm", "沙暴", ElementType.ROCK, MoveCategory.STATUS, 0, 10, MoveEffect.SANDSTORM);
        putMove("m_hail", "冰雹", ElementType.ICE, MoveCategory.STATUS, 0, 10, MoveEffect.HAIL);
        putMove("m_electric_terrain", "电气场地", ElementType.ELECTRIC, MoveCategory.STATUS, 0, 10, MoveEffect.ELECTRIC_TERRAIN);
        putMove("m_grassy_terrain", "青草场地", ElementType.GRASS, MoveCategory.STATUS, 0, 10, MoveEffect.GRASSY_TERRAIN);
        putMove("m_misty_terrain", "薄雾场地", ElementType.FAIRY, MoveCategory.STATUS, 0, 10, MoveEffect.MISTY_TERRAIN);
        putMove("m_psychic_terrain", "精神场地", ElementType.PSYCHIC, MoveCategory.STATUS, 0, 10, MoveEffect.PSYCHIC_TERRAIN);

        // ---- 异常状态类变化技能：power=0、STATUS，命中后必定使目标陷入对应异常 ----
        putMove("m_thunder_wave", "电磁波", ElementType.ELECTRIC, MoveCategory.STATUS, 0, 20, 90,
                StatusCondition.PARALYSIS, 100);
        putMove("m_poison_powder", "毒粉", ElementType.POISON, MoveCategory.STATUS, 0, 35, 75,
                StatusCondition.POISON, 100);
        putMove("m_toxic", "剧毒", ElementType.POISON, MoveCategory.STATUS, 0, 10, 90,
                StatusCondition.BADLY_POISON, 100);
        putMove("m_sleep_powder", "催眠粉", ElementType.GRASS, MoveCategory.STATUS, 0, 15, 75,
                StatusCondition.SLEEP, 100);
        putMove("m_will_o_wisp", "鬼火", ElementType.FIRE, MoveCategory.STATUS, 0, 15, 85,
                StatusCondition.BURN, 100);
        putMove("m_confuse_ray", "奇异之光", ElementType.GHOST, MoveCategory.STATUS, 0, 10, 100,
                StatusCondition.CONFUSION, 100);

        // ---- 附带异常状态的攻击技能：按概率触发 ----
        putMove("m_body_slam", "泰山压顶", ElementType.NORMAL, MoveCategory.PHYSICAL, 85, 15, 100,
                StatusCondition.PARALYSIS, 30);
        putMove("m_ice_fang", "冰冻牙", ElementType.ICE, MoveCategory.PHYSICAL, 65, 15, 95,
                StatusCondition.FREEZE, 10);
    }

    private void registerBuiltinSpecies() {
        // ---- 进化链：基础形态（可遭遇/捕获），进化形态（wild=0 不进野怪池） ----
        // baseExp 按六维种族值总和 × 0.2（基础形态）/ 0.35（进化形态）取整，与 species.csv 的口径一致
        putSpecies("s_fire_cat", "焰尾猫", ElementType.FIRE, null,
                new Stats(45, 52, 43, 60, 50, 65), 64, 45, true,
                "s_flame_lion", 16, List.of("m_tackle", "m_ember"),
                "8:m_flame_wheel", "10:m_sunny_day", "11:m_will_o_wisp", "13:m_flamethrower");
        putSpecies("s_flame_lion", "烈焰狮", ElementType.FIRE, null,
                new Stats(65, 75, 58, 90, 68, 95), 158, 45, false,
                null, 0, List.of("m_tackle", "m_ember", "m_flame_wheel", "m_fire_blast"));

        putSpecies("s_water_tad", "泡泡蛙", ElementType.WATER, null,
                new Stats(44, 48, 65, 50, 64, 43), 63, 45, true,
                "s_surge_frog", 16, List.of("m_tackle", "m_bubble"),
                "9:m_aqua_jet", "12:m_rain_dance", "15:m_hydro_pump");
        putSpecies("s_surge_frog", "涌浪蛙", ElementType.WATER, null,
                new Stats(68, 70, 80, 85, 80, 70), 159, 45, false,
                null, 0, List.of("m_tackle", "m_bubble", "m_aqua_jet", "m_muddy_water"));

        putSpecies("s_leaf_chick", "叶芽雀", ElementType.GRASS, ElementType.FLYING,
                new Stats(45, 49, 49, 65, 65, 45), 64, 45, true,
                "s_gale_owl", 16, List.of("m_tackle", "m_vine_whip"),
                "9:m_razor_leaf", "11:m_sleep_powder", "13:m_grassy_terrain", "15:m_wing_attack");
        putSpecies("s_gale_owl", "苍翼枭", ElementType.GRASS, ElementType.FLYING,
                new Stats(65, 75, 60, 85, 75, 90), 158, 45, false,
                null, 0, List.of("m_tackle", "m_vine_whip", "m_razor_leaf", "m_leaf_blade"));

        putSpecies("s_spark_rat", "电光鼠", ElementType.ELECTRIC, null,
                new Stats(35, 55, 40, 50, 50, 90), 60, 190, true,
                "s_volt_mink", 22, List.of("m_tackle", "m_thunder_shock"),
                "8:m_quick", "10:m_thunder_wave", "14:m_electric_terrain", "18:m_thunderbolt");
        putSpecies("s_volt_mink", "迅雷貂", ElementType.ELECTRIC, null,
                new Stats(60, 80, 55, 85, 65, 125), 165, 190, false,
                null, 0, List.of("m_tackle", "m_quick", "m_thunder_shock", "m_volt_tackle"));

        // ---- 无进化的独立精灵（出生只带 1~2 招，其余按等级习得，保证低等级技能较少） ----
        putSpecies("s_ice_fox", "冰晶狐", ElementType.ICE, null,
                new Stats(40, 45, 40, 65, 45, 65), 60, 120, true,
                null, 0, List.of("m_tackle", "m_icy_wind"),
                "7:m_quick", "9:m_confuse_ray", "10:m_hail", "12:m_ice_fang");
        putSpecies("s_rock_tort", "岩甲龟", ElementType.ROCK, ElementType.GROUND,
                new Stats(44, 48, 65, 50, 64, 43), 63, 45, true,
                null, 0, List.of("m_tackle", "m_rock_throw"),
                "6:m_mud_slap", "11:m_rock_slide", "14:m_sandstorm");
        putSpecies("s_wing_viper", "翼毒蛇", ElementType.POISON, ElementType.FLYING,
                new Stats(55, 60, 44, 40, 54, 55), 62, 90, true,
                null, 0, List.of("m_tackle", "m_wing_attack"),
                "7:m_quick", "9:m_poison_powder", "11:m_toxic", "12:m_cross_poison");
    }

    private void registerBuiltinItems() {
        putItem("i_potion", "伤药", ItemCategory.HEAL, 20);
        putItem("i_super_potion", "好伤药", ItemCategory.HEAL, 50);
        putItem("i_poke_ball", "精灵球", ItemCategory.POKE_BALL, 3);
        putItem("i_great_ball", "超级球", ItemCategory.POKE_BALL, 5);
        putItem("i_ultra_ball", "高级球", ItemCategory.POKE_BALL, 8);
        putItem("i_master_ball", "大师球", ItemCategory.POKE_BALL, 255, true);

        // ---- 倍率固定（不依赖对手等级/体重/回合数等条件）的其它球种 ----
        // 纪念球与贵重球在正作中是「基准倍率」的收藏球，这里保持 ×3（与精灵球同效）不作增强。
        putItem("i_premier_ball", "纪念球", ItemCategory.POKE_BALL, 3);
        putItem("i_cherish_ball", "贵重球", ItemCategory.POKE_BALL, 3);
        putItem("i_safari_ball", "狩猎球", ItemCategory.POKE_BALL, 4.5);
        putItem("i_sport_ball", "竞赛球", ItemCategory.POKE_BALL, 4.5);

        // ---- 异常状态解除道具：分别对应单种异常与全部主要异常（解毒药同时解除中毒与剧毒） ----
        putItem("i_antidote", "解毒药", "POISON|BADLY_POISON");
        putItem("i_paralyze_heal", "麻痹药", "PARALYSIS");
        putItem("i_burn_heal", "灼伤药", "BURN");
        putItem("i_ice_heal", "解冻药", "FREEZE");
        putItem("i_awakening", "醒睡药", "SLEEP");
        putItem("i_full_heal", "万灵药", "ALL");
    }

    private void putMove(String id, String name, ElementType type, MoveCategory category,
                         int power, int pp) {
        putMove(id, name, type, category, power, pp, MoveEffect.NONE);
    }

    /** 带效果注册技能（天气/场地变化类技能使用）。 */
    private void putMove(String id, String name, ElementType type, MoveCategory category,
                         int power, int pp, MoveEffect effect) {
        moveMap.put(id, new Move(id, name, type, category, power, 100, pp, effect));
    }

    /**
     * 带异常状态注册技能（命中后按概率使目标陷入异常）。
     *
     * @param accuracy 命中率（-1 表示必中）
     * @param inflicts 命中后可能施加的异常状态
     * @param chance   触发概率百分比（0~100）
     */
    private void putMove(String id, String name, ElementType type, MoveCategory category,
                         int power, int pp, int accuracy, StatusCondition inflicts, int chance) {
        moveMap.put(id, new Move(id, name, type, category, power, accuracy, pp, 0,
                MoveEffect.NONE, inflicts, chance));
    }

    /**
     * 注册一个物种。
     *
     * @param baseExp      种族经验值 baseExp（击倒该族精灵时折算经验的基数，见 {@code GrowthService}）
     * @param evolvesTo    进化目标 id（无则 null）
     * @param evolveLevel  进化等级（0 = 不进化）
     * @param moves        出生即会的技能 id 列表
     * @param learn        按等级习得技能，格式 "等级:技能id"（如 "9:m_flame_wheel"）
     */
    private void putSpecies(String id, String name, ElementType t1, ElementType t2, Stats base,
                            int baseExp, int catchRate, boolean wild, String evolvesTo, int evolveLevel,
                            List<String> moves, String... learn) {
        Map<Integer, String> learnAt = new LinkedHashMap<>();
        for (String spec : learn) {
            String[] pair = spec.split(":", -1);
            if (pair.length == 2) {
                try {
                    learnAt.put(Integer.parseInt(pair[0].trim()), pair[1].trim());
                } catch (NumberFormatException ignored) {
                    // 跳过格式错误的习得项
                }
            }
        }
        speciesMap.put(id, new Species(id, name, t1, t2, base, baseExp, catchRate, moves,
                evolvesTo, evolveLevel, learnAt));
        if (wild) {
            wildPool.add(id);
        }
    }

    private void putItem(String id, String name, ItemCategory category, double effect) {
        itemMap.put(id, new Item(id, name, category, effect));
    }

    private void putItem(String id, String name, ItemCategory category, double effect, boolean alwaysCatch) {
        itemMap.put(id, new Item(id, name, category, effect, alwaysCatch));
    }

    /**
     * 注册解除异常状态的道具。
     *
     * @param curesSpec 可解除的异常状态（{@code ALL} 表示全部主要异常；也可填状态英文名，
     *                  多项用 {@code |}/{@code ,}/空白 分隔，如 {@code POISON|BADLY_POISON}）
     */
    private void putItem(String id, String name, String curesSpec) {
        itemMap.put(id, Item.cureItem(id, name, curesSpec));
    }

    private void registerBuiltinEquipment() {
        putEquipment("e_charcoal", "木炭", HeldItemEffect.DAMAGE_TYPE, "FIRE|1.2", "火属性招式威力提升 20%");
        putEquipment("e_mystic_water", "神秘水滴", HeldItemEffect.DAMAGE_TYPE, "WATER|1.2", "水属性招式威力提升 20%");
        putEquipment("e_magnet", "磁铁", HeldItemEffect.DAMAGE_TYPE, "ELECTRIC|1.2", "电属性招式威力提升 20%");
        putEquipment("e_expert_belt", "达人带", HeldItemEffect.SUPER_EFFECTIVE, "1.2", "效果拔群时伤害提升 20%");
        putEquipment("e_leftovers", "剩饭", HeldItemEffect.END_TURN_HEAL, "0.0625", "每回合末回复最大 HP 的 1/16");
        putEquipment("e_shell_bell", "贝壳之铃", HeldItemEffect.LIFE_STEAL, "0.125", "攻击造成伤害的 1/8 回复自身 HP");
        putEquipment("e_quick_claw", "先制之爪", HeldItemEffect.FIRST_STRIKE, "20", "20% 概率无视速度先手出招");
        putEquipment("e_eviolite", "进化辉石", HeldItemEffect.EVOLITE, "1.5", "未最终进化时防御与特防提升 50%");
        // 批次①携带物：招式威力修正
        putEquipment("e_muscle_band", "力量头带", HeldItemEffect.PHYSICAL_DAMAGE, "1.1", "物理招式伤害提升 10%");
        putEquipment("e_wise_glasses", "博识眼镜", HeldItemEffect.SPECIAL_DAMAGE, "1.1", "特殊招式伤害提升 10%");
        putEquipment("e_life_orb", "生命宝珠", HeldItemEffect.LIFE_ORB, "1.3|0.1",
                "招式伤害提升 30%，每次命中后失去最大 HP 的 1/10");
        // 速度与出手顺序
        putEquipment("e_iron_ball", "黑色铁球", HeldItemEffect.SPEED_MULTIPLIER, "0.5|GROUND",
                "速度降低 50%，且被地面化（不再免疫地面系招式）");
        putEquipment("e_lagging_tail", "后攻之尾", HeldItemEffect.MOVE_LAST, "",
                "同先制度时必定最后出招");
        // 伤害判定修正
        putEquipment("e_ring_target", "标靶", HeldItemEffect.IGNORE_IMMUNITY, "",
                "携带者失去属性免疫，原本无效的招式变为 1 倍");
        putEquipment("e_air_balloon", "气球", HeldItemEffect.GROUND_IMMUNE, "",
                "免疫地面系招式，被地面系招式命中后消耗");
        // 回合末效果
        putEquipment("e_black_sludge", "黑色污泥", HeldItemEffect.POISON_HEAL, "0.0625|0.125",
                "毒属性每回合末回复 1/16 HP，非毒属性每回合末失去 1/8 HP");
        putEquipment("e_flame_orb", "火焰宝珠", HeldItemEffect.END_TURN_STATUS, "BURN",
                "回合结束时变为灼伤状态");
        putEquipment("e_toxic_orb", "剧毒宝珠", HeldItemEffect.END_TURN_STATUS, "BADLY_POISON",
                "回合结束时变为剧毒状态");
        // 天气延长
        putEquipment("e_heat_rock", "炽热岩石", HeldItemEffect.WEATHER_DURATION, "SUNNY|8",
                "携带者开启大晴天时持续 8 回合");
        putEquipment("e_damp_rock", "潮湿岩石", HeldItemEffect.WEATHER_DURATION, "RAIN|8",
                "携带者开启下雨时持续 8 回合");
        putEquipment("e_smooth_rock", "沙沙岩石", HeldItemEffect.WEATHER_DURATION, "SANDSTORM|8",
                "携带者开启沙暴时持续 8 回合");
        putEquipment("e_icy_rock", "冰冷岩石", HeldItemEffect.WEATHER_DURATION, "HAIL|8",
                "携带者开启冰雹时持续 8 回合");
        // 保命
        putEquipment("e_focus_sash", "气势披带", HeldItemEffect.FOCUS_SASH, "",
                "满 HP 时受到致死伤害保留 1 HP，触发后消耗");
        putEquipment("e_focus_band", "气势头带", HeldItemEffect.FOCUS_BAND, "10",
                "HP 归零时 10% 概率保留 1 HP");
        // 讲究系
        putEquipment("e_choice_specs", "讲究眼镜", HeldItemEffect.CHOICE, "SPECIAL|1.5",
                "特殊招式伤害提升 50%，但只能使用第一个招式");
        putEquipment("e_choice_scarf", "讲究围巾", HeldItemEffect.CHOICE, "SPEED|1.5",
                "速度提升 50%，但只能使用第一个招式");
        // 批次②树果：异常治疗（陷入对应异常时立即治愈并消耗）
        putEquipment("b_cheri", "樱子果", HeldItemEffect.CURE_STATUS, "PARALYSIS", "陷入麻痹时立即治愈");
        putEquipment("b_pecha", "桃桃果", HeldItemEffect.CURE_STATUS, "POISON|BADLY_POISON", "陷入中毒时立即治愈");
        putEquipment("b_rawst", "莓莓果", HeldItemEffect.CURE_STATUS, "BURN", "陷入灼伤时立即治愈");
        putEquipment("b_chesto", "零余果", HeldItemEffect.CURE_STATUS, "SLEEP", "陷入睡眠时立即治愈");
        putEquipment("b_aspear", "利木果", HeldItemEffect.CURE_STATUS, "FREEZE", "陷入冰冻时立即治愈");
        putEquipment("b_persim", "柿仔果", HeldItemEffect.CURE_STATUS, "CONFUSION", "陷入混乱时立即治愈");
        putEquipment("b_lum", "木子果", HeldItemEffect.CURE_STATUS, "ALL", "陷入任何异常状态时立即治愈");
        // 批次②树果：HP 回复（HP 低于阈值时回复并消耗）
        putEquipment("b_oran", "橙橙果", HeldItemEffect.HEAL_HP, "0.5|10", "HP 不高于 1/2 时回复 10 HP");
        putEquipment("b_sitrus", "文柚果", HeldItemEffect.HEAL_HP, "0.5|0.25", "HP 不高于 1/2 时回复最大 HP 的 1/4");
        putEquipment("b_figy", "勿花果", HeldItemEffect.HEAL_HP, "0.25|0.125", "HP 不高于 1/4 时回复最大 HP 的 1/8");
        putEquipment("b_wiki", "异奇果", HeldItemEffect.HEAL_HP, "0.25|0.125", "HP 不高于 1/4 时回复最大 HP 的 1/8");
        putEquipment("b_aguav", "乐芭果", HeldItemEffect.HEAL_HP, "0.25|0.125", "HP 不高于 1/4 时回复最大 HP 的 1/8");
        putEquipment("b_iapapa", "芭亚果", HeldItemEffect.HEAL_HP, "0.25|0.125", "HP 不高于 1/4 时回复最大 HP 的 1/8");
        // 批次②树果：PP 回复
        putEquipment("b_leppa", "苹野果", HeldItemEffect.HEAL_PP, "10", "招式 PP 耗尽时回复 10 PP");
        // 批次②树果：属性减伤（受效果拔群攻击时消耗）
        putEquipment("b_occa", "巧可果", HeldItemEffect.RESIST_TYPE, "FIRE|0.5", "受到效果拔群的火属性招式时伤害减半");
        putEquipment("b_passho", "千香果", HeldItemEffect.RESIST_TYPE, "WATER|0.5", "受到效果拔群的水属性招式时伤害减半");
        putEquipment("b_wacan", "烛木果", HeldItemEffect.RESIST_TYPE, "ELECTRIC|0.5", "受到效果拔群的电属性招式时伤害减半");
        putEquipment("b_rindo", "罗子果", HeldItemEffect.RESIST_TYPE, "GRASS|0.5", "受到效果拔群的草属性招式时伤害减半");
        putEquipment("b_yache", "番荔果", HeldItemEffect.RESIST_TYPE, "ICE|0.5", "受到效果拔群的冰属性招式时伤害减半");
        putEquipment("b_chople", "莲蒲果", HeldItemEffect.RESIST_TYPE, "FIGHTING|0.5", "受到效果拔群的格斗属性招式时伤害减半");
        putEquipment("b_kebia", "通通果", HeldItemEffect.RESIST_TYPE, "POISON|0.5", "受到效果拔群的毒属性招式时伤害减半");
        putEquipment("b_shuca", "腰木果", HeldItemEffect.RESIST_TYPE, "GROUND|0.5", "受到效果拔群的地面属性招式时伤害减半");
        putEquipment("b_coba", "扁樱果", HeldItemEffect.RESIST_TYPE, "FLYING|0.5", "受到效果拔群的飞行属性招式时伤害减半");
        putEquipment("b_payapa", "霹霹果", HeldItemEffect.RESIST_TYPE, "PSYCHIC|0.5", "受到效果拔群的超能力属性招式时伤害减半");
        putEquipment("b_tanga", "莓榴果", HeldItemEffect.RESIST_TYPE, "BUG|0.5", "受到效果拔群的虫属性招式时伤害减半");
        putEquipment("b_charti", "草蚕果", HeldItemEffect.RESIST_TYPE, "ROCK|0.5", "受到效果拔群的岩石属性招式时伤害减半");
        putEquipment("b_kasib", "佛柑果", HeldItemEffect.RESIST_TYPE, "GHOST|0.5", "受到效果拔群的幽灵属性招式时伤害减半");
        putEquipment("b_haban", "刺耳果", HeldItemEffect.RESIST_TYPE, "DARK|0.5", "受到效果拔群的恶属性招式时伤害减半");
        putEquipment("b_roseli", "洛玫果", HeldItemEffect.RESIST_TYPE, "FAIRY|0.5", "受到效果拔群的妖精属性招式时伤害减半");
        putEquipment("b_babiri", "灯浆果", HeldItemEffect.RESIST_TYPE, "NORMAL|0.5|ALWAYS",
                "受到一般属性招式时伤害减半（一般属性无克制关系，故不看效果拔群）");
    }

    private void putEquipment(String id, String name, HeldItemEffect effectType, String param, String description) {
        equipmentMap.put(id, new HeldItem(id, name, effectType, param, description));
    }

    // ------------------------------------------------------------------
    // CSV 外部数据覆盖加载
    // ------------------------------------------------------------------

    private void loadCsvOverrides() {
        loadCsv("/data/moves.csv", this::applyMoveRow);
        loadCsv("/data/species.csv", this::applySpeciesRow);
        loadCsv("/data/items.csv", this::applyItemRow);
        loadCsv("/data/equipment.csv", this::applyEquipmentRow);
    }

    private void loadCsv(String resource, Consumer<String> rowConsumer) {
        try (InputStream in = GameData.class.getResourceAsStream(resource)) {
            if (in == null) {
                LogUtil.info("[GameData] 未找到 " + resource + "，使用内建数据");
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                boolean header = true;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (header) {
                        header = false; // 首个非注释行：真正的表头行（首列为 id）才跳过，否则按数据行处理
                        if ("id".equalsIgnoreCase(line.split(",", -1)[0].trim())) {
                            continue;
                        }
                    }
                    rowConsumer.accept(line);
                }
            }
        } catch (IOException e) {
            LogUtil.info("[GameData] 读取 " + resource + " 失败: " + e.getMessage());
        }
    }

    private void applyMoveRow(String line) {
        String[] c = line.split(",", -1);
        if (c.length < 8) {
            return;
        }
        ElementType type = ElementType.parse(c[2]);
        String cat = c[3].trim().toUpperCase(java.util.Locale.ROOT);
        MoveCategory category = switch (cat) {
            case "SPECIAL" -> MoveCategory.SPECIAL;
            case "STATUS" -> MoveCategory.STATUS;
            default -> MoveCategory.PHYSICAL;
        };
        int power = parseInt(c[4]);
        int accuracy = parseInt(c[5]);
        int maxPp = parseInt(c[6]);
        int priority = parseInt(c[7]);
        StatusCondition inflicts = c.length > 8 ? StatusCondition.parse(c[8]) : StatusCondition.NONE;
        int chance = c.length > 9 ? parseInt(c[9]) : 0;
        MoveEffect effect = c.length > 10 ? MoveEffect.parse(c[10]) : MoveEffect.NONE;
        String id = c[0].trim();
        moveMap.put(id, new Move(id, c[1].trim(), type, category, power, accuracy, maxPp, priority,
                effect, inflicts, chance));
    }

    /**
     * 覆盖一个物种。
     *
     * <p><b>已知问题（见《对接文档_战斗模块数据对接.md》§7 R-01）</b>：本方法期望的列序
     * {@code ...,catchRate,wild,evolvesTo,evolveLevel,moves,learns} 与现行 {@code /data/species.csv}
     * 的实际列序 {@code ...,evolutionLevel,evolutionTarget,baseExp,captureRate,category,description}
     * 不一致，故此处取不到种族经验值（baseExp 保持 {@code 0}，经验折算退化为按六维种族值总和）。
     * 游戏主流程的对手与队友都经 {@link org.example.integration.PokemonBattleAdapter#toBattleSpecies}
     * 转换，自带 baseExp；本路径只影响内建注册表被 CSV 覆盖后的数据。</p>
     */
    private void applySpeciesRow(String line) {
        String[] c = line.split(",", -1);
        if (c.length < 16) {
            return;
        }
        // 只解析战斗模块种族格式：c[11] 为野生标志（0/1）。
        // 宝可梦库 species.csv 中该列为进化目标 id（如 charmeleon），跳过以避免产生劣化条目。
        String wildFlag = c[11].trim();
        if (!"0".equals(wildFlag) && !"1".equals(wildFlag)) {
            return;
        }
        ElementType t1 = ElementType.parse(c[2]);
        ElementType t2 = c[3].trim().isEmpty() ? null : ElementType.parse(c[3]);
        Stats base = new Stats(parseInt(c[4]), parseInt(c[5]), parseInt(c[6]),
                parseInt(c[7]), parseInt(c[8]), parseInt(c[9]));
        int catchRate = parseInt(c[10]);
        boolean wild = "1".equals(wildFlag);
        String evolvesTo = c[12].trim().isEmpty() ? null : c[12].trim();
        int evolveLevel = parseInt(c[13]);
        List<String> moves = new ArrayList<>();
        for (String part : c[14].split(";")) {
            String mv = part.trim();
            if (!mv.isEmpty() && moves.size() < 4) {
                moves.add(mv);
            }
        }
        Map<Integer, String> learnAt = new LinkedHashMap<>();
        for (String spec : c[15].split(";")) {
            String[] pair = spec.split(":", -1);
            if (pair.length == 2) {
                try {
                    learnAt.put(Integer.parseInt(pair[0].trim()), pair[1].trim());
                } catch (NumberFormatException ignored) {
                    // 跳过格式错误的习得项
                }
            }
        }
        String id = c[0].trim();
        speciesMap.put(id, new Species(id, c[1].trim(), t1, t2, base, catchRate,
                Collections.unmodifiableList(moves), evolvesTo, evolveLevel, learnAt));
        if (wild && !wildPool.contains(id)) {
            wildPool.add(id);
        }
    }

    private void applyItemRow(String line) {
        String[] c = line.split(",", -1);
        if (c.length < 4) {
            return;
        }
        ItemCategory category = switch (c[2].trim().toUpperCase(java.util.Locale.ROOT)) {
            case "BALL", "POKE_BALL" -> ItemCategory.POKE_BALL;
            case "CURE" -> ItemCategory.CURE;
            default -> ItemCategory.HEAL;
        };
        double effect = parseDouble(c[3]);
        boolean alwaysCatch = c.length > 4 && "1".equals(c[4].trim());
        String curesSpec = c.length > 5 ? c[5].trim() : "";
        String id = c[0].trim();
        itemMap.put(id, new Item(id, c[1].trim(), category, effect, alwaysCatch, curesSpec));
    }

    private void applyEquipmentRow(String line) {
        String[] c = line.split(",", -1);
        if (c.length < 5) {
            return;
        }
        HeldItemEffect effectType;
        try {
            effectType = HeldItemEffect.valueOf(c[2].trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return; // 未知效果类型：跳过该行
        }
        String id = c[0].trim();
        equipmentMap.put(id, new HeldItem(id, c[1].trim(), effectType, c[3].trim(), c[4].trim()));
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
