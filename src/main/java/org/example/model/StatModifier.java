package org.example.model;

/**
 * 六项能力修正的枚举（用于性格对能力的 ±10% 修正，以及战斗中的能力等级变化）。
 * <p>契约：接口文档 v1.0 §2.3。</p>
 */
public enum StatModifier {
    HP,
    ATTACK,
    DEFENSE,
    SP_ATTACK,
    SP_DEFENSE,
    SPEED;

    /** 中文显示名（用于战斗日志与能力等级播报）。 */
    public String getDisplayName() {
        return switch (this) {
            case HP -> "HP";
            case ATTACK -> "攻击";
            case DEFENSE -> "防御";
            case SP_ATTACK -> "特攻";
            case SP_DEFENSE -> "特防";
            case SPEED -> "速度";
        };
    }

    /**
     * 解析能力项英文名（大小写不敏感，容忍前后空格）；无法解析时返回 {@code null}。
     *
     * @param name 能力项英文名，如 {@code SP_ATTACK}
     */
    public static StatModifier parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
