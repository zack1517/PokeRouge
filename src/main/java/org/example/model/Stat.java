package org.example.model;

import java.util.Locale;

/**
 * 可被<b>能力等级</b>（-6 ~ +6）影响的能力项。
 *
 * <p>HP 不参与能力等级变化；命中率与闪避等级暂未实现。等级到实际数值的换算见
 * {@link Pokemon#stageMultiplier(int)}，读取入口为 {@link Pokemon#getStatStage(Stat)}。</p>
 */
public enum Stat {

    /** 物理攻击。 */
    ATTACK("物攻"),
    /** 物理防御。 */
    DEFENSE("物防"),
    /** 特殊攻击。 */
    SP_ATTACK("特攻"),
    /** 特殊防御。 */
    SP_DEFENSE("特防"),
    /** 速度。 */
    SPEED("速度");

    private final String displayName;

    Stat(String displayName) {
        this.displayName = displayName;
    }

    /** 中文名（战斗播报用）。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 解析能力名（大小写不敏感，{@code -} 与 {@code _} 等价），未知返回 {@code null}。 */
    public static Stat parse(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Stat stat : values()) {
            if (stat.name().equals(normalized)) {
                return stat;
            }
        }
        return null;
    }
}
