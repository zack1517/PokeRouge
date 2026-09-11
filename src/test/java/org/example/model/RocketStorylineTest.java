package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 火箭队剧情线与神兽偶遇的单元测试（《需求文档》§5.1 / §5.2 / §5.3）。
 *
 * <p>覆盖三行分支表、神兽「每局至多一次」、击败首领后必然触发的 0 点神兽偶遇、
 * 冠军战后首领侵略战的触发条件，以及火箭队线节点「不可失败」的判定。</p>
 */
class RocketStorylineTest {

    /** 开一轮远征并清空随机生成的节点，保证断言确定性。 */
    private static RogueTurnManager newManager() {
        RogueTurnManager manager = new RogueTurnManager();
        manager.startRun(List.of());
        manager.getRunData().getAvailableOptions().clear();
        return manager;
    }

    /** 往当前段的节点列表里塞一个指定类型与消耗的节点。 */
    private static Option addOption(RogueTurnManager manager, OptionType type, int cost) {
        Option option = new Option(type.getDisplayName(), type, cost, "测试节点");
        manager.getRunData().getAvailableOptions().add(option);
        return option;
    }

    // ------------------------------------------------------------------
    // §5.2 火箭队节点开启剧情线 / §5.1 神兽每局一次
    // ------------------------------------------------------------------

    @Test
    void testEnterRocketNodeUnlocksStoryline() {
        RogueTurnManager manager = newManager();
        Option rocket = addOption(manager, OptionType.ROCKET, RouteConfig.ROCKET_AP_COST);

        assertFalse(manager.isRocketLineUnlocked(), "初始未开启剧情线");
        assertTrue(manager.consumeNode(rocket), "行动点足够时可进入火箭队节点");

        assertTrue(manager.isRocketLineUnlocked(), "进入过火箭队节点即开启剧情线");
        assertFalse(manager.isRocketBossDefeated(), "仅进入队员节点不等于击败首领");
    }

    @Test
    void testEnterLegendaryMarksMetAndClearsPending() {
        RogueTurnManager manager = newManager();
        manager.getRunData().setPendingLegendary(true);
        Option legendary = addOption(manager, OptionType.LEGENDARY, 0);

        assertTrue(manager.consumeNode(legendary));

        assertTrue(manager.isLegendaryMet(), "进入神兽节点即算本局已触发");
        assertFalse(manager.isPendingLegendary(), "必然触发的那次走过之后不再是待触发状态");
    }

    @Test
    void testStartRunResetsStorylineFlags() {
        RogueTurnManager manager = newManager();
        manager.getRunData().setRocketLineUnlocked(true);
        manager.getRunData().setRocketBossDefeated(true);
        manager.getRunData().setLegendaryMet(true);
        manager.getRunData().setPendingLegendary(true);
        manager.getRunData().setAggressionTriggered(true);

        manager.startRun(List.of());

        assertFalse(manager.isRocketLineUnlocked(), "新开一轮剧情线重置");
        assertFalse(manager.isRocketBossDefeated());
        assertFalse(manager.isLegendaryMet());
        assertFalse(manager.isPendingLegendary());
        assertFalse(manager.getRunData().isAggressionTriggered());
    }

    // ------------------------------------------------------------------
    // §5.3 击败火箭队首领 → 必然触发一次 0 点神兽偶遇
    // ------------------------------------------------------------------

    @Test
    void testRocketBossVictoryAppendsFreeLegendary() {
        RogueTurnManager manager = newManager();
        manager.enterSegment(4);

        Option added = manager.resolveRocketBossVictory();

        assertTrue(manager.isRocketBossDefeated(), "击败首领后标记已收束剧情线");
        assertTrue(manager.isPendingLegendary(), "获得大师球后进入待触发状态");
        assertNotNull(added);
        assertEquals(OptionType.LEGENDARY, added.getType());
        assertEquals(0, added.getCost(), "火箭队线必然触发的那次神兽偶遇不消耗行动点");
        assertTrue(manager.getRunData().getAvailableOptions().contains(added),
                "必然触发的神兽偶遇被追加进本段路线");
    }

