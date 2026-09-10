package org.example.pokemon.domain;

/**
 * 宝可梦属性枚举。
 *
 * <p>枚举类型天然实现 {@link java.io.Serializable}，无需显式声明。</p>
 */
public enum ElementType {

    NORMAL("一般"),
    FIRE("火"),
    WATER("水"),
    GRASS("草"),
    ELECTRIC("电"),
    ICE("冰"),
    FIGHTING("格斗"),
    POISON("毒"),
    GROUND("地面"),
    FLYING("飞行"),
    PSYCHIC("超能力"),
    BUG("虫"),
    ROCK("岩石"),
    GHOST("幽灵"),
    DARK("恶"),
    DRAGON("龙"),
    STEEL("钢"),
    FAIRY("妖精");

    private final String displayName;

    ElementType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 返回该属性的中文名。
     *
     * @return 中文名
     */
    public String getDisplayName() {
        return displayName;
    }
}
