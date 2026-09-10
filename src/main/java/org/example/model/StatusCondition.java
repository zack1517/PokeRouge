package org.example.model;

/**
 * 精灵异常状态。
 * <p>契约：接口文档 v1.0 §2.2。与现有战斗引擎（{@code BattleEngine}）尚未接入，
 * 先按契约提供枚举与判定，后续战斗模块按需启用。</p>
 */
public enum StatusCondition {

    /** 无异常。 */
    NONE("无"),
    /** 中毒：回合结束持续扣血。 */
    POISON("中毒"),
    /** 麻痹：速度减半、有概率无法行动。 */
    PARALYSIS("麻痹"),
    /** 灼伤：回合结束持续扣血、物攻减半。 */
    BURN("灼伤"),
    /** 睡眠：无法行动数回合。 */
    SLEEP("睡眠"),
    /** 冰冻：无法行动直到解除。 */
    FREEZE("冰冻"),
    /** 濒死：HP 归零。 */
    FAINTED("濒死");

    private final String displayName;

    StatusCondition(String displayName) {
        this.displayName = displayName;
    }

    /** 界面对外展示名称（中文）。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 该异常是否阻止精灵行动（睡眠/濒死）。 */
    public boolean preventsAction() {
        return this == SLEEP || this == FAINTED;
    }
}
