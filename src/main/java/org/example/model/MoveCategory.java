package org.example.model;

/**
 * 技能类别。
 * <p>物理技能伤害结算使用物攻/物防，特殊技能使用特攻/特防。</p>
 */
public enum MoveCategory {

    /** 物理技能：结算物攻 vs 物防。 */
    PHYSICAL,
    /** 特殊技能：结算特攻 vs 特防。 */
    SPECIAL,
    /** 变化技能：不直接造成伤害（如天气/场地技、状态技）。 */
    STATUS
}
