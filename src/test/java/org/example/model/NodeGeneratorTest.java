package org.example.model;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * {@link NodeGenerator} 的单元测试：覆盖《需求文档》§4.2 的生成规则 ——
 * 常驻节点（路人 / 野外精灵 / 医院）每段必出、商店与特殊事件按概率出现、
 * 野外精灵有概率免单、必然节点由推进阶段决定且不占行动点。
 *
 * <p>用两个「恒命中 / 恒落空」的随机源把概率判定变成确定性断言，避免随机测试的偶发失败。</p>
 */
class NodeGeneratorTest {

    /** 随机数恒取下界：所有百分比判定都命中（商店与特殊事件必出、野外精灵必免单）。 */
    private static Random alwaysHit() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    /** 随机数恒取上界：所有百分比判定都落空。 */
    private static Random alwaysMiss() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        };
    }

    /**
     * 按脚本依次返回 {@code nextInt(100)} 的结果（用完后固定返回最后一个值），
     * 用于把「某个概率判定命中、另一个落空」写成确定性断言。
     */
    private static Random scripted(int... values) {
        return new Random() {
            private int cursor;

            @Override
            public int nextInt(int bound) {
                int value = values[Math.min(cursor++, values.length - 1)];
                return Math.min(value, bound - 1);
            }
        };
    }

    private static boolean hasType(List<Option> options, OptionType type) {
        return options.stream().anyMatch(option -> option.getType() == type);
    }

    @Test
    void 常驻节点每段必出() {
        for (int segment = 1; segment <= RouteConfig.TOTAL_SEGMENTS; segment++) {
            List<Option> options = new NodeGenerator(alwaysMiss()).generateSegment(segment).getRouteOptions();

            assertTrue(hasType(options, OptionType.TRAINER), "第 " + segment + " 段应有路人节点");
            assertTrue(hasType(options, OptionType.WILD), "第 " + segment + " 段应有野外精灵节点");
            assertTrue(hasType(options, OptionType.HOSPITAL), "第 " + segment + " 段应有医院节点");
        }
    }

    @Test
    void 随机节点按概率出现且不超过节点数上限() {
        List<Option> hit = new NodeGenerator(alwaysHit()).generateSegment(1).getRouteOptions();
        assertTrue(hasType(hit, OptionType.SHOP), "概率命中时应有商店");
        assertTrue(hasType(hit, OptionType.ROCKET), "概率命中时特殊事件槽位应放入优先级最高的火箭队节点");
        assertTrue(hit.size() <= RouteConfig.MAX_ROUTE_NODES,
                "节点数不应超过上限，实际 " + hit.size());

        List<Option> miss = new NodeGenerator(alwaysMiss()).generateSegment(1).getRouteOptions();
        assertFalse(hasType(miss, OptionType.SHOP), "概率落空时不应有商店");
        assertFalse(hasType(miss, OptionType.ROCKET), "概率落空时不应有火箭队节点");
        assertEquals(3, miss.size(), "概率落空时只剩 3 个常驻节点");
    }

    /** 特殊事件槽位只有一个：火箭队 / 宝可梦交换依次落空时，装备补给落入链尾。 */
    @Test
    void 火箭队判定落空时特殊事件槽位退回通用特殊事件() {
        // 段 1 的判定顺序：野外精灵免单 → 商店 → 火箭队 → 宝可梦交换 → 装备补给
        List<Option> options = new NodeGenerator(scripted(99, 0, 99, 99, 0))
                .generateSegment(1).getRouteOptions();

        assertTrue(hasType(options, OptionType.SHOP), "商店独立判定命中");
        assertFalse(hasType(options, OptionType.ROCKET), "火箭队判定落空");
        assertFalse(hasType(options, OptionType.TRADE), "宝可梦交换判定落空");
        assertTrue(hasType(options, OptionType.REWARD), "装备补给落入特殊事件链末尾");
    }

    /** 宝可梦交换：火箭队判定落空后按 30% 概率命中，消耗 2 点行动点。 */
    @Test
    void 宝可梦交换按概率出现在特殊事件槽位() {
        // 野外免单落空(99) → 商店命中(0) → 火箭队落空(99) → 宝可梦交换命中(0)
        List<Option> options = new NodeGenerator(scripted(99, 0, 99, 0)).generateSegment(1).getRouteOptions();

        assertTrue(hasType(options, OptionType.TRADE), "宝可梦交换概率命中时应出现在特殊事件槽位");
        assertFalse(hasType(options, OptionType.REWARD), "宝可梦交换优先于装备补给判定");
        Option trade = options.stream().filter(o -> o.getType() == OptionType.TRADE).findFirst().orElseThrow();
        assertEquals(2, trade.getCost(), "宝可梦交换消耗 2 点行动点");
    }

    @Test
    void 每个节点的行动点消耗取自其类型配置() {
        NodeGenerator generator = new NodeGenerator(alwaysMiss());

        assertEquals(OptionType.TRAINER.getApCost(), generator.createTrainer().getCost());
        assertEquals(2, generator.createTrainer().getCost(), "§4.1 路人消耗 2 点");
        assertEquals(OptionType.HOSPITAL.getApCost(), generator.createHospital().getCost());
        assertEquals(1, generator.createHospital().getCost(), "§4.1 医院消耗 1 点");
        assertEquals(OptionType.SHOP.getApCost(), generator.createShop().getCost());
        assertEquals(1, generator.createShop().getCost(), "§4.1 商店消耗 1 点");
    }

    @Test
    void 野外精灵有概率免单() {
        Option free = new NodeGenerator(alwaysHit()).createWild();
        assertEquals(0, free.getCost(), "命中免单概率时不消耗行动点");
        assertTrue(free.getDescription().contains(NodeGenerator.FREE_HINT),
                "免单时应在描述里提示玩家：" + free.getDescription());

        Option paid = new NodeGenerator(alwaysMiss()).createWild();
        assertEquals(OptionType.WILD.getApCost(), paid.getCost(), "未命中时按常规消耗");
        assertEquals(1, paid.getCost(), "§4.1 野外精灵消耗 1 点");
        assertFalse(paid.getDescription().contains(NodeGenerator.FREE_HINT));
    }

    @Test
    void 必然节点由推进阶段决定且不占用行动点() {
        NodeGenerator generator = new NodeGenerator(alwaysMiss());

        Option gym = generator.createMandatoryOption(RoutePhase.GYM, 2);
        assertNotNull(gym);
        assertEquals(OptionType.GYM, gym.getType());
        assertEquals(0, gym.getCost(), "必然节点不占行动点");
        assertTrue(gym.getType().isMandatory());
        assertTrue(gym.getName().contains("第 2 段"), "道馆名应带上段号：" + gym.getName());

        assertEquals(OptionType.ELITE_FOUR,
                generator.createMandatoryOption(RoutePhase.ELITE_FOUR, 5).getType());
        assertEquals(OptionType.CHAMPION,
                generator.createMandatoryOption(RoutePhase.CHAMPION, 5).getType());
        assertEquals(0, generator.createMandatoryOption(RoutePhase.CHAMPION, 5).getCost());
    }

    @Test
    void 探索与通关阶段没有必然节点() {
        NodeGenerator generator = new NodeGenerator(alwaysMiss());

        assertNull(generator.createMandatoryOption(RoutePhase.EXPLORING, 1));
        assertNull(generator.createMandatoryOption(RoutePhase.CLEARED, 5));
        assertNull(generator.createMandatoryOption(null, 1));
    }

    @Test
    void 段计划带上段号行动点上限与道馆预告() {
        SegmentPlan plan = new NodeGenerator(alwaysMiss()).generateSegment(3);

        assertEquals(3, plan.getSegment());
        assertEquals(RouteConfig.apLimitForSegment(3), plan.getApLimit());
        assertFalse(plan.getRouteOptions().isEmpty());
        assertNotNull(plan.getMandatoryOption(), "每段都应预告本段末的道馆战");
        assertEquals(OptionType.GYM, plan.getMandatoryOption().getType());
    }

    @Test
    void 非法段号按第一段处理() {
        SegmentPlan plan = new NodeGenerator(alwaysMiss()).generateSegment(0);

        assertEquals(1, plan.getSegment());
        assertEquals(RouteConfig.apLimitForSegment(1), plan.getApLimit());
    }
}