package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 精灵技能库测试：升级学到的技能全部保留进技能库（无上限），
 * 出战只携带技能槽中的最多 4 招，玩家可自由更换出战技能。
 */
class PokemonMovePoolTest {

    private static Move move(String id) {
        return new Move(id, "招式" + id, ElementType.NORMAL, MoveCategory.PHYSICAL, 50, 100, 20);
    }

    private static Species species() {
        return new Species("sp_pool", "测试精灵", ElementType.NORMAL, null,
                new Stats(100, 100, 100, 100, 100, 100), 100,
                List.of(), null, 0, Map.of());
    }

    private static Pokemon pokemonWith(Move... moves) {
        return Pokemon.create(species(), 10, List.of(moves), new Stats(0, 0, 0, 0, 0, 0));
    }

    // ------------------------------------------------------------------
    // 创建与初始技能
    // ------------------------------------------------------------------

    @Test
    void 创建时初始技能全部进库而出战最多四招() {
        Pokemon p = pokemonWith(move("m1"), move("m2"), move("m3"), move("m4"), move("m5"));

        assertEquals(4, p.getMoveSlots().size(), "出战技能槽最多 4 招");
        assertEquals(5, p.getKnownMoves().size(), "初始技能全部保留进技能库");
        assertTrue(p.knowsMove("m5"), "第 5 招应保留在技能库中");
        assertFalse(p.hasMove("m5"), "第 5 招不应自动出战");
    }

    @Test
    void 技能库视图不可变() {
        Pokemon p = pokemonWith(move("m1"));

        assertTrue(p.getKnownMoves() instanceof java.util.List);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> p.getKnownMoves().clear());
    }

    // ------------------------------------------------------------------
    // 学习进库
    // ------------------------------------------------------------------

    @Test
    void 满槽时学招只进技能库不占出战槽() {
        Pokemon p = pokemonWith(move("m1"), move("m2"), move("m3"), move("m4"));

        assertTrue(p.learnMoveToPool(move("m5")), "学招应成功进入技能库");

        assertEquals(4, p.getMoveSlots().size(), "出战槽仍为 4 招");
        assertTrue(p.knowsMove("m5"), "新招保留在技能库中");
        assertFalse(p.hasMove("m5"), "满槽时新招不自动出战");
    }

    @Test
    void 有空槽时学招自动装上出战槽() {
        Pokemon p = pokemonWith(move("m1"), move("m2"));

        assertTrue(p.learnMoveToPool(move("m3")));

        assertEquals(3, p.getMoveSlots().size(), "有空槽时新招自动出战");
        assertTrue(p.hasMove("m3"));
        assertTrue(p.knowsMove("m3"));
    }

    @Test
    void 重复学招不产生重复槽位与重复库项() {
        Pokemon p = pokemonWith(move("m1"), move("m2"));

        p.learnMoveToPool(move("m1"));
        p.learnMove(move("m2"));

        assertEquals(2, p.getMoveSlots().size(), "出战槽不重复");
        assertEquals(2, p.getKnownMoves().size(), "技能库不重复");
    }

    @Test
    void 空技能学招被拒绝() {
        Pokemon p = pokemonWith(move("m1"));

        assertFalse(p.learnMoveToPool(null), "null 技能不可学习");
        assertFalse(p.learnMove(null), "null 技能不可学习");
        assertEquals(1, p.getKnownMoves().size());
    }

    // ------------------------------------------------------------------
    // 更换出战技能
    // ------------------------------------------------------------------

    @Test
    void 换招替换指定槽位且被换下技能留在库中() {
        Pokemon p = pokemonWith(move("m1"), move("m2"), move("m3"), move("m4"));
        p.learnMoveToPool(move("m5"));

        Move replaced = p.swapBattleMove(0, p.getKnownMoves().get(4));

        assertEquals("m1", replaced.getId(), "返回被换下的技能");
        assertEquals("m5", p.getMoveSlots().get(0).getMove().getId(), "槽位 0 换成新招");
        assertEquals(4, p.getMoveSlots().size());
        assertTrue(p.knowsMove("m1"), "被换下的技能仍保留在技能库中");
        assertFalse(p.hasMove("m1"), "被换下的技能不再出战");
    }

    @Test
    void 换招拒绝非法输入() {
        Pokemon p = pokemonWith(move("m1"), move("m2"), move("m3"), move("m4"));

        assertNull(p.swapBattleMove(-1, move("m9")), "槽位越界（负数）");
        assertNull(p.swapBattleMove(4, move("m9")), "槽位越界（超上限）");
        assertNull(p.swapBattleMove(0, null), "空技能");
        assertNull(p.swapBattleMove(0, move("m9")), "技能不在库中");
        assertNull(p.swapBattleMove(0, move("m1")), "技能已在出战槽");
        assertEquals(4, p.getMoveSlots().size(), "非法换招不改变出战槽");
    }

    @Test
    void 换回被换下的技能可行() {
        Pokemon p = pokemonWith(move("m1"), move("m2"), move("m3"), move("m4"));
        p.learnMoveToPool(move("m5"));

        p.swapBattleMove(0, p.getKnownMoves().get(4)); // 槽0: m1→m5
        Move back = p.swapBattleMove(1, p.getKnownMoves().get(0)); // 槽1: m2→m1

        assertEquals("m2", back.getId(), "返回槽 1 被换下的技能");
        assertEquals("m1", p.getMoveSlots().get(1).getMove().getId(), "换下的技能可再换回");
        assertTrue(p.hasMove("m1"));
        assertTrue(p.hasMove("m5"), "两次换招后两招都应出战");
        assertTrue(p.knowsMove("m2"), "被换下的 m2 仍在技能库中");
    }

    // ------------------------------------------------------------------
    // 与既有 API 的兼容
    // ------------------------------------------------------------------

    @Test
    void learnMove学到的技能同时进库() {
        Pokemon p = pokemonWith(move("m1"));

        assertTrue(p.learnMove(move("m2")));

        assertTrue(p.knowsMove("m2"), "learnMove 学到的技能同样进技能库");
        assertEquals(2, p.getKnownMoves().size());
    }

    @Test
    void 读档兜底技能库等于出战技能() {
        Species s = species();
        List<MoveSlot> slots = new ArrayList<>();
        slots.add(new MoveSlot(move("m1"), 5));
        slots.add(new MoveSlot(move("m2"), 3));

        Pokemon restored = Pokemon.restore(s, 12, new Stats(0, 0, 0, 0, 0, 0),
                slots, 0L, StatusCondition.NONE, 0, 0, 0, 30);

        assertEquals(2, restored.getKnownMoves().size(), "旧档技能库退化为出战技能");
        assertTrue(restored.knowsMove("m1"));
        assertTrue(restored.knowsMove("m2"));
    }
}
