package com.bao01.model;

/**
 * 招式分类。
 *
 * <p>决定伤害公式中攻击 / 防御所取的能力项：
 * 物理招式取 攻击 vs 防御，特殊招式取 特攻 vs 特防；
 * 变化类招式不造成直接伤害，转而触发招式效果（如改变能力等级）。
 */
public enum MoveCategory {

    PHYSICAL("物理"),
    SPECIAL("特殊"),
    STATUS("变化");

    private final String label;

    MoveCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
