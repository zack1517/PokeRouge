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

    /** 批次②新增的 30 种树果（异常治疗 7 / HP 回复 6 / PP 回复 1 / 属性减伤 16）。 */
    private static final List<String> BATCH2_IDS = List.of(
            "b_cheri", "b_pecha", "b_rawst", "b_chesto", "b_aspear", "b_persim", "b_lum",
            "b_oran", "b_sitrus", "b_figy", "b_wiki", "b_aguav", "b_iapapa",
            "b_leppa",
            "b_occa", "b_passho", "b_wacan", "b_rindo", "b_yache", "b_chople", "b_kebia",
            "b_shuca", "b_coba", "b_payapa", "b_tanga", "b_charti", "b_kasib", "b_haban",
            "b_roseli", "b_babiri");

    /** 批次④新增的 6 件装备（招式标记 / 会心 / 畏缩）。 */
    private static final List<String> BATCH4_IDS = List.of(
            "e_punch_glove", "e_rocky_helmet", "e_safety_goggles",
            "e_razor_claw", "e_kings_rock", "e_metronome");

    // ------------------------------------------------------------------
    // equipment.csv 加载
    // ------------------------------------------------------------------

    @Test
    void 装备Csv全部可查询() {
        GameData data = GameData.instance();
        assertEquals(62, data.allEquipment().size(), "equipment.csv 应注册 32 件装备 + 30 种树果");
        List<String> all = new java.util.ArrayList<>(List.of("e_charcoal", "e_mystic_water", "e_magnet",
                "e_expert_belt", "e_leftovers", "e_shell_bell", "e_quick_claw", "e_eviolite"));
        all.addAll(BATCH1_IDS);
        all.addAll(BATCH2_IDS);
        all.addAll(BATCH4_IDS);
        for (String id : all) {
            HeldItem item = data.equipment(id);
            assertNotNull(item, "缺少装备 " + id);
            assertFalse(item.getName().isBlank(), id + " 名称不应为空");
            assertFalse(item.getDescription().isBlank(), id + " 效果说明不应为空");
        }
        assertNull(data.equipment("e_not_exist"), "未注册的装备应返回 null");
        assertNull(data.equipment("b_not_exist"), "未注册的树果应返回 null");
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
    // 批次② 树果参数解析
    // ------------------------------------------------------------------

    @Test
    void 批次二树果效果类型与参数正确() {
        GameData data = GameData.instance();

        assertEquals(HeldItemEffect.CURE_STATUS, data.equipment("b_cheri").getEffectType());
        assertEquals(HeldItemEffect.CURE_STATUS, data.equipment("b_lum").getEffectType());
        assertEquals(HeldItemEffect.HEAL_HP, data.equipment("b_oran").getEffectType());
        assertEquals(HeldItemEffect.HEAL_PP, data.equipment("b_leppa").getEffectType());
        assertEquals(HeldItemEffect.RESIST_TYPE, data.equipment("b_occa").getEffectType());
        assertEquals(HeldItemEffect.RESIST_TYPE, data.equipment("b_babiri").getEffectType());

        HeldItem oran = data.equipment("b_oran");
        assertEquals(0.5, oran.healThresholdRatio(), 1e-9);
        assertEquals(10, oran.healAmount(), 1e-9, "橙橙果为固定点数额度（10 HP）");

        HeldItem sitrus = data.equipment("b_sitrus");
        assertEquals(0.5, sitrus.healThresholdRatio(), 1e-9);
        assertEquals(0.25, sitrus.healAmount(), 1e-9, "文柚果为最大 HP 比例额度");

        for (String id : List.of("b_figy", "b_wiki", "b_aguav", "b_iapapa")) {
            HeldItem danger = data.equipment(id);
            assertEquals(0.25, danger.healThresholdRatio(), 1e-9, id + " 阈值应为 1/4");
            assertEquals(0.125, danger.healAmount(), 1e-9, id + " 回复量应为 1/8");
        }

        assertEquals(10, data.equipment("b_leppa").ppRestoreAmount());

        HeldItem occa = data.equipment("b_occa");
        assertEquals("FIRE", occa.resistTypeParam());
        assertEquals(0.5, occa.resistMultiplier(), 1e-9);
        assertFalse(occa.resistUnconditional(), "巧可果仍需效果拔群才生效");

        HeldItem babiri = data.equipment("b_babiri");
        assertEquals("NORMAL", babiri.resistTypeParam());
        assertEquals(0.5, babiri.resistMultiplier(), 1e-9);
        assertTrue(babiri.resistUnconditional(), "灯浆果对一般属性招式无条件减伤");
    }

    // ------------------------------------------------------------------
    // 批次④ 装备参数解析
    // ------------------------------------------------------------------

    @Test
    void 批次四装备效果类型与参数正确() {
        GameData data = GameData.instance();

        HeldItem glove = data.equipment("e_punch_glove");
        assertEquals(HeldItemEffect.PUNCH_BOOST, glove.getEffectType());
        assertEquals(1.1, glove.doubleParam(), 1e-9);

        HeldItem helmet = data.equipment("e_rocky_helmet");
        assertEquals(HeldItemEffect.CONTACT_PUNISH, helmet.getEffectType());
        assertEquals(0.1667, helmet.doubleParam(), 1e-9, "反伤比例为最大 HP 的 1/6");

        HeldItem goggles = data.equipment("e_safety_goggles");
        assertEquals(HeldItemEffect.POWDER_IMMUNE, goggles.getEffectType());
        assertTrue(goggles.getParam().isEmpty(), "防尘护目镜无参数");

        HeldItem claw = data.equipment("e_razor_claw");
        assertEquals(HeldItemEffect.CRIT_BOOST, claw.getEffectType());
        assertEquals(1, claw.doubleParam(), 1e-9, "会心等级 +1");

        HeldItem rock = data.equipment("e_kings_rock");
        assertEquals(HeldItemEffect.FLINCH_CHANCE, rock.getEffectType());
        assertEquals(10, rock.chanceParam(), 1e-9);

        HeldItem metronome = data.equipment("e_metronome");
        assertEquals(HeldItemEffect.CONSECUTIVE_BOOST, metronome.getEffectType());
        assertEquals(0.2, metronome.doubleParam(), 1e-9, "每层增幅 20%");
    }

    @Test
    void 异常治疗树果解析出对应状态集合() {
        GameData data = GameData.instance();

        var cheri = data.equipment("b_cheri").cureStatuses();
        assertEquals(1, cheri.size());
        assertTrue(cheri.contains(StatusCondition.PARALYSIS));

        var pecha = data.equipment("b_pecha").cureStatuses();
        assertEquals(2, pecha.size(), "桃桃果应同时治疗中毒与剧毒");
        assertTrue(pecha.contains(StatusCondition.POISON));
        assertTrue(pecha.contains(StatusCondition.BADLY_POISON));
        assertFalse(pecha.contains(StatusCondition.BURN), "未列出的异常不应被治疗");

        assertEquals(java.util.Set.of(StatusCondition.BURN), data.equipment("b_rawst").cureStatuses());
        assertEquals(java.util.Set.of(StatusCondition.SLEEP), data.equipment("b_chesto").cureStatuses());
        assertEquals(java.util.Set.of(StatusCondition.FREEZE), data.equipment("b_aspear").cureStatuses());
        assertEquals(java.util.Set.of(StatusCondition.CONFUSION), data.equipment("b_persim").cureStatuses());

        var lum = data.equipment("b_lum").cureStatuses();
        assertTrue(lum.contains(StatusCondition.POISON));
        assertTrue(lum.contains(StatusCondition.PARALYSIS));
        assertTrue(lum.contains(StatusCondition.BURN));
        assertTrue(lum.contains(StatusCondition.SLEEP));
        assertTrue(lum.contains(StatusCondition.FREEZE));
        assertTrue(lum.contains(StatusCondition.CONFUSION), "木子果按需求表描述治疗所有异常（含混乱）");
        assertFalse(lum.contains(StatusCondition.NONE), "NONE 不是可治疗的异常");
        assertFalse(lum.contains(StatusCondition.FAINTED), "濒死不是可治疗的异常");
    }

    @Test
    void 非异常治疗树果的治疗集合为空() {
        GameData data = GameData.instance();
        for (String id : List.of("b_oran", "b_sitrus", "b_leppa", "b_occa", "b_babiri")) {
            assertTrue(data.equipment(id).cureStatuses().isEmpty(), id + " 不应解析出治疗状态");
        }
    }

    @Test
    void 异常治疗树果解析容错() {
        HeldItem blank = new HeldItem("b_x", "空参数果", HeldItemEffect.CURE_STATUS, "", "");
        assertTrue(blank.cureStatuses().isEmpty(), "空参数不应抛异常");

        HeldItem unknown = new HeldItem("b_y", "未知状态果", HeldItemEffect.CURE_STATUS,
                "oops|BURN|also_oops", "");
        assertEquals(java.util.Set.of(StatusCondition.BURN), unknown.cureStatuses(),
                "无法识别的状态名应被跳过，其余仍生效");
    }

    @Test
    void 树果参数解析越界安全回退() {
        HeldItem leaf = new HeldItem("b_z", "缺额度果", HeldItemEffect.HEAL_PP, "", "");
        assertEquals(0, leaf.ppRestoreAmount(), "缺失额度应以 0 回退");

        HeldItem noParam = new HeldItem("b_w", "无参减伤果", HeldItemEffect.RESIST_TYPE, "", "");
        assertEquals("", noParam.resistTypeParam());
        assertEquals(1.0, noParam.resistMultiplier(), 1e-9, "缺失倍率应回退 1.0（不减伤）");
        assertFalse(noParam.resistUnconditional());

        HeldItem bad = new HeldItem("b_v", "坏倍率果", HeldItemEffect.RESIST_TYPE, "FIRE|oops", "");
        assertEquals(1.0, bad.resistMultiplier(), 1e-9, "倍率解析失败应回退 1.0");
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
