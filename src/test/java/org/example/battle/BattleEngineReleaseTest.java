package org.example.battle;

import org.example.model.ElementType;
import org.example.model.HeldItem;
import org.example.model.HeldItemEffect;
import org.example.model.Item;
import org.example.model.ItemCategory;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 满队捕捉放生测试：队伍已满时捕捉成功，新精灵不直接入队而是挂起等待玩家放生
 * 腾位；放生的精灵携带装备时装备自动返还装备库（脱下即返还，装备全库唯一）。
 */
class BattleEngineReleaseTest {

    /** 低威力普通系物理技能：让野生精灵能行动但不至于击倒玩家。 */
    private static final Move TAP = new Move("m_tap", "轻拍", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 1, 100, 40);

    private static Pokemon playerPokemon(String id) {
        return Pokemon.create(species(id, 300), 20, List.of(TAP));
    }

    private static Pokemon wildPokemon() {
        return Pokemon.create(species("wild_sp", 300), 20, List.of(TAP));
    }

    private static Species species(String id, int hp) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(hp, 20, 300, 20, 300, 10), 51,
                List.of(), null, 0, Map.of());
    }

    /** 满队（6 只）玩家：前 5 只普通，最后一只携带装备。 */
    private static Player fullPlayer(HeldItem carriedByLast) {
        Player player = new Player("玩家");
        for (int i = 0; i < 6; i++) {
            player.addPokemon(playerPokemon("mine_sp_" + i));
        }
        if (carriedByLast != null) {
            player.addEquipment(carriedByLast);
            player.equip(player.getParty().get(5), carriedByLast);
        }
        return player;
    }

    private static Item masterBall() {
        return new Item("i_master_ball", "大师球", ItemCategory.POKE_BALL, 255, true);
    }

    private static BattleService battle(Player player) {
        Item ball = masterBall();
        player.getBag().add(ball, 1);
        BattleService engine = BattleServices.newBattle(player, wildPokemon(), new Random(1));
        engine.useItem(ball);
        return engine;
    }

    @Test
    void 满队捕捉成功时新精灵挂起等待放生() {
        BattleService engine = battle(fullPlayer(null));

        assertEquals(BattleService.Status.CAUGHT, engine.getStatus());
        assertNotNull(engine.capturedAwaitingRelease(), "满队捕捉应挂起新精灵");
        assertEquals(6, engine.getPlayer().getParty().size(), "放生前队伍保持 6 只");
    }

    @Test
    void 放生携带装备的精灵后装备返还且新精灵入队() {
        HeldItem charcoal = new HeldItem("e_charcoal", "木炭", HeldItemEffect.DAMAGE_TYPE,
                "FIRE|1.2", "火属性招式威力提升 20%");
        BattleService engine = battle(fullPlayer(charcoal));
        Pokemon captured = engine.capturedAwaitingRelease();
        assertNotNull(captured);

        // 放生携带装备的第 6 只
        assertTrue(engine.releaseToMakeRoom(5));

        Player player = engine.getPlayer();
        assertEquals(6, player.getParty().size(), "放生腾位后新精灵入队，队伍仍 6 只");
        assertTrue(player.getParty().contains(captured), "新精灵应入队");
        assertNull(player.getParty().get(5).getHeldItem(), "入队精灵不应携带被放生者的装备");
        assertTrue(player.getEquipment().contains(charcoal), "装备应仍在玩家装备库中");
        assertTrue(String.join("\n", engine.getLog()).contains("返还"), "日志应提示装备返还");
        assertNull(engine.capturedAwaitingRelease(), "入队后挂起应清空");
    }

    @Test
    void 放生未携带装备的精灵不涉及装备且正常腾位() {
        BattleService engine = battle(fullPlayer(null));
        Pokemon captured = engine.capturedAwaitingRelease();

        assertTrue(engine.releaseToMakeRoom(0));

        Player player = engine.getPlayer();
        assertEquals(6, player.getParty().size());
        assertTrue(player.getParty().contains(captured));
        assertNull(engine.capturedAwaitingRelease());
    }

    @Test
    void 放生出战精灵后出战顺延到下一只() {
        BattleService engine = battle(fullPlayer(null));
        Pokemon captured = engine.capturedAwaitingRelease();
        Player player = engine.getPlayer();
        Pokemon active = engine.playerActive();
        Pokemon next = player.getParty().get(1);
        int activeIndex = player.getParty().indexOf(active);

        assertTrue(engine.releaseToMakeRoom(activeIndex));

        assertNotEquals(active, engine.playerActive(), "被放生的出战精灵不应仍是出战");
        assertEquals(next, engine.playerActive(), "出战应顺延到原第二只");
        assertTrue(player.getParty().contains(captured));
    }

    @Test
    void 放弃挂起的精灵则队伍保持不变() {
        BattleService engine = battle(fullPlayer(null));
        Pokemon captured = engine.capturedAwaitingRelease();

        assertTrue(engine.discardCaptured());

        assertNull(engine.capturedAwaitingRelease(), "放弃后挂起应清空");
        assertEquals(6, engine.getPlayer().getParty().size());
        assertFalse(engine.getPlayer().getParty().contains(captured), "放弃的精灵不应入队");
    }

    @Test
    void 非满队捕捉直接入队不产生挂起() {
        Player player = new Player("玩家");
        player.addPokemon(playerPokemon("mine_sp_0"));
        BattleService engine = battle(player);

        assertEquals(BattleService.Status.CAUGHT, engine.getStatus());
        assertNull(engine.capturedAwaitingRelease(), "非满队捕捉应直接入队");
        assertEquals(2, engine.getPlayer().getParty().size());
        assertFalse(engine.releaseToMakeRoom(0), "无挂起精灵时不应能放生");
        assertFalse(engine.discardCaptured(), "无挂起精灵时不应能放弃");
    }
}
