package org.example.model;

import java.util.Locale;

/**
 * 元素属性枚举。
 * <p>定义对战系统的元素体系；克制关系由 {@link TypeChart} 统一维护。</p>
 */
public enum ElementType {

    /** 普通。 */
    NORMAL("普通"),
    /** 火。 */
    FIRE("火"),
    /** 水。 */
    WATER("水"),
    /** 草。 */
    GRASS("草"),
    /** 电。 */
    ELECTRIC("电"),
    /** 冰。 */
    ICE("冰"),
    /** 格斗。 */
    FIGHTING("格斗"),
    /** 毒。 */
    POISON("毒"),
    /** 地面。 */
    GROUND("地面"),
    /** 飞行。 */
    FLYING("飞行"),
    /** 虫。 */
    BUG("虫"),
    /** 岩石。 */
    ROCK("岩石"),
    /** 幽灵。 */
    GHOST("幽灵"),
    /** 龙。 */
    DRAGON("龙");

    private final String displayName;

    ElementType(String displayName) {
        this.displayName = displayName;
    }

    /** 界面对外展示名称（中文）。 */
    public String getDisplayName() {
        return displayName;
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
