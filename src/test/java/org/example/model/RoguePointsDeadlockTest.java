package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「点数耗尽才打层 BOSS」的死局回归测试。
 *
 * <p>起始点数为 {@code 8 + 层号 * 3 + 0~4}，而事件消耗不保证与之整除：本层若没有刷出
 * 1 点的「野怪遭遇」，可反复选择的只剩 3 点的「训练家挑战」，点数就会停在 1 或 2 点——
 * 既买不起任何事件，也永远走不到「点数归零」，玩家卡死在楼层页。此处固化
 * 「买不起任何事件」同样视为点数耗尽、直接进入 BOSS 的行为。</p>
 */
class RoguePointsDeadlockTest {

    /** 固定楼层内容，避免依赖随机刷出的事件组合。 */
    private static final class FixedFloorGenerator extends OptionGenerator {
        private final FloorData floorData;

        FixedFloorGenerator(FloorData floorData) {
            this.floorData = floorData;
        }

        @Override
        public FloorData generateFloor(int floor) {
            return floorData;
        }
    }

    private static Option option(String name, OptionType type, int cost) {
        return new Option(name, type, cost, "测试选项");
    }

    /** 第 1 层：只有 3 点事件，没有 1 点的「野怪遭遇」，且起始点数为指定的 {@code points}。 */
    private static RogueTurnManager managerWithoutCheapOption(int points) {
        List<Option> options = List.of(
                option("训练家挑战", OptionType.ENEMY, 3),
                option("神秘礼物", OptionType.RANDOM, 3),
                option("临时急救站", OptionType.HOSPITAL, 3),
                option("装备补给", OptionType.REWARD, 3));
        FloorData floorData = new FloorData(1, points, options,
                option("BOSS: 终极挑战", OptionType.ENEMY, 7));
        RogueTurnManager manager = new RogueTurnManager(new RunData(), new FixedFloorGenerator(floorData));
        manager.startRun(List.of(), 1); // 空队伍：模拟战斗不影响判定，BOSS 必定失败
        return manager;
    }

    @Test
    void leftoverPointsThatCannotBuyAnythingCountAsExhausted() {
        RogueTurnManager manager = managerWithoutCheapOption(14);
        RunData data = manager.getRunData();
        Option enemy = data.getAvailableOptions().get(0);

        for (int i = 0; i < 4; i++) {
            assertTrue(manager.consumeOption(enemy), "3 点的训练家挑战应可反复选择");
        }

        assertEquals(2, data.getCurrentPoints(), "14 点只能花到 2 点（本层没有 1 点事件）");
        assertFalse(manager.hasAffordableOption(), "剩余 2 点买不起任何事件");
        assertTrue(manager.isPointsExhausted(), "买不起任何事件时应视为点数已耗尽");
    }

    @Test
    void selectOptionEntersBossInsteadOfDeadlockingOnUnspentPoints() {
        RogueTurnManager manager = managerWithoutCheapOption(4);
        RunData data = manager.getRunData();

        manager.selectOption(data.getAvailableOptions().get(0)); // 4 - 3 = 1

        assertTrue(manager.isGameOver(), "应直接进入 BOSS 战（空队伍必败），而不是卡在楼层页");
        assertEquals(0, data.getCurrentPoints(), "进入 BOSS 后剩余点数归零");
    }

    @Test
    void bossIsNotTriggeredWhileAnAffordableOptionRemains() {
        RogueTurnManager manager = managerWithoutCheapOption(14);
        RunData data = manager.getRunData();

        manager.selectOption(data.getAvailableOptions().get(0)); // 14 - 3 = 11

        assertTrue(manager.hasAffordableOption(), "剩余 11 点仍买得起事件");
        assertFalse(manager.isPointsExhausted(), "仍有可消费事件时不应提前进入 BOSS");
        assertFalse(manager.isGameOver(), "未进入 BOSS，本轮不应结束");
        assertEquals(1, data.getCurrentFloor(), "未进入 BOSS，楼层不推进");
    }

    @Test
    void pointsDrainedToZeroStillTriggerBoss() {
        RogueTurnManager manager = managerWithoutCheapOption(9);
        RunData data = manager.getRunData();
        Option enemy = data.getAvailableOptions().get(0);

        manager.selectOption(enemy); // 9 -> 6
        assertFalse(manager.isGameOver(), "6 点仍买得起事件");
        manager.selectOption(enemy); // 6 -> 3
        assertFalse(manager.isGameOver(), "3 点仍买得起事件");
        manager.selectOption(enemy); // 3 -> 0：精确耗尽

        assertEquals(0, data.getCurrentPoints());
        assertTrue(manager.isGameOver(), "点数归零应触发 BOSS（空队伍必败）");
    }

    @Test
    void hiddenEventIsNeverConsideredAffordable() {
        RogueTurnManager manager = managerWithoutCheapOption(5);
        RunData data = manager.getRunData();

        manager.consumeOption(data.getAvailableOptions().get(1)); // 神秘礼物：5 - 3 = 2，原位替换为隐藏事件

        assertEquals("隐藏事件", data.getAvailableOptions().get(1).getName());
        assertTrue(data.getAvailableOptions().get(1).isHiddenEvent());
        assertFalse(manager.hasAffordableOption(), "0 成本的隐藏事件不可选，不能算作可消费事件");
        assertTrue(manager.isPointsExhausted());
    }
}
