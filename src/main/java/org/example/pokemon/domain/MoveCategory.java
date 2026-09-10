package org.example.pokemon.domain;

/**
 * 技能分类枚举。
 *
 * <p>枚举类型天然实现 {@link java.io.Serializable}，无需显式声明。</p>
 */
public enum MoveCategory {

    PHYSICAL("物理"),
    SPECIAL("特殊"),
    STATUS("变化");

    private final String displayName;

    MoveCategory(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 返回该技能分类的中文名。
     *
     * @return 中文名
     */
    public String getDisplayName() {
        return displayName;
    }
}
