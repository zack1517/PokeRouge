package org.example.battle;

import org.example.model.ElementType;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.StatusCondition;
import org.example.model.Trainer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 演出事件测试：战斗引擎在每次行动结算时把「具备动画价值的动作」压入事件队列，
 * 由 {@link BattleService#drainEvents()} 一次性取走。本测试覆盖事件类型、阵营、属性/分类
 * 与成败标志，并校验「取走即清空」的语义。
 *
 * <p>测试数据手工构造，速度快的一方固定先手（速度差 300 : 10），配合 {@code nextInt→0}
 * 等固定随机源即可精确断言事件序列，无需统计采样。</p>
 */
class BattleEventTest {

    /** 低威力普通系物理技能：让敌方行动但不至于击倒玩家。 */
    private static final Move TAP = new Move("m_tap", "轻拍", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 1, 100, 40);

    /** 高威力火系特殊技能：对低耐久目标稳定一击必杀。 */
    private static final Move BLAST = new Move("m_blast", "喷射火焰", ElementType.FIRE,
            MoveCategory.SPECIAL, 500, 100, 40);

    /** 必中睡眠的变化类技能（命中后必定施加睡眠）。 */
    private static final Move LULL = new Move("m_lull", "催眠术", ElementType.PSYCHIC,
            MoveCategory.STATUS, 0, 100, 40, 0, MoveEffect.NONE, StatusCondition.SLEEP, 100);

    /** 固定随机源：{@code nextDouble} 恒返回同一值，{@code nextInt}/{@code nextBoolean} 取 0。 */
    private static final class FixedRandom extends Random {

        private final double doubleValue;

        FixedRandom(double doubleValue) {
            this.doubleValue = doubleValue;
        }

        @Override
        public int nextInt(int bound) {
            return 0;
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

    private static Species species(String id, int hp, int attack, int speed) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(hp, attack, 40, attack, 40, speed), 51,
                List.of(), null, 0, Map.of());
    }

    /** 高速高攻玩家精灵：先手且不易被击倒。 */
    private static Pokemon fast(String id) {
        return Pokemon.create(species(id, 300, 200, 300), 20, List.of(TAP, BLAST, LULL));
    }

    /** 低速敌方精灵：固定后手。 */
    private static Pokemon slow(String id, int hp) {
        return Pokemon.create(species(id, hp, 20, 10), 20, List.of(TAP));
    }

    private static Player playerWith(Pokemon... party) {
        Player player = new Player("玩家");
        for (Pokemon p : party) {
            player.addPokemon(p);
        }
        return player;
    }

    private static BattleService wildBattle(double randomValue, Pokemon... party) {
        return BattleServices.newBattle(playerWith(party), slow("wild_sp", 300), new FixedRandom(randomValue));
    }

    /** 取本回合中第一个指定类型的事件。 */
    private static BattleEvent first(BattleService battle, BattleEvent.Kind kind) {
        return battle.drainEvents().stream()
                .filter(e -> e.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未产生 " + kind + " 事件"));
    }

    // ------------------------------------------------------------------
    // 开场与取走语义
    // ------------------------------------------------------------------

    @Test
    void 开场事件只投递一次且取走即清空() {
        BattleService battle = wildBattle(0.5, fast("mine_sp"));

        List<BattleEvent> opening = battle.drainEvents();

        assertEquals(1, opening.size(), "构造战斗只应产生一个开场事件");
        assertEquals(BattleEvent.Kind.BATTLE_START, opening.get(0).kind());
        assertNull(opening.get(0).side(), "开场事件属于双方，阵营为空");
        assertTrue(battle.drainEvents().isEmpty(), "事件取走后不应重复投递");
    }

    // ------------------------------------------------------------------
    // 技能释放与受击
    // ------------------------------------------------------------------

    @Test
    void 伤害招式产生释放与受击事件并携带属性分类() {
        BattleService battle = wildBattle(0.5, fast("mine_sp"));
        battle.drainEvents();

        battle.useMove(battle.playerActive().getMoveSlots().get(1)); // 喷射火焰

        List<BattleEvent> events = battle.drainEvents();
        BattleEvent cast = events.get(0);
        assertEquals(BattleEvent.Kind.MOVE, cast.kind());
        assertEquals(BattleEvent.Side.PLAYER, cast.side());
        assertEquals("mine_sp", cast.actor());
        assertEquals("喷射火焰", cast.moveName());
        assertEquals(ElementType.FIRE, cast.element(), "弹道配色取招式属性");
        assertEquals(MoveCategory.SPECIAL, cast.category(), "弹道形态取招式分类");

        BattleEvent hit = events.get(1);
        assertEquals(BattleEvent.Kind.HIT, hit.kind());
        assertEquals(BattleEvent.Side.FOE, hit.side(), "受击方是野生精灵");
        assertEquals(ElementType.FIRE, hit.element());
        assertEquals(BattleEvent.Side.PLAYER, hit.opponent(), "受击方的对方即施法方");
    }

    @Test
    void 击倒时在受击后追加倒下事件() {
        BattleService battle = BattleServices.newBattle(playerWith(fast("mine_sp")),
                slow("wild_sp", 40), new FixedRandom(0.5));
        battle.drainEvents();

        battle.useMove(battle.playerActive().getMoveSlots().get(1));

        List<BattleEvent> events = battle.drainEvents();
        assertEquals(3, events.size(), "一击必杀只应产生 释放/受击/倒下 三个事件: " + events);
        assertEquals(BattleEvent.Kind.MOVE, events.get(0).kind());
        assertEquals(BattleEvent.Kind.HIT, events.get(1).kind());
        assertEquals(BattleEvent.Kind.FAINT, events.get(2).kind());
        assertEquals(BattleEvent.Side.FOE, events.get(2).side());
        assertEquals("wild_sp", events.get(2).actor());
    }

    @Test
    void 变化招式产生变化类受击事件() {
        BattleService battle = wildBattle(0.5, fast("mine_sp"));
        battle.drainEvents();

        battle.useMove(battle.playerActive().getMoveSlots().get(2)); // 催眠术

        List<BattleEvent> events = battle.drainEvents();
        BattleEvent cast = events.get(0);
        assertEquals(BattleEvent.Kind.MOVE, cast.kind());
        assertEquals(MoveCategory.STATUS, cast.category());
        BattleEvent hit = events.get(1);
        assertEquals(BattleEvent.Kind.HIT, hit.kind());
        assertEquals(BattleEvent.Side.FOE, hit.side());
        assertEquals(MoveCategory.STATUS, hit.category(), "变化招式也应有受击演出");
    }

    // ------------------------------------------------------------------
    // 放出与收回
    // ------------------------------------------------------------------

    @Test
    void 主动换宠先收回再放出() {
        BattleService battle = wildBattle(0.5, fast("mine_a_sp"), fast("mine_b_sp"));
        battle.drainEvents();

        battle.switchActive(1);

        List<BattleEvent> events = battle.drainEvents();
        assertEquals(BattleEvent.Kind.RECALL, events.get(0).kind());
        assertEquals(BattleEvent.Side.PLAYER, events.get(0).side());
        assertEquals("mine_a_sp", events.get(0).actor(), "收回事件携带被换下的精灵名");
        assertEquals(BattleEvent.Kind.SEND_OUT, events.get(1).kind());
        assertEquals(BattleEvent.Side.PLAYER, events.get(1).side());
        assertEquals("mine_b_sp", events.get(1).actor(), "放出事件携带新上场的精灵名");
    }

    @Test
    void 敌方倒下后自动派出下一只产生放出事件() {
        Player player = playerWith(Pokemon.create(species("mine_sp", 300, 200, 300), 20, List.of(BLAST)));
        Trainer trainer = new Trainer("劲敌");
        trainer.addPokemon(slow("foe_a_sp", 40));
        trainer.addPokemon(slow("foe_b_sp", 40));
        BattleService battle = BattleServices.newTrainerBattle(player, trainer, new FixedRandom(0.5));
        battle.drainEvents();

        battle.useMove(battle.playerActive().getMoveSlots().get(0));

        List<BattleEvent> events = battle.drainEvents();
        assertEquals(BattleEvent.Kind.FAINT, events.get(2).kind());
        assertEquals(BattleEvent.Side.FOE, events.get(2).side());
        BattleEvent last = events.get(events.size() - 1);
        assertEquals(BattleEvent.Kind.SEND_OUT, last.kind());
        assertEquals(BattleEvent.Side.FOE, last.side());
        assertEquals("foe_b_sp", last.actor());
    }

    @Test
    void 己方倒下后自动换宠产生放出事件() {
        Pokemon player = Pokemon.create(species("mine_sp", 40, 10, 10), 20, List.of(TAP));
        Trainer trainer = new Trainer("强敌");
        trainer.addPokemon(Pokemon.create(species("boss_sp", 300, 200, 300), 20, List.of(BLAST)));
        Player owner = playerWith(player, Pokemon.create(species("backup_sp", 300, 10, 10), 20, List.of(TAP)));
        BattleService battle = BattleServices.newTrainerBattle(owner, trainer, new FixedRandom(0.5));
        battle.drainEvents();

        battle.useMove(owner.getActive().getMoveSlots().get(0));

        List<BattleEvent> events = battle.drainEvents();
        BattleEvent last = events.get(events.size() - 1);
        assertEquals(BattleEvent.Kind.SEND_OUT, last.kind(), "倒下后应自动放出下一只");
        assertEquals(BattleEvent.Side.PLAYER, last.side());
        assertEquals("backup_sp", last.actor());
    }

    // ------------------------------------------------------------------
    // 投球与逃跑的成败标志
    // ------------------------------------------------------------------

    @Test
    void 投球事件携带捕捉成败标志() {
        // 精灵球(×3) 对满血 51 捕获率目标的成功率为 0.2：0.1 命中、0.3 落空
        Item ball = new Item("i_poke_ball", "精灵球", ItemCategory.POKE_BALL, 3);

        Player caughtPlayer = playerWith(fast("mine_sp"));
        caughtPlayer.getBag().add(ball, 1);
        BattleService caught = BattleServices.newBattle(caughtPlayer, slow("wild_sp", 300), new FixedRandom(0.1));
        caught.drainEvents();
        caught.useItem(ball);

        BattleEvent success = first(caught, BattleEvent.Kind.CAPTURE);
        assertTrue(success.success(), "随机值低于成功率应捕捉成功");
        assertEquals("精灵球", success.actor(), "投球事件携带球名");
        assertEquals(BattleEvent.Side.FOE, success.side(), "精灵球恒投向敌方野生精灵");

        Player missedPlayer = playerWith(fast("mine_sp"));
        missedPlayer.getBag().add(ball, 1);
        BattleService missed = BattleServices.newBattle(missedPlayer, slow("wild_sp", 300), new FixedRandom(0.3));
        missed.drainEvents();
        missed.useItem(ball);

        assertFalse(first(missed, BattleEvent.Kind.CAPTURE).success(), "随机值高于成功率应挣脱");
    }

    @Test
    void 逃跑事件携带逃跑成败标志() {
        // 玩家远快于野生精灵，逃跑成功率约 0.93
        BattleService escaped = wildBattle(0.0, fast("mine_sp"));
        escaped.drainEvents();
        escaped.tryRun();

        BattleEvent flee = first(escaped, BattleEvent.Kind.RUN);
        assertTrue(flee.success(), "随机值低于成功率应逃跑成功");
        assertEquals(BattleEvent.Side.PLAYER, flee.side());

        BattleService blocked = wildBattle(0.99, fast("mine_sp"));
        blocked.drainEvents();
        blocked.tryRun();

        assertFalse(first(blocked, BattleEvent.Kind.RUN).success());
    }

    @Test
    void 训练师战逃跑不产生演出事件() {
        Player player = playerWith(fast("mine_sp"));
        Trainer trainer = new Trainer("劲敌");
        trainer.addPokemon(slow("foe_sp", 300));
        BattleService battle = BattleServices.newTrainerBattle(player, trainer, new FixedRandom(0.5));
        battle.drainEvents();

        battle.tryRun();

        assertTrue(battle.drainEvents().isEmpty(), "训练师战无法逃跑，只应追加提示日志");
    }

    // ------------------------------------------------------------------
    // 道具
    // ------------------------------------------------------------------

    @Test
    void 使用回复道具产生道具事件() {
        Item potion = new Item("i_potion", "伤药", ItemCategory.HEAL, 50);
        Pokemon hurt = fast("mine_sp");
        hurt.takeDamage(120);
        Player player = playerWith(hurt);
        player.getBag().add(potion, 1);
        BattleService battle = BattleServices.newBattle(player, slow("wild_sp", 300), new FixedRandom(0.5));
        battle.drainEvents();

        battle.useItem(potion);

        BattleEvent event = first(battle, BattleEvent.Kind.ITEM);
        assertEquals("伤药", event.actor());
        assertEquals(BattleEvent.Side.PLAYER, event.side());
    }
}
