package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RouteConfig} 的单元测试：把《需求文档》§4 里「行动点上限随进度提升」「商店随进展变多、
 * 变贵」「敌人随进度变强」「自回血 1/5」这些可调数值的<b>趋势</b>钉住。
 *
 * <p>具体数值仍是「待配置」项，因此这里只断言相对关系（递增 / 有上限 / 非法输入兜底），
 * 不锁死绝对值 —— 调平衡时改 {@link RouteConfig} 不会大面积翻测试。</p>
 */
class RouteConfigTest {

    @Test
    void 行动点上限从基础值起步并随段递增() {
        assertEquals(RouteConfig.BASE_AP_LIMIT, RouteConfig.apLimitForSegment(1));
        assertTrue(RouteConfig.apLimitForSegment(2) > RouteConfig.apLimitForSegment(1));
        assertTrue(RouteConfig.apLimitForSegment(5) > RouteConfig.apLimitForSegment(4));
        assertEquals(RouteConfig.apLimitForSegment(1), RouteConfig.apLimitForSegment(0),
                "非法段号按第 1 段处理");
        assertEquals(RouteConfig.apLimitForSegment(1), RouteConfig.apLimitForSegment(-3));
    }

    @Test
    void 自回血为最大hp的五分之一且不产生负数() {
        assertEquals(20, RouteConfig.nodeHealAmount(100));
        assertEquals(0, RouteConfig.nodeHealAmount(0));
        assertEquals(0, RouteConfig.nodeHealAmount(-50), "非法 HP 不应算出负回复量");
        assertEquals(0, RouteConfig.nodeHealAmount(4), "整数除法向下取整");
    }

    @Test
    void 商店商品数量随段增长但有上限() {
        assertEquals(RouteConfig.BASE_SHOP_STOCK, RouteConfig.shopStockSize(1));
        assertTrue(RouteConfig.shopStockSize(3) > RouteConfig.shopStockSize(1));
        assertEquals(RouteConfig.MAX_SHOP_STOCK, RouteConfig.shopStockSize(99));
        assertEquals(RouteConfig.BASE_SHOP_STOCK, RouteConfig.shopStockSize(0), "非法段号按第 1 段处理");
    }

    @Test
    void 商店售价首段为原价其后随通胀上浮() {
        assertEquals(100, RouteConfig.shopPrice(100, 1));
        assertTrue(RouteConfig.shopPrice(100, 2) > 100);
        assertTrue(RouteConfig.shopPrice(100, 5) > RouteConfig.shopPrice(100, 2));
        assertEquals(RouteConfig.shopPrice(100, 1), RouteConfig.shopPrice(100, 0),
                "非法段号按第 1 段处理");
    }

    @Test
    void 金币奖惩随段提高() {
        assertTrue(RouteConfig.trainerWinGold(2) > RouteConfig.trainerWinGold(1));
        assertTrue(RouteConfig.wildWinGold(2) > RouteConfig.wildWinGold(1));
        assertTrue(RouteConfig.specialGold(2) > RouteConfig.specialGold(1));
        assertTrue(RouteConfig.gymWinGold(2) > RouteConfig.gymWinGold(1));
        assertTrue(RouteConfig.eliteFourWinGold(2) > RouteConfig.eliteFourWinGold(1));
        assertTrue(RouteConfig.championWinGold(2) > RouteConfig.championWinGold(1));
        assertTrue(RouteConfig.defeatGoldPenalty(2) > RouteConfig.defeatGoldPenalty(1));
    }

    @Test
    void 冠军奖励高于四天王高于道馆() {
        assertTrue(RouteConfig.gymWinGold(1) > RouteConfig.trainerWinGold(1),
                "道馆奖励应显著高于路人");
        assertTrue(RouteConfig.eliteFourWinGold(1) > RouteConfig.gymWinGold(1));
        assertTrue(RouteConfig.championWinGold(1) > RouteConfig.eliteFourWinGold(1));
        assertTrue(RouteConfig.mandatoryDefeatGoldPenalty(1) > RouteConfig.defeatGoldPenalty(1),
                "道馆 / 四天王战败的代价应高于路人 / 野外");
    }

    @Test
    void 敌人强度随段推进且强度阶梯为道馆四天王冠军() {
        assertTrue(RouteConfig.gymLevelBonus(3) > RouteConfig.gymLevelBonus(1));
        assertTrue(RouteConfig.eliteFourLevelBonus(3) > RouteConfig.gymLevelBonus(3),
                "四天王强于同段道馆");
        assertTrue(RouteConfig.championLevelBonus(3) > RouteConfig.eliteFourLevelBonus(3),
                "冠军最强");
        assertTrue(RouteConfig.trainerLevelBonus(1) > RouteConfig.wildLevelBonus(1),
                "路人训练家强于野外精灵");
    }

    @Test
    void 对手队伍规模有上限() {
        assertTrue(RouteConfig.gymPartySize(99) <= 3);
        assertTrue(RouteConfig.eliteFourPartySize(99) <= 4);
        assertTrue(RouteConfig.championPartySize(99) <= 6);
        assertTrue(RouteConfig.championPartySize(1) >= RouteConfig.gymPartySize(1));
    }

    @Test
    void 总段数与节点上限已被配置钉住() {
        assertEquals(5, RouteConfig.TOTAL_SEGMENTS);
        assertTrue(RouteConfig.MAX_ROUTE_NODES >= 5, "3 个常驻节点外还要容得下商店与特殊事件");
        assertTrue(RouteConfig.STARTING_GOLD > 0, "新远征必须带得动起始金币");
        assertTrue(RouteConfig.SPECIAL_AP_COST > 0, "普通特殊事件（神兽偶遇）消耗行动点");
    }
}