    @Test
    void testPendingFreeLegendaryKeepsGymFromTriggering() {
        RogueTurnManager manager = newManager();
        manager.getRunData().setAp(0);
        manager.resolveRocketBossVictory();

        assertFalse(manager.advanceAfterNode(),
                "还有 0 点神兽节点可走时不得提前触发道馆战");

        manager.consumeNode(manager.getRunData().getAvailableOptions().get(0));
        assertTrue(manager.advanceAfterNode(), "0 点神兽走完后行动点耗尽，触发道馆战");
        assertEquals(RoutePhase.GYM, manager.getRunData().getPhase());
    }

    // ------------------------------------------------------------------
    // §5.3 分支表：冠军战之后的三条走向
    // ------------------------------------------------------------------

    /** 推进到末段冠军战前（末段道馆 → 四天王 → 冠军）。 */
    private static void advanceToChampion(RogueTurnManager manager) {
        manager.enterSegment(RouteConfig.TOTAL_SEGMENTS);
        manager.enterPhase(RoutePhase.GYM);
        manager.resolveMandatoryVictory();
        manager.resolveMandatoryVictory();
        assertEquals(RoutePhase.CHAMPION, manager.getRunData().getPhase(), "测试前提：已到冠军战");
    }

    @Test
    void testChampionVictoryWithUnlockedLineWithoutBossTriggersInvasion() {
        RogueTurnManager manager = newManager();
        advanceToChampion(manager);
        manager.getRunData().setRocketLineUnlocked(true);

        manager.resolveMandatoryVictory();

        assertEquals(RoutePhase.ROCKET_INVASION, manager.getRunData().getPhase(),
                "进入过火箭队节点却没打抓捕事件：击败冠军后触发首领侵略战");
        assertTrue(manager.getRunData().isAggressionTriggered());
        assertFalse(manager.isCleared(), "侵略战未打完不算通关");
    }

    @Test
    void testChampionVictoryWithBossDefeatedClearsRun() {
        RogueTurnManager manager = newManager();
        advanceToChampion(manager);
        manager.getRunData().setRocketLineUnlocked(true);
        manager.getRunData().setRocketBossDefeated(true);

        manager.resolveMandatoryVictory();

        assertTrue(manager.isCleared(), "已提前击败首领：冠军战后正常通关");
        assertFalse(manager.getRunData().isAggressionTriggered(), "不触发侵略战");
    }

    @Test
    void testChampionVictoryWithoutRocketLineClearsRun() {
        RogueTurnManager manager = newManager();
        advanceToChampion(manager);

        manager.resolveMandatoryVictory();

        assertTrue(manager.isCleared(), "全程未进入火箭队节点：击败冠军即正常通关");
        assertFalse(manager.getRunData().isAggressionTriggered());
    }

    @Test
    void testInvasionVictoryClearsRunAndPaysGold() {
        RogueTurnManager manager = newManager();
        manager.enterSegment(RouteConfig.TOTAL_SEGMENTS);
        manager.enterPhase(RoutePhase.ROCKET_INVASION);
        int goldBefore = manager.getRunData().getGold();

        manager.resolveMandatoryVictory();

        assertTrue(manager.isCleared(), "击败侵略的首领即通关");
        assertEquals(goldBefore + RouteConfig.bossAggressionWinGold(RouteConfig.TOTAL_SEGMENTS),
                manager.getRunData().getGold(), "侵略战胜利发放对应金币");
    }

    @Test
    void testInvasionDefeatEndsRunImmediately() {
        RogueTurnManager manager = newManager();
        manager.enterPhase(RoutePhase.ROCKET_INVASION);

        assertFalse(manager.resolveMandatoryDefeat(), "首领侵略战不可失败");
        assertTrue(manager.isGameOver());
        assertEquals(0, manager.getRunData().getRetryUsed(), "侵略战不消耗重试机会");
    }

    @Test
    void testDefeatEndsRunOnlyForRocketLineNodes() {
        assertTrue(OptionType.ROCKET.defeatEndsRun(), "火箭队队员节点不可失败");
        assertTrue(OptionType.ROCKET_CAPTURE.defeatEndsRun(), "抓捕神兽不可失败");
        assertTrue(OptionType.ROCKET_INVASION.defeatEndsRun(), "首领侵略战不可失败");
        assertFalse(OptionType.TRAINER.defeatEndsRun(), "路人战败仅扣金币");
        assertFalse(OptionType.WILD.defeatEndsRun(), "野外战败仅扣金币");
        assertFalse(OptionType.LEGENDARY.defeatEndsRun(), "神兽战败仅扣金币");
        assertFalse(OptionType.GYM.defeatEndsRun(), "道馆战败走重试规则");
    }

