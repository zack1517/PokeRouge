package org.example.model;

/**
 * 装备效果类型。
 *
 * <p>对应 equipment.csv 的 effectType 列，值域固定（引擎按此枚举应用效果）：</p>
 * <ul>
 *     <li>{@link #DAMAGE_TYPE}：携带者使用对应属性招式时威力提升（param 为 属性|倍率）</li>
 *     <li>{@link #SUPER_EFFECTIVE}：效果拔群时伤害提升（param 为倍率）</li>
 *     <li>{@link #END_TURN_HEAL}：回合末按最大 HP 比例回血（param 为比例）</li>
 *     <li>{@link #LIFE_STEAL}：攻击造成伤害按比例吸血（param 为比例）</li>
 *     <li>{@link #FIRST_STRIKE}：概率无视速度先手（param 为概率百分比）</li>
 *     <li>{@link #EVOLITE}：未最终进化时防御/特防提升（param 为倍率）</li>
 * </ul>
 */
public enum HeldItemEffect {
    DAMAGE_TYPE,
    SUPER_EFFECTIVE,
    END_TURN_HEAL,
    LIFE_STEAL,
    FIRST_STRIKE,
    EVOLITE
}
