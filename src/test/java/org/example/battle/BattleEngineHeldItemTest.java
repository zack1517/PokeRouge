package org.example.battle;

import org.example.model.ElementType;
import org.example.model.HeldItem;
import org.example.model.HeldItemEffect;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.StatusCondition;
import org.example.model.Weather;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装备战斗效果测试：验证可携带装备在战斗引擎中的实际效果。
 *
 * <p>伤害类断言依赖<b>同种子随机数序列一致</b>：无装备与有装备两场战斗注入相同
 * {@code new Random(42)}，除装备外配置完全相同，随机伤害浮动（0.85~1.0）因此一致，
 * 伤害比即为装备倍率本身。先制之爪/气势头带用运行时扫描种子保证确定性触发/不触发。</p>
 */
class BattleEngineHeldItemTest {

    /** 高威力普通系物理技能：伤害大，取整误差占比小；对本测试中的防守方不致死（HP 5000）。 */
    private static final Move BIG_HIT = new Move("m_big", "重击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 500, 100, 40);

    /** 强威力普通系物理技能：敌方先手时对玩家造成明显但不致死伤害（给回血/吸血留出空间）。 */
    private static final Move MED_HIT = new Move("m_med", "撞击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 300, 100, 40);

    /** 微威力技能：防守方反击用，不影响测量。 */
    private static final Move WEAK_HIT = new Move("m_weak", "轻拍", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 1, 100, 40);

    private static final Move FIRE_MOVE = new Move("m_fire", "烈焰重拳", ElementType.FIRE,
            MoveCategory.PHYSICAL, 500, 100, 40);
    private static final Move WATER_MOVE = new Move("m_water", "怒涛重击", ElementType.WATER,
            MoveCategory.PHYSICAL, 500, 100, 40);
    private static final Move ELECTRIC_MOVE = new Move("m_elec", "雷霆重击", ElementType.ELECTRIC,
            MoveCategory.PHYSICAL, 500, 100, 40);

    private static final HeldItem CHARCOAL = item("e_charcoal", "木炭", HeldItemEffect.DAMAGE_TYPE, "FIRE|1.2");
    private static final HeldItem MYSTIC_WATER = item("e_mystic_water", "神秘水滴", HeldItemEffect.DAMAGE_TYPE, "WATER|1.2");
    private static final HeldItem MAGNET = item("e_magnet", "磁铁", HeldItemEffect.DAMAGE_TYPE, "ELECTRIC|1.2");
    private static final HeldItem EXPERT_BELT = item("e_expert_belt", "达人带", HeldItemEffect.SUPER_EFFECTIVE, "1.2");
    private static final HeldItem LEFTOVERS = item("e_leftovers", "剩饭", HeldItemEffect.END_TURN_HEAL, "0.0625");
    private static final HeldItem SHELL_BELL = item("e_shell_bell", "贝壳之铃", HeldItemEffect.LIFE_STEAL, "0.125");
    private static final HeldItem QUICK_CLAW = item("e_quick_claw", "先制之爪", HeldItemEffect.FIRST_STRIKE, "20");
    private static final HeldItem EVIOLITE = item("e_eviolite", "进化辉石", HeldItemEffect.EVOLITE, "1.5");

    // ---- 批次①新增装备 ----
    private static final Move BIG_SPECIAL_HIT = new Move("m_big_spec", "巨浪", ElementType.NORMAL,
            MoveCategory.SPECIAL, 500, 100, 40);
    private static final Move GROUND_MOVE = new Move("m_ground", "地震", ElementType.GROUND,
            MoveCategory.PHYSICAL, 500, 100, 40);
    /** 无效果的变化招：用于「不产生任何伤害」的对照回合。 */
    private static final Move IDLE_MOVE = new Move("m_idle", "瞪眼", ElementType.NORMAL,
            MoveCategory.STATUS, 0, 100, 40);
    private static final Move SUNNY_MOVE = new Move("m_sunny", "大晴天", ElementType.FIRE,
            MoveCategory.STATUS, 0, 100, 40, MoveEffect.SUNNY_DAY);

    private static final HeldItem MUSCLE_BAND = item("e_muscle_band", "力量头带", HeldItemEffect.PHYSICAL_DAMAGE, "1.1");
    private static final HeldItem WISE_GLASSES = item("e_wise_glasses", "博识眼镜", HeldItemEffect.SPECIAL_DAMAGE, "1.1");
    private static final HeldItem LIFE_ORB = item("e_life_orb", "生命宝珠", HeldItemEffect.LIFE_ORB, "1.3|0.1");
    private static final HeldItem IRON_BALL = item("e_iron_ball", "黑色铁球", HeldItemEffect.SPEED_MULTIPLIER, "0.5|GROUND");
    private static final HeldItem LAGGING_TAIL = item("e_lagging_tail", "后攻之尾", HeldItemEffect.MOVE_LAST, "");
    private static final HeldItem RING_TARGET = item("e_ring_target", "标靶", HeldItemEffect.IGNORE_IMMUNITY, "");
    private static final HeldItem AIR_BALLOON = item("e_air_balloon", "气球", HeldItemEffect.GROUND_IMMUNE, "");
    private static final HeldItem BLACK_SLUDGE = item("e_black_sludge", "黑色污泥", HeldItemEffect.POISON_HEAL, "0.0625|0.125");
    private static final HeldItem FLAME_ORB = item("e_flame_orb", "火焰宝珠", HeldItemEffect.END_TURN_STATUS, "BURN");
    private static final HeldItem TOXIC_ORB = item("e_toxic_orb", "剧毒宝珠", HeldItemEffect.END_TURN_STATUS, "BADLY_POISON");
    private static final HeldItem HEAT_ROCK = item("e_heat_rock", "炽热岩石", HeldItemEffect.WEATHER_DURATION, "SUNNY|8");
    private static final HeldItem FOCUS_SASH = item("e_focus_sash", "气势披带", HeldItemEffect.FOCUS_SASH, "");
    private static final HeldItem FOCUS_BAND = item("e_focus_band", "气势头带", HeldItemEffect.FOCUS_BAND, "10");
    private static final HeldItem CHOICE_SPECS = item("e_choice_specs", "讲究眼镜", HeldItemEffect.CHOICE, "SPECIAL|1.5");
    private static final HeldItem CHOICE_SCARF = item("e_choice_scarf", "讲究围巾", HeldItemEffect.CHOICE, "SPEED|1.5");

    private static HeldItem item(String id, String name, HeldItemEffect effect, String param) {
        return new HeldItem(id, name, effect, param, name + " 测试描述");
    }

    private static Species species(String id, ElementType type, int hp, int atk, int def,
                                   int spe, String evolvesTo) {
        return new Species(id, id, type, null,
                new Stats(hp, atk, def, atk, def, spe), 100,
                List.of(), evolvesTo, 0, Map.of());
    }

    /**
     * 固定个体值（全 31）。{@code Pokemon.create(species, level, movePool)} 每次都会调用
     * {@code Stats.randomIv()}，导致「有装备」与「无装备」两场战斗的面板（HP/攻/防/速）都不同，
     * 伤害比、回血量、最大 HP 比例全部不可比。本测试所有精灵一律走固定个体值。
     */
    private static final Stats FIXED_IVS = new Stats(31, 31, 31, 31, 31, 31);

    /** 以固定个体值创建满血个体。 */
    private static Pokemon poke(Species species, int level, List<Move> movePool) {
        return Pokemon.create(species, level, movePool, FIXED_IVS);
    }

    /**
     * 打一场并返回防守方承受的伤害：攻击者高速先手、高攻高威技能；防守方低速低攻、血厚不死。
     * 除攻击者装备外两场配置完全一致，且注入相同种子 {@code 42} 保证随机伤害浮动一致。
     */
    private static int damageDealt(Move move, HeldItem attackerItem, HeldItem defenderItem,
                                   ElementType attackerType, ElementType defenderType,
                                   String defenderEvolvesTo) {
        Pokemon attacker = poke(
                species("atk_sp", attackerType, 1000, 100, 100, 200, null), 50, List.of(move));
        if (attackerItem != null) {
            attacker.setHeldItem(attackerItem);
        }
        Pokemon defender = poke(
                species("def_sp", defenderType, 5000, 10, 50, 10, defenderEvolvesTo), 50, List.of(WEAK_HIT));
        if (defenderItem != null) {
            defender.setHeldItem(defenderItem);
        }
        Player player = new Player("玩家");
        player.addPokemon(attacker);
        BattleEngine engine = new BattleEngine(player, defender, new Random(42));
        engine.useMove(attacker.getMoveSlots().get(0));
        return defender.getMaxHp() - defender.getCurrentHp();
    }

    /**
     * 打一个回合并返回引擎：玩家低速（敌方先手打玩家）、血厚不死；敌方高速、血厚。
     * 用于剩饭（回合末结算）与贝壳之铃（受击后吸血）的测试。
     */
    private static BattleEngine runOneRound(HeldItem mineItem) {
        return runOneRound(mineItem, ElementType.NORMAL);
    }

    /** 同上，但可指定玩家属性（用于黑色污泥的毒属性回复判定）。 */
    private static BattleEngine runOneRound(HeldItem mineItem, ElementType mineType) {
        Pokemon mine = poke(
                species("p_sp", mineType, 800, 100, 50, 10, null), 50, List.of(BIG_HIT));
        if (mineItem != null) {
            mine.setHeldItem(mineItem);
        }
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 200, null), 50, List.of(MED_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        return engine;
    }

    // ------------------------------------------------------------------
    // 属性强化装备（木炭/神秘水滴/磁铁）
    // ------------------------------------------------------------------

    @Test
    void 属性强化装备使对应属性招式威力提升两成() {
        assertDamageMultiplier(FIRE_MOVE, CHARCOAL, ElementType.FIRE, 1.2);
        assertDamageMultiplier(WATER_MOVE, MYSTIC_WATER, ElementType.WATER, 1.2);
        assertDamageMultiplier(ELECTRIC_MOVE, MAGNET, ElementType.ELECTRIC, 1.2);
    }

    @Test
    void 属性强化装备对非对应属性招式无效() {
        int without = damageDealt(WATER_MOVE, null, null, ElementType.WATER, ElementType.NORMAL, null);
        int with = damageDealt(WATER_MOVE, CHARCOAL, null, ElementType.WATER, ElementType.NORMAL, null);
        assertEquals(without, with, "木炭不应提升水属性招式伤害");
    }

    private static void assertDamageMultiplier(Move move, HeldItem item,
                                               ElementType attackerType, double expected) {
        int without = damageDealt(move, null, null, attackerType, ElementType.NORMAL, null);
        int with = damageDealt(move, item, null, attackerType, ElementType.NORMAL, null);
        assertEquals(expected, (double) with / without, 0.05,
                item.getName() + " 应使伤害变为 " + expected + " 倍（无装备 " + without + "，有装备 " + with + "）");
    }

    // ------------------------------------------------------------------
    // 达人带
    // ------------------------------------------------------------------

    @Test
    void 达人带仅对效果拔群招式增伤() {
        // 水打火：效果拔群（2.0）→ 达人带增伤 20%
        int normalHit = damageDealt(WATER_MOVE, null, null, ElementType.WATER, ElementType.FIRE, null);
        int beltHit = damageDealt(WATER_MOVE, EXPERT_BELT, null, ElementType.WATER, ElementType.FIRE, null);
        assertEquals(1.2, (double) beltHit / normalHit, 0.05, "克制时达人带应增伤 20%");

        // 水打普通：非克制 → 达人带不生效
        int normalPlain = damageDealt(WATER_MOVE, null, null, ElementType.WATER, ElementType.NORMAL, null);
        int beltPlain = damageDealt(WATER_MOVE, EXPERT_BELT, null, ElementType.WATER, ElementType.NORMAL, null);
        assertEquals(1.0, (double) beltPlain / normalPlain, 0.05, "非克制时达人带不应增伤");
    }

    // ------------------------------------------------------------------
    // 剩饭（回合末回血）
    // ------------------------------------------------------------------

    @Test
    void 剩饭回合末回复最大Hp的十六分之一() {
        BattleEngine without = runOneRound(null);
        BattleEngine with = runOneRound(LEFTOVERS);
        int maxHp = with.playerActive().getMaxHp();
        int hpNo = without.playerActive().getCurrentHp();
        int hpWith = with.playerActive().getCurrentHp();
        assertEquals((int) (maxHp * 0.0625), hpWith - hpNo, "回合末应回复最大 HP 的 1/16（截断）");
        assertTrue(with.getLog().stream().anyMatch(line -> line.contains("剩饭")), "日志应包含剩饭回血");
    }

    // ------------------------------------------------------------------
    // 贝壳之铃（吸血）
    // ------------------------------------------------------------------

    @Test
    void 贝壳之铃按造成伤害的八分之一吸血() {
        BattleEngine without = runOneRound(null);
        BattleEngine with = runOneRound(SHELL_BELL);
        int hpNo = without.playerActive().getCurrentHp();
        int hpWith = with.playerActive().getCurrentHp();
        // 玩家反击 BIG_HIT 对敌方造成的伤害（两场同种子完全一致），吸血量应为其 1/8
        Pokemon foe = without.foeActive();
        int foeDamage = foe.getMaxHp() - foe.getCurrentHp();
        assertTrue(foeDamage > 0, "玩家应确实造成伤害");
        assertEquals((int) (foeDamage * 0.125), hpWith - hpNo, "吸血量应为造成伤害的 1/8（截断）");
        assertTrue(with.getLog().stream().anyMatch(line -> line.contains("贝壳之铃")), "日志应包含吸血记录");
    }

    // ------------------------------------------------------------------
    // 先制之爪
    // ------------------------------------------------------------------

    @Test
    void 先制之爪触发时无视速度抢先出手() {
        long seed = seedWhereClawTriggers();
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(BIG_HIT));
        mine.setHeldItem(QUICK_CLAW);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 300, 100, 50, 200, null), 50, List.of(MED_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(seed));
        engine.useMove(mine.getMoveSlots().get(0));
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("先制之爪抢先行动")),
                "日志应包含先制之爪抢先行动");
        assertEquals(mine.getMaxHp(), mine.getCurrentHp(), "玩家先手一击必杀，敌方没有出手机会");
    }

    @Test
    void 先制之爪未触发时仍按速度判定() {
        long seed = seedWhereClawFails();
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(BIG_HIT));
        mine.setHeldItem(QUICK_CLAW);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 300, 100, 50, 200, null), 50, List.of(MED_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(seed));
        engine.useMove(mine.getMoveSlots().get(0));
        assertTrue(mine.getCurrentHp() < mine.getMaxHp(), "爪未触发时应按速度判定，敌方先手攻击玩家");
        assertFalse(engine.getLog().stream().anyMatch(line -> line.contains("先制之爪抢先行动")),
                "爪未触发时不应有先制之爪日志");
    }

    @Test
    void 双方先制之爪均触发时回退到速度判定() {
        long seed = seedWhereBothClawsTrigger();
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(BIG_HIT));
        mine.setHeldItem(QUICK_CLAW);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 300, 100, 50, 200, null), 50, List.of(MED_HIT));
        foe.setHeldItem(QUICK_CLAW);
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(seed));
        engine.useMove(mine.getMoveSlots().get(0));
        assertTrue(mine.getCurrentHp() < mine.getMaxHp(), "双方均触发时回退速度判定，速度更快的敌方先手");
        assertFalse(engine.getLog().stream().anyMatch(line -> line.contains("先制之爪抢先行动")),
                "双方均触发时不应播报先制之爪");
    }

    /** 运行时扫描种子：使引擎第一个随机调用 {@code nextInt(100)} 落在触发区间 [0,20)。 */
    private static long seedWhereClawTriggers() {
        for (long s = 0; s < 100_000; s++) {
            if (new Random(s).nextInt(100) < 20) {
                return s;
            }
        }
        throw new AssertionError("找不到触发先制之爪的随机种子");
    }

    /** 运行时扫描种子：使引擎第一个随机调用 {@code nextInt(100)} 落在非触发区间 [20,100)。 */
    private static long seedWhereClawFails() {
        for (long s = 0; s < 100_000; s++) {
            if (new Random(s).nextInt(100) >= 20) {
                return s;
            }
        }
        throw new AssertionError("找不到不触发先制之爪的随机种子");
    }

    /** 运行时扫描种子：前两个随机调用 {@code nextInt(100)} 均落在触发区间（双方均触发）。 */
    private static long seedWhereBothClawsTrigger() {
        for (long s = 0; s < 1_000_000; s++) {
            Random probe = new Random(s);
            if (probe.nextInt(100) < 20 && probe.nextInt(100) < 20) {
                return s;
            }
        }
        throw new AssertionError("找不到双方均触发先制之爪的随机种子");
    }

    // ------------------------------------------------------------------
    // 进化辉石
    // ------------------------------------------------------------------

    @Test
    void 进化辉石为未最终进化携带者减伤() {
        int without = damageDealt(BIG_HIT, null, null, ElementType.NORMAL, ElementType.NORMAL, "sp_evo");
        int with = damageDealt(BIG_HIT, null, EVIOLITE, ElementType.NORMAL, ElementType.NORMAL, "sp_evo");
        assertTrue(with < without, "携带进化辉石后承受伤害应降低");
        assertEquals(1.5, (double) without / with, 0.1, "双防提升 50%，承受伤害应约为无辉石的 2/3");
    }

    @Test
    void 进化辉石对最终进化不生效() {
        int without = damageDealt(BIG_HIT, null, null, ElementType.NORMAL, ElementType.NORMAL, null);
        int with = damageDealt(BIG_HIT, null, EVIOLITE, ElementType.NORMAL, ElementType.NORMAL, null);
        assertEquals(without, with, "最终进化（无进化目标）携带辉石不应减伤");
    }

    // ------------------------------------------------------------------
    // 批次①：力量头带 / 博识眼镜（按招式类别增伤）
    // ------------------------------------------------------------------

    @Test
    void 力量头带提升物理招式伤害一成() {
        assertDamageMultiplier(BIG_HIT, MUSCLE_BAND, ElementType.NORMAL, 1.1);
    }

    @Test
    void 博识眼镜提升特殊招式伤害一成() {
        assertDamageMultiplier(BIG_SPECIAL_HIT, WISE_GLASSES, ElementType.NORMAL, 1.1);
    }

    @Test
    void 力量头带与博识眼镜对另一类招式无效() {
        int physical = damageDealt(BIG_HIT, null, null, ElementType.NORMAL, ElementType.NORMAL, null);
        int physicalWithGlasses = damageDealt(BIG_HIT, WISE_GLASSES, null,
                ElementType.NORMAL, ElementType.NORMAL, null);
        assertEquals(physical, physicalWithGlasses, "博识眼镜不应提升物理招式伤害");

        int special = damageDealt(BIG_SPECIAL_HIT, null, null, ElementType.NORMAL, ElementType.NORMAL, null);
        int specialWithBand = damageDealt(BIG_SPECIAL_HIT, MUSCLE_BAND, null,
                ElementType.NORMAL, ElementType.NORMAL, null);
        assertEquals(special, specialWithBand, "力量头带不应提升特殊招式伤害");
    }

    // ------------------------------------------------------------------
    // 批次①：生命宝珠（增伤 + 反伤）
    // ------------------------------------------------------------------

    @Test
    void 生命宝珠提升三成伤害() {
        assertDamageMultiplier(BIG_HIT, LIFE_ORB, ElementType.NORMAL, 1.3);
    }

    @Test
    void 生命宝珠造成伤害后反噬一成最大Hp() {
        BattleEngine without = runOneRound(null);
        BattleEngine with = runOneRound(LIFE_ORB);
        int maxHp = with.playerActive().getMaxHp();
        int lost = without.playerActive().getCurrentHp() - with.playerActive().getCurrentHp();
        assertEquals((int) (maxHp * 0.1), lost, "生命宝珠反伤应为最大 HP 的 1/10（含敌方已造成的伤害）");
        assertTrue(with.getLog().stream().anyMatch(line -> line.contains("生命宝珠")),
                "日志应包含生命宝珠反伤");
    }

    // ------------------------------------------------------------------
    // 批次①：讲究眼镜 / 讲究围巾（增伤加速 + 招式锁定）
    // ------------------------------------------------------------------

    @Test
    void 讲究眼镜提升五成特殊招式伤害() {
        assertDamageMultiplier(BIG_SPECIAL_HIT, CHOICE_SPECS, ElementType.NORMAL, 1.5);
    }

    @Test
    void 讲究眼镜锁定首个使用过的招式() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 200, null), 50,
                List.of(BIG_SPECIAL_HIT, BIG_HIT));
        mine.setHeldItem(CHOICE_SPECS);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(WEAK_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));

        List<String> first = engine.useMove(mine.getMoveSlots().get(0));
        assertTrue(first.stream().anyMatch(line -> line.contains("巨浪")), "首回合应能使用特殊招式");
        int foeHpAfterFirst = foe.getCurrentHp();

        List<String> second = engine.useMove(mine.getMoveSlots().get(1));
        assertEquals(1, second.size(), "锁定后改选其它招式只应给出提示，不消耗回合");
        assertTrue(second.get(0).contains("讲究眼镜") && second.get(0).contains("只能使用"),
                "应播报讲究眼镜的招式锁定提示，实际：" + second);
        assertEquals(foeHpAfterFirst, foe.getCurrentHp(), "被锁定的回合敌方不应受到伤害");
    }

    @Test
    void 讲究围巾提升五成实际速度() {
        Pokemon pokemon = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 100, null), 50, List.of(BIG_HIT));
        int base = pokemon.effectiveSpeed();
        pokemon.setHeldItem(CHOICE_SCARF);
        assertEquals((int) Math.round(base * 1.5), pokemon.effectiveSpeed(), "讲究围巾应使实际速度 ×1.5");
    }

    // ------------------------------------------------------------------
    // 批次①：黑色铁球（减速 + 地面化）
    // ------------------------------------------------------------------

    @Test
    void 黑色铁球使实际速度减半() {
        Pokemon pokemon = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 100, null), 50, List.of(BIG_HIT));
        int base = pokemon.effectiveSpeed();
        pokemon.setHeldItem(IRON_BALL);
        assertEquals(Math.max(1, (int) Math.round(base * 0.5)), pokemon.effectiveSpeed(),
                "黑色铁球应使实际速度 ×0.5");
    }

    @Test
    void 黑色铁球使飞行系失去地面招式免疫() {
        assertEquals(0, damageDealt(GROUND_MOVE, null, null,
                ElementType.GROUND, ElementType.FLYING, null), "飞行系应对地面系招式免疫");
        int grounded = damageDealt(GROUND_MOVE, null, IRON_BALL,
                ElementType.GROUND, ElementType.FLYING, null);
        assertTrue(grounded > 0, "携带黑色铁球后应能受到地面系招式伤害");
    }

    // ------------------------------------------------------------------
    // 批次①：后攻之尾（最后出手）
    // ------------------------------------------------------------------

    @Test
    void 后攻之尾使速度更快的携带者最后出手() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 2000, 100, 50, 200, null), 50, List.of(BIG_HIT));
        mine.setHeldItem(LAGGING_TAIL);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(MED_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("后攻之尾")),
                "日志应包含后攻之尾提示");
        assertTrue(mine.getCurrentHp() < mine.getMaxHp(), "速度更慢的敌方应先出手攻击玩家");
    }

    // ------------------------------------------------------------------
    // 批次①：标靶（失去属性免疫）
    // ------------------------------------------------------------------

    @Test
    void 标靶使携带者失去属性免疫() {
        assertEquals(0, damageDealt(BIG_HIT, null, null,
                        ElementType.NORMAL, ElementType.GHOST, null),
                "一般系招式对幽灵系应无效");
        int normalHit = damageDealt(BIG_HIT, null, RING_TARGET,
                ElementType.NORMAL, ElementType.GHOST, null);
        assertTrue(normalHit > 0, "携带标靶后一般系招式应能命中幽灵系");

        int groundHit = damageDealt(GROUND_MOVE, null, RING_TARGET,
                ElementType.GROUND, ElementType.FLYING, null);
        assertTrue(groundHit > 0, "携带标靶后地面系招式应能命中飞行系");
    }

    // ------------------------------------------------------------------
    // 批次①：气球（免疫地面系招式并被消耗）
    // ------------------------------------------------------------------

    @Test
    void 气球免疫地面系招式并被消耗() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 200, null), 50, List.of(WEAK_HIT));
        mine.setHeldItem(AIR_BALLOON);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(GROUND_MOVE));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        assertEquals(mine.getMaxHp(), mine.getCurrentHp(), "气球应完全挡下地面系招式");
        assertNull(mine.getHeldItem(), "气球命中一次后应被消耗");
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("气球")),
                "日志应包含气球挡招提示");
    }

    // ------------------------------------------------------------------
    // 批次①：气势披带 / 气势头带（保命）
    // ------------------------------------------------------------------

    @Test
    void 气势披带在满Hp受致死伤害时保留1Hp并被消耗() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(WEAK_HIT));
        mine.setHeldItem(FOCUS_SASH);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 300, 50, 200, null), 50, List.of(BIG_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        assertEquals(1, mine.getCurrentHp(), "气势披带应让满 HP 的携带者保留 1 HP");
        assertNull(mine.getHeldItem(), "气势披带触发后应被消耗");
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("气势披带")),
                "日志应包含气势披带撑住提示");
    }

    @Test
    void 气势披带在非满Hp时不触发() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(WEAK_HIT));
        mine.setHeldItem(FOCUS_SASH);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 300, 50, 200, null), 50, List.of(MED_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        // 首回合只被打掉一部分 HP（未致死，不触发）
        engine.useMove(mine.getMoveSlots().get(0));
        assertTrue(mine.getCurrentHp() > 1 && mine.getCurrentHp() < mine.getMaxHp(), "首回合应只是掉血");
        assertNotNull(mine.getHeldItem(), "未致死时气势披带不应被消耗");
        // 第二回合被打成 0（此时非满 HP，披带已失效）
        engine.useMove(mine.getMoveSlots().get(0));
        assertTrue(mine.isFainted(), "非满 HP 时气势披带不应保留 1 HP");
    }

    @Test
    void 气势头带按一成概率保留1Hp() {
        int triggered = 0;
        int notTriggered = 0;
        for (long seed = 0; seed < 400; seed++) {
            Pokemon mine = poke(
                    species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(WEAK_HIT));
            mine.setHeldItem(FOCUS_BAND);
            Pokemon foe = poke(
                    species("f_sp", ElementType.NORMAL, 5000, 300, 50, 200, null), 50, List.of(BIG_HIT));
            Player player = new Player("玩家");
            player.addPokemon(mine);
            BattleEngine engine = new BattleEngine(player, foe, new Random(seed));
            engine.useMove(mine.getMoveSlots().get(0));
            boolean bandMessage = engine.getLog().stream().anyMatch(line -> line.contains("气势头带"));
            if (mine.isFainted()) {
                notTriggered++;
                assertFalse(bandMessage, "未触发时不应播报气势头带（种子 " + seed + "）");
            } else {
                triggered++;
                assertEquals(1, mine.getCurrentHp(), "触发时气势头带应保留 1 HP（种子 " + seed + "）");
                assertTrue(bandMessage, "触发时应播报气势头带（种子 " + seed + "）");
            }
        }
        assertTrue(triggered > 0, "10% 概率应在 400 个种子中至少触发一次");
        assertTrue(notTriggered > 0, "10% 概率应在 400 个种子中至少有一次不触发");
    }

    // ------------------------------------------------------------------
    // 批次①：黑色污泥（毒属性回复 / 非毒属性扣血）
    // ------------------------------------------------------------------

    @Test
    void 黑色污泥让毒属性携带者回合末回血() {
        BattleEngine without = runOneRound(null, ElementType.POISON);
        BattleEngine with = runOneRound(BLACK_SLUDGE, ElementType.POISON);
        int maxHp = with.playerActive().getMaxHp();
        int healed = with.playerActive().getCurrentHp() - without.playerActive().getCurrentHp();
        assertEquals((int) (maxHp * 0.0625), healed, "毒属性携带者应回复最大 HP 的 1/16");
        assertTrue(with.getLog().stream().anyMatch(line -> line.contains("黑色污泥")),
                "日志应包含黑色污泥回血");
    }

    @Test
    void 黑色污泥让非毒属性携带者回合末扣血() {
        BattleEngine without = runOneRound(null, ElementType.NORMAL);
        BattleEngine with = runOneRound(BLACK_SLUDGE, ElementType.NORMAL);
        int maxHp = with.playerActive().getMaxHp();
        int lost = without.playerActive().getCurrentHp() - with.playerActive().getCurrentHp();
        assertEquals((int) (maxHp * 0.125), lost, "非毒属性携带者应扣除最大 HP 的 1/8");
    }

    // ------------------------------------------------------------------
    // 批次①：火焰宝珠 / 剧毒宝珠（回合末陷入异常状态）
    // ------------------------------------------------------------------

    @Test
    void 火焰宝珠在回合末使携带者陷入灼伤且当回合不扣血() {
        BattleEngine without = runOneRound(null);
        BattleEngine with = runOneRound(FLAME_ORB);
        assertEquals(StatusCondition.BURN, with.playerActive().getStatus(), "火焰宝珠应使携带者陷入灼伤");
        assertEquals(without.playerActive().getCurrentHp(), with.playerActive().getCurrentHp(),
                "宝珠在异常状态结算之后生效，陷入灼伤的当回合不应额外扣血");
        assertTrue(with.getLog().stream().anyMatch(line -> line.contains("火焰宝珠")),
                "日志应包含火焰宝珠触发提示");
    }

    @Test
    void 剧毒宝珠在回合末使携带者陷入剧毒() {
        BattleEngine with = runOneRound(TOXIC_ORB);
        assertEquals(StatusCondition.BADLY_POISON, with.playerActive().getStatus(),
                "剧毒宝珠应使携带者陷入剧毒");
    }

    // ------------------------------------------------------------------
    // 批次①：天气岩石（延长天气）
    // ------------------------------------------------------------------

    @Test
    void 炽热岩石将晴天延长至八回合() {
        assertEquals(Weather.NONE, weatherAfterSevenRounds(null), "无岩石时晴天应在 5 回合后结束");
        assertEquals(Weather.SUNNY, weatherAfterSevenRounds(HEAT_ROCK), "炽热岩石应把晴天延长到 8 回合");
    }

    @Test
    void 炽热岩石对非对应天气无效() {
        assertEquals(Weather.NONE, weatherAfterSevenRounds(item("e_damp_rock_x", "潮湿岩石",
                HeldItemEffect.WEATHER_DURATION, "RAIN|8")), "潮湿岩石不应延长晴天");
    }

    /** 开启晴天后连打 7 个回合（首回合开天气，其余空转），返回收尾时的天气。 */
    private static Weather weatherAfterSevenRounds(HeldItem rock) {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 5000, 100, 50, 200, null), 50,
                List.of(SUNNY_MOVE, IDLE_MOVE));
        if (rock != null) {
            mine.setHeldItem(rock);
        }
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(WEAK_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        for (int round = 0; round < 6; round++) {
            engine.useMove(mine.getMoveSlots().get(1));
        }
        return engine.getWeather();
    }
}
