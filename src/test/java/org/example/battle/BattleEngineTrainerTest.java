package org.example.battle;

import org.example.model.ElementType;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.Trainer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 训练师轮战引擎测试：与训练家战斗时，只有<b>某一方所有精灵全部倒下</b>才结束战斗。
 *
 * <p>测试数据全部手工构造（不依赖数据文件）：技能威力足够一击必杀，并用速度差固定先后手，
 * 从而让「谁先行动、谁被击倒」完全确定。</p>
 */
class BattleEngineTrainerTest {

    /** 高威力普通系物理技能：对本测试中的目标稳定一击必杀。 */
    private static final Move SLAM = new Move("m_slam", "猛击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 500, 100, 40);

    private static Species species(String id, int hp, int atk, int def, int spe) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(hp, atk, def, atk, def, spe), 100,
                List.of(), null, 0, Map.of());
    }

    /** 队员：低速、低耐久（必被击倒）。 */
    private static Pokemon weak(String id, int level) {
        return Pokemon.create(species(id + "_sp", 40, 10, 40, 10), level, List.of(SLAM));
    }

    /** 队员：高速、高物攻（先手且一击必杀）。 */
    private static Pokemon strong(String id, int level) {
        return Pokemon.create(species(id + "_sp", 200, 200, 40, 200), level, List.of(SLAM));
    }

    private static Player playerWith(Pokemon... party) {
        Player player = new Player("玩家");
        for (Pokemon p : party) {
            player.addPokemon(p);
        }
        return player;
    }

    private static Trainer trainerWith(String name, Pokemon... party) {
        Trainer trainer = new Trainer(name);
        for (Pokemon p : party) {
            trainer.addPokemon(p);
        }
        return trainer;
    }

    // ------------------------------------------------------------------
    // 对方整队全倒才退出
    // ------------------------------------------------------------------

    @Test
    void 敌方单只倒下时战斗继续并自动派出下一只() {
        Player player = playerWith(strong("mine", 20));
        Trainer trainer = trainerWith("劲敌", weak("foe_a", 20), weak("foe_b", 20));
        BattleService battle = BattleServices.newTrainerBattle(player, trainer);

        battle.useMove(player.getActive().getMoveSlots().get(0));

        assertEquals(BattleService.Status.ONGOING, battle.getStatus(), "还有健康精灵时战斗不应结束");
        assertTrue(trainer.getParty().get(0).isFainted());
        assertFalse(trainer.getParty().get(1).isFainted());
        assertEquals(trainer.getParty().get(1), trainer.getActive(), "应自动派出下一只健康精灵");
        assertEquals(trainer.getParty().get(1), battle.foeActive());
        assertTrue(battle.getLog().stream().anyMatch(line -> line.contains("派出了")));
        // 换宠不消耗额外行动：新上场的精灵满血，且当回合不会再攻击玩家
        assertEquals(trainer.getParty().get(1).getMaxHp(), trainer.getParty().get(1).getCurrentHp(),
                "新上场的精灵应是满血");
        assertEquals(player.getActive().getMaxHp(), player.getActive().getCurrentHp(),
                "换宠当回合新精灵不应行动");

        battle.useMove(player.getActive().getMoveSlots().get(0));

        assertEquals(BattleService.Status.PLAYER_WIN, battle.getStatus(), "对方整队倒下才获胜");
        assertTrue(trainer.isPartyAllFainted());
        assertFalse(player.isPartyAllFainted());
    }

    @Test
    void 敌方三只全部倒下才判定玩家获胜() {
        Player player = playerWith(strong("mine", 20));
        Trainer trainer = trainerWith("道馆馆主",
                weak("foe_0", 20), weak("foe_1", 20), weak("foe_2", 20));
        BattleService battle = BattleServices.newTrainerBattle(player, trainer);
        MoveSlot slot = player.getActive().getMoveSlots().get(0);

        for (int i = 0; i < 2; i++) {
            battle.useMove(slot);
            assertEquals(BattleService.Status.ONGOING, battle.getStatus(),
                    "第 " + (i + 1) + " 只倒下时战斗仍应继续");
            assertFalse(trainer.isPartyAllFainted());
        }

        battle.useMove(slot);

        assertEquals(BattleService.Status.PLAYER_WIN, battle.getStatus());
        assertTrue(trainer.isPartyAllFainted());
    }

    // ------------------------------------------------------------------
    // 己方整队全倒才退出
    // ------------------------------------------------------------------

    @Test
    void 己方单只倒下时挂起等待玩家选择替补() {
        Pokemon first = weak("mine_a", 20);
        Pokemon second = weak("mine_b", 20);
        Player player = playerWith(first, second);
        Trainer trainer = trainerWith("强敌", strong("boss", 20));
        BattleService battle = BattleServices.newTrainerBattle(player, trainer);

        battle.useMove(player.getActive().getMoveSlots().get(0));

        assertEquals(BattleService.Status.ONGOING, battle.getStatus(), "队伍仍有健康精灵时不应战败");
        assertTrue(first.isFainted());
        assertFalse(second.isFainted());
        assertTrue(battle.isAwaitingReplacement(), "倒下后应等待玩家选择替补，而不是自动换宠");
        assertEquals(first, battle.playerActive(), "未选择前出战精灵不下场");

        List<String> logs = battle.chooseReplacement(1);

        assertFalse(battle.isAwaitingReplacement(), "选完替补后应恢复行动");
        assertEquals(second, player.getActive());
        assertEquals(second, battle.playerActive());
        assertTrue(logs.stream().anyMatch(line -> line.contains("派出了")), "应有派出提示: " + logs);
    }

    @Test
    void 己方最后一只倒下才判定战败() {
        Pokemon first = weak("mine_a", 20);
        Pokemon second = weak("mine_b", 20);
        Player player = playerWith(first, second);
        Trainer trainer = trainerWith("强敌", strong("boss", 20));
        BattleService battle = BattleServices.newTrainerBattle(player, trainer);

        battle.useMove(player.getActive().getMoveSlots().get(0)); // 己方首只倒下，等待玩家补位
        battle.chooseReplacement(1);                              // 玩家选出第二只
        battle.useMove(player.getActive().getMoveSlots().get(0)); // 第二只（最后一只）倒下

        assertEquals(BattleService.Status.PLAYER_LOSE, battle.getStatus());
        assertFalse(battle.isAwaitingReplacement(), "没有健康精灵时直接判负，不应等待补位");
        assertTrue(player.isPartyAllFainted());
        assertFalse(trainer.getActive().isFainted(), "对方仍有存活精灵，应是战败而非获胜");
    }

    // ------------------------------------------------------------------
    // 训练师轮战的限制：不可逃跑、不可捕捉
    // ------------------------------------------------------------------

    @Test
    void 训练师战无法逃跑且战斗继续() {
        Player player = playerWith(strong("mine", 20));
        Trainer trainer = trainerWith("劲敌", weak("foe", 20));
        BattleService battle = BattleServices.newTrainerBattle(player, trainer);

        List<String> logs = battle.tryRun();

        assertEquals(BattleService.Status.ONGOING, battle.getStatus());
        assertTrue(logs.stream().anyMatch(line -> line.contains("无法逃跑")), "应有无法逃跑提示: " + logs);
    }

    @Test
    void 训练师战无法捕捉且精灵球不消耗() {
        Player player = playerWith(strong("mine", 20));
        Trainer trainer = trainerWith("劲敌", weak("foe", 20));
        Item ball = new Item("i_master", "大师球", ItemCategory.POKE_BALL, 0, true);
        player.getBag().add(ball, 1);
        BattleService battle = BattleServices.newTrainerBattle(player, trainer);

        List<String> logs = battle.useItem(ball);

        assertEquals(BattleService.Status.ONGOING, battle.getStatus());
        assertEquals(1, battle.getBag().countOf(ball), "训练师战投球不应消耗精灵球");
        assertTrue(logs.stream().anyMatch(line -> line.contains("无法被捕捉")), "应有无法捕捉提示: " + logs);
    }

    // ------------------------------------------------------------------
    // 敌方信息接口与野生遭遇回归
    // ------------------------------------------------------------------

    @Test
    void 训练师战经训练师接口暴露敌方精灵() {
        Player player = playerWith(strong("mine", 20));
        Trainer trainer = trainerWith("劲敌", weak("foe", 20));
        BattleService battle = BattleServices.newTrainerBattle(player, trainer);

        assertNotNull(battle.getTrainer());
        assertEquals(trainer, battle.getTrainer());
        assertEquals(trainer.getActive(), battle.foeActive());
        assertNull(battle.getWild());
    }

    @Test
    void 野生遭遇仍是单只倒下即结束() {
        Player player = playerWith(strong("mine", 20));
        Pokemon wild = weak("wild", 20);
        BattleService battle = BattleServices.newBattle(player, wild);

        battle.useMove(player.getActive().getMoveSlots().get(0));

        assertEquals(BattleService.Status.PLAYER_WIN, battle.getStatus());
        assertTrue(wild.isFainted());
        assertNull(battle.getTrainer(), "野生遭遇不应有训练师");
    }

    @Test
    void 训练师队伍全倒时拒绝开始战斗() {
        Player player = playerWith(strong("mine", 20));
        Trainer trainer = trainerWith("劲敌", weak("foe", 20));
        trainer.getParty().forEach(p -> p.takeDamage(p.getCurrentHp()));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> BattleServices.newTrainerBattle(player, trainer));
    }
}
