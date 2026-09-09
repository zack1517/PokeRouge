package org.example.pokemon.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExperienceCalculator} 的单元测试。
 */
class ExperienceCalculatorTest {

    @Test
    void testExpToLevel_returnsNonNegativeForLevels1To100() {
        for (int level = 1; level <= 100; level++) {
            assertTrue(ExperienceCalculator.expToLevel(level) >= 0,
                    "等级 " + level + " 的累计经验不应为负数");
        }
    }

    @Test
    void testExpToLevel_increasesAsLevelIncreases() {
        for (int level = 2; level <= 100; level++) {
            assertTrue(ExperienceCalculator.expToLevel(level) > ExperienceCalculator.expToLevel(level - 1),
                    "等级 " + level + " 的累计经验应大于等级 " + (level - 1));
        }
    }

    @Test
    void testExpToLevel_levelOneOrBelowReturnsZero() {
        assertEquals(0, ExperienceCalculator.expToLevel(1));
        assertEquals(0, ExperienceCalculator.expToLevel(0));
        assertEquals(0, ExperienceCalculator.expToLevel(-5));
    }

    @Test
    void testExpToNextLevel_returnsRemainingExpCorrectly() {
        // 2 级累计需要 6 经验：从 0 经验升 2 级还需 6
        assertEquals(6, ExperienceCalculator.expToNextLevel(1, 0));
        // 3 级累计需要 22 经验：已有 6 经验时还需 16
        assertEquals(16, ExperienceCalculator.expToNextLevel(2, 6));
        // 经验恰好达到下一级所需时，剩余为 0
        assertEquals(0, ExperienceCalculator.expToNextLevel(1, 6));
    }
}
