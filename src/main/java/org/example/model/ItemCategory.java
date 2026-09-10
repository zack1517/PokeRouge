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
    POKE_BALL
}
