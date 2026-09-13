package org.example.model;

/**
 * 道具类别。
 */
public enum ItemCategory {

    /** 恢复类道具：回复我方精灵 HP。 */
    HEAL,
    /** 解除类道具：治愈我方精灵的异常状态（见 {@link Item#getCuresSpec()}）。 */
    CURE,
    /** 精灵球：用于捕捉野生精灵。 */
    POKE_BALL,
    /**
     * 升级类道具：直接提升我方精灵的等级（见 {@link Item#getEffect()}，单位为「提升的等级数」）。
     * <p>当前只有神奇糖果。精灵球之外的消耗品均可在局外使用，但局外使用不包含战斗中的投球判定，
     * 因此本类别只作用于我方队伍成员。</p>
     */
    LEVEL_UP
}
