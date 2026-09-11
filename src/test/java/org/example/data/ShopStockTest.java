package org.example.data;

import org.example.model.RouteConfig;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;

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
        ShopStock first = ShopStock.forSegment(1, new Random(7));

        for (ShopStock.Entry entry : first.entries()) {
            assertTrue(FIRST_SEGMENT_ITEMS.contains(entry.itemId()),
                    "第 1 段不应出现未解锁商品，实际：" + entry.itemId());
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
}