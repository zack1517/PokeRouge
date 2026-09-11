package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 神秘礼物（{@link OptionType#RANDOM}）的经验收益随层数增长。 */
class RogueRandomEventTest {

    /** 升到下一级需 3×50² + 3×50 + 1 = 7651 点经验，本事件奖励远不足以触发升级，便于精确断言增量。 */
    private static final int BASE_LEVEL = 50;

    private static final OptionType RANDOM = OptionType.RANDOM;

    private static Pokemon pokemon(int level) {
        Species species = new Species("test_sp", "测试精灵", ElementType.NORMAL, null,
                new Stats(50, 50, 50, 50, 50, 50), 100, List.of("m_slam"), null, 0, Map.of());
        return Pokemon.create(species, level, List.of());
    }

    private static RogueTurnManager managerWithTeam(List<PokemonInstance> team, int floor) {
        RunData data = new RunData();
        data.setTeam(team);
        data.setCurrentFloor(floor);
        return new RogueTurnManager(data, new OptionGenerator());
    }

    private static Option mysteryGift(int floor) {
        return new Option("神秘礼物", RANDOM, 2 + floor, "测试选项");
    }

    /** 第 1 层保持改造前的 25~54，改动只影响第 2 层及以后。 */
    @Test
    void firstFloorKeepsLegacyRange() {
        int base = RogueTurnManager.randomEventBaseExp(1);
        int spread = RogueTurnManager.randomEventExpSpread(1);

        assertEquals(25, base);
        assertEquals(30, spread);
        assertEquals(54, base + spread - 1, "第 1 层值域上限应仍为 54");
    }

    /** 层号越界（0 或负数）按第 1 层处理，避免负的经验或负的随机宽度。 */
    @Test
    void floorBelowOneFallsBackToFirstFloor() {
        assertEquals(RogueTurnManager.randomEventBaseExp(1), RogueTurnManager.randomEventBaseExp(0));
        assertEquals(RogueTurnManager.randomEventBaseExp(1), RogueTurnManager.randomEventBaseExp(-5));
        assertEquals(RogueTurnManager.randomEventExpSpread(1), RogueTurnManager.randomEventExpSpread(0));
        assertEquals(RogueTurnManager.randomEventExpSpread(1), RogueTurnManager.randomEventExpSpread(-5));
    }

    /** 下限、上限与平均收益都随层号严格递增。 */
    @Test
    void rewardGrowsMonotonicallyWithFloor() {
        int previousBase = 0;
        int previousTop = 0;
        double previousAverage = 0;
        for (int floor = 1; floor <= 30; floor++) {
            int base = RogueTurnManager.randomEventBaseExp(floor);
            int spread = RogueTurnManager.randomEventExpSpread(floor);
            int top = base + spread - 1;

            assertTrue(spread > 0, "第 " + floor + " 层随机宽度必须为正");
            assertTrue(base > previousBase, "第 " + floor + " 层经验下限应高于上一层");
            assertTrue(top > previousTop, "第 " + floor + " 层经验上限应高于上一层");
            double average = base + (spread - 1) / 2.0;
            assertTrue(average > previousAverage, "第 " + floor + " 层平均经验应高于上一层");

            previousBase = base;
            previousTop = top;
            previousAverage = average;
        }
    }

    /** 结算后全队每只精灵的经验增量都落在本层区间内。 */
    @Test
    void wholeTeamGainsExpWithinFloorRange() {
        for (int floor : List.of(1, 2, 5, 10)) {
            int base = RogueTurnManager.randomEventBaseExp(floor);
            int spread = RogueTurnManager.randomEventExpSpread(floor);

            List<PokemonInstance> team = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                team.add(new PokemonInstance(pokemon(BASE_LEVEL)));
            }
            RogueTurnManager manager = managerWithTeam(team, floor);

            manager.resolveOptionEffect(mysteryGift(floor));

            for (PokemonInstance instance : team) {
                long gained = instance.getPokemon().getExp();
                assertTrue(gained >= base && gained <= base + spread - 1,
                        "第 " + floor + " 层经验增量应落在 " + base + "~" + (base + spread - 1) + "，实际 " + gained);
            }
        }
    }

    /** 深层收益确实明显高于第 1 层，且随层号继续拉开。 */
    @Test
    void deepFloorRewardsMoreThanFirstFloor() {
        assertEquals(115, RogueTurnManager.randomEventBaseExp(10), "第 10 层下限应为 115");
        assertEquals(75, RogueTurnManager.randomEventExpSpread(10), "第 10 层随机宽度应为 75");
        assertEquals(189, RogueTurnManager.randomEventBaseExp(10)
                + RogueTurnManager.randomEventExpSpread(10) - 1, "第 10 层上限应为 189");
        assertTrue(RogueTurnManager.randomEventBaseExp(10) > RogueTurnManager.randomEventBaseExp(1),
                "第 10 层下限应高于第 1 层");
        assertTrue(RogueTurnManager.randomEventBaseExp(20) > RogueTurnManager.randomEventBaseExp(10),
                "第 20 层下限应继续高于第 10 层");
    }

    /** 满级精灵不再累积经验，事件本身不应抛异常。 */
    @Test
    void maxLevelPokemonDoesNotBreakEvent() {
        PokemonInstance maxed = new PokemonInstance(pokemon(Pokemon.MAX_LEVEL));
        List<PokemonInstance> team = new ArrayList<>(List.of(maxed));
        RogueTurnManager manager = managerWithTeam(team, 3);

        manager.resolveOptionEffect(mysteryGift(3));

        assertEquals(0, maxed.getPokemon().getExp(), "满级后不再累积经验");
    }

    /** 空队伍 / 队伍含空槽时事件安全降级。 */
    @Test
    void emptyTeamDoesNotBreakEvent() {
        RogueTurnManager manager = managerWithTeam(new ArrayList<>(), 2);

        manager.resolveOptionEffect(mysteryGift(2));

        assertEquals(2, manager.getRunData().getCurrentFloor(), "事件不改变楼层");
    }
}
