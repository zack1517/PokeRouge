package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RogueTurnManager} 的单元测试：聚焦 UI 接管战斗时的扣点拆分（{@link RogueTurnManager#consumeOption(Option)}）
 * 与 {@link RogueTurnManager#selectOption(Option)} 的旧行为回归。
 */
class RogueTurnManagerTest {

    /** 构造第 1 层、仅含一个指定选项的回合管理器（清空随机生成的选项，保证断言确定性）。 */
    private static RogueTurnManager newManagerWithOption(OptionType type, String name, int cost) {
        RogueTurnManager manager = new RogueTurnManager();
        manager.startRun(List.of(), 1); // 空队伍：战斗模拟不会改变测试状态
        RunData data = manager.getRunData();
        data.getAvailableOptions().clear();
        data.getAvailableOptions().add(new Option(name, type, cost, "测试选项"));
        return manager;
    }

    @Test
    void testConsumeOption_deductsPointsWithoutBossTrigger() {
        RogueTurnManager manager = newManagerWithOption(OptionType.WILD, "测试野怪", 1);
        RunData data = manager.getRunData();
        data.setCurrentPoints(1);
        Option wild = data.getAvailableOptions().get(0);

        assertTrue(manager.consumeOption(wild));

        assertEquals(0, data.getCurrentPoints(), "应精确扣除选项点数");
        assertFalse(manager.isGameOver(), "consumeOption 只扣点，不自动触发 BOSS 判定");
        assertEquals(1, data.getCurrentFloor(), "楼层不应推进");
    }

    @Test
    void testConsumeOption_insufficientPointsReturnsFalseAndKeepsPoints() {
        RogueTurnManager manager = newManagerWithOption(OptionType.WILD, "测试野怪", 3);
        RunData data = manager.getRunData();
        data.setCurrentPoints(2);
        Option wild = data.getAvailableOptions().get(0);

        assertFalse(manager.consumeOption(wild));

        assertEquals(2, data.getCurrentPoints(), "扣点失败时点数保持不变");
    }

    @Test
    void testConsumeOption_hiddenEventReturnsFalse() {
        RogueTurnManager manager = newManagerWithOption(OptionType.RANDOM, "隐藏事件", 0);
        RunData data = manager.getRunData();
        data.setCurrentPoints(10);

        assertFalse(manager.consumeOption(data.getAvailableOptions().get(0)));

        assertEquals(10, data.getCurrentPoints(), "隐藏事件不应扣点");
    }

    @Test
    void testConsumeOption_hospitalOptionReplacedByHiddenEventInPlace() {
        RogueTurnManager manager = newManagerWithOption(OptionType.HOSPITAL, "临时急救站", 3);
        RunData data = manager.getRunData();
        data.setCurrentPoints(10);
        Option hospital = data.getAvailableOptions().get(0);

        assertTrue(manager.consumeOption(hospital));

        assertEquals(7, data.getCurrentPoints(), "急救站消耗 3 点");
        Option replaced = data.getAvailableOptions().get(0);
        assertEquals("隐藏事件", replaced.getName(), "用掉的急救站应原位替换为隐藏事件");
    }

    @Test
    void testSelectOption_zeroPointsTriggersSimulatedBossAndGameOverForEmptyTeam() {
        RogueTurnManager manager = newManagerWithOption(OptionType.WILD, "测试野怪", 1);
        RunData data = manager.getRunData();
        data.setCurrentPoints(1);
        Option wild = data.getAvailableOptions().get(0);

        manager.selectOption(wild); // 旧版组合行为：扣点 + 结算 + 点数耗尽触发模拟 BOSS

        assertEquals(0, data.getCurrentPoints());
        assertTrue(manager.isGameOver(), "空队伍在模拟 BOSS 中必败，本轮应结束");
    }
}
