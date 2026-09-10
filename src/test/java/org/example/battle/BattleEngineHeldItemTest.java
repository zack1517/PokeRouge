package org.example.battle;

import org.example.model.ElementType;
import org.example.model.HeldItem;
import org.example.model.HeldItemEffect;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装备战斗效果测试：验证 8 种可携带装备在战斗引擎中的实际效果。
 *
 * <p>伤害类断言依赖<b>同种子随机数序列一致</b>：无装备与有装备两场战斗注入相同
 * {@code new Random(42)}，除装备外配置完全相同，随机伤害浮动（0.85~1.0）因此一致，
 * 伤害比即为装备倍率本身。先制之爪用运行时扫描种子保证确定性触发/不触发。</p>
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
     * 打一场并返回防守方承受的伤害：攻击者高速先手、高攻高威技能；防守方低速低攻、血厚不死。
     * 除攻击者装备外两场配置完全一致，且注入相同种子 {@code 42} 保证随机伤害浮动一致。
     */
    private static int damageDealt(Move move, HeldItem attackerItem, HeldItem defenderItem,
                                   ElementType attackerType, ElementType defenderType,
                                   String defenderEvolvesTo) {
        Pokemon attacker = Pokemon.create(
                species("atk_sp", attackerType, 1000, 100, 100, 200, null), 50, List.of(move));
        if (attackerItem != null) {
            attacker.setHeldItem(attackerItem);
        }
        Pokemon defender = Pokemon.create(
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
        Pokemon mine = Pokemon.create(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(BIG_HIT));
        if (mineItem != null) {
            mine.setHeldItem(mineItem);
        }
        Pokemon foe = Pokemon.create(
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
        Pokemon mine = Pokemon.create(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(BIG_HIT));
        mine.setHeldItem(QUICK_CLAW);
        Pokemon foe = Pokemon.create(
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
        Pokemon mine = Pokemon.create(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(BIG_HIT));
        mine.setHeldItem(QUICK_CLAW);
        Pokemon foe = Pokemon.create(
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
        Pokemon mine = Pokemon.create(
                species("p_sp", ElementType.NORMAL, 800, 100, 50, 10, null), 50, List.of(BIG_HIT));
        mine.setHeldItem(QUICK_CLAW);
        Pokemon foe = Pokemon.create(
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
}
