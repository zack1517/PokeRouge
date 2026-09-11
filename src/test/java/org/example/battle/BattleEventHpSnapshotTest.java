package org.example.battle;

import org.example.model.ElementType;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.Trainer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件 HP 快照测试：每个「具备动画价值」的事件都携带受影响一方<b>变化后</b>的 HP
 * （{@link BattleEvent.Hp}），界面在播放该步动画<b>之前</b>先把血条刷成该数值，
 * 从而实现「一方出手 → 对方先扣血再播受击动画 → 另一方出手」的演出顺序。
 *
 * <p>快照是这条演出时序的唯一数据来源（事件顺序本身由引擎保证、不由界面推断），
 * 因此这里把它当契约固定下来：受击 / 倒下 / 道具 / 放出事件必须带可用的快照，
 * 且数值等于压入事件那一刻该精灵的真实 HP。</p>
 *
 * <p>测试数据手工构造，速度快的一方固定先手（速度 300 : 10），配合 {@code nextInt→0}
 * 等固定随机源即可精确断言，无需统计采样。</p>
 */
class BattleEventHpSnapshotTest {

    /** 低威力普通系物理技能：让双方都能互相打一下而不至于击倒。 */
    private static final Move TAP = new Move("m_tap", "轻拍", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 1, 100, 40);

    /** 高威力火系特殊技能：对低耐久目标稳定一击必杀。 */
    private static final Move BLAST = new Move("m_blast", "喷射火焰", ElementType.FIRE,
            MoveCategory.SPECIAL, 500, 100, 40);

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
        return Pokemon.create(species(id, 300, 200, 300), 20, List.of(TAP, BLAST));
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

    // ------------------------------------------------------------------
    // 受击：快照必须是扣血后的数值
    // ------------------------------------------------------------------

    @Test
    void 受击事件携带该方扣血后的HP快照() {
        BattleService battle = wildBattle(0.5, fast("mine_sp"));
        battle.drainEvents();
        Pokemon mine = battle.playerActive();
        Pokemon wild = battle.foeActive();
        int mineBefore = mine.getCurrentHp();
        int wildBefore = wild.getCurrentHp();

        battle.useMove(mine.getMoveSlots().get(0)); // 轻拍

        List<BattleEvent> events = battle.drainEvents();
        assertEquals(4, events.size(), "双方各出手一次应产生 释放/受击 两组事件: " + events);

        BattleEvent foeHit = events.get(1);
        assertEquals(BattleEvent.Kind.HIT, foeHit.kind());
        assertEquals(BattleEvent.Side.FOE, foeHit.side(), "先手方的对面先受击");
        assertTrue(foeHit.hp().present(), "受击事件必须携带 HP 快照");
        assertEquals(wildBefore, foeHit.hp().max());
        assertTrue(foeHit.hp().current() < wildBefore,
                "快照应是扣血后的数值，界面才能在受击动画开始时就显示掉血: " + foeHit.hp());
        assertEquals(wild.getCurrentHp(), foeHit.hp().current(), "快照应与压入事件那一刻的真实 HP 一致");

        BattleEvent playerHit = events.get(3);
        assertEquals(BattleEvent.Kind.HIT, playerHit.kind());
        assertEquals(BattleEvent.Side.PLAYER, playerHit.side());
        assertTrue(playerHit.hp().present());
        assertTrue(playerHit.hp().current() < mineBefore, "后手方受击同样先扣血: " + playerHit.hp());
        assertEquals(mine.getCurrentHp(), playerHit.hp().current());
    }

    @Test
    void 双方出手顺序为释放与受击交替且受击方为对面() {
        BattleService battle = wildBattle(0.5, fast("mine_sp"));
        battle.drainEvents();

        battle.useMove(battle.playerActive().getMoveSlots().get(0));

        List<BattleEvent> events = battle.drainEvents();
        assertEquals(BattleEvent.Kind.MOVE, events.get(0).kind());
        assertEquals(BattleEvent.Side.PLAYER, events.get(0).side());
        assertEquals(BattleEvent.Kind.HIT, events.get(1).kind());
        assertEquals(BattleEvent.Side.FOE, events.get(1).side(), "受击方是出手方的对面");

        assertEquals(BattleEvent.Kind.MOVE, events.get(2).kind());
        assertEquals(BattleEvent.Side.FOE, events.get(2).side(), "第一个人的演出结束后才轮到另一个人");
        assertEquals(BattleEvent.Kind.HIT, events.get(3).kind());
        assertEquals(BattleEvent.Side.PLAYER, events.get(3).side());
    }

