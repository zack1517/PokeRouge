package org.example.util;

/**
 * 经验计算器：medium-fast 成长曲线（宝可梦标准，n^3 * 0.8）。
 * <p>契约：接口文档 v1.0 §3.1。现有 {@code Pokemon} 内部使用自身的分段曲线，
 * 本类为契约补充，供需要按 medium-fast 曲线的场景（外部模块/新经验结算）使用。</p>
 */
public final class ExperienceCalculator {

    private ExperienceCalculator() {
    }

    /**
     * 升级到目标等级所需的累计经验。
     * medium-fast: n^3 * 0.8（四舍五入），等级 ≤ 1 时返回 0。
     */
    public static int expToLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        return (int) Math.round(Math.pow(level, 3) * 0.8);
    }

    /** 从当前等级、当前累计经验升级到下一级所需经验。 */
    public static int expToNextLevel(int currentLevel, int currentExp) {
        return expToLevel(currentLevel + 1) - currentExp;
    }
}
