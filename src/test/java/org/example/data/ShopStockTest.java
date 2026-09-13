package org.example.data;

import org.example.model.HeldItem;
import org.example.model.RouteConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShopStock} 的单元测试：覆盖《需求文档》§4.2 商店节点 ——
 * 消耗品每段随机上架、装备全量上架，以及金币计价。
 *
 * <p>货架分消耗品与装备两个分区：<b>消耗品</b>从池里随机抽
 * {@link RouteConfig#shopConsumableStockSize(int)} 件（第 1 段 3 件 → 上限 6 件），池为 15 件
 * （另有 1 件剧情专属道具只进目录、不进商店）；<b>装备</b>把未拥有的<b>全量</b>列出（47 件装备
 * + 30 种树果，按 {@code HeldItemEffect} 定基础价），玩家进店即可任选购买。「越往后越多」只由
 * 消耗品格位数量与售价通胀体现。</p>
 *
 * <p>用固定随机源保证结果可复现；只断言趋势与上架约束，不锁死具体商品组合。</p>
 */
class ShopStockTest {

    @Test
    void 消耗品数量随段增长且不超过上限() {
        for (int segment = 1; segment <= RouteConfig.TOTAL_SEGMENTS; segment++) {
            ShopStock stock = ShopStock.forSegment(segment, new Random(segment));

            assertFalse(stock.isEmpty(), "第 " + segment + " 段商店不应为空");
            int consumables = entriesOf(stock, false).size();
            assertTrue(consumables <= RouteConfig.shopConsumableStockSize(segment),
                    "第 " + segment + " 段消耗品不应超过格位上限");
            assertTrue(consumables <= RouteConfig.MAX_SHOP_CONSUMABLE_STOCK);
        }
    }

    @Test
    void 越靠后的段消耗品上架越多而装备数量恒定() {
        int first = entriesOf(ShopStock.forSegment(1, new Random(1)), false).size();
        int last = entriesOf(ShopStock.forSegment(RouteConfig.TOTAL_SEGMENTS, new Random(1)), false).size();

        assertTrue(last > first, "越靠后的段消耗品格位越多：" + first + " -> " + last);
        assertEquals(entriesOf(ShopStock.forSegment(1, new Random(1)), true).size(),
                entriesOf(ShopStock.forSegment(RouteConfig.TOTAL_SEGMENTS, new Random(1)), true).size(),
                "装备全量上架，数量不随段号变化");
    }

    @Test
    void 首段即可上架全部消耗品() {
        Set<String> pool = new TreeSet<>(ShopStock.sellableConsumableIds());
        Set<String> seen = sampledConsumableIds(1, 120);

        assertEquals(pool, seen, "第 1 段就应能抽到全部可售消耗品，实际：" + seen);
    }

    @Test
    void 球种与高级药品不再按段位解锁() {
        Set<String> first = sampledConsumableIds(1, 120);

        assertTrue(first.containsAll(Set.of("i_premier_ball", "i_safari_ball",
                        "i_sport_ball", "i_cherish_ball")),
                "全部球种都应在第 1 段即可上架，实际：" + first);
        assertTrue(first.containsAll(Set.of("i_full_heal", "i_ultra_ball")),
                "高级药品也应在第 1 段即可上架，实际：" + first);
    }

    @Test
    void 大师球不在货架上() {
        assertFalse(ShopStock.sellableConsumableIds().contains("i_master_ball"),
                "大师球为火箭队首领战战利品，不应进入商店抽签池");

        for (int segment = 1; segment <= RouteConfig.TOTAL_SEGMENTS; segment++) {
            assertFalse(sampledIds(segment, 120).contains("i_master_ball"),
                    "第 " + segment + " 段货架不应出现大师球");
        }
    }

    @Test
    void 球类商品倍率来自道具注册表() {
        GameData data = GameData.instance();

        assertEquals(4.5, data.item("i_safari_ball").getEffect(), "狩猎球为固定倍率 ×4.5");
        assertEquals(4.5, data.item("i_sport_ball").getEffect(), "竞赛球为固定倍率 ×4.5");
        assertEquals(3.0, data.item("i_premier_ball").getEffect(), "纪念球为基准倍率 ×3");
        assertEquals(3.0, data.item("i_cherish_ball").getEffect(), "贵重球为基准倍率 ×3");
        assertFalse(data.item("i_safari_ball").isAlwaysCatch(), "固定倍率球不应必定捕捉");
    }

    @Test
    void 商品展示名与注册表一致() {
        GameData data = GameData.instance();

        for (int seed = 1; seed <= 80; seed++) {
            for (ShopStock.Entry entry : ShopStock.forSegment(RouteConfig.TOTAL_SEGMENTS, new Random(seed)).entries()) {
                String registryName = entry.isEquipment()
                        ? data.equipment(entry.itemId()).getName()
                        : data.item(entry.itemId()).getName();
                assertEquals(registryName, entry.itemName(),
                        "展示名必须取自注册表，避免两处各自维护后漂移：" + entry.itemId());
            }
        }
    }

    @Test
    void 每段货架都一次列全未拥有的装备() {
        int all = ShopStock.sellableEquipment(Set.of()).size();

        for (int segment = 1; segment <= RouteConfig.TOTAL_SEGMENTS; segment++) {
            for (int seed = 1; seed <= 40; seed++) {
                ShopStock stock = ShopStock.forSegment(segment, new Random(seed));
                Set<String> onShelf = new TreeSet<>(entriesOf(stock, true).stream()
                        .map(ShopStock.Entry::itemId).toList());

                assertEquals(all, onShelf.size(),
                        "第 " + segment + " 段应一次列出全部 " + all + " 件装备（seed=" + seed + "）");
            }
        }
    }

    @Test
    void 首段即可任选任意一件装备购买() {
        Set<String> all = GameData.instance().allEquipment().stream()
                .map(HeldItem::getId)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> onShelf = new TreeSet<>(entriesOf(ShopStock.forSegment(1, new Random(1)), true).stream()
                .map(ShopStock.Entry::itemId).toList());

        assertEquals(all, onShelf, "第 1 段的货架就应覆盖全部装备（含高价讲究系 / 生命宝珠）");
        assertTrue(onShelf.contains("e_life_orb") && onShelf.contains("e_choice_band"),
                "高价装备也应在第 1 段直接可买");
    }

    @Test
    void 全部装备都能上架且第一件就可能出现() {
        Set<String> all = GameData.instance().allEquipment().stream()
                .map(HeldItem::getId)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> pool = ShopStock.sellableEquipment(Set.of()).stream()
                .map(HeldItem::getId)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(all, pool, "装备池应等于数据表的全部装备（不分段解锁）");
        assertTrue(all.size() >= 77, "装备与树果合计应不少于 77 件，实际：" + all.size());
        assertTrue(all.stream().anyMatch(id -> id.startsWith("b_")), "树果也应上架（30 种）");
    }

    @Test
    void 装备与段号无关一次列全() {
        Set<String> all = GameData.instance().allEquipment().stream()
                .map(HeldItem::getId)
                .collect(Collectors.toSet());

        assertEquals(all, Set.copyOf(ShopStock.sellableEquipment(Set.of()).stream()
                        .map(HeldItem::getId).toList()),
                "第 1 段的装备池就应等于全量装备池，不再按段位放开");

        Set<String> firstSegment = new TreeSet<>(entriesOf(ShopStock.forSegment(1, new Random(1)), true).stream()
                .map(ShopStock.Entry::itemId).toList());
        assertEquals(all.size(), firstSegment.size(),
                "第 1 段就应一次列出全部装备，实际只见到 " + firstSegment.size() + " 件");
        assertTrue(firstSegment.contains("e_life_orb") && firstSegment.contains("e_choice_band"),
                "高价的讲究系与生命宝珠也应在第 1 段直接可买，实际：" + firstSegment);
    }

    @Test
    void 已拥有的装备不再上架() {
        List<HeldItem> all = GameData.instance().allEquipment();
        Set<String> owned = all.stream()
                .map(HeldItem::getId)
                .filter(id -> !id.equals("e_charcoal"))
                .collect(Collectors.toSet());

        List<HeldItem> pool = ShopStock.sellableEquipment(owned);

        assertEquals(List.of("e_charcoal"), pool.stream().map(HeldItem::getId).toList(),
                "未拥有的装备才应留在装备池");

        for (int seed = 1; seed <= 40; seed++) {
            ShopStock stock = ShopStock.forSegment(RouteConfig.TOTAL_SEGMENTS, new Random(seed), owned);
            assertTrue(stock.entries().stream().noneMatch(entry -> owned.contains(entry.itemId())),
                    "已拥有的装备不应出现在货架上：" + stock.entries());
            assertEquals(List.of("e_charcoal"), entriesOf(stock, true).stream()
                            .map(ShopStock.Entry::itemId).toList(),
                    "未拥有的那件装备仍应照常上架");
        }
    }

    @Test
    void 消耗品商品池排除不售卖的道具() {
        List<String> pool = ShopStock.sellableConsumableIds();

        assertEquals(15, pool.size(), "可售消耗品应为 15 件（大师球除外）");
        assertFalse(pool.contains("i_master_ball"), "大师球不进商店");
        assertTrue(pool.containsAll(List.of("i_potion", "i_poke_ball", "i_antidote",
                        "i_super_potion", "i_great_ball", "i_safari_ball",
                        "i_full_heal", "i_ultra_ball", "i_sport_ball", "i_cherish_ball")),
                "全部可售消耗品都应在池中，实际：" + pool);
        assertEquals(pool.size(), Set.copyOf(pool).size(), "商品池 id 不应重复");
    }

    @Test
    void 首段消耗品填满格位且按原价出售() {
        ShopStock stock = ShopStock.forSegment(1, new Random(1));

        List<ShopStock.Entry> consumables = entriesOf(stock, false);
        assertEquals(RouteConfig.BASE_SHOP_CONSUMABLE_STOCK, consumables.size(),
                "第 1 段应上架 " + RouteConfig.BASE_SHOP_CONSUMABLE_STOCK + " 件消耗品");
        assertTrue(consumables.stream()
                        .allMatch(entry -> ShopStock.sellableConsumableIds().contains(entry.itemId())),
                "第 1 段消耗品应全部来自可售商品池：" + consumables);

        ShopStock.Entry potion = stock.entries().stream()
                .filter(entry -> entry.itemId().equals("i_potion"))
                .findFirst()
                .orElse(null);
        if (potion != null) {
            assertEquals(40, potion.price(), "第 1 段没有通胀，按基础价出售");
        }
    }

    @Test
    void 末段消耗品取上限且售价被通胀抬高() {
        ShopStock stock = ShopStock.forSegment(RouteConfig.TOTAL_SEGMENTS, new Random(5));

        assertEquals(RouteConfig.MAX_SHOP_CONSUMABLE_STOCK, entriesOf(stock, false).size(),
                "消耗品池足够大时取格位上限");
        assertEquals(ShopStock.sellableEquipment(Set.of()).size(), entriesOf(stock, true).size(),
                "装备始终全量上架");
        assertTrue(stock.entries().stream().filter(ShopStock.Entry::isEquipment)
                        .allMatch(entry -> entry.price() >= 60),
                "末段装备售价应已被通胀抬高");
    }

    @Test
    void 商品条目字段完整且售价为正() {
        ShopStock stock = ShopStock.forSegment(4, new Random(3));

        for (ShopStock.Entry entry : stock.entries()) {
            assertFalse(entry.itemId().isBlank(), "商品 id 不能为空");
            assertFalse(entry.itemName().isBlank(), "商品展示名不能为空");
            assertTrue(entry.price() > 0, "售价必须为正：" + entry.itemName());
            if (entry.isEquipment()) {
                assertFalse(entry.description().isBlank(), "装备应带上效果说明：" + entry.itemId());
            }
        }
    }

    @Test
    void 已售出的装备可单独下架() {
        ShopStock stock = ShopStock.forSegment(RouteConfig.TOTAL_SEGMENTS, new Random(7));
        ShopStock.Entry equipment = stock.entries().stream()
                .filter(ShopStock.Entry::isEquipment)
                .findFirst()
                .orElseThrow();

        ShopStock after = stock.withoutEntry(equipment.itemId());

        assertEquals(stock.entries().size() - 1, after.entries().size());
        assertTrue(after.entries().stream().noneMatch(entry -> entry.itemId().equals(equipment.itemId())));
        assertEquals(stock.entries(), stock.withoutEntry("i_not_exist").entries(),
                "下架不存在的商品应原样返回");
    }

    @Test
    void 段号可读且非法段号按第一段处理() {
        assertEquals(4, ShopStock.forSegment(4, new Random(2)).segment());
        assertEquals(1, ShopStock.forSegment(0, new Random(2)).segment());
        assertEquals(1, ShopStock.forSegment(-5, new Random(2)).segment());
    }

    @Test
    void 库存列表不可变() {
        ShopStock stock = ShopStock.forSegment(2, new Random(2));

        assertThrows(UnsupportedOperationException.class,
                () -> stock.entries().add(new ShopStock.Entry("x", "x", 1)),
                "库存列表不可变，避免界面层改写商店内容");
    }

    @Test
    void 相同随机源结果可复现() {
        assertEquals(ShopStock.forSegment(3, new Random(99)).entries(),
                ShopStock.forSegment(3, new Random(99)).entries(),
                "固定随机源应产出完全一致的库存，测试与复现才能稳定");
    }

    @Test
    void 空随机源回退到默认随机() {
        assertFalse(ShopStock.forSegment(2, null).isEmpty(), "随机源为 null 时不应崩，也不应给出空商店");
        assertFalse(ShopStock.forSegment(2, new Random(1), null).isEmpty(),
                "已拥有装备集合为 null 时不应崩");
    }

    @Test
    void 目录列全消耗品与装备() {
        List<ShopStock.CatalogEntry> catalog = ShopStock.catalog();

        assertEquals(93, catalog.size(), "目录应为 16 件消耗品 + 77 件装备");
        assertEquals(16, catalog.stream().filter(e -> !e.equipment()).count(), "消耗品 16 件");
        assertEquals(77, catalog.stream().filter(ShopStock.CatalogEntry::equipment).count(),
                "装备与树果合计 77 件");
        assertEquals(catalog.size(), catalog.stream().map(ShopStock.CatalogEntry::id).distinct().count(),
                "目录 id 不应重复");
    }

    @Test
    void 目录条目字段合法() {
        List<ShopStock.CatalogEntry> catalog = ShopStock.catalog();

        for (ShopStock.CatalogEntry entry : catalog) {
            assertFalse(entry.name().isBlank(), entry.id() + " 应有展示名");
            assertTrue(entry.basePrice() > 0, entry.id() + " 基础价应为正");
            if (entry.equipment()) {
                assertFalse(entry.description().isBlank(), entry.id() + " 装备应有效果说明");
                assertTrue(entry.sold(), entry.id() + " 装备都应可购买");
            }
        }
    }

    @Test
    void 目录列出不售卖的大师球() {
        ShopStock.CatalogEntry master = ShopStock.catalog().stream()
                .filter(entry -> entry.id().equals("i_master_ball")).findFirst().orElseThrow();

        assertFalse(master.sold(), "大师球应标注为不售卖");
        assertFalse(master.equipment(), "大师球是消耗品而非装备");
        assertFalse(master.name().isBlank(), "大师球仍应有展示名供图鉴使用");
    }

    @Test
    void 目录与货架的口径一致() {
        List<ShopStock.CatalogEntry> catalog = ShopStock.catalog();
        ShopStock.CatalogEntry potion = catalog.stream()
                .filter(e -> !e.equipment() && e.id().equals("i_potion")).findFirst().orElseThrow();

        // 第 1 段的货架必须只抽出目录里「在售」的商品
        for (ShopStock.Entry entry : ShopStock.forSegment(1, new Random(7)).entries()) {
            ShopStock.CatalogEntry definition = catalog.stream()
                    .filter(e -> e.id().equals(entry.itemId())).findFirst().orElseThrow();
            assertTrue(definition.sold(), entry.itemId() + " 不在售却出现在第 1 段货架");
        }
        assertEquals(40, potion.basePrice(), "伤药基础价应与消耗品池一致");
    }

    /** 取货架上的某类商品（{@code equipment = true} 取装备，否则取消耗品），保持货架顺序。 */
    private static List<ShopStock.Entry> entriesOf(ShopStock stock, boolean equipment) {
        return stock.entries().stream()
                .filter(entry -> entry.isEquipment() == equipment)
                .toList();
    }

    /** 用多组固定随机源反复采样指定段位，返回出现过的全部商品 id（避免单次洗牌的偶然性）。 */
    private static Set<String> sampledIds(int segment, int seeds) {
        Set<String> ids = new TreeSet<>();
        for (int seed = 1; seed <= seeds; seed++) {
            for (ShopStock.Entry entry : ShopStock.forSegment(segment, new Random(seed)).entries()) {
                ids.add(entry.itemId());
            }
        }
        return ids;
    }

    /** 只采集消耗品 id（装备与树果的 id 前缀为 {@code e_} / {@code b_}）。 */
    private static Set<String> sampledConsumableIds(int segment, int seeds) {
        Set<String> ids = new TreeSet<>();
        for (String id : sampledIds(segment, seeds)) {
            if (id.startsWith("i_")) {
                ids.add(id);
            }
        }
        return ids;
    }
}
