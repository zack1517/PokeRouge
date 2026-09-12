package org.example.battle;

import org.example.model.ElementType;
import org.example.model.HeldItem;
import org.example.model.HeldItemEffect;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.MoveFlag;
import org.example.model.MoveSlot;
import org.example.model.MoveStatChange;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.StatModifier;
import org.example.model.Stats;
import org.example.model.StatusCondition;
import org.example.model.Weather;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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

    // ------------------------------------------------------------------
    // 批次②：树果（异常治疗 / HP 回复 / PP 回复 / 属性减伤）
    // ------------------------------------------------------------------

    /** 必定施加中毒的物理招：用于异常治疗树果测试。 */
    private static final Move POISON_MOVE = new Move("m_bpoison", "毒针", ElementType.POISON,
            MoveCategory.PHYSICAL, 1, 100, 40, 0, MoveEffect.NONE, StatusCondition.POISON, 100);
    /** 必定施加灼伤的变化招：用于异常治疗树果的「参数不匹配」用例。 */
    private static final Move BURN_MOVE = new Move("m_bburn", "鬼火", ElementType.FIRE,
            MoveCategory.STATUS, 0, 100, 40, 0, MoveEffect.NONE, StatusCondition.BURN, 100);
    /** 必定施加混乱的变化招：用于木子果。 */
    private static final Move CONFUSE_MOVE = new Move("m_bconfuse", "奇异光线", ElementType.NORMAL,
            MoveCategory.STATUS, 0, 100, 40, 0, MoveEffect.NONE, StatusCondition.CONFUSION, 100);
    /** PP 上限 20 的微威力招：用于苹野果「回补 10 PP」的精确断言。 */
    private static final Move DRAINABLE_MOVE = new Move("m_bdrain", "练手", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 1, 100, 20);

    private static final HeldItem PECHA = item("b_pecha", "桃桃果", HeldItemEffect.CURE_STATUS, "POISON|BADLY_POISON");
    private static final HeldItem LUM = item("b_lum", "木子果", HeldItemEffect.CURE_STATUS, "ALL");
    private static final HeldItem ORAN = item("b_oran", "橙橙果", HeldItemEffect.HEAL_HP, "0.5|10");
    private static final HeldItem SITRUS = item("b_sitrus", "文柚果", HeldItemEffect.HEAL_HP, "0.5|0.25");
    private static final HeldItem FIGY = item("b_figy", "勿花果", HeldItemEffect.HEAL_HP, "0.25|0.125");
    private static final HeldItem LEPPA = item("b_leppa", "苹野果", HeldItemEffect.HEAL_PP, "10");
    private static final HeldItem OCCA = item("b_occa", "巧可果", HeldItemEffect.RESIST_TYPE, "FIRE|0.5");
    private static final HeldItem BABIRI = item("b_babiri", "灯浆果", HeldItemEffect.RESIST_TYPE, "NORMAL|0.5|ALWAYS");

    /**
     * 打一场并返回防守方：攻击方高速先手、用必定生效的变化招施加异常；防守方血厚不死、按需携带树果。
     * 两场配置除防守方装备外完全一致，且注入相同种子。
     */
    private static Pokemon statusInflictedDefender(Move inflictingMove, HeldItem defenderItem) {
        Pokemon attacker = poke(species("atk_sp", ElementType.NORMAL, 1000, 100, 100, 200, null), 50,
                List.of(inflictingMove));
        Pokemon defender = poke(species("def_sp", ElementType.NORMAL, 5000, 10, 50, 10, null), 50,
                List.of(WEAK_HIT));
        if (defenderItem != null) {
            defender.setHeldItem(defenderItem);
        }
        Player player = new Player("玩家");
        player.addPokemon(attacker);
        BattleEngine engine = new BattleEngine(player, defender, new Random(42));
        engine.useMove(attacker.getMoveSlots().get(0));
        return defender;
    }

    /**
     * 打一场并返回防守方（野怪）：攻击方高速先手、用指定属性招式攻击指定属性的防守方，
     * 防守方按需携带树果。用于属性减伤树果的「是否被消耗」断言。
     */
    private static Pokemon attackedDefender(Move move, HeldItem defenderItem,
                                            ElementType moveType, ElementType defenderType) {
        Pokemon attacker = poke(species("atk_sp", moveType, 1000, 100, 100, 200, null), 50, List.of(move));
        Pokemon defender = poke(species("def_sp", defenderType, 5000, 10, 50, 10, null), 50,
                List.of(WEAK_HIT));
        if (defenderItem != null) {
            defender.setHeldItem(defenderItem);
        }
        Player player = new Player("玩家");
        player.addPokemon(attacker);
        BattleEngine engine = new BattleEngine(player, defender, new Random(42));
        engine.useMove(attacker.getMoveSlots().get(0));
        return defender;
    }

    /**
     * 把玩家精灵预先打到指定 HP 后打一个回合，返回引擎。用于 HP 回复树果测试。
     *
     * @param mineItem 玩家携带的树果（可为 {@code null}）
     * @param mineHp   玩家进入本回合时的 HP；{@code <= 0} 表示不预先扣血（保持满 HP）
     * @param foeMove  敌方招式，决定玩家本回合是否会受击（{@link #WEAK_HIT} 会受击、{@link #IDLE_MOVE} 不会）
     */
    private static BattleEngine runOneRoundAtHp(HeldItem mineItem, int mineHp, Move foeMove) {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(BIG_HIT));
        if (mineItem != null) {
            mine.setHeldItem(mineItem);
        }
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 200, null), 50, List.of(foeMove));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        if (mineHp > 0) {
            mine.takeDamage(mine.getCurrentHp() - mineHp);
        }
        engine.useMove(mine.getMoveSlots().get(0));
        return engine;
    }

    @Test
    void 异常治疗树果治愈携带者刚陷入的异常并消耗() {
        assertEquals(StatusCondition.POISON, statusInflictedDefender(POISON_MOVE, null).getStatus(),
                "前置条件：无树果时应正常中毒");
        Pokemon cured = statusInflictedDefender(POISON_MOVE, PECHA);
        assertEquals(StatusCondition.NONE, cured.getStatus(), "桃桃果应治愈刚陷入的中毒");
        assertNull(cured.getHeldItem(), "触发后应消耗树果");
    }

    @Test
    void 异常治疗树果只对参数所列异常生效() {
        Pokemon untouched = statusInflictedDefender(BURN_MOVE, PECHA);
        assertEquals(StatusCondition.BURN, untouched.getStatus(), "桃桃果不治灼伤");
        assertEquals(PECHA, untouched.getHeldItem(), "未触发不应消耗树果");
    }

    @Test
    void 木子果治愈全部异常含混乱() {
        assertEquals(StatusCondition.NONE, statusInflictedDefender(BURN_MOVE, LUM).getStatus(),
                "木子果应治愈灼伤");
        assertEquals(StatusCondition.NONE, statusInflictedDefender(POISON_MOVE, LUM).getStatus(),
                "木子果应治愈中毒");
        Pokemon confused = statusInflictedDefender(CONFUSE_MOVE, LUM);
        assertFalse(confused.isConfused(), "木子果应治愈混乱");
        assertNull(confused.getHeldItem(), "触发后应消耗树果");
    }

    @Test
    void HP回复树果在受击低于阈值时立即回复() {
        int low = 100;
        BattleEngine without = runOneRoundAtHp(null, low, WEAK_HIT);
        BattleEngine with = runOneRoundAtHp(ORAN, low, WEAK_HIT);
        assertEquals(10, with.playerActive().getCurrentHp() - without.playerActive().getCurrentHp(),
                "橙橙果应在受击后立即回复 10 HP");
        assertNull(with.playerActive().getHeldItem(), "触发后应消耗树果");
        assertTrue(with.getLog().stream().anyMatch(line -> line.contains("橙橙果")),
                "日志应包含橙橙果触发提示");
    }

    @Test
    void HP回复树果在回合末结算() {
        int low = 100;
        BattleEngine without = runOneRoundAtHp(null, low, IDLE_MOVE);
        BattleEngine with = runOneRoundAtHp(ORAN, low, IDLE_MOVE);
        assertEquals(10, with.playerActive().getCurrentHp() - without.playerActive().getCurrentHp(),
                "敌方未造成伤害时橙橙果应在回合末结算");
    }

    @Test
    void HP回复树果在满HP时不触发() {
        BattleEngine engine = runOneRoundAtHp(ORAN, 0, IDLE_MOVE);
        assertEquals(ORAN, engine.playerActive().getHeldItem(), "满 HP 时不应消耗树果");
        assertEquals(engine.playerActive().getMaxHp(), engine.playerActive().getCurrentHp(),
                "满 HP 未受损");
    }

    @Test
    void 文柚果回复最大HP的四分之一() {
        BattleEngine without = runOneRoundAtHp(null, 100, IDLE_MOVE);
        BattleEngine with = runOneRoundAtHp(SITRUS, 100, IDLE_MOVE);
        int maxHp = with.playerActive().getMaxHp();
        assertEquals((int) (maxHp * 0.25),
                with.playerActive().getCurrentHp() - without.playerActive().getCurrentHp(),
                "文柚果应回复最大 HP 的 1/4");
    }

    @Test
    void 危果树果仅在HP低于四分之一时触发() {
        BattleEngine without = runOneRoundAtHp(null, 100, IDLE_MOVE);
        BattleEngine with = runOneRoundAtHp(FIGY, 100, IDLE_MOVE);
        int maxHp = with.playerActive().getMaxHp();
        assertEquals((int) (maxHp * 0.125),
                with.playerActive().getCurrentHp() - without.playerActive().getCurrentHp(),
                "勿花果应回复最大 HP 的 1/8");
        BattleEngine above = runOneRoundAtHp(FIGY, maxHp / 2, IDLE_MOVE);
        assertEquals(FIGY, above.playerActive().getHeldItem(), "HP 高于 1/4 时勿花果不应触发");
    }

    @Test
    void 苹野果在招式PP耗尽时回补PP并消耗() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(DRAINABLE_MOVE));
        MoveSlot slot = mine.getMoveSlots().get(0);
        for (int i = 0; i < DRAINABLE_MOVE.getMaxPp(); i++) {
            slot.usePp();
        }
        assertTrue(slot.exhausted(), "前置条件：招式 PP 已归零");
        mine.setHeldItem(LEPPA);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 200, null), 50, List.of(WEAK_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(slot);
        assertEquals(9, slot.getCurrentPp(), "补 10 PP 后消耗 1 PP，应剩 9");
        assertNull(mine.getHeldItem(), "触发后应消耗树果");
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("苹野果")),
                "日志应包含苹野果触发提示");
    }

    @Test
    void 苹野果在PP未耗尽时不触发() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(DRAINABLE_MOVE));
        MoveSlot slot = mine.getMoveSlots().get(0);
        mine.setHeldItem(LEPPA);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 200, null), 50, List.of(WEAK_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(slot);
        assertEquals(DRAINABLE_MOVE.getMaxPp() - 1, slot.getCurrentPp(), "正常消耗 1 PP");
        assertEquals(LEPPA, mine.getHeldItem(), "PP 未耗尽时不应消耗树果");
    }

    @Test
    void 属性减伤树果使效果拔群招式伤害减半并消耗() {
        int without = damageDealt(FIRE_MOVE, null, null, ElementType.FIRE, ElementType.GRASS, null);
        int with = damageDealt(FIRE_MOVE, null, OCCA, ElementType.FIRE, ElementType.GRASS, null);
        assertEquals(0.5, (double) with / without, 0.05, "巧可果应把效果拔群的火系招式伤害减半");
        assertNull(attackedDefender(FIRE_MOVE, OCCA, ElementType.FIRE, ElementType.GRASS).getHeldItem(),
                "触发后应消耗树果");
    }

    @Test
    void 属性减伤树果属性不匹配时不触发() {
        int without = damageDealt(WATER_MOVE, null, null, ElementType.WATER, ElementType.GRASS, null);
        int with = damageDealt(WATER_MOVE, null, OCCA, ElementType.WATER, ElementType.GRASS, null);
        assertEquals(without, with, "水属性招式不应触发巧可果");
        assertEquals(OCCA, attackedDefender(WATER_MOVE, OCCA, ElementType.WATER, ElementType.GRASS).getHeldItem(),
                "未触发不应消耗树果");
    }

    @Test
    void 属性减伤树果对非效果拔群招式不触发() {
        // 火打水：效果不理想（0.5），故属性匹配但不应触发
        assertEquals(OCCA, attackedDefender(FIRE_MOVE, OCCA, ElementType.FIRE, ElementType.WATER).getHeldItem(),
                "非效果拔群时巧可果不应触发");
    }

    @Test
    void 灯浆果对一般属性招式无条件减伤() {
        int without = damageDealt(BIG_HIT, null, null, ElementType.NORMAL, ElementType.NORMAL, null);
        int with = damageDealt(BIG_HIT, null, BABIRI, ElementType.NORMAL, ElementType.NORMAL, null);
        assertEquals(0.5, (double) with / without, 0.05, "灯浆果减伤不要求效果拔群");
    }

    // ------------------------------------------------------------------
    // 批次④：招式标记（接触 / 拳 / 粉末）与装备
    // ------------------------------------------------------------------

    /** 接触类物理招（无拳标记）：凸凸头盔反伤与拳击手套的对照组。 */
    private static final Move CONTACT_HIT = new Move("m_contact", "猛撞", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 500, 100, 40, 0, MoveEffect.NONE,
            StatusCondition.NONE, 0, Set.of(MoveFlag.CONTACT));

    /** 拳类物理招（拳类招式同时具备接触标记）：拳击手套增伤、免疫凸凸头盔反伤。 */
    private static final Move PUNCH_HIT = new Move("m_punch", "雷电拳", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 500, 100, 40, 0, MoveEffect.NONE,
            StatusCondition.NONE, 0, Set.of(MoveFlag.CONTACT, MoveFlag.PUNCH));

    /** 粉末类变化招（必定中毒）：防尘护目镜免疫对象。 */
    private static final Move POWDER_MOVE = new Move("m_powder", "毒粉", ElementType.POISON,
            MoveCategory.STATUS, 0, 100, 40, 0, MoveEffect.NONE,
            StatusCondition.POISON, 100, Set.of(MoveFlag.POWDER));

    /** 非粉末的变化招（必定中毒）：防尘护目镜的对照组，验证只免疫粉末类。 */
    private static final Move POISON_GAS_MOVE = new Move("m_gas", "毒瓦斯", ElementType.POISON,
            MoveCategory.STATUS, 0, 100, 40, 0, MoveEffect.NONE, StatusCondition.POISON, 100);

    /** 开启沙暴的变化招：防尘护目镜的回合末免伤用例。 */
    private static final Move SANDSTORM_MOVE = new Move("m_sand", "沙暴", ElementType.ROCK,
            MoveCategory.STATUS, 0, 100, 40, MoveEffect.SANDSTORM);

    /** 与 {@link #BIG_HIT} 数值相同但 id 不同的招式：节拍器「换招归零」的对照招。 */
    private static final Move BIG_HIT_ALT = new Move("m_big_alt", "重击·改", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 500, 100, 40);

    private static final HeldItem PUNCH_GLOVE = item("e_punch_glove", "拳击手套", HeldItemEffect.PUNCH_BOOST, "1.1");
    private static final HeldItem ROCKY_HELMET = item("e_rocky_helmet", "凸凸头盔", HeldItemEffect.CONTACT_PUNISH, "0.1667");
    private static final HeldItem SAFETY_GOGGLES = item("e_safety_goggles", "防尘护目镜", HeldItemEffect.POWDER_IMMUNE, "");
    private static final HeldItem RAZOR_CLAW = item("e_razor_claw", "锐利之爪", HeldItemEffect.CRIT_BOOST, "1");
    private static final HeldItem KINGS_ROCK = item("e_kings_rock", "王者之证", HeldItemEffect.FLINCH_CHANCE, "10");
    private static final HeldItem METRONOME_ITEM = item("e_metronome", "节拍器", HeldItemEffect.CONSECUTIVE_BOOST, "0.2");

    /**
     * 玩家（高速、血厚）用指定招式打一个回合，返回引擎：对手血厚不死、按 {@code foeMove} 行动。
     * 用于凸凸头盔反伤、王者之证畏缩这类需要观测双方状态的用例；除装备与种子外配置完全一致。
     */
    private static BattleEngine engineAfterPlayerAttack(Move move, HeldItem attackerItem,
                                                        HeldItem defenderItem, Move foeMove, long seed) {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 1000, 100, 50, 200, null), 50, List.of(move));
        if (attackerItem != null) {
            mine.setHeldItem(attackerItem);
        }
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(foeMove));
        if (defenderItem != null) {
            foe.setHeldItem(defenderItem);
        }
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(seed));
        engine.useMove(mine.getMoveSlots().get(0));
        return engine;
    }

    /** 同 {@link #damageDealt}，但可指定随机种子（会心/畏缩等需要扫描种子的确定性用例）。 */
    private static int damageDealtWithSeed(Move move, HeldItem attackerItem, long seed) {
        Pokemon attacker = poke(
                species("atk_sp", ElementType.NORMAL, 1000, 100, 100, 200, null), 50, List.of(move));
        if (attackerItem != null) {
            attacker.setHeldItem(attackerItem);
        }
        Pokemon defender = poke(
                species("def_sp", ElementType.NORMAL, 5000, 10, 50, 10, null), 50, List.of(WEAK_HIT));
        Player player = new Player("玩家");
        player.addPokemon(attacker);
        BattleEngine engine = new BattleEngine(player, defender, new Random(seed));
        engine.useMove(attacker.getMoveSlots().get(0));
        return defender.getMaxHp() - defender.getCurrentHp();
    }

    @Test
    void 拳击手套提升拳类招式伤害一成() {
        assertDamageMultiplier(PUNCH_HIT, PUNCH_GLOVE, ElementType.NORMAL, 1.1);
    }

    @Test
    void 拳击手套对非拳类招式无效() {
        int without = damageDealt(CONTACT_HIT, null, null, ElementType.NORMAL, ElementType.NORMAL, null);
        int with = damageDealt(CONTACT_HIT, PUNCH_GLOVE, null, ElementType.NORMAL, ElementType.NORMAL, null);
        assertEquals(without, with, "接触但非拳类的招式不应被拳击手套增伤");
    }

    @Test
    void 凸凸头盔按最大Hp六分之一反伤接触类招式使用者() {
        BattleEngine without = engineAfterPlayerAttack(CONTACT_HIT, null, null, IDLE_MOVE, 42);
        BattleEngine with = engineAfterPlayerAttack(CONTACT_HIT, null, ROCKY_HELMET, IDLE_MOVE, 42);
        int maxHp = with.playerActive().getMaxHp();
        int lost = without.playerActive().getCurrentHp() - with.playerActive().getCurrentHp();
        assertEquals((int) (maxHp * 0.1667), lost, "反伤应为攻击方最大 HP 的 1/6");
        assertEquals(ROCKY_HELMET, with.foeActive().getHeldItem(), "反伤不应消耗凸凸头盔");
        assertTrue(with.getLog().stream().anyMatch(line -> line.contains("凸凸头盔")),
                "日志应包含凸凸头盔反伤");
    }

    @Test
    void 凸凸头盔对非接触类招式不反伤() {
        BattleEngine without = engineAfterPlayerAttack(WATER_MOVE, null, null, IDLE_MOVE, 42);
        BattleEngine with = engineAfterPlayerAttack(WATER_MOVE, null, ROCKY_HELMET, IDLE_MOVE, 42);
        assertEquals(without.playerActive().getCurrentHp(), with.playerActive().getCurrentHp(),
                "非接触类招式不应触发凸凸头盔反伤");
    }

    @Test
    void 凸凸头盔对拳击手套的拳类招式不反伤() {
        BattleEngine gloved = engineAfterPlayerAttack(PUNCH_HIT, PUNCH_GLOVE, ROCKY_HELMET, IDLE_MOVE, 42);
        BattleEngine bare = engineAfterPlayerAttack(PUNCH_HIT, null, ROCKY_HELMET, IDLE_MOVE, 42);
        assertTrue(bare.playerActive().getCurrentHp() < bare.playerActive().getMaxHp(),
                "未戴拳击手套时拳类招式仍属接触，应被反伤");
        assertEquals(gloved.playerActive().getMaxHp(), gloved.playerActive().getCurrentHp(),
                "拳击手套让拳类招式不视为接触，不应被反伤");
    }

    @Test
    void 锐利之爪会心时伤害提升五成() {
        long seed = seedWhereClawCrits();
        int plain = damageDealtWithSeed(BIG_HIT, null, seed);
        int crit = damageDealtWithSeed(BIG_HIT, RAZOR_CLAW, seed);
        assertEquals(1.5, (double) crit / plain, 0.02,
                "会心伤害应为 1.5 倍（无装备 " + plain + "、锐利之爪 " + crit + "）");
        assertTrue(damageLog(BIG_HIT, RAZOR_CLAW, seed).contains("击中要害"), "携带锐利之爪时应会心一击");
        assertFalse(damageLog(BIG_HIT, null, seed).contains("击中要害"), "无装备时该种子不应会心");
    }

    @Test
    void 锐利之爪把会心概率从二十四分之一提升到八分之一() {
        int rounds = 400;
        int plain = 0;
        int claw = 0;
        for (long index = 0; index < rounds; index++) {
            long seed = scatteredSeed(index);
            if (damageLog(BIG_HIT, null, seed).contains("击中要害")) {
                plain++;
            }
            if (damageLog(BIG_HIT, RAZOR_CLAW, seed).contains("击中要害")) {
                claw++;
            }
        }
        assertEquals(1.0 / 24.0, (double) plain / rounds, 0.03,
                "无装备时会心率应为 1/24，实测 " + plain + "/" + rounds);
        assertEquals(1.0 / 8.0, (double) claw / rounds, 0.04,
                "锐利之爪（1 级）会心率应为 1/8，实测 " + claw + "/" + rounds);
    }

    /**
     * 打散后的随机种子：小整数（0、1、2…）作种子时 {@link Random} 首个 {@code next(31)} 的高位
     * 比特随种子线性缓慢变化（实测 {@code nextInt(8)} 连续多个种子恒为同值），统计型用例会得到
     * 严重偏差的会心率，故一律用黄金比例乘子打散后再做二次哈希。
     */
    private static long scatteredSeed(long index) {
        return new Random(index * 0x9E3779B97F4A7C15L + 0x9E3779B97F4A7C15L).nextLong();
    }

    /**
     * 一个回合内玩家（高速）攻击造成的伤害日志：对手空转，因此日志中的「击中要害」必属玩家。
     */
    private static String damageLog(Move move, HeldItem attackerItem, long seed) {
        return String.join("|", engineAfterPlayerAttack(move, attackerItem, null, IDLE_MOVE, seed).getLog());
    }

    /**
     * 运行时扫描种子：令首个随机调用（会心判定）在 1 级（{@code nextInt(8)}）命中、0 级
     * （{@code nextInt(24)}）不命中。两种分母都只消耗一次内部随机数，后续伤害浮动因此完全一致，
     * 两份伤害之比即会心倍率本身。
     */
    private static long seedWhereClawCrits() {
        for (long index = 0; index < 10_000; index++) {
            long seed = scatteredSeed(index);
            if (new Random(seed).nextInt(8) == 0 && new Random(seed).nextInt(24) != 0) {
                return seed;
            }
        }
        throw new AssertionError("找不到锐利之爪会心而无装备不会心的随机种子");
    }

    @Test
    void 王者之证按概率使目标畏缩从而无法行动() {
        BattleEngine flinched = engineAfterPlayerAttack(BIG_HIT, KINGS_ROCK, null, MED_HIT, seedWhereFlinchTriggers());
        assertTrue(flinched.getLog().stream().anyMatch(line -> line.contains("畏缩了")),
                "触发时日志应播报目标畏缩");
        assertTrue(flinched.getLog().stream().anyMatch(line -> line.contains("无法行动")),
                "畏缩目标本回合无法行动");
        assertEquals(flinched.playerActive().getMaxHp(), flinched.playerActive().getCurrentHp(),
                "目标畏缩未出手，玩家不应受到伤害");

        BattleEngine plain = engineAfterPlayerAttack(BIG_HIT, KINGS_ROCK, null, MED_HIT, seedWhereFlinchFails());
        assertFalse(plain.getLog().stream().anyMatch(line -> line.contains("畏缩了")),
                "未触发时不应有畏缩日志");
        assertTrue(plain.playerActive().getCurrentHp() < plain.playerActive().getMaxHp(),
                "未触发时敌方正常行动并造成伤害");
    }

    @Test
    void 王者之证对变化招不触发畏缩() {
        for (long seed = 0; seed < 30; seed++) {
            BattleEngine engine = engineAfterPlayerAttack(IDLE_MOVE, KINGS_ROCK, null, IDLE_MOVE, seed);
            assertFalse(engine.getLog().stream().anyMatch(line -> line.contains("畏缩了")),
                    "变化招不应触发王者之证（种子 " + seed + "）");
        }
    }

    /**
     * 运行时扫描种子：令首个随机调用（会心判定 {@code nextInt(24)}）、第二次（伤害浮动
     * {@code nextDouble()}）之后的畏缩判定 {@code nextInt(100)} 落在触发区间 [0,10)。
     */
    private static long seedWhereFlinchTriggers() {
        for (long s = 0; s < 100_000; s++) {
            Random probe = new Random(s);
            probe.nextInt(24);
            probe.nextDouble();
            if (probe.nextInt(100) < 10) {
                return s;
            }
        }
        throw new AssertionError("找不到触发王者之证的随机种子");
    }

    /** 同 {@link #seedWhereFlinchTriggers()}，但畏缩判定落在非触发区间 [10,100)。 */
    private static long seedWhereFlinchFails() {
        for (long s = 0; s < 100_000; s++) {
            Random probe = new Random(s);
            probe.nextInt(24);
            probe.nextDouble();
            if (probe.nextInt(100) >= 10) {
                return s;
            }
        }
        throw new AssertionError("找不到不触发王者之证的随机种子");
    }

    @Test
    void 节拍器连续使用同一招式逐次增伤且封顶两倍() {
        int[] same = damageSequence(METRONOME_ITEM, false, 10);
        int[] alternating = damageSequence(METRONOME_ITEM, true, 10);
        int[] alternatingPlain = damageSequence(null, true, 10);
        assertEquals(alternating[0], same[0], "首次使用（连续次数 1）不应有增伤");
        assertEquals(1.2, (double) same[1] / alternating[1], 0.05, "连续第 2 次应增伤至 1.2 倍");
        assertEquals(1.4, (double) same[2] / alternating[2], 0.05, "连续第 3 次应增伤至 1.4 倍");
        assertEquals(2.0, (double) same[6] / alternating[6], 0.05, "连续第 7 次应封顶 2.0 倍");
        assertEquals(2.0, (double) same[9] / alternating[9], 0.05, "连续第 10 次应保持封顶 2.0 倍");
        assertEquals(1.0, (double) alternating[2] / alternatingPlain[2], 0.05,
                "换招后连续次数归零，节拍器不应再有增伤");
    }

    @Test
    void 未携带节拍器时连续使用同一招式不增伤() {
        int[] same = damageSequence(null, false, 3);
        int[] alternating = damageSequence(null, true, 3);
        assertEquals(alternating[2], same[2], "未携带节拍器时连续使用不应增伤");
    }

    /**
     * 连打 {@code rounds} 个回合，返回每回合对防守方造成的伤害。
     * {@code alternating} 为 {@code true} 时严格交替两个数值相同但 id 不同的招式（连续次数恒为 1），
     * 否则一直使用同一招式（连续次数逐回合累加）。两场除出招顺序外配置一致、种子相同，
     * 因此每回合的会心与伤害浮动完全一致，伤害之比即节拍器倍率差。
     */
    private static int[] damageSequence(HeldItem item, boolean alternating, int rounds) {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 5000, 100, 50, 200, null), 50,
                List.of(BIG_HIT, BIG_HIT_ALT));
        if (item != null) {
            mine.setHeldItem(item);
        }
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 200000, 100, 50, 10, null), 50, List.of(WEAK_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        int[] damage = new int[rounds];
        for (int round = 0; round < rounds; round++) {
            int before = foe.getCurrentHp();
            engine.useMove(mine.getMoveSlots().get(alternating ? round % 2 : 0));
            damage[round] = before - foe.getCurrentHp();
        }
        return damage;
    }

    @Test
    void 防尘护目镜免疫粉末类招式且不消耗() {
        assertEquals(StatusCondition.POISON, statusInflictedDefender(POWDER_MOVE, null).getStatus(),
                "前置条件：无护目镜时应中毒");
        Pokemon goggled = statusInflictedDefender(POWDER_MOVE, SAFETY_GOGGLES);
        assertEquals(StatusCondition.NONE, goggled.getStatus(), "护目镜应免疫粉末类招式");
        assertEquals(SAFETY_GOGGLES, goggled.getHeldItem(), "免疫粉末招式不应消耗护目镜");
    }

    @Test
    void 防尘护目镜只免疫粉末类招式() {
        Pokemon gassed = statusInflictedDefender(POISON_GAS_MOVE, SAFETY_GOGGLES);
        assertEquals(StatusCondition.POISON, gassed.getStatus(), "非粉末类招式不应被护目镜免疫");
    }

    @Test
    void 防尘护目镜免疫沙暴回合末伤害() {
        BattleEngine without = sandstormRounds(null);
        BattleEngine with = sandstormRounds(SAFETY_GOGGLES);
        int maxHp = with.playerActive().getMaxHp();
        assertEquals(maxHp, with.playerActive().getCurrentHp(), "护目镜应完全免疫沙暴回合末伤害");
        assertEquals(Math.max(1, (int) (maxHp * 0.0625)) * 3,
                maxHp - without.playerActive().getCurrentHp(),
                "无护目镜时自开启当回合起每回合应扣除最大 HP 的 1/16（共 3 回合）");
        assertTrue(with.getLog().stream().anyMatch(line -> line.contains("不受沙暴影响")),
                "日志应播报护目镜免疫沙暴");
    }

    /** 开启沙暴后连打 3 个回合（首回合开天气，随后空转 2 回合）：用于护目镜的回合末免伤断言。 */
    private static BattleEngine sandstormRounds(HeldItem goggles) {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 1000, 100, 50, 200, null), 50,
                List.of(SANDSTORM_MOVE, IDLE_MOVE));
        if (goggles != null) {
            mine.setHeldItem(goggles);
        }
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(IDLE_MOVE));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        for (int round = 0; round < 2; round++) {
            engine.useMove(mine.getMoveSlots().get(1));
        }
        return engine;
    }

    // ------------------------------------------------------------------
    // 批次⑤：招式能力等级变化
    // ------------------------------------------------------------------

    /** 叫声：声音类变化招，令目标物攻 −1。 */
    private static final Move GROWL_MOVE = new Move("m_growl", "叫声", ElementType.NORMAL,
            MoveCategory.STATUS, 0, 100, 40, 0, MoveEffect.NONE, StatusCondition.NONE, 0,
            Set.of(MoveFlag.SOUND), MoveStatChange.parse("ATTACK|-1"));

    /** 摇尾巴：非声音类变化招，令目标物防 −1。 */
    private static final Move TAIL_WHIP_MOVE = new Move("m_tail_whip", "摇尾巴", ElementType.NORMAL,
            MoveCategory.STATUS, 0, 100, 40, 0, MoveEffect.NONE, StatusCondition.NONE, 0,
            Set.of(), MoveStatChange.parse("DEFENSE|-1"));

    /** 硬邦邦：必中的自强化变化招，令自身物防 +1。 */
    private static final Move HARDEN_MOVE = new Move("m_harden", "硬邦邦", ElementType.NORMAL,
            MoveCategory.STATUS, 0, -1, 40, 0, MoveEffect.NONE, StatusCondition.NONE, 0,
            Set.of(), MoveStatChange.parse("DEFENSE|1|SELF"));

    /** 高速移动：令自身速度 +2。 */
    private static final Move AGILITY_MOVE = new Move("m_agility", "高速移动", ElementType.PSYCHIC,
            MoveCategory.STATUS, 0, -1, 40, 0, MoveEffect.NONE, StatusCondition.NONE, 0,
            Set.of(), MoveStatChange.parse("SPEED|2|SELF"));

    /** 命中率 0 的招式：必定打空（{@code < 0} 才是必中，故 0 仍会掷骰且永不命中）。 */
    private static final Move NEVER_HIT_MOVE = new Move("m_never", "失手", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 200, 0, 40);

    @Test
    void 叫声降低目标的物攻一级() {
        BattleEngine engine = engineAfterPlayerAttack(GROWL_MOVE, null, null, IDLE_MOVE, 42);
        assertEquals(-1, engine.foeActive().getStatStage(StatModifier.ATTACK), "目标物攻应降 1 级");
        assertEquals(0, engine.playerActive().getStatStage(StatModifier.ATTACK), "不应影响使用者");
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("攻击降低了")),
                "日志应播报物攻下降");
    }

    @Test
    void 摇尾巴降低目标的物防一级() {
        BattleEngine engine = engineAfterPlayerAttack(TAIL_WHIP_MOVE, null, null, IDLE_MOVE, 42);
        assertEquals(-1, engine.foeActive().getStatStage(StatModifier.DEFENSE));
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("防御降低了")));
    }

    @Test
    void 自强化招式作用于使用者自己() {
        BattleEngine engine = engineAfterPlayerAttack(HARDEN_MOVE, null, null, IDLE_MOVE, 42);
        assertEquals(1, engine.playerActive().getStatStage(StatModifier.DEFENSE), "硬邦邦应提升自身物防");
        assertEquals(0, engine.foeActive().getStatStage(StatModifier.DEFENSE), "不应影响目标");

        BattleEngine fast = engineAfterPlayerAttack(AGILITY_MOVE, null, null, IDLE_MOVE, 42);
        assertEquals(2, fast.playerActive().getStatStage(StatModifier.SPEED), "高速移动应提升自身速度 2 级");
    }

    @Test
    void 能力等级变化真实影响后续伤害() {
        BattleEngine plain = runOneRound(null);
        int plainLost = plain.foeActive().getMaxHp() - plain.foeActive().getCurrentHp();

        // 先用叫声把对手物攻降到 −2（伤害降为一半），再让对手攻击我方
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 200, null), 50,
                List.of(GROWL_MOVE, BIG_HIT));
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(MED_HIT));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        engine.useMove(mine.getMoveSlots().get(0));
        assertEquals(-2, engine.foeActive().getStatStage(StatModifier.ATTACK));
        int mineHp = engine.playerActive().getCurrentHp();
        engine.useMove(mine.getMoveSlots().get(1));
        int lostWithDebuff = mineHp - engine.playerActive().getCurrentHp();
        assertTrue(lostWithDebuff < plainLost,
                "物攻 −2 后对手造成的伤害应低于无减益时的 " + plainLost + "，实际 " + lostWithDebuff);
    }

    @Test
    void 能力等级变化不会越过正负六() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 200, null), 50, List.of(AGILITY_MOVE));
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(IDLE_MOVE));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        for (int i = 0; i < 5; i++) {
            engine.useMove(mine.getMoveSlots().get(0));
        }
        assertEquals(Pokemon.MAX_STAT_STAGE, engine.playerActive().getStatStage(StatModifier.SPEED));
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("已经无法再提高了")),
                "到上限后应播报无法再提高");
    }

    // ------------------------------------------------------------------
    // 批次⑤：命中判定
    // ------------------------------------------------------------------

    @Test
    void 命中判定失败时打空且不造成伤害() {
        assertEquals(0, damageDealtWithSeed(NEVER_HIT_MOVE, null, 42), "命中率 0 的招式应永不造成伤害");
    }

    @Test
    void 必中招式不受命中判定影响() {
        Move sureHit = new Move("m_sure", "必中撞击", ElementType.NORMAL, MoveCategory.PHYSICAL,
                200, -1, 40);
        assertTrue(damageDealtWithSeed(sureHit, null, 42) > 0, "accuracy < 0 应视为必中");
        Move full = new Move("m_full", "撞击", ElementType.NORMAL, MoveCategory.PHYSICAL, 200, 100, 40);
        assertEquals(damageDealtWithSeed(sureHit, null, 42), damageDealtWithSeed(full, null, 42),
                "必中与命中率 100 的同一招式伤害应一致");
    }

    // ------------------------------------------------------------------
    // 批次⑤：能力等级联动装备
    // ------------------------------------------------------------------

    private static final HeldItem ASSAULT_VEST = item("e_assault_vest", "突击背心", HeldItemEffect.ASSAULT_VEST, "1.5");
    private static final HeldItem CLEAR_AMULET = item("e_clear_amulet", "清净坠饰", HeldItemEffect.CLEAR_AMULET, "");
    private static final HeldItem WEAKNESS_POLICY = item("e_weakness_policy", "弱点保险", HeldItemEffect.WEAKNESS_POLICY, "2");
    private static final HeldItem BLUNDER_POLICY = item("e_blunder_policy", "打空保险", HeldItemEffect.BLUNDER_POLICY, "2");
    private static final HeldItem THROAT_SPRAY = item("e_throat_spray", "爽喉喷雾", HeldItemEffect.THROAT_SPRAY, "1");
    private static final HeldItem CHOICE_BAND = item("e_choice_band", "讲究头带", HeldItemEffect.CHOICE, "ATTACK|1.5");
    private static final HeldItem TERRAIN_SEED_ELECTRIC = item("e_electric_seed", "电气种子", HeldItemEffect.TERRAIN_SEED, "ELECTRIC|DEFENSE|1");
    private static final HeldItem TERRAIN_SEED_GRASSY = item("e_grassy_seed", "青草种子", HeldItemEffect.TERRAIN_SEED, "GRASSY|DEFENSE|1");
    private static final HeldItem ABSORB_BULB = item("e_absorb_bulb", "球根", HeldItemEffect.TYPE_REACTION, "WATER|SP_ATTACK|1");
    private static final HeldItem CELL_BATTERY = item("e_cell_battery", "充电电池", HeldItemEffect.TYPE_REACTION, "ELECTRIC|ATTACK|1");
    private static final HeldItem UTILITY_UMBRELLA = item("e_utility_umbrella", "万能伞", HeldItemEffect.UTILITY_UMBRELLA, "");

    @Test
    void 讲究头带提升物理招式伤害五成() {
        assertDamageMultiplierWithSeed(BIG_HIT, CHOICE_BAND, 1.5);
    }

    /** 同 {@link #assertDamageMultiplier}，但攻击者属性固定为一般系、种子固定 42。 */
    private static void assertDamageMultiplierWithSeed(Move move, HeldItem item, double expected) {
        int without = damageDealtWithSeed(move, null, 42);
        int with = damageDealtWithSeed(move, item, 42);
        assertEquals(expected, (double) with / without, 0.05,
                item.getName() + " 应使伤害变为 " + expected + " 倍（无装备 " + without + "，有装备 " + with + "）");
    }

    @Test
    void 讲究头带对特殊招式无效() {
        int without = damageDealtWithSeed(BIG_SPECIAL_HIT, null, 42);
        int with = damageDealtWithSeed(BIG_SPECIAL_HIT, CHOICE_BAND, 42);
        assertEquals(without, with, "讲究头带不应提升特殊招式伤害");
    }

    @Test
    void 突击背心降低特殊招式受到的伤害() {
        Pokemon plain = attackedDefender(BIG_SPECIAL_HIT, null, ElementType.NORMAL, ElementType.NORMAL);
        Pokemon vested = attackedDefender(BIG_SPECIAL_HIT, ASSAULT_VEST, ElementType.NORMAL, ElementType.NORMAL);
        int plainLost = plain.getMaxHp() - plain.getCurrentHp();
        int vestedLost = vested.getMaxHp() - vested.getCurrentHp();
        assertEquals(1.5, (double) plainLost / vestedLost, 0.05,
                "特防 1.5 倍应使受到的伤害降为约 2/3（无背心 " + plainLost + "，有背心 " + vestedLost + "）");
    }

    @Test
    void 突击背心对物理招式无效() {
        Pokemon plain = attackedDefender(BIG_HIT, null, ElementType.NORMAL, ElementType.NORMAL);
        Pokemon vested = attackedDefender(BIG_HIT, ASSAULT_VEST, ElementType.NORMAL, ElementType.NORMAL);
        assertEquals(plain.getMaxHp() - plain.getCurrentHp(), vested.getMaxHp() - vested.getCurrentHp(),
                "突击背心只提升特防，物理招式伤害不应变化");
    }

    @Test
    void 突击背心禁止携带者使用变化招式() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 200, null), 50,
                List.of(GROWL_MOVE, BIG_HIT));
        mine.setHeldItem(ASSAULT_VEST);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(IDLE_MOVE));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        assertEquals(0, engine.foeActive().getStatStage(StatModifier.ATTACK), "变化招应被禁止");
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("无法使用变化招式")));
        assertEquals(40, mine.getMoveSlots().get(0).getCurrentPp(), "被禁止时不应消耗 PP");

        engine.useMove(mine.getMoveSlots().get(1));
        assertTrue(foe.getMaxHp() - foe.getCurrentHp() > 0, "攻击招式仍应可用");
    }

    @Test
    void 突击背心使敌方AI放弃变化招式() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(IDLE_MOVE));
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 200, null), 50, List.of(GROWL_MOVE, MED_HIT));
        foe.setHeldItem(ASSAULT_VEST);
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        assertEquals(0, engine.playerActive().getStatStage(StatModifier.ATTACK), "AI 不应选用变化招");
    }

    @Test
    void 清净坠饰免疫对手造成的能力下降() {
        BattleEngine protectedEngine = engineAfterPlayerAttack(GROWL_MOVE, null, CLEAR_AMULET, IDLE_MOVE, 42);
        assertEquals(0, protectedEngine.foeActive().getStatStage(StatModifier.ATTACK), "坠饰应免疫下降");
        assertEquals(CLEAR_AMULET, protectedEngine.foeActive().getHeldItem(), "免疫不应消耗坠饰");
        assertTrue(protectedEngine.getLog().stream().anyMatch(line -> line.contains("没有被降低")));

        BattleEngine plain = engineAfterPlayerAttack(GROWL_MOVE, null, null, IDLE_MOVE, 42);
        assertEquals(-1, plain.foeActive().getStatStage(StatModifier.ATTACK), "无坠饰时仍应被降低");
    }

    @Test
    void 清净坠饰不影响自身造成的能力变化() {
        BattleEngine engine = engineAfterPlayerAttack(TAIL_WHIP_MOVE, CLEAR_AMULET, null, IDLE_MOVE, 42);
        assertEquals(-1, engine.foeActive().getStatStage(StatModifier.DEFENSE), "由对手造成，不受坠饰影响");

        // 硬邦邦是「自身造成」的提升，坠饰不应拦截（本来也只拦下降）
        BattleEngine self = engineAfterPlayerAttack(HARDEN_MOVE, CLEAR_AMULET, null, IDLE_MOVE, 42);
        assertEquals(1, self.playerActive().getStatStage(StatModifier.DEFENSE));
    }

    @Test
    void 弱点保险被效果拔群命中后提升双攻并消耗() {
        // 水打火：效果拔群
        Pokemon triggered = attackedDefender(WATER_MOVE, WEAKNESS_POLICY, ElementType.WATER, ElementType.FIRE);
        assertEquals(2, triggered.getStatStage(StatModifier.ATTACK), "物攻应 +2");
        assertEquals(2, triggered.getStatStage(StatModifier.SP_ATTACK), "特攻应 +2");
        assertNull(triggered.getHeldItem(), "触发后应消耗");

        Pokemon plain = attackedDefender(WATER_MOVE, null, ElementType.WATER, ElementType.FIRE);
        assertEquals(0, plain.getStatStage(StatModifier.ATTACK));
    }

    @Test
    void 弱点保险在非效果拔群时不触发() {
        Pokemon untouched = attackedDefender(WATER_MOVE, WEAKNESS_POLICY, ElementType.WATER, ElementType.NORMAL);
        assertEquals(0, untouched.getStatStage(StatModifier.ATTACK), "非效果拔群不应触发");
        assertEquals(WEAKNESS_POLICY, untouched.getHeldItem(), "未触发不应消耗");
    }

    @Test
    void 打空保险在招式未命中后提升速度并消耗() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 200, null), 50, List.of(NEVER_HIT_MOVE));
        mine.setHeldItem(BLUNDER_POLICY);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(IDLE_MOVE));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        assertEquals(2, mine.getStatStage(StatModifier.SPEED), "打空后速度应 +2");
        assertNull(mine.getHeldItem(), "触发后应消耗");
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("没有命中")));
    }

    @Test
    void 打空保险在命中时不触发() {
        int[] result = blunderPolicyOnHit();
        assertEquals(0, result[0], "命中时不应提升速度");
        assertEquals(1, result[1], "命中时不应消耗");
    }

    /** @return 长度为 2 的数组：[0] 速度等级，[1] 是否仍携带打空保险（1=仍携带） */
    private static int[] blunderPolicyOnHit() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 200, null), 50, List.of(BIG_HIT));
        mine.setHeldItem(BLUNDER_POLICY);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(IDLE_MOVE));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        new BattleEngine(player, foe, new Random(42)).useMove(mine.getMoveSlots().get(0));
        return new int[]{mine.getStatStage(StatModifier.SPEED), mine.getHeldItem() == null ? 0 : 1};
    }

    @Test
    void 爽喉喷雾在使用声音类招式后提升特攻并消耗() {
        BattleEngine engine = engineAfterPlayerAttack(GROWL_MOVE, THROAT_SPRAY, null, IDLE_MOVE, 42);
        assertEquals(1, engine.playerActive().getStatStage(StatModifier.SP_ATTACK), "使用声音招后特攻应 +1");
        assertNull(engine.playerActive().getHeldItem(), "触发后应消耗");
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("爽喉喷雾")));
    }

    @Test
    void 爽喉喷雾对非声音类招式不触发() {
        BattleEngine engine = engineAfterPlayerAttack(TAIL_WHIP_MOVE, THROAT_SPRAY, null, IDLE_MOVE, 42);
        assertEquals(0, engine.playerActive().getStatStage(StatModifier.SP_ATTACK), "非声音招不应触发");
        assertEquals(THROAT_SPRAY, engine.playerActive().getHeldItem(), "未触发不应消耗");
    }

    @Test
    void 爽喉喷雾在攻击招路径同样生效() {
        Move soundAttack = new Move("m_sound_atk", "爆音波", ElementType.NORMAL, MoveCategory.PHYSICAL,
                200, 100, 40, 0, MoveEffect.NONE, StatusCondition.NONE, 0,
                Set.of(MoveFlag.SOUND));
        BattleEngine engine = engineAfterPlayerAttack(soundAttack, THROAT_SPRAY, null, IDLE_MOVE, 42);
        assertEquals(1, engine.playerActive().getStatStage(StatModifier.SP_ATTACK));
        assertNull(engine.playerActive().getHeldItem());
    }

    @Test
    void 属性反应装备受到对应属性招式后提升能力并消耗() {
        Pokemon bulb = attackedDefender(WATER_MOVE, ABSORB_BULB, ElementType.WATER, ElementType.NORMAL);
        assertEquals(1, bulb.getStatStage(StatModifier.SP_ATTACK), "球根应提升特攻");
        assertNull(bulb.getHeldItem(), "触发后应消耗");

        Pokemon battery = attackedDefender(ELECTRIC_MOVE, CELL_BATTERY, ElementType.ELECTRIC, ElementType.NORMAL);
        assertEquals(1, battery.getStatStage(StatModifier.ATTACK), "充电电池应提升物攻");
        assertNull(battery.getHeldItem());
    }

    @Test
    void 属性反应装备对非对应属性招式不触发() {
        Pokemon bulb = attackedDefender(FIRE_MOVE, ABSORB_BULB, ElementType.FIRE, ElementType.NORMAL);
        assertEquals(0, bulb.getStatStage(StatModifier.SP_ATTACK), "球根只对水属性招式反应");
        assertEquals(ABSORB_BULB, bulb.getHeldItem(), "未触发不应消耗");
    }

    // ------------------------------------------------------------------
    // 批次⑤：场地种子与万能伞
    // ------------------------------------------------------------------

    /** 开启电气场地的变化招：场地种子用例。 */
    private static final Move ELECTRIC_TERRAIN_MOVE = new Move("m_e_terrain", "电气场地", ElementType.ELECTRIC,
            MoveCategory.STATUS, 0, 100, 40, MoveEffect.ELECTRIC_TERRAIN);

    /** 开启青草场地的变化招：场地种子不匹配用例。 */
    private static final Move GRASSY_TERRAIN_MOVE = new Move("m_g_terrain", "青草场地", ElementType.GRASS,
            MoveCategory.STATUS, 0, 100, 40, MoveEffect.GRASSY_TERRAIN);

    @Test
    void 场地种子在场地匹配时提升能力并消耗() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 200, null), 50,
                List.of(ELECTRIC_TERRAIN_MOVE));
        mine.setHeldItem(TERRAIN_SEED_ELECTRIC);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(IDLE_MOVE));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(mine.getMoveSlots().get(0));
        assertEquals(1, mine.getStatStage(StatModifier.DEFENSE), "电气场地应触发电气种子");
        assertNull(mine.getHeldItem(), "触发后应消耗");
        assertTrue(engine.getLog().stream().anyMatch(line -> line.contains("电气种子")));
    }

    @Test
    void 场地种子在场地不匹配时不触发() {
        Pokemon mine = poke(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 200, null), 50,
                List.of(GRASSY_TERRAIN_MOVE));
        mine.setHeldItem(TERRAIN_SEED_ELECTRIC);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(IDLE_MOVE));
        Player player = new Player("玩家");
        player.addPokemon(mine);
        new BattleEngine(player, foe, new Random(42)).useMove(mine.getMoveSlots().get(0));
        assertEquals(0, mine.getStatStage(StatModifier.DEFENSE), "青草场地不应触发电气种子");
        assertEquals(TERRAIN_SEED_ELECTRIC, mine.getHeldItem(), "未触发不应消耗");
    }

    @Test
    void 换上的精灵在场地已开启时立即触发种子() {
        Pokemon first = poke(
                species("p_part", ElementType.NORMAL, 800, 100, 50, 200, null), 50, List.of(ELECTRIC_TERRAIN_MOVE));
        Pokemon second = poke(
                species("p_second", ElementType.NORMAL, 800, 100, 50, 100, null), 50, List.of(WEAK_HIT));
        second.setHeldItem(TERRAIN_SEED_ELECTRIC);
        Pokemon foe = poke(
                species("f_sp", ElementType.NORMAL, 5000, 100, 50, 10, null), 50, List.of(IDLE_MOVE));
        Player player = new Player("玩家");
        player.addPokemon(first);
        player.addPokemon(second);
        BattleEngine engine = new BattleEngine(player, foe, new Random(42));
        engine.useMove(first.getMoveSlots().get(0));
        engine.switchActive(1);
        assertEquals(1, second.getStatStage(StatModifier.DEFENSE), "电气种子应在换上时触发");
        assertNull(second.getHeldItem(), "触发后应消耗");
    }

    @Test
    void 万能伞使携带者免受晴天火属性增伤() {
        int without = sunnyFireDamage(null);
        int with = sunnyFireDamage(UTILITY_UMBRELLA);
        assertTrue(without > with, "晴天火属性增伤应被万能伞取消（无伞 " + without + "，有伞 " + with + "）");
        // 两次调用除装备外完全一致，随机序列相同，故可直接比对晴天 1.5 倍增伤是否被抵消
        assertEquals(1.5, (double) without / with, 0.05,
                "有伞时晴天增伤应被完全取消（无伞 " + without + "，有伞 " + with + "）");
    }

    /** 先开晴天、再由携带 {@code attackerItem} 的攻击者用火属性招式打同一个防守方，返回伤害。 */
    private static int sunnyFireDamage(HeldItem attackerItem) {
        Pokemon attacker = poke(
                species("atk_sp", ElementType.NORMAL, 1000, 100, 100, 200, null), 50,
                List.of(SUNNY_MOVE, FIRE_MOVE));
        if (attackerItem != null) {
            attacker.setHeldItem(attackerItem);
        }
        Pokemon defender = poke(
                species("def_sp", ElementType.NORMAL, 5000, 10, 50, 10, null), 50, List.of(IDLE_MOVE));
        Player player = new Player("玩家");
        player.addPokemon(attacker);
        BattleEngine engine = new BattleEngine(player, defender, new Random(42));
        engine.useMove(attacker.getMoveSlots().get(0));
        int before = defender.getCurrentHp();
        engine.useMove(attacker.getMoveSlots().get(1));
        return before - defender.getCurrentHp();
    }

    @Test
    void 万能伞使携带者免受沙暴回合末伤害() {
        BattleEngine without = sandstormRounds(null);
        BattleEngine with = sandstormRounds(UTILITY_UMBRELLA);
        int maxHp = with.playerActive().getMaxHp();
        assertEquals(maxHp, with.playerActive().getCurrentHp(), "万能伞应完全免疫沙暴回合末伤害");
        assertTrue(without.playerActive().getCurrentHp() < maxHp, "无伞时应受到沙暴伤害");
        assertTrue(with.getLog().stream().anyMatch(line -> line.contains("不受沙暴影响")),
                "日志应播报万能伞免疫沙暴");
    }
}
