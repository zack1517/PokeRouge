package org.example.view;

import org.example.data.GameData;
import org.example.data.ShopStock;
import org.example.model.ElementType;
import org.example.model.HeldItem;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 道具图鉴数据层测试：{@link ItemDexData} 与商店目录（{@link ShopStock#catalog()}）的一致性、
 * 玩家持有情况合并、装备穿戴标注与消耗品描述文本。
 *
 * <p>图鉴口径为「全量列出 + 标注已拥有 / 未拥有」（不做收集解锁），因此这里锁的是
 * 「条数 = 商店目录条数」「排序为消耗品在前」与「持有/穿戴标注取自玩家数据」。</p>
 */
class ItemDexDataTest {

    private static final Stats FIXED_IVS = new Stats(31, 31, 31, 31, 31, 31);

    private static final Move TACKLE = new Move("m_tackle", "撞击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 40, 100, 35);

    @Test
    void 图鉴条数与商店目录一致且全量列出() {
        List<ItemDexData.Entry> entries = ItemDexData.build(null);

        assertEquals(ShopStock.catalog().size(), entries.size(),
                "图鉴必须列全商店目录的每一件商品");
        assertEquals(93, entries.size(), "当前目录应为 16 件消耗品 + 77 件装备");

        Set<String> ids = entries.stream().map(ItemDexData.Entry::id).collect(Collectors.toSet());
        assertEquals(entries.size(), ids.size(), "图鉴条目 id 不应重复");
        assertTrue(ids.contains("i_potion"), "消耗品应在图鉴中");
        assertTrue(ids.contains("e_focus_sash"), "装备应在图鉴中");

        for (ItemDexData.Entry entry : entries) {
            assertFalse(entry.name().isBlank(), entry.id() + " 应有展示名");
            assertTrue(entry.basePrice() > 0, entry.id() + " 基础价应为正");
            assertTrue(entry.unlockSegment() >= 1 && entry.unlockSegment() <= 5,
                    entry.id() + " 解锁段位应在 1~5，实际 " + entry.unlockSegment());
            assertFalse(entry.owned(), "无玩家数据时不应标为已拥有");
            assertFalse(entry.equipped(), "无玩家数据时不应标为已穿戴");
        }
    }

    @Test
    void 消耗品在前装备在后() {
        List<ItemDexData.Entry> entries = ItemDexData.build(null);

        int firstEquipment = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).equipment()) {
                firstEquipment = i;
                break;
            }
        }
        assertTrue(firstEquipment > 0, "应存在装备条目");
        assertTrue(entries.subList(0, firstEquipment).stream().noneMatch(ItemDexData.Entry::equipment),
                "装备之前不应混入装备条目");
        assertTrue(entries.subList(firstEquipment, entries.size()).stream()
                .allMatch(ItemDexData.Entry::equipment), "装备区之后不应再出现消耗品");
        assertEquals(16, firstEquipment, "消耗品应为 16 件");
    }

    @Test
    void 装备按解锁段位与基础价递增排序() {
        List<ItemDexData.Entry> entries = ItemDexData.build(null).stream()
                .filter(ItemDexData.Entry::equipment).toList();

        for (int i = 1; i < entries.size(); i++) {
            ItemDexData.Entry prev = entries.get(i - 1);
            ItemDexData.Entry curr = entries.get(i);
            assertTrue(prev.unlockSegment() < curr.unlockSegment()
                            || (prev.unlockSegment() == curr.unlockSegment()
                            && prev.basePrice() <= curr.basePrice()),
                    "装备应「解锁段位 → 基础价」有序：" + prev.id() + " -> " + curr.id());
        }
    }

    @Test
    void 消耗品持有数来自背包() {
        Player player = new Player("玩家");
        Item potion = GameData.instance().item("i_potion");
        player.getBag().add(potion, 3);

        ItemDexData.Entry entry = find(ItemDexData.build(player), "i_potion");
        assertEquals(3, entry.ownedCount(), "持有数应取背包件数");
        assertTrue(entry.owned());
        assertFalse(entry.equipped(), "消耗品没有穿戴概念");
        assertNull(entry.holderName());
    }

    @Test
    void 装备持有与穿戴状态来自装备库与队伍() {
        GameData data = GameData.instance();
        Player player = new Player("玩家");
        Pokemon partner = pokemon("伙伴");
        player.addPokemon(partner);

        ItemDexData.Entry before = find(ItemDexData.build(player), "e_focus_sash");
        assertFalse(before.owned());
        assertFalse(before.equipped());
        assertNull(before.holderName());

        HeldItem sash = data.equipment("e_focus_sash");
        assertTrue(player.addEquipment(sash));
        ItemDexData.Entry inBag = find(ItemDexData.build(player), "e_focus_sash");
        assertTrue(inBag.owned());
        assertFalse(inBag.equipped());
        assertNull(inBag.holderName());

        assertTrue(player.equip(partner, sash));
        ItemDexData.Entry worn = find(ItemDexData.build(player), "e_focus_sash");
        assertTrue(worn.owned());
        assertTrue(worn.equipped());
        assertEquals("伙伴", worn.holderName());

        assertTrue(player.unequip(partner));
        ItemDexData.Entry off = find(ItemDexData.build(player), "e_focus_sash");
        assertTrue(off.owned());
        assertFalse(off.equipped());
    }

    @Test
    void 装备描述与效果说明取自数据表() {
        ItemDexData.Entry entry = find(ItemDexData.build(null), "e_focus_sash");
        HeldItem sash = GameData.instance().equipment("e_focus_sash");

        assertEquals(sash.getName(), entry.name());
        assertEquals(sash.getDescription(), entry.description());
        assertTrue(entry.equipment());
    }

    @Test
    void 消耗品描述按类别生成() {
        assertEquals("回复 20 点 HP",
                ItemDexData.describe(GameData.instance().item("i_potion")));
        assertEquals("捕捉倍率 ×3",
                ItemDexData.describe(GameData.instance().item("i_poke_ball")));
        assertEquals("必定捕捉成功",
                ItemDexData.describe(GameData.instance().item("i_master_ball")));
        assertEquals("解除全部主要异常状态",
                ItemDexData.describe(GameData.instance().item("i_full_heal")));
        assertTrue(ItemDexData.describe(GameData.instance().item("i_antidote")).startsWith("解除"),
                "单项解除药应列出可解除的异常状态名");
        assertEquals("", ItemDexData.describe(null), "数据缺失时描述为空串");
    }

    @Test
    void 小数倍率保留小数点整数倍率不带小数() {
        assertEquals("捕捉倍率 ×3", ItemDexData.describe(
                new Item("i_t", "测试球", ItemCategory.POKE_BALL, 3)));
        assertEquals("捕捉倍率 ×4.5", ItemDexData.describe(
                new Item("i_t", "测试球", ItemCategory.POKE_BALL, 4.5)));
    }

    private static ItemDexData.Entry find(List<ItemDexData.Entry> entries, String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("图鉴中缺少条目 " + id));
    }

    private static Pokemon pokemon(String name) {
        Species species = new Species("sp_" + name, name, ElementType.NORMAL, null,
                new Stats(100, 100, 100, 100, 100, 100), 100, List.of(), null, 0, Map.of());
        return Pokemon.create(species, 10, List.of(TACKLE), FIXED_IVS);
    }
}
