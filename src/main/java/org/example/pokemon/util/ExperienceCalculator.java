package org.example.pokemon.util;

/**
 * 经验值计算器，基于 medium-fast 成长曲线（n^3 * 0.8）。
 */
public class ExperienceCalculator {

    private ExperienceCalculator() {
    }

    /**
     * 升级到目标等级所需的累计经验。
     *
     * @param level 目标等级
     * @return 累计经验，等级不大于 1 时返回 0
     */
    public static int expToLevel(int level) {
        if (level <= 1) return 0;
        return (int) Math.round(Math.pow(level, 3) * 0.8);
    }

    /**
     * 从当前等级到下一级所需的经验。
     *
     * @param currentLevel 当前等级
     * @param currentExp 当前已累计经验
     * @return 升级所需经验
     */
    public static int expToNextLevel(int currentLevel, int currentExp) {
        return expToLevel(currentLevel + 1) - currentExp;
    }
}
