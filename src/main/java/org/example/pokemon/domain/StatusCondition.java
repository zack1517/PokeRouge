package org.example.pokemon.domain;

/**
 * 异常状态枚举。
 *
 * <p>枚举类型天然实现 {@link java.io.Serializable}，无需显式声明。</p>
 */
public enum StatusCondition {

    NONE("无", false),
    POISON("中毒", false),
    PARALYSIS("麻痹", false),
    BURN("灼伤", false),
    SLEEP("睡眠", true),
    FREEZE("冰冻", true),
    FAINTED("濒死", true);

    private final String displayName;

    private final boolean preventsAction;

    StatusCondition(String displayName, boolean preventsAction) {
        this.displayName = displayName;
        this.preventsAction = preventsAction;
    }

    /**
     * 返回该异常状态的中文名。
     *
     * @return 中文名
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 判断该异常状态是否阻止宝可梦行动。
     * 睡眠、濒死、冰冻状态会阻止行动。
     *
     * @return 阻止行动返回 true，否则返回 false
     */
    public boolean preventsAction() {
        return preventsAction;
    }
}
