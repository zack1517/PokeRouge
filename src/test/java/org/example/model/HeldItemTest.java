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
 * 装备数据与装备库测试：equipment.csv 加载（全部可查询、参数解析正确）与
 * {@link Player} 装备库行为（去重、穿戴唯一、自动脱下、脱下后仍保留）。
 */
class HeldItemTest {

    /** 批次①新增的 18 件携带物（与首发的 8 件合计 26 件）。 */
    private static final List<String> BATCH1_IDS = List.of(
            "e_muscle_band", "e_wise_glasses", "e_life_orb", "e_iron_ball", "e_lagging_tail",
            "e_ring_target", "e_air_balloon", "e_black_sludge", "e_flame_orb", "e_toxic_orb",
            "e_heat_rock", "e_damp_rock", "e_smooth_rock", "e_icy_rock",
            "e_focus_sash", "e_focus_band", "e_choice_specs", "e_choice_scarf");

    // ------------------------------------------------------------------
    // equipment.csv 加载
    // ------------------------------------------------------------------

    @Test
    void 装备Csv全部可查询() {
        GameData data = GameData.instance();
        assertEquals(26, data.allEquipment().size(), "equipment.csv 应注册 26 件装备");
        List<String> all = new java.util.ArrayList<>(List.of("e_charcoal", "e_mystic_water", "e_magnet",
                "e_expert_belt", "e_leftovers", "e_shell_bell", "e_quick_claw", "e_eviolite"));
        all.addAll(BATCH1_IDS);
        for (String id : all) {
            HeldItem item = data.equipment(id);
            assertNotNull(item, "缺少装备 " + id);
            assertFalse(item.getName().isBlank(), id + " 名称不应为空");
            assertFalse(item.getDescription().isBlank(), id + " 效果说明不应为空");
        }
        assertNull(data.equipment("e_not_exist"), "未注册的装备应返回 null");
    }

    @Test
    void 批次一新装备效果类型与参数正确() {
        GameData data = GameData.instance();

        assertEquals(HeldItemEffect.PHYSICAL_DAMAGE, data.equipment("e_muscle_band").getEffectType());
        assertEquals(1.1, data.equipment("e_muscle_band").doubleParam(), 1e-9);
        assertEquals(HeldItemEffect.SPECIAL_DAMAGE, data.equipment("e_wise_glasses").getEffectType());
        assertEquals(1.1, data.equipment("e_wise_glasses").doubleParam(), 1e-9);

        HeldItem orb = data.equipment("e_life_orb");
        assertEquals(HeldItemEffect.LIFE_ORB, orb.getEffectType());
        assertEquals(1.3, orb.damageMultiplier(), 1e-9);
        assertEquals(0.1, orb.recoilRatio(), 1e-9);

        HeldItem ball = data.equipment("e_iron_ball");
        assertEquals(HeldItemEffect.SPEED_MULTIPLIER, ball.getEffectType());
        assertEquals(0.5, ball.doubleParam(), 1e-9);
        assertEquals("GROUND", ball.textPart(1), "黑色铁球第 2 段参数应为 GROUND（地面化）");

        // 无参数的一次性/顺序类装备：参数为空且不抛异常
        for (String id : List.of("e_lagging_tail", "e_ring_target", "e_air_balloon", "e_focus_sash")) {
            assertTrue(GameData.instance().equipment(id).getParam().isEmpty(), id + " 不应有参数");
        }
        assertEquals(HeldItemEffect.MOVE_LAST, data.equipment("e_lagging_tail").getEffectType());
        assertEquals(HeldItemEffect.IGNORE_IMMUNITY, data.equipment("e_ring_target").getEffectType());
        assertEquals(HeldItemEffect.GROUND_IMMUNE, data.equipment("e_air_balloon").getEffectType());
        assertEquals(HeldItemEffect.FOCUS_SASH, data.equipment("e_focus_sash").getEffectType());

        HeldItem sludge = data.equipment("e_black_sludge");
        assertEquals(HeldItemEffect.POISON_HEAL, sludge.getEffectType());
        assertEquals(0.0625, sludge.healRatio(), 1e-9);
        assertEquals(0.125, sludge.damageRatio(), 1e-9);

        assertEquals("BURN", data.equipment("e_flame_orb").statusParam());
        assertEquals("BADLY_POISON", data.equipment("e_toxic_orb").statusParam());

        assertEquals("SUNNY", data.equipment("e_heat_rock").weatherParam());
        assertEquals(8, data.equipment("e_heat_rock").durationTurns());
        assertEquals("RAIN", data.equipment("e_damp_rock").weatherParam());
        assertEquals("SANDSTORM", data.equipment("e_smooth_rock").weatherParam());
        assertEquals("HAIL", data.equipment("e_icy_rock").weatherParam());

        assertEquals(10, data.equipment("e_focus_band").doubleParam(), 1e-9);

        assertEquals("SPECIAL", data.equipment("e_choice_specs").choiceKind());
        assertEquals(1.5, data.equipment("e_choice_specs").choiceMultiplier(), 1e-9);
        assertEquals("SPEED", data.equipment("e_choice_scarf").choiceKind());
        assertEquals(1.5, data.equipment("e_choice_scarf").choiceMultiplier(), 1e-9);
    }

    @Test
    void 多段参数解析越界安全回退() {
        HeldItem item = new HeldItem("e_x", "多段", HeldItemEffect.POISON_HEAL, "0.0625", "");
        assertEquals("0.0625", item.textPart(0));
        assertEquals("", item.textPart(1), "越界段应返回空串");
        assertEquals(0.5, item.doublePart(1, 0.5), 1e-9, "越界段应回退默认值");
        assertEquals("", item.textPart(-1), "负下标应返回空串");

        HeldItem bad = new HeldItem("e_y", "坏段", HeldItemEffect.LIFE_ORB, "1.3|oops", "");
        assertEquals(1.3, bad.damageMultiplier(), 1e-9);
        assertEquals(0, bad.recoilRatio(), 1e-9, "解析失败应回退 0");

        HeldItem empty = new HeldItem("e_z", "空参数", HeldItemEffect.FOCUS_SASH, "", "");
        assertEquals("", empty.choiceKind(), "空参数应返回空串");
        assertEquals(0, empty.durationTurns());
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
