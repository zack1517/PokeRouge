package org.example.battle;

import org.example.data.GameData;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.StatusCondition;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 招式专属变化技能（守住 / 寄生种子 / 睡觉）与先制度判定测试。
 *
 * <p>守住：本回合挡下对方一切招式（含变化招），被挡下时不掷命中骰、不产生命中演出；
 * 连续使用成功率按 {@code 100 >> 连续次数} 递减；回合结束后保护失效。</p>
 *
 * <p>寄生种子：草系免疫、重复施加无效；每回合末被种下的精灵损失最大 HP 的 1/8，
 * 等量回复对方出战精灵；离场即清除。</p>
 *
 * <p>睡觉：HP 回满并陷入 2 回合计数睡眠；HP 全满或已处于其它主要异常状态时失败。</p>
 *
 * <p>先制度：先比较 {@link Move#getPriority()}，相同才回退到速度（先制之爪）。
 * 个体值统一传 0，配合 {@link ScriptedRandom} 固定随机源，使断言完全可复现。</p>
 */
class BattleEngineSpecialStatusMoveTest {

    /** 无个体值：属性只由种族值与等级决定，便于断言。 */
    private static final Stats NO_IV = new Stats(0, 0, 0, 0, 0, 0);

    /** 玩家与被动敌方共用的低威力物理技能，只用于推动回合。 */
    private static final Move TAP = move("m_tap", "轻拍", MoveCategory.PHYSICAL, 1, 100, 0, MoveEffect.NONE);

    /** 敌方普通物理技能（先制度 0）。 */
    private static final Move SLAM = move("m_slam", "猛撞", MoveCategory.PHYSICAL, 60, 100, 0, MoveEffect.NONE);

    /** 先制度 +1 的物理技能。 */
    private static final Move QUICK = move("m_quick", "电光一闪", MoveCategory.PHYSICAL, 40, 100, 1,
            MoveEffect.NONE);

    /** 命中率仅 50 的物理技能，用于观察是否真的掷了命中骰。 */
    private static final Move SHAKY = move("m_shaky", "乱打", MoveCategory.PHYSICAL, 60, 50, 0, MoveEffect.NONE);

    /** 完全无效果的变化技能：敌方使用它时不会造成任何伤害，也不会施加状态。 */
    private static final Move NO_EFFECT = move("m_guard", "变硬", MoveCategory.STATUS, 0, 100, 0,
            MoveEffect.NONE);

    /** 守住：先制度 +4、必中。 */
    private static final Move PROTECT = move("m_protect", "守住", MoveCategory.STATUS, 0, -1, 4,
            MoveEffect.PROTECT);

    /** 寄生种子：命中率 90。 */
    private static final Move LEECH_SEED = move("m_leech", "寄生种子", MoveCategory.STATUS, 0, 90, 0,
            MoveEffect.LEECH_SEED);

    /** 睡觉：必中。 */
    private static final Move REST = move("m_rest", "睡觉", MoveCategory.STATUS, 0, -1, 0, MoveEffect.REST);

    private static Move move(String id, String name, MoveCategory category, int power, int accuracy,
                             int priority, MoveEffect effect) {
        return new Move(id, name, ElementType.NORMAL, category, power, accuracy, 40, priority, effect,
                StatusCondition.NONE, 0, List.of());
    }

    // ------------------------------------------------------------------
    // 固定随机源
    // ------------------------------------------------------------------

    /** {@code nextInt} 恒返回 {@code intValue % bound}；{@code nextDouble} 恒返回 {@code doubleValue}。 */
    private static class ScriptedRandom extends Random {

        private final int intValue;
        private final double doubleValue;

        ScriptedRandom(int intValue, double doubleValue) {
            this.intValue = intValue;
            this.doubleValue = doubleValue;
        }

        @Override
        public int nextInt(int bound) {
            return bound <= 0 ? 0 : Math.floorMod(intValue, bound);
        }

        @Override
        public double nextDouble() {
            return doubleValue;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }
    }

    /** 记录每次 {@code nextInt} 的 bound，用于断言「是否真的掷了某一类骰子」。 */
    private static final class RecordingRandom extends ScriptedRandom {

        private final List<Integer> intBounds = new ArrayList<>();

        RecordingRandom(int intValue, double doubleValue) {
            super(intValue, doubleValue);
        }

        @Override
        public int nextInt(int bound) {
            intBounds.add(bound);
            return super.nextInt(bound);
        }
    }

    // ------------------------------------------------------------------
    // 构造助手
    // ------------------------------------------------------------------

    private static Species species(String id, ElementType type, int hp, int atk, int def, int spe) {
        return new Species(id, id, type, null, new Stats(hp, atk, def, atk, def, spe), 100,
                List.of(), null, 0, Map.of());
    }

    private static Pokemon pokemon(String id, ElementType type, int hp, int atk, int def, int spe,
                                   Move... moves) {
        return Pokemon.create(species(id, type, hp, atk, def, spe), 50, List.of(moves), NO_IV);
    }

    /** 玩家精灵：速度 100、耐久足够撑住反打。 */
    private static Pokemon playerPokemon(Move... moves) {
        return pokemon("p_sp", ElementType.NORMAL, 300, 20, 300, 100, moves);
    }

    /** 被动敌方精灵：只会无效果变化技能，速度 150（比玩家快），用于隔离被测效果。 */
    private static Pokemon passiveFoe(Move... extra) {
        return pokemon("f_sp", ElementType.NORMAL, 300, 20, 100, 150, extra);
    }

    /** 攻击型敌方精灵：速度 150、HP 厚，用于观察伤害与挡下效果。 */
    private static Pokemon attackingFoe(Move... moves) {
        return pokemon("f_sp", ElementType.NORMAL, 5000, 100, 100, 150, moves);
    }

    /** 取出指定 id 的技能槽；找不到直接失败。 */
    private static MoveSlot slotOf(Pokemon pokemon, String moveId) {
        return pokemon.getMoveSlots().stream()
                .filter(s -> s.getMove().getId().equals(moveId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少技能 " + moveId));
    }

    private static BattleService battle(Pokemon mine, Pokemon foe, Random random) {
        Player player = new Player("玩家");
        player.addPokemon(mine);
        return BattleServices.newBattle(player, foe, random);
    }

    /** 默认随机源：掷骰恒取 0、伤害浮动 0.9。 */
    private static BattleService battle(Pokemon mine, Pokemon foe) {
        return battle(mine, foe, new ScriptedRandom(0, 0.9));
    }

    // ------------------------------------------------------------------
    // 先制度
    // ------------------------------------------------------------------

    @Test
    void 先制度更高的技能无视速度先出手() {
        // 玩家速度 100 慢于敌方 150，但电光一闪先制度 +1，应先出手
        Pokemon mine = playerPokemon(TAP, QUICK);
        Pokemon foe = passiveFoe(SLAM);

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_quick"));

        String log = joinLog(engine);
        assertTrue(log.contains("p_sp 使用了【电光一闪】"), "玩家应使用先制技能：" + log);
        assertTrue(log.indexOf("p_sp 使用了") < log.indexOf("f_sp 使用了"),
                "先制度 +1 应先于先制度 0 出手（无视速度）：" + log);
    }

    @Test
    void 敌方先制技能抢先于速度更快的玩家() {
        // 玩家速度 200 快于敌方 150，但敌方技能先制度 +1，应先出手
        Pokemon mine = pokemon("p_sp", ElementType.NORMAL, 300, 20, 300, 200, TAP);
        Pokemon foe = passiveFoe(QUICK);

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_tap"));

        String log = joinLog(engine);
        assertTrue(log.indexOf("f_sp 使用了") < log.indexOf("p_sp 使用了"),
                "敌方先制度更高时应抢在速度更快的玩家之前：" + log);
    }

    @Test
    void 先制度相同时回退速度判定() {
        Pokemon mine = pokemon("p_sp", ElementType.NORMAL, 300, 20, 300, 200, TAP);
        Pokemon foe = passiveFoe(SLAM); // 速度 150

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_tap"));

        String log = joinLog(engine);
        assertTrue(log.indexOf("p_sp 使用了") < log.indexOf("f_sp 使用了"),
                "先制度相同时速度更快的一方先出手：" + log);
    }

    @Test
    void 敌方技能先制度混合时按抽取到的技能判定先后手() {
        // 敌方携带先制 +1 与普通技能：Random 恒取 0 → 抽到列表中的第一个技能
        Pokemon mine = pokemon("p_sp", ElementType.NORMAL, 300, 20, 300, 200, TAP);

        Pokemon priorityFirst = passiveFoe(QUICK, SLAM);
        BattleService engine = battle(mine, priorityFirst);
        engine.useMove(slotOf(mine, "m_tap"));
        String log = joinLog(engine);
        assertTrue(log.contains("f_sp 使用了【电光一闪】"), "应先抽取到先制技能：" + log);
        assertTrue(log.indexOf("f_sp 使用了") < log.indexOf("p_sp 使用了"),
                "抽到先制技能时敌方先出手：" + log);

        Pokemon normalFirst = passiveFoe(SLAM, QUICK);
        BattleService engine2 = battle(mine, normalFirst);
        engine2.useMove(slotOf(mine, "m_tap"));
        String log2 = joinLog(engine2);
        assertTrue(log2.contains("f_sp 使用了【猛撞】"), "应先抽取到普通技能：" + log2);
        assertTrue(log2.indexOf("p_sp 使用了") < log2.indexOf("f_sp 使用了"),
                "抽到普通技能时先制度相同，速度更快的玩家先出手：" + log2);
    }

    // ------------------------------------------------------------------
    // 守住
    // ------------------------------------------------------------------

    @Test
    void 守住挡下当回合的敌方攻击() {
        Pokemon mine = playerPokemon(TAP, PROTECT);
        Pokemon foe = attackingFoe(SLAM);

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_protect"));

        assertEquals(mine.getMaxHp(), mine.getCurrentHp(), "守住应完全免疫伤害");
        String log = joinLog(engine);
        assertTrue(log.contains("保护了自己"), "应播报守住成功：" + log);
        assertTrue(log.contains("用守住挡下了攻击"), "应播报挡下攻击：" + log);
        assertTrue(log.contains("f_sp 使用了【猛撞】"), "敌方仍应出手：" + log);
        assertFalse(log.contains("造成了"), "被挡下时不应有伤害播报：" + log);
    }

    @Test
    void 守住也能挡下变化类技能() {
        Pokemon mine = playerPokemon(TAP, PROTECT);
        Pokemon foe = passiveFoe(NO_EFFECT);

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_protect"));

        String log = joinLog(engine);
        assertTrue(log.contains("用守住挡下了攻击"), "守住应挡下变化招：" + log);
        assertFalse(log.contains("什么也没有发生"), "被挡下的变化招不应产生效果：" + log);
    }

    @Test
    void 被守住挡下时不掷命中骰子() {
        Pokemon mine = playerPokemon(TAP, PROTECT);
        Pokemon foe = attackingFoe(SHAKY); // 命中率 50，若掷骰会记录 bound=100
        RecordingRandom random = new RecordingRandom(99, 0.9); // 99 < 100 → 守住成功

        BattleService engine = battle(mine, foe, random);
        engine.useMove(slotOf(mine, "m_protect"));

        assertEquals(mine.getMaxHp(), mine.getCurrentHp(), "守住应完全免疫伤害");
        assertEquals(1, Collections.frequency(random.intBounds, 100),
                "只有守住自身的成功掷骰；被挡下的招式不再掷命中骰：" + random.intBounds);
    }

    @Test
    void 守住只覆盖当回合() {
        Pokemon mine = playerPokemon(TAP, PROTECT);
        Pokemon foe = attackingFoe(SLAM);

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_protect"));
        assertEquals(mine.getMaxHp(), mine.getCurrentHp(), "第 1 回合应被守住保护");

        engine.useMove(slotOf(mine, "m_tap")); // 第 2 回合改用普通招
        assertTrue(mine.getCurrentHp() < mine.getMaxHp(), "第 2 回合守住应已失效，玩家应被打到");
    }

    @Test
    void 连续使用守住成功率递减() {
        Pokemon mine = playerPokemon(TAP, PROTECT);
        Pokemon foe = attackingFoe(SLAM);
        // 第 1 回合成功率 100%（60 < 100 成功）；第 2 回合成功率 50%（60 >= 50 失败）
        BattleService engine = battle(mine, foe, new ScriptedRandom(60, 0.9));

        engine.useMove(slotOf(mine, "m_protect"));
        assertEquals(mine.getMaxHp(), mine.getCurrentHp(), "首次守住必定成功");
        assertEquals(1, mine.getProtectStreak());

        engine.useMove(slotOf(mine, "m_protect"));
        assertEquals(2, mine.getProtectStreak(), "连续次数应继续累加");
        assertTrue(joinLog(engine).contains("守住没有成功"), "第 2 次守住应失败：" + joinLog(engine));
        assertTrue(mine.getCurrentHp() < mine.getMaxHp(), "守住失败后应被打到");
    }

    @Test
    void 改用其它技能后守住连续次数清零() {
        Pokemon mine = playerPokemon(TAP, PROTECT);
        Pokemon foe = passiveFoe(NO_EFFECT);

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_protect"));
        assertEquals(1, mine.getProtectStreak());

        engine.useMove(slotOf(mine, "m_tap"));
        assertEquals(0, mine.getProtectStreak(), "改用其它技能后应重置连续守住次数");
    }

    // ------------------------------------------------------------------
    // 寄生种子
    // ------------------------------------------------------------------

    @Test
    void 寄生种子每回合末吸取八分之一并等量回复对方() {
        Pokemon mine = playerPokemon(TAP, LEECH_SEED);
        Pokemon foe = passiveFoe(NO_EFFECT);
        int drain = Math.max(1, foe.getMaxHp() / 8);
        mine.takeDamage(drain + 10);
        foe.takeDamage(200);
        int playerBefore = mine.getCurrentHp();
        int foeBefore = foe.getCurrentHp();

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_leech"));

        assertTrue(foe.isSeeded(), "目标应被种下寄生种子");
        assertEquals(foeBefore - drain, foe.getCurrentHp(), "被种下的精灵应损失最大 HP 的 1/8");
        assertEquals(playerBefore + drain, mine.getCurrentHp(), "对方出战精灵应等量回复");
        String log = joinLog(engine);
        assertTrue(log.contains("被种下了寄生种子"), log);
        assertTrue(log.contains("寄生种子吸取了"), log);
        assertTrue(log.contains("恢复了"), log);

        // 第 2 回合：种子持续生效，无需要再次施加（再次施放只会提示「已经被种下」而不会造成伤害）
        engine.useMove(slotOf(mine, "m_leech"));
        assertEquals(foeBefore - drain * 2, foe.getCurrentHp(), "寄生种子应每回合持续扣除");
    }

    @Test
    void 草系免疫寄生种子() {
        Pokemon mine = playerPokemon(TAP, LEECH_SEED);
        Pokemon foe = pokemon("f_sp", ElementType.GRASS, 300, 20, 100, 150, NO_EFFECT);

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_leech"));

        assertFalse(foe.isSeeded(), "草系不应被种下寄生种子");
        assertTrue(joinLog(engine).contains("因属性免疫"), joinLog(engine));
    }

    @Test
    void 重复使用寄生种子无效() {
        Pokemon mine = playerPokemon(TAP, LEECH_SEED);
        Pokemon foe = passiveFoe(NO_EFFECT);

        BattleService engine = battle(mine, foe);
        foe.setSeeded(true); // 开局清空挥发性状态后再标记，模拟已被种下过
        engine.useMove(slotOf(mine, "m_leech"));

        assertTrue(joinLog(engine).contains("已经被种下了寄生种子"), joinLog(engine));
    }

    @Test
    void 换宠清除寄生种子() {
        Player player = new Player("玩家");
        Pokemon first = playerPokemon(TAP);
        Pokemon second = pokemon("q_sp", ElementType.NORMAL, 300, 20, 300, 100, TAP);
        player.addPokemon(first);
        player.addPokemon(second);
        Pokemon foe = passiveFoe(NO_EFFECT);

        BattleService engine = BattleServices.newBattle(player, foe, new ScriptedRandom(0, 0.9));
        // 开局会清空双方挥发性状态，因此在场后再分别标记：下场方残留 + 上场方残留
        first.setSeeded(true);
        second.setSeeded(true);

        engine.switchActive(1);

        assertFalse(first.isSeeded(), "下场的精灵应清除寄生种子");
        assertFalse(second.isSeeded(), "上场的精灵不应带着种子");
    }

    // ------------------------------------------------------------------
    // 睡觉
    // ------------------------------------------------------------------

    @Test
    void 睡觉回满HP并陷入睡眠() {
        Pokemon mine = playerPokemon(TAP, REST);
        Pokemon foe = passiveFoe(NO_EFFECT);
        mine.takeDamage(200);

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_rest"));

        assertEquals(mine.getMaxHp(), mine.getCurrentHp(), "睡觉应回满 HP");
        assertEquals(StatusCondition.SLEEP, mine.getStatus(), "睡觉应陷入睡眠");
        assertTrue(joinLog(engine).contains("睡着了"), joinLog(engine));

        engine.useMove(slotOf(mine, "m_tap")); // 睡眠计数 2 → 1
        assertEquals(StatusCondition.SLEEP, mine.getStatus(), "下一回合仍应处于睡眠");
        assertTrue(joinLog(engine).contains("正在呼呼大睡"), joinLog(engine));

        engine.useMove(slotOf(mine, "m_tap")); // 睡眠计数 1 → 0
        assertEquals(StatusCondition.NONE, mine.getStatus(), "睡满后应醒来");
        assertTrue(joinLog(engine).contains("醒过来了"), joinLog(engine));
    }

    @Test
    void 睡觉在满血时失败() {
        Pokemon mine = playerPokemon(TAP, REST);
        Pokemon foe = passiveFoe(NO_EFFECT);

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_rest"));

        assertEquals(StatusCondition.NONE, mine.getStatus(), "满血时不应陷入睡眠");
        assertTrue(joinLog(engine).contains("睡觉失败"), joinLog(engine));
    }

    @Test
    void 睡觉在已有异常状态时失败() {
        Pokemon mine = playerPokemon(TAP, REST);
        Pokemon foe = passiveFoe(NO_EFFECT);
        mine.takeDamage(200);
        mine.setStatus(StatusCondition.BURN);

        BattleService engine = battle(mine, foe);
        engine.useMove(slotOf(mine, "m_rest"));

        assertEquals(StatusCondition.BURN, mine.getStatus(), "已有主要异常时不应被替换为睡眠");
        assertTrue(mine.getCurrentHp() < mine.getMaxHp(), "失败时不应回复 HP");
        assertTrue(joinLog(engine).contains("无法睡觉"), joinLog(engine));
    }

    // ------------------------------------------------------------------
    // 模型层与数据装配
    // ------------------------------------------------------------------

    @Test
    void 完全恢复会清除守住与寄生种子的挥发性状态() {
        Pokemon p = playerPokemon();
        p.setProtected(true);
        p.setProtectStreak(3);
        p.setSeeded(true);

        p.fullRestore();

        assertFalse(p.isProtected(), "恢复后不应仍处于守住状态");
        assertEquals(0, p.getProtectStreak(), "恢复后连续守住次数应清零");
        assertFalse(p.isSeeded(), "恢复后应清除寄生种子");
    }

    @Test
    void 战斗开始时清除双方残留的挥发性状态() {
        Pokemon mine = playerPokemon();
        Pokemon foe = passiveFoe();
        mine.setProtected(true);
        mine.setProtectStreak(2);
        foe.setSeeded(true);

        battle(mine, foe);

        assertFalse(mine.isProtected());
        assertEquals(0, mine.getProtectStreak());
        assertFalse(foe.isSeeded());
    }

    @Test
    void 招式数据文件已装配守住寄生种子睡觉的专属效果() {
        GameData data = GameData.instance();

        assertEquals(MoveEffect.PROTECT, data.move("protect").getEffect(), "守住应绑定 PROTECT");
        assertEquals(4, data.move("protect").getPriority(), "守住先制度应为 +4");
        assertEquals(MoveEffect.LEECH_SEED, data.move("leech-seed").getEffect(), "寄生种子应绑定 LEECH_SEED");
        assertEquals(MoveEffect.REST, data.move("rest").getEffect(), "睡觉应绑定 REST");
        assertTrue(data.move("tackle").getEffect() == MoveEffect.NONE, "无效果招式不应绑定专属效果");
    }

    private static String joinLog(BattleService engine) {
        return String.join("\n", engine.getLog());
    }
}
