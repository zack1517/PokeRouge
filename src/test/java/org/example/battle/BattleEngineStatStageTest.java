package org.example.battle;

import org.example.data.GameData;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stat;
import org.example.model.StatChange;
import org.example.model.Stats;
import org.example.model.StatusCondition;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 能力等级（stat stages）与命中率判定测试。
 *
 * <p>能力等级：-6 ~ +6，倍率遵循正作（+2 为 ×2、-2 为 ×0.5），换宠/战斗开始清零；
 * 变化类技能通过 {@link Move#getStatChanges()} 声明增减目标，引擎按实际变化量播报。</p>
 *
 * <p>命中率：{@code -1}（必中）与 {@code >= 100} 恒命中且<b>不消耗随机数</b>，
 * 仅 {@code 0 ~ 99} 才掷骰；未命中不造成伤害、不施加附加效果、不产生命中演出事件。</p>
 *
 * <p>个体值统一传 0（{@link Stats#randomIv} 不参与），配合 {@link ScriptedRandom} /
 * {@link RecordingRandom} 固定随机源，使断言完全可复现。</p>
 */
class BattleEngineStatStageTest {

    /** 无个体值：属性只由种族值与等级决定，便于断言。 */
    private static final Stats NO_IV = new Stats(0, 0, 0, 0, 0, 0);

    /** 低速高耐久玩家用普通系物理技能：威力 1，只用于推动回合。 */
    private static final Move TAP = move("m_tap", "轻拍", MoveCategory.PHYSICAL, 1, 100, List.of());

    /** 敌方使用的普通系物理技能：威力用于观察能力等级对伤害的影响。 */
    private static final Move SLAM = move("m_slam", "猛撞", MoveCategory.PHYSICAL, 60, 100, List.of());

    /** 完全无效果的变化技能：供被动敌方使用，使日志中不可能出现伤害播报。 */
    private static final Move NO_EFFECT = move("m_guard", "变硬", MoveCategory.STATUS, 0, 100, List.of());

    /** 叫声：降低对方物攻一级（必中）。 */
    private static final Move GROWL = move("m_growl", "叫声", MoveCategory.STATUS, 0, 100,
            List.of(new StatChange(StatChange.Recipient.OPPONENT, Stat.ATTACK, -1)));

    /** 硬邦邦：提高自身物防一级（必中）。 */
    private static final Move HARDEN = move("m_harden", "硬邦邦", MoveCategory.STATUS, 0, -1,
            List.of(new StatChange(StatChange.Recipient.SELF, Stat.DEFENSE, 1)));

    /** 高速移动：提高自身速度两级（必中）。 */
    private static final Move AGILITY = move("m_agility", "高速移动", MoveCategory.STATUS, 0, -1,
            List.of(new StatChange(StatChange.Recipient.SELF, Stat.SPEED, 2)));

    private static Move move(String id, String name, MoveCategory category, int power, int accuracy,
                             List<StatChange> statChanges) {
        return new Move(id, name, ElementType.NORMAL, category, power, accuracy, 40, 0,
                MoveEffect.NONE, StatusCondition.NONE, 0, statChanges);
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

    /** 记录每次 {@code nextInt} 的 bound，用于断言「是否真的掷了命中骰」。 */
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

    private static Species species(String id, int hp, int atk, int def, int spe) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(hp, atk, def, atk, def, spe), 100,
                List.of(), null, 0, Map.of());
    }

    /** 玩家精灵：速度 100、耐久足够撑住反打。 */
    private static Pokemon playerPokemon(Move... moves) {
        List<Move> pool = new ArrayList<>();
        pool.add(TAP);
        pool.addAll(List.of(moves));
        return Pokemon.create(species("p_sp", 300, 20, 300, 100), 50, pool, NO_IV);
    }

    /** 敌方精灵：只会 {@link #SLAM}，速度 150（比玩家基础速度更快）。 */
    private static Pokemon foePokemon() {
        return Pokemon.create(species("f_sp", 5000, 100, 100, 150), 50, List.of(SLAM), NO_IV);
    }

    /** 被动敌方精灵：只会无效果变化技能，不造成伤害，用于隔离玩家的命中判定。 */
    private static Pokemon passiveFoe() {
        return Pokemon.create(species("f_passive", 5000, 20, 100, 150), 50, List.of(NO_EFFECT), NO_IV);
    }

    private static BattleService battle(Pokemon mine, Pokemon foe, Random random) {
        Player player = new Player("玩家");
        player.addPokemon(mine);
        return BattleServices.newBattle(player, foe, random);
    }

    /** 默认随机源：命中骰恒过（0）、伤害浮动 0.9。 */
    private static BattleService battle(Pokemon mine, Pokemon foe) {
        return battle(mine, foe, new ScriptedRandom(0, 0.9));
    }

    // ------------------------------------------------------------------
    // 模型层：倍率、裁剪与清理
    // ------------------------------------------------------------------

    @Test
    void 能力等级倍率遵循正作数值() {
        assertEquals(1.0, Pokemon.stageMultiplier(0), 1e-9);
        assertEquals(1.5, Pokemon.stageMultiplier(1), 1e-9);
        assertEquals(2.0, Pokemon.stageMultiplier(2), 1e-9);
        assertEquals(3.0, Pokemon.stageMultiplier(4), 1e-9);
        assertEquals(4.0, Pokemon.stageMultiplier(6), 1e-9);
        assertEquals(2.0 / 3.0, Pokemon.stageMultiplier(-1), 1e-9);
        assertEquals(0.5, Pokemon.stageMultiplier(-2), 1e-9);
        assertEquals(1.0 / 3.0, Pokemon.stageMultiplier(-4), 1e-9);
        assertEquals(0.25, Pokemon.stageMultiplier(-6), 1e-9);
    }

    @Test
    void 能力等级裁剪在正负六之间且返回实际变化量() {
        Pokemon p = playerPokemon();
        assertEquals(6, p.changeStatStage(Stat.ATTACK, 6));
        assertEquals(6, p.getStatStage(Stat.ATTACK));
        assertEquals(0, p.changeStatStage(Stat.ATTACK, 1), "已达上限时实际变化量为 0");
        assertEquals(-1, p.changeStatStage(Stat.ATTACK, -1));
        assertEquals(5, p.getStatStage(Stat.ATTACK));
        p.clearStatStages();
        assertEquals(-6, p.changeStatStage(Stat.DEFENSE, -9), "单次跌幅裁剪到 -6");
        assertEquals(0, p.changeStatStage(Stat.DEFENSE, -1), "已达下限时实际变化量为 0");
        assertEquals(0, p.changeStatStage(null, 1), "空能力项不产生变化");
        assertEquals(0, p.changeStatStage(Stat.SPEED, 0));
    }

    @Test
    void 能力等级只影响对应能力项且中立时行为不变() {
        Pokemon p = playerPokemon();
        int baseAttack = p.effectiveAttack();
        int baseDefense = p.effectiveDefense();
        int baseSpAttack = p.effectiveSpAttack();
        int baseSpDefense = p.effectiveSpDefense();
        int baseSpeed = p.effectiveSpeed();

        assertTrue(p.hasNoStatStages(), "新建个体应无任何能力等级");

        p.changeStatStage(Stat.ATTACK, 2);
        assertEquals(baseAttack * 2, p.effectiveAttack());
        assertEquals(baseDefense, p.effectiveDefense(), "其它能力项不应被连带影响");
        assertEquals(baseSpAttack, p.effectiveSpAttack());
        assertEquals(baseSpDefense, p.effectiveSpDefense());
        assertEquals(baseSpeed, p.effectiveSpeed());

        p.changeStatStage(Stat.ATTACK, -4); // 净 -2
        assertEquals(Math.max(1, (int) Math.round(baseAttack * 0.5)), p.effectiveAttack());
    }

    @Test
    void 清除能力等级后回到中立数值() {
        Pokemon p = playerPokemon();
        int base = p.effectiveAttack();
        p.changeStatStage(Stat.ATTACK, 3);
        assertTrue(p.effectiveAttack() > base);
        p.clearStatStages();
        assertTrue(p.hasNoStatStages());
        assertEquals(base, p.effectiveAttack());
    }

    @Test
    void 完全恢复会清除能力等级() {
        Pokemon p = playerPokemon();
        p.changeStatStage(Stat.DEFENSE, 2);
        p.fullRestore();
        assertTrue(p.hasNoStatStages(), "回满 HP/PP 的恢复应同时清除挥发性能力等级");
    }

    // ------------------------------------------------------------------
    // 数据层：声明解析与 CSV 装配
    // ------------------------------------------------------------------

    @Test
    void 解析能力等级变化声明() {
        assertEquals(List.of(new StatChange(StatChange.Recipient.SELF, Stat.SPEED, 2)),
                StatChange.parseAll("SELF:SPEED:+2"));
        assertEquals(List.of(
                        new StatChange(StatChange.Recipient.SELF, Stat.SPEED, 2),
                        new StatChange(StatChange.Recipient.OPPONENT, Stat.ATTACK, -1)),
                StatChange.parseAll("SELF:SPEED:+2;OPPONENT:ATTACK:-1"));
        assertEquals(List.of(), StatChange.parseAll(null));
        assertEquals(List.of(), StatChange.parseAll("  "));
        assertEquals(List.of(), StatChange.parseAll("SELF:UNKNOWN:+1"), "未知能力项应被忽略");
        assertEquals(List.of(), StatChange.parseAll("SELF:ATTACK:0"), "幅度为 0 应被忽略");
        assertEquals(List.of(), StatChange.parseAll("SELF:ATTACK"), "字段不足应被忽略");
        assertEquals(new StatChange(StatChange.Recipient.SELF, Stat.SPEED, 6),
                StatChange.parseAll("SELF:SPEED:+99").get(0), "幅度裁剪到 +6");
        assertThrows(IllegalArgumentException.class,
                () -> new StatChange(null, Stat.ATTACK, 1));
    }

    @Test
    void 招式数据文件的能力等级变化已装配到招式上() {
        GameData data = GameData.instance();
        assertEquals(List.of(new StatChange(StatChange.Recipient.OPPONENT, Stat.ATTACK, -1)),
                data.move("growl").getStatChanges(), "叫声应降低对方物攻");
        assertEquals(List.of(new StatChange(StatChange.Recipient.OPPONENT, Stat.DEFENSE, -1)),
                data.move("tail-whip").getStatChanges(), "摇尾巴应降低对方物防");
        assertEquals(List.of(new StatChange(StatChange.Recipient.SELF, Stat.DEFENSE, 1)),
                data.move("harden").getStatChanges(), "硬邦邦应提高自身物防");
        assertEquals(List.of(new StatChange(StatChange.Recipient.SELF, Stat.SPEED, 2)),
                data.move("agility").getStatChanges(), "高速移动应提高自身速度两级");
        assertTrue(data.move("tackle").getStatChanges().isEmpty(), "无效果招式不应声明能力等级变化");
    }

    // ------------------------------------------------------------------
    // 引擎层：能力等级生效
    // ------------------------------------------------------------------

    @Test
    void 叫声降低对方物攻并削弱其输出() {
        Pokemon mine = playerPokemon(GROWL);
        Pokemon foe = foePokemon();
        int attackBefore = foe.effectiveAttack();

        BattleService engine = battle(mine, foe);
        assertTrue(engine.getLog().isEmpty());
        engine.useMove(mine.getMoveSlots().get(1));

        assertEquals(-1, foe.getStatStage(Stat.ATTACK));
        assertTrue(foe.effectiveAttack() < attackBefore, "对方物攻应实际下降");
        assertTrue(joinLog(engine).contains("物攻"), "应播报能力变化：" + joinLog(engine));
        assertFalse(joinLog(engine).contains("什么也没有发生"), "不应再落到无效果兜底");
    }

    @Test
    void 硬邦邦提高自身物防并减少受到的伤害() {
        Pokemon mine = playerPokemon(HARDEN);
        int defenseBefore = mine.effectiveDefense();
        Pokemon foe = foePokemon();

        BattleService engine = battle(mine, foe);
        engine.useMove(mine.getMoveSlots().get(1));

        assertEquals(1, mine.getStatStage(Stat.DEFENSE));
        assertTrue(mine.effectiveDefense() > defenseBefore);
        assertTrue(joinLog(engine).contains("物防"));
    }

    @Test
    void 能力等级上限时提示已无法再提高() {
        Pokemon mine = playerPokemon(HARDEN);
        Pokemon foe = foePokemon();
        BattleService engine = battle(mine, foe);

        engine.useMove(mine.getMoveSlots().get(1)); // +1
        // PP 已扣，用反弹回 PP 的方式难以连发；直接补齐等级再打一次以验证上限文案
        while (mine.getStatStage(Stat.DEFENSE) < Pokemon.MAX_STAT_STAGE) {
            mine.changeStatStage(Stat.DEFENSE, 1);
        }
        mine.getMoveSlots().get(1).restore(1);
        engine.useMove(mine.getMoveSlots().get(1));

        assertEquals(Pokemon.MAX_STAT_STAGE, mine.getStatStage(Stat.DEFENSE));
        assertTrue(joinLog(engine).contains("已经无法再提高了"), "应提示已达上限：" + joinLog(engine));
    }

    @Test
    void 高速移动改变先后手() {
        Pokemon mine = playerPokemon(AGILITY);
        Pokemon foe = foePokemon();
        assertTrue(foe.effectiveSpeed() > mine.effectiveSpeed(), "前置条件：敌方基础速度更快");

        BattleService engine = battle(mine, foe);
        engine.useMove(mine.getMoveSlots().get(1)); // 第 1 回合：提速
        assertEquals(2, mine.getStatStage(Stat.SPEED));
        assertTrue(mine.effectiveSpeed() > foe.effectiveSpeed(), "提速后应反超敌方");

        List<String> turn2 = engine.useMove(mine.getMoveSlots().get(0)); // 第 2 回合
        int mineAt = indexOfContaining(turn2, "p_sp 使用了");
        int foeAt = indexOfContaining(turn2, "f_sp 使用了");
        assertTrue(mineAt >= 0 && foeAt >= 0, "双方本回合都应行动：" + turn2);
        assertTrue(mineAt < foeAt, "提速后玩家应先手：" + turn2);
    }

    @Test
    void 战斗开始时清空双方能力等级() {
        Pokemon mine = playerPokemon();
        Pokemon foe = foePokemon();
        mine.changeStatStage(Stat.ATTACK, 3);
        foe.changeStatStage(Stat.DEFENSE, -2);

        battle(mine, foe);

        assertTrue(mine.hasNoStatStages(), "战斗开始应清除玩家残留能力等级");
        assertTrue(foe.hasNoStatStages(), "战斗开始应清除敌方残留能力等级");
    }

    @Test
    void 换宠清除下场精灵的能力等级() {
        Pokemon first = playerPokemon(GROWL);
        Pokemon second = Pokemon.create(species("q_sp", 300, 20, 300, 100), 50, List.of(TAP), NO_IV);
        Pokemon foe = foePokemon();
        Player player = new Player("玩家");
        player.addPokemon(first);
        player.addPokemon(second);

        BattleService engine = BattleServices.newBattle(player, foe, new ScriptedRandom(0, 0.9));
        engine.useMove(first.getMoveSlots().get(1)); // 叫声（顺带验证能力等级可施加）
        first.changeStatStage(Stat.DEFENSE, 2);

        engine.switchActive(1);

        assertTrue(first.hasNoStatStages(), "下场的精灵应放弃其能力等级");
        assertTrue(second.hasNoStatStages(), "上场的精灵应从中立等级开始");
    }

    // ------------------------------------------------------------------
    // 命中率判定
    // ------------------------------------------------------------------

    @Test
    void 命中率为负表示必中且不消耗随机数() {
        Move sureHit = move("m_sure", "必中拳", MoveCategory.PHYSICAL, 10, -1, List.of());
        Pokemon mine = Pokemon.create(species("p_sp", 300, 20, 300, 100), 50, List.of(sureHit), NO_IV);
        Pokemon foe = foePokemon();
        RecordingRandom random = new RecordingRandom(99, 0.9);

        battle(mine, foe, random).useMove(mine.getMoveSlots().get(0));

        assertTrue(foe.getCurrentHp() < foe.getMaxHp(), "必中招式应造成伤害");
        assertFalse(random.intBounds.contains(100), "必中不应掷命中骰：" + random.intBounds);
    }

    @Test
    void 命中率一百恒命中且不消耗随机数() {
        Pokemon mine = playerPokemon(); // 轻拍命中率 100
        Pokemon foe = foePokemon();
        RecordingRandom random = new RecordingRandom(99, 0.9);

        battle(mine, foe, random).useMove(mine.getMoveSlots().get(0));

        assertTrue(foe.getCurrentHp() < foe.getMaxHp(), "命中率 100 应造成伤害");
        assertFalse(random.intBounds.contains(100), "命中率 100 不应掷命中骰：" + random.intBounds);
    }

    @Test
    void 未通过命中判定时不造成伤害且不播报命中() {
        Move shaky = move("m_shaky", "乱打", MoveCategory.PHYSICAL, 10, 50, List.of());
        Pokemon mine = Pokemon.create(species("p_sp", 300, 20, 300, 100), 50, List.of(shaky), NO_IV);
        Pokemon foe = passiveFoe();
        RecordingRandom random = new RecordingRandom(50, 0.9); // nextInt(100) == 50，未达 50，判定失败

        BattleService engine = battle(mine, foe, random);
        int foeHp = foe.getCurrentHp();
        engine.useMove(mine.getMoveSlots().get(0));

        assertTrue(random.intBounds.contains(100), "命中率 50 应掷命中骰：" + random.intBounds);
        assertEquals(foeHp, foe.getCurrentHp(), "未命中不应造成伤害");
        assertTrue(joinLog(engine).contains("没有命中"), "应播报未命中：" + joinLog(engine));
        assertFalse(joinLog(engine).contains("造成"), "未命中不应有伤害播报：" + joinLog(engine));
    }

    @Test
    void 通过命中判定时造成伤害() {
        Move shaky = move("m_shaky", "乱打", MoveCategory.PHYSICAL, 10, 50, List.of());
        Pokemon mine = Pokemon.create(species("p_sp", 300, 20, 300, 100), 50, List.of(shaky), NO_IV);
        Pokemon foe = foePokemon();

        BattleService engine = battle(mine, foe, new RecordingRandom(49, 0.9)); // 49 < 50，命中
        engine.useMove(mine.getMoveSlots().get(0));

        assertTrue(foe.getCurrentHp() < foe.getMaxHp(), "命中应造成伤害");
        assertTrue(joinLog(engine).contains("造成"), "应播报伤害：" + joinLog(engine));
    }

    @Test
    void 未命中时不施加异常状态() {
        Move shakyParalysis = new Move("m_shaky", "麻痹针", ElementType.NORMAL, MoveCategory.STATUS,
                0, 50, 20, 0, MoveEffect.NONE, StatusCondition.PARALYSIS, 100);
        Pokemon mine = Pokemon.create(species("p_sp", 300, 20, 300, 100), 50,
                List.of(shakyParalysis), NO_IV);
        Pokemon foe = passiveFoe();

        BattleService engine = battle(mine, foe, new ScriptedRandom(50, 0.9)); // 命中失败
        engine.useMove(mine.getMoveSlots().get(0));

        assertEquals(StatusCondition.NONE, foe.getStatus(), "未命中不应施加异常状态");
    }

    @Test
    void 未命中时不改变能力等级() {
        Move shakyGrowl = move("m_shaky_growl", "小声叫", MoveCategory.STATUS, 0, 50,
                List.of(new StatChange(StatChange.Recipient.OPPONENT, Stat.ATTACK, -1)));
        Pokemon mine = Pokemon.create(species("p_sp", 300, 20, 300, 100), 50, List.of(shakyGrowl), NO_IV);
        Pokemon foe = passiveFoe();

        BattleService engine = battle(mine, foe, new ScriptedRandom(50, 0.9)); // 命中失败
        engine.useMove(mine.getMoveSlots().get(0));

        assertEquals(0, foe.getStatStage(Stat.ATTACK), "未命中不应施加能力等级变化");
        assertFalse(joinLog(engine).contains("物攻"), "未命中不应播报能力变化：" + joinLog(engine));
    }

    @Test
    void 未命中不产生命中演出事件() {
        Move shaky = move("m_shaky", "乱打", MoveCategory.PHYSICAL, 10, 50, List.of());
        Pokemon mine = Pokemon.create(species("p_sp", 300, 20, 300, 100), 50, List.of(shaky), NO_IV);
        Pokemon foe = passiveFoe();

        BattleService engine = battle(mine, foe, new ScriptedRandom(50, 0.9));
        engine.drainEvents(); // 丢弃开场事件
        engine.useMove(mine.getMoveSlots().get(0));
        List<BattleEvent> events = engine.drainEvents();

        // 玩家招式只应有 MOVE 事件（未命中 → 无 HIT）；敌方命中会产生自己的 MOVE + HIT
        long playerHits = events.stream()
                .filter(e -> e.kind() == BattleEvent.Kind.HIT && e.side() == BattleEvent.Side.FOE)
                .count();
        assertEquals(0, playerHits, "未命中不应在敌方身上产生命中事件：" + events);
        assertTrue(events.stream().anyMatch(e -> e.kind() == BattleEvent.Kind.MOVE
                && e.side() == BattleEvent.Side.PLAYER), "未命中仍应保留出招事件");
    }

    // ------------------------------------------------------------------

    private static String joinLog(BattleService engine) {
        return String.join("\n", engine.getLog());
    }

    private static int indexOfContaining(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }
}
