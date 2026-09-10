package org.example.model;

import java.util.Locale;

/**
 * 元素属性枚举。
 * <p>定义对战系统的元素体系；克制关系由 {@link TypeChart} 统一维护。</p>
 */
public enum ElementType {

    /** 普通。 */
    NORMAL("普通", "#A8A878"),
    /** 火。 */
    FIRE("火", "#F08030"),
    /** 水。 */
    WATER("水", "#6890F0"),
    /** 草。 */
    GRASS("草", "#78C850"),
    /** 电。 */
    ELECTRIC("电", "#F8D030"),
    /** 冰。 */
    ICE("冰", "#98D8D8"),
    /** 格斗。 */
    FIGHTING("格斗", "#C03028"),
    /** 毒。 */
    POISON("毒", "#A040A0"),
    /** 地面。 */
    GROUND("地面", "#E0C068"),
    /** 飞行。 */
    FLYING("飞行", "#A890F0"),
    /** 虫。 */
    BUG("虫", "#A8B820"),
    /** 岩石。 */
    ROCK("岩石", "#B8A038"),
    /** 幽灵。 */
    GHOST("幽灵", "#705898"),
    /** 龙。 */
    DRAGON("龙", "#7038F8"),
    /** 超能力。 */
    PSYCHIC("超能力", "#F85888"),
    /** 妖精。 */
    FAIRY("妖精", "#EE99AC"),
    /** 恶（兼容补充：对齐新宝可梦系统的全部属性）。 */
    DARK("恶", "#705848"),
    /** 钢（兼容补充：对齐新宝可梦系统的全部属性）。 */
    STEEL("钢", "#B8B8D0");

    private final String displayName;
    private final String colorCode;

    ElementType(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    /** 界面对外展示名称（中文）。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 契约补充：CSS 颜色代码（§2.1）。 */
    public String getColorCode() {
        return colorCode;
    }

    /** 按名字解析，大小写不敏感；找不到返回 null。 */
    public static ElementType parse(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (ElementType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
