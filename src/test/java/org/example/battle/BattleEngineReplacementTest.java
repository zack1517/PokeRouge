package org.example.battle;

import org.example.model.ElementType;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stat;
import org.example.model.Stats;
import org.example.model.Trainer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 己方出战精灵倒下后的「补位」测试：引擎不再自动换宠，而是挂起等待玩家调用
 * {@link BattleService#chooseReplacement(int)} 选出下一只上场精灵。
 *
 * <p>覆盖三点契约：<b>硬门禁</b>（等待期间一切回合行动抛 {@link IllegalStateException}）、
 * <b>补位语义</b>（不消耗回合、敌方不行动、上场精灵从中立状态开始并产生放出事件）、
 * <b>非法选择</b>（倒下的精灵 / 越界下标不生效且保持等待）。</p>
 *
 * <p>测试数据手工构造：高速一击必杀的敌方精灵固定先手，低速队员必被击倒，配合固定随机源
 * 即可精确复现「队员倒下 → 等待补位」的局面。</p>
 */
class BattleEngineReplacementTest {

    /** 一击必杀的普通系物理技能。 */
    private static final Move SLAM = new Move("m_slam", "猛击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 500, 100, 40);

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

    private static Species species(String id, int hp, int atk, int def, int spe) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(hp, atk, def, atk, def, spe), 100,
                List.of(), null, 0, Map.of());
    }

    /** 低速低耐久队员：必被敌方一击击倒。 */
    private static Pokemon weak(String id) {
        return Pokemon.create(species(id + "_sp", 40, 10, 40, 10), 20, List.of(SLAM));
    }

    /** 高速高耐久精灵：先手且不会被低速队员击倒。 */
    private static Pokemon strong(String id) {
        return Pokemon.create(species(id + "_sp", 300, 200, 40, 200), 20, List.of(SLAM));
    }

    private static Player playerWith(Pokemon... party) {
        Player player = new Player("玩家");
        for (Pokemon p : party) {
            player.addPokemon(p);
        }
        return player;
    }

    /** 打到「首只倒下、等待补位」的野生遭遇：返回已挂起的战斗。 */
    private static BattleService awaitingWildBattle(Pokemon... party) {
        BattleService battle = BattleServices.newBattle(playerWith(party), strong("wild_sp"),
                new FixedRandom(0.5));
        battle.drainEvents();
        battle.useMove(battle.playerActive().getMoveSlots().get(0));
        battle.drainEvents();
        assertTrue(battle.isAwaitingReplacement(), "首只倒下后应进入等待补位状态");
        return battle;
    }

    // ------------------------------------------------------------------
    // 硬门禁：等待补位期间拒绝一切回合行动
    // ------------------------------------------------------------------

    @Test
    void 等待补位时战斗未结束但拒绝一切回合行动() {
        BattleService battle = awaitingWildBattle(weak("mine_a"), strong("mine_b"));

        assertTrue(battle.isOngoing(), "等待补位不属于战斗结束");
        assertTrue(battle.isAwaitingReplacement());
        assertThrows(IllegalStateException.class,
                () -> battle.useMove(battle.playerActive().getMoveSlots().get(0)));
        assertThrows(IllegalStateException.class, battle::tryRun);
        assertThrows(IllegalStateException.class, () -> battle.switchActive(1));
        assertThrows(IllegalStateException.class, () -> battle.useItem(
                new Item("i_potion", "伤药", ItemCategory.HEAL, 20)));
        assertTrue(battle.isAwaitingReplacement(), "被拒绝的行动不应改变等待状态");
    }

    @Test
    void 非等待状态下调用补位抛异常() {
        BattleService battle = BattleServices.newBattle(playerWith(weak("mine_a"), strong("mine_b")),
                strong("wild_sp"), new FixedRandom(0.5));

        assertFalse(battle.isAwaitingReplacement());
        assertThrows(IllegalStateException.class, () -> battle.chooseReplacement(1));
    }

    // ------------------------------------------------------------------
    // 补位语义：不消耗回合、敌方不行动、产生放出事件
    // ------------------------------------------------------------------

    @Test
    void 补位后恢复行动且新精灵满血上场() {
        Pokemon backup = strong("mine_b");
        BattleService battle = awaitingWildBattle(weak("mine_a"), backup);

        List<String> logs = battle.chooseReplacement(1);

        assertFalse(battle.isAwaitingReplacement(), "选完替补即恢复行动");
        assertEquals(backup, battle.playerActive());
        assertEquals(backup.getMaxHp(), backup.getCurrentHp(), "补位不消耗回合，新精灵不应挨打");
        assertTrue(logs.stream().anyMatch(line -> line.contains("派出了")), "应有派出提示: " + logs);

        // 恢复行动后可以正常出招
        battle.useMove(battle.playerActive().getMoveSlots().get(0));
        assertFalse(battle.isAwaitingReplacement());
    }

    @Test
    void 补位产生玩家放出事件() {
        BattleService battle = awaitingWildBattle(weak("mine_a"), strong("mine_b"));

        battle.chooseReplacement(1);

        List<BattleEvent> events = battle.drainEvents();
        assertEquals(1, events.size(), "补位只产生一条放出事件: " + events);
        BattleEvent out = events.get(0);
        assertEquals(BattleEvent.Kind.SEND_OUT, out.kind());
        assertEquals(BattleEvent.Side.PLAYER, out.side());
        assertEquals("mine_b_sp", out.actor());
        assertTrue(out.hp().present(), "放出事件应携带 HP 快照");
    }

    @Test
    void 补位上场的精灵从中立状态开始() {
        Pokemon backup = strong("mine_b");
        backup.changeStatStage(Stat.ATTACK, 2);
        backup.setSeeded(true);
        BattleService battle = awaitingWildBattle(weak("mine_a"), backup);

        battle.chooseReplacement(1);

        assertTrue(backup.hasNoStatStages(), "上场后应清除能力等级");
        assertFalse(backup.isSeeded(), "上场后应清除寄生种子等挥发性状态");
    }

    // ------------------------------------------------------------------
    // 非法选择：不生效且保持等待
    // ------------------------------------------------------------------

    @Test
    void 选择已倒下的精灵不生效且保持等待() {
        BattleService battle = awaitingWildBattle(weak("mine_a"), strong("mine_b"));
        Pokemon faintedLead = battle.playerActive();

        List<String> logs = battle.chooseReplacement(0);

        assertTrue(battle.isAwaitingReplacement(), "非法选择后应仍等待补位");
        assertEquals(faintedLead, battle.playerActive(), "出战精灵不应改变");
        assertTrue(battle.playerActive().isFainted(), "倒下的精灵仍留在场上等待玩家重新选择");
        assertTrue(logs.stream().anyMatch(line -> line.contains("倒下的精灵不能上场")), "应有提示: " + logs);
    }

    @Test
    void 越界下标不生效且保持等待() {
        BattleService battle = awaitingWildBattle(weak("mine_a"), strong("mine_b"));
        Pokemon faintedLead = battle.playerActive();

        battle.chooseReplacement(9);

        assertTrue(battle.isAwaitingReplacement(), "越界下标后应仍等待补位");
        assertEquals(faintedLead, battle.playerActive(), "出战精灵不应改变");
    }

    // ------------------------------------------------------------------
    // 无健康精灵：直接判负而非等待
    // ------------------------------------------------------------------

    @Test
    void 队伍全倒时直接判负而非等待补位() {
        Player player = playerWith(weak("mine_a"));
        Trainer trainer = new Trainer("强敌");
        trainer.addPokemon(strong("boss"));
        BattleService battle = BattleServices.newTrainerBattle(player, trainer, new FixedRandom(0.5));

        battle.useMove(player.getActive().getMoveSlots().get(0));

        assertEquals(BattleService.Status.PLAYER_LOSE, battle.getStatus());
        assertFalse(battle.isAwaitingReplacement(), "没有健康精灵时不应等待补位");
        assertThrows(IllegalStateException.class, () -> battle.chooseReplacement(0));
    }
}