    @Test
    void 一击必杀时受击与倒下事件携带零血量快照() {
        BattleService battle = BattleServices.newBattle(playerWith(fast("mine_sp")),
                slow("wild_sp", 40), new FixedRandom(0.5));
        battle.drainEvents();
        Pokemon wild = battle.foeActive();
        int wildMax = wild.getMaxHp();

        battle.useMove(battle.playerActive().getMoveSlots().get(1)); // 喷射火焰

        List<BattleEvent> events = battle.drainEvents();
        assertEquals(3, events.size(), "一击必杀只应产生 释放/受击/倒下 三个事件: " + events);

        BattleEvent hit = events.get(1);
        assertEquals(BattleEvent.Kind.HIT, hit.kind());
        assertTrue(hit.hp().present());
        assertEquals(0, hit.hp().current(), "先扣血到 0，界面在受击时即显示空血条");
        assertEquals(wildMax, hit.hp().max());

        BattleEvent faint = events.get(2);
        assertEquals(BattleEvent.Kind.FAINT, faint.kind());
        assertTrue(faint.hp().present(), "倒下事件必须携带 HP 快照");
        assertEquals(0, faint.hp().current());
    }

    // ------------------------------------------------------------------
    // 道具与放出
    // ------------------------------------------------------------------

    @Test
    void 道具事件携带场上精灵的HP快照() {
        Item potion = new Item("i_potion", "伤药", ItemCategory.HEAL, 50);
        Pokemon hurt = fast("mine_sp");
        hurt.takeDamage(120);
        Player player = playerWith(hurt);
        player.getBag().add(potion, 1);
        BattleService battle = BattleServices.newBattle(player, slow("wild_sp", 300), new FixedRandom(0.5));
        battle.drainEvents();
        int maxHp = hurt.getMaxHp();
        int expected = Math.min(maxHp, hurt.getCurrentHp() + 50);
        assertTrue(expected > hurt.getCurrentHp(), "前置条件：伤药应有回复空间");

        battle.useItem(potion);

        BattleEvent event = battle.drainEvents().stream()
                .filter(e -> e.kind() == BattleEvent.Kind.ITEM)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未产生 ITEM 事件"));
        assertTrue(event.hp().present(), "道具事件必须携带 HP 快照，回复要在道具动画前体现在血条上");
        assertEquals(expected, event.hp().current(), "快照应是回复后的场上精灵 HP");
        assertEquals(maxHp, event.hp().max());
    }

    @Test
    void 补位的放出事件携带新上场精灵的HP快照() {
        Pokemon frontline = Pokemon.create(species("mine_sp", 40, 10, 10), 20, List.of(TAP));
        Pokemon backup = Pokemon.create(species("backup_sp", 300, 10, 10), 20, List.of(TAP));
        Trainer trainer = new Trainer("强敌");
        trainer.addPokemon(Pokemon.create(species("boss_sp", 300, 200, 300), 20, List.of(BLAST)));
        BattleService battle = BattleServices.newTrainerBattle(playerWith(frontline, backup), trainer,
                new FixedRandom(0.5));
        battle.drainEvents();

        battle.useMove(battle.playerActive().getMoveSlots().get(0));
        battle.drainEvents();
        assertTrue(battle.isAwaitingReplacement(), "倒下后应等待玩家选择替补");

        battle.chooseReplacement(1);

        List<BattleEvent> events = battle.drainEvents();
        BattleEvent last = events.get(events.size() - 1);
        assertEquals(BattleEvent.Kind.SEND_OUT, last.kind(), "补位后应放出玩家选中的精灵");
        assertEquals(BattleEvent.Side.PLAYER, last.side());
        assertEquals("backup_sp", last.actor());
        assertTrue(last.hp().present(), "放出事件必须携带 HP 快照，新精灵的血条要随放出动画一起切换");
        assertEquals(backup.getMaxHp(), last.hp().current(), "换上的是一只全新精灵");
        assertEquals(backup.getMaxHp(), last.hp().max());
    }

    // ------------------------------------------------------------------
    // 无 HP 变化的事件不带快照（界面据此跳过刷新）
    // ------------------------------------------------------------------

    @Test
    void 无HP变化的事件不带可用快照() {
        BattleService battle = wildBattle(0.3, fast("mine_sp"));
        List<BattleEvent> opening = battle.drainEvents();

        assertFalse(opening.get(0).hp().present(), "开场事件不涉及 HP 变化");

        battle.tryRun();

        BattleEvent run = battle.drainEvents().stream()
                .filter(e -> e.kind() == BattleEvent.Kind.RUN)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未产生 RUN 事件"));
        assertFalse(run.hp().present(), "逃跑事件不涉及 HP 变化");
        assertEquals(BattleEvent.Hp.NONE, run.hp());
    }

    @Test
    void 空快照视为无数据() {
        assertFalse(BattleEvent.Hp.NONE.present());
        assertFalse(new BattleEvent.Hp(0, 0).present(), "最大 HP 缺失时不算可用快照");
        assertTrue(new BattleEvent.Hp(0, 100).present(), "0 HP 是合法快照（倒下）");
    }
}
