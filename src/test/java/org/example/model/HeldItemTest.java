package org.example.model;

import org.example.data.GameData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装备数据与装备库测试：equipment.csv 加载（8 件全部可查询、参数解析正确）与
 * {@link Player} 装备库行为（去重、穿戴唯一、自动脱下、脱下后仍保留）。
 */
class HeldItemTest {

    // ------------------------------------------------------------------
    // equipment.csv 加载
    // ------------------------------------------------------------------

    @Test
    void 装备Csv八件全部可查询() {
        GameData data = GameData.instance();
        assertEquals(8, data.allEquipment().size(), "equipment.csv 应注册 8 件装备");
        for (String id : List.of("e_charcoal", "e_mystic_water", "e_magnet", "e_expert_belt",
                "e_leftovers", "e_shell_bell", "e_quick_claw", "e_eviolite")) {
            HeldItem item = data.equipment(id);
            assertNotNull(item, "缺少装备 " + id);
            assertFalse(item.getName().isBlank(), id + " 名称不应为空");
            assertFalse(item.getDescription().isBlank(), id + " 效果说明不应为空");
        }
        assertNull(data.equipment("e_not_exist"), "未注册的装备应返回 null");
    }

    @Test
    void 装备参数解析正确() {
        GameData data = GameData.instance();

        HeldItem charcoal = data.equipment("e_charcoal");
        assertEquals(HeldItemEffect.DAMAGE_TYPE, charcoal.getEffectType());
        assertEquals("FIRE", charcoal.typeParam());
        assertEquals(1.2, charcoal.doubleParam(), 1e-9);

        assertEquals("WATER", data.equipment("e_mystic_water").typeParam());
        assertEquals(1.2, data.equipment("e_mystic_water").doubleParam(), 1e-9);
        assertEquals("ELECTRIC", data.equipment("e_magnet").typeParam());
        assertEquals(1.2, data.equipment("e_magnet").doubleParam(), 1e-9);

        assertEquals(HeldItemEffect.SUPER_EFFECTIVE, data.equipment("e_expert_belt").getEffectType());
        assertEquals(1.2, data.equipment("e_expert_belt").doubleParam(), 1e-9);

        assertEquals(HeldItemEffect.END_TURN_HEAL, data.equipment("e_leftovers").getEffectType());
        assertEquals(0.0625, data.equipment("e_leftovers").doubleParam(), 1e-9);

        assertEquals(HeldItemEffect.LIFE_STEAL, data.equipment("e_shell_bell").getEffectType());
        assertEquals(0.125, data.equipment("e_shell_bell").doubleParam(), 1e-9);

        assertEquals(HeldItemEffect.FIRST_STRIKE, data.equipment("e_quick_claw").getEffectType());
        assertEquals(20, data.equipment("e_quick_claw").chanceParam(), 1e-9);

        assertEquals(HeldItemEffect.EVOLITE, data.equipment("e_eviolite").getEffectType());
        assertEquals(1.5, data.equipment("e_eviolite").doubleParam(), 1e-9);
    }

    @Test
    void 非属性强化装备无属性参数且坏参数安全回退() {
        assertNull(GameData.instance().equipment("e_leftovers").typeParam(),
                "非 DAMAGE_TYPE 装备不应有属性参数");
        // 坏参数解析失败时回退默认值，不抛异常
        HeldItem bad = new HeldItem("e_bad", "坏装备", HeldItemEffect.DAMAGE_TYPE, "FIRE|oops", "");
        assertEquals("FIRE", bad.typeParam());
        assertEquals(1.0, bad.doubleParam(), 1e-9);
        assertEquals(0, new HeldItem("e_bad2", "坏爪", HeldItemEffect.FIRST_STRIKE, "oops", "").chanceParam(), 1e-9);
    }

    // ------------------------------------------------------------------
    // Player 装备库
    // ------------------------------------------------------------------

    private static Species species() {
        return new Species("sp_test", "测试精灵", ElementType.NORMAL, null,
                new Stats(100, 50, 50, 50, 50, 50), 100,
                List.of(), null, 0, Map.of());
    }

    private static Move move() {
        return new Move("m_test", "撞击", ElementType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35);
    }

    private static HeldItem item(String id, String name) {
        return new HeldItem(id, name, HeldItemEffect.END_TURN_HEAL, "0.0625", name + " 测试描述");
    }

    @Test
    void 装备库去重() {
        Player player = new Player("测试");
        HeldItem item = item("e_test", "测试装备");
        assertTrue(player.addEquipment(item));
        assertFalse(player.addEquipment(item), "重复入库应被拒绝");
        assertFalse(player.addEquipment(null), "null 装备应被拒绝");
        assertEquals(1, player.getEquipment().size());
    }

    @Test
    void 穿戴同一装备时自动从其他精灵脱下() {
        Player player = new Player("测试");
        Pokemon p1 = Pokemon.create(species(), 10, List.of(move()));
        Pokemon p2 = Pokemon.create(species(), 10, List.of(move()));
        player.addPokemon(p1);
        player.addPokemon(p2);
        HeldItem item = item("e_test", "测试装备");
        player.addEquipment(item);

        assertTrue(player.equip(p1, item));
        assertEquals(item, p1.getHeldItem());
        assertNull(p2.getHeldItem());

        assertTrue(player.equip(p2, item), "换穿到另一只精灵应成功");
        assertNull(p1.getHeldItem(), "同一件装备应自动从原穿戴者脱下");
        assertEquals(item, p2.getHeldItem());

        // 未入库的装备不可穿戴
        HeldItem outsider = item("e_out", "局外装备");
        assertFalse(player.equip(p1, outsider));
        assertNull(p1.getHeldItem());
        assertEquals(item, p2.getHeldItem(), "原穿戴关系不应被破坏");

        // 脱下后装备仍保留在装备库中
        assertTrue(player.unequip(p2));
        assertNull(p2.getHeldItem());
        assertEquals(1, player.getEquipment().size());
        assertFalse(player.unequip(p2), "重复脱下应返回 false");
    }

    @Test
    void 装备库快照不可变() {
        Player player = new Player("测试");
        player.addEquipment(item("e_test", "测试装备"));
        List<HeldItem> snapshot = player.getEquipment();
        assertEquals(1, snapshot.size());
        boolean rejected = false;
        try {
            snapshot.add(item("e_x", "尝试注入"));
        } catch (UnsupportedOperationException ex) {
            rejected = true;
        }
        assertTrue(rejected, "装备库快照应不可修改");
        assertEquals(1, player.getEquipment().size());
    }
}
