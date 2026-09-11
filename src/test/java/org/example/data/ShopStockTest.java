package org.example.data;

import org.example.model.RouteConfig;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShopStock} 的单元测试：覆盖《需求文档》§4.2 商店节点 ——
 * 「随游戏进展商品种类与数量越多」以及金币计价。
 *
 * <p>用固定随机源保证结果可复现；只断言趋势与上架约束，不锁死具体商品组合。</p>
 */
class ShopStockTest {

    /** 第 1 段解锁的商品 id（池中解锁段位为 1 的三件）。 */
    private static final Set<String> FIRST_SEGMENT_ITEMS =
            Set.of("i_potion", "i_poke_ball", "i_antidote");

    @Test
    void 商品数量随段增长且不超过上限() {
        for (int segment = 1; segment <= RouteConfig.TOTAL_SEGMENTS; segment++) {
            ShopStock stock = ShopStock.forSegment(segment, new Random(segment));

            assertFalse(stock.isEmpty(), "第 " + segment + " 段商店不应为空");
            assertTrue(stock.entries().size() <= RouteConfig.shopStockSize(segment));
            assertTrue(stock.entries().size() <= RouteConfig.MAX_SHOP_STOCK);
        }
    }

    @Test
    void 越靠后的段商品越多() {
        int first = ShopStock.forSegment(1, new Random(1)).entries().size();
        int last = ShopStock.forSegment(RouteConfig.TOTAL_SEGMENTS, new Random(1)).entries().size();

        assertTrue(last > first, "越靠后的段商品种类与数量越多：" + first + " -> " + last);
    }

    @Test
    void 只上架已解锁的商品() {
        Set<String> seen = sampledIds(1, 80);

        assertEquals(FIRST_SEGMENT_ITEMS, seen,
                "第 1 段的商品池应恰为解锁的三件基础商品，实际：" + seen);
    }

    @Test
    void 新增球种按解锁段位进入商品池() {
        assertFalse(sampledIds(1, 80).contains("i_safari_ball"),
                "狩猎球解锁段位为 2，不应出现在第 1 段");
        assertTrue(sampledIds(2, 80).containsAll(Set.of("i_premier_ball", "i_safari_ball")),
                "第 2 段应能上架纪念球与狩猎球，实际：" + sampledIds(2, 80));
        assertTrue(sampledIds(3, 80).contains("i_sport_ball"), "竞赛球解锁段位为 3");
        assertTrue(sampledIds(4, 80).contains("i_cherish_ball"), "贵重球解锁段位为 4");
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
    void 商品展示名与道具注册表一致() {
        GameData data = GameData.instance();

        for (int seed = 1; seed <= 80; seed++) {
            for (ShopStock.Entry entry : ShopStock.forSegment(RouteConfig.TOTAL_SEGMENTS, new Random(seed)).entries()) {
                assertEquals(data.item(entry.itemId()).getName(), entry.itemName(),
                        "展示名必须取自道具注册表，避免两处各自维护后漂移：" + entry.itemId());
            }
        }
    }

    @Test
    void 首段库存为基础商品且按原价出售() {
        ShopStock stock = ShopStock.forSegment(1, new Random(1));

        assertEquals(RouteConfig.BASE_SHOP_STOCK, stock.entries().size(),
                "第 1 段解锁的三件商品刚好填满库存");
        ShopStock.Entry potion = stock.entries().stream()
                .filter(entry -> entry.itemId().equals("i_potion"))
                .findFirst()
                .orElseThrow();
        assertEquals(40, potion.price(), "第 1 段没有通胀，按基础价出售");
    }

    @Test
    void 末段库存达到数量上限且售价被通胀抬高() {
        ShopStock stock = ShopStock.forSegment(RouteConfig.TOTAL_SEGMENTS, new Random(5));

        assertEquals(RouteConfig.MAX_SHOP_STOCK, stock.entries().size(),
                "商品池足够大时库存取上限");
        assertTrue(stock.entries().stream().anyMatch(entry -> entry.price() > 40),
                "末段售价应已被通胀抬高");
    }

    @Test
    void 商品条目字段完整且售价为正() {
        ShopStock stock = ShopStock.forSegment(4, new Random(3));

        for (ShopStock.Entry entry : stock.entries()) {
            assertFalse(entry.itemId().isBlank(), "商品 id 不能为空");
            assertFalse(entry.itemName().isBlank(), "商品展示名不能为空");
            assertTrue(entry.price() > 0, "售价必须为正：" + entry.itemName());
        }
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
}