    // ------------------------------------------------------------------
    // 节点生成：剧情线状态决定特殊事件槽位
    // ------------------------------------------------------------------

    @Test
    void testGenerateSegmentPendingLegendaryAlwaysAddsFreeLegendary() {
        NodeGenerator generator = new NodeGenerator();

        SegmentPlan plan = generator.generateSegment(2, false, false, false, true);

        Option free = plan.getRouteOptions().stream()
                .filter(option -> option.getType() == OptionType.LEGENDARY)
                .findFirst()
                .orElse(null);
        assertNotNull(free, "有待触发的神兽时必然生成该节点");
        assertEquals(0, free.getCost(), "必然触发的那次神兽偶遇不消耗行动点");
    }

    @Test
    void testGenerateSegmentBeforeLateGameHasNoLegendaryOrCapture() {
        NodeGenerator generator = new NodeGenerator();

        for (int i = 0; i < 60; i++) {
            SegmentPlan plan = generator.generateSegment(1, true, false, false, false);
            assertTrue(plan.getRouteOptions().stream()
                            .noneMatch(option -> option.getType() == OptionType.LEGENDARY),
                    "神兽偶遇仅游戏后期出现");
            assertTrue(plan.getRouteOptions().stream()
                            .noneMatch(option -> option.getType() == OptionType.ROCKET_CAPTURE),
                    "抓捕神兽仅游戏后期出现");
        }
    }

    @Test
    void testGenerateSegmentLegendaryMetSuppressesLegendary() {
        NodeGenerator generator = new NodeGenerator();

        for (int i = 0; i < 60; i++) {
            SegmentPlan plan = generator.generateSegment(5, false, false, true, false);
            assertTrue(plan.getRouteOptions().stream()
                            .noneMatch(option -> option.getType() == OptionType.LEGENDARY),
                    "神兽偶遇每局至多一次，已触发过就不再生成");
        }
    }

    @Test
    void testGenerateSegmentRocketNodeAvailableInEverySegment() {
        NodeGenerator generator = new NodeGenerator();

        for (int segment = 1; segment <= RouteConfig.TOTAL_SEGMENTS; segment++) {
            boolean seen = false;
            for (int i = 0; i < 200 && !seen; i++) {
                seen = generator.generateSegment(segment, false, false, false, false)
                        .getRouteOptions().stream()
                        .anyMatch(option -> option.getType() == OptionType.ROCKET);
            }
            assertTrue(seen, "第 " + segment + " 段应有概率出现火箭队节点");
        }
    }

    @Test
    void testRouteOptionCountStaysWithinLimit() {
        NodeGenerator generator = new NodeGenerator();

        for (int i = 0; i < 60; i++) {
            SegmentPlan plan = generator.generateSegment(5, true, false, false, false);
            assertTrue(plan.getRouteOptions().size() <= RouteConfig.MAX_ROUTE_NODES,
                    "路线节点数量不应超过上限");
        }
    }

    // ------------------------------------------------------------------
    // 金币（§5.2 高收益）
    // ------------------------------------------------------------------

    @Test
    void testRocketLineGoldRewardsOutweighRegularNodes() {
        RogueTurnManager manager = newManager();
        RunData data = manager.getRunData();
        data.setGold(0);

        int rocket = manager.awardWinGold(OptionType.ROCKET);
        assertTrue(rocket > RouteConfig.trainerWinGold(1), "火箭队节点收益应高于路人节点");

        data.setGold(0);
        assertEquals(RouteConfig.rocketCaptureWinGold(1), manager.awardWinGold(OptionType.ROCKET_CAPTURE));
        assertTrue(RouteConfig.rocketCaptureWinGold(1) > rocket, "抓捕神兽的收益更高");

        data.setGold(0);
        assertEquals(RouteConfig.bossAggressionWinGold(1),
                manager.awardWinGold(OptionType.ROCKET_INVASION));
    }

    @Test
    void testLegendaryRewardMatchesConfig() {
        RogueTurnManager manager = newManager();
        manager.getRunData().setGold(0);

        assertEquals(RouteConfig.legendaryWinGold(1), manager.awardWinGold(OptionType.LEGENDARY));
    }
}
