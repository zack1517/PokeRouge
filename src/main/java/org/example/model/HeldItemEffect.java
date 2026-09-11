package org.example.model;

/**
 * 装备效果类型。
 *
 * <p>对应 equipment.csv 的 effectType 列，值域固定（引擎按此枚举应用效果）。param 一律用
 * {@code |} 分隔多段参数，解析见 {@link HeldItem#textPart(int)} / {@link HeldItem#doublePart(int, double)}。</p>
 *
 * <p><b>招式威力类</b></p>
 * <ul>
 *     <li>{@link #DAMAGE_TYPE}：携带者使用对应属性招式时威力提升（param 为 属性|倍率）</li>
 *     <li>{@link #SUPER_EFFECTIVE}：效果拔群时伤害提升（param 为倍率）</li>
 *     <li>{@link #PHYSICAL_DAMAGE}：物理招式伤害提升（param 为倍率，力量头带）</li>
 *     <li>{@link #SPECIAL_DAMAGE}：特殊招式伤害提升（param 为倍率，博识眼镜）</li>
 *     <li>{@link #LIFE_ORB}：全类别招式伤害提升，每次命中后反伤（param 为 倍率|反伤比例，生命宝珠）</li>
 * </ul>
 *
 * <p><b>速度与出手顺序类</b></p>
 * <ul>
 *     <li>{@link #SPEED_MULTIPLIER}：实际速度按倍率修正。param 为 {@code 倍率[|GROUND]}，
 *         第 2 段填 {@code GROUND} 时携带者被「地面化」——地面系招式不再被其飞行属性免疫
 *         （黑色铁球 {@code 0.5|GROUND}）</li>
 *     <li>{@link #MOVE_LAST}：同先制度时必定后出手（后攻之尾）</li>
 *     <li>{@link #FIRST_STRIKE}：概率无视速度先手（param 为概率百分比）</li>
 * </ul>
 *
 * <p><b>伤害判定修正类</b></p>
 * <ul>
 *     <li>{@link #IGNORE_IMMUNITY}：携带者失去属性免疫，使原本 0 倍（无效）的招式变为 1 倍（标靶）</li>
 *     <li>{@link #GROUND_IMMUNE}：免疫地面系招式，被地面系招式命中后消耗（气球）</li>
 * </ul>
 *
 * <p><b>回合末效果类</b></p>
 * <ul>
 *     <li>{@link #END_TURN_HEAL}：回合末按最大 HP 比例回血（param 为比例）</li>
 *     <li>{@link #POISON_HEAL}：毒属性回合末回血、非毒属性回合末扣血（param 为 回血比例|扣血比例，黑色污泥）</li>
 *     <li>{@link #END_TURN_STATUS}：回合末陷入指定主要异常（param 为状态英文名，火焰宝珠 BURN / 剧毒宝珠 BADLY_POISON）</li>
 *     <li>{@link #LIFE_STEAL}：攻击造成伤害按比例吸血（param 为比例）</li>
 * </ul>
 *
 * <p><b>天气类</b></p>
 * <ul>
 *     <li>{@link #WEATHER_DURATION}：携带者开启对应天气时延长持续回合（param 为 天气英文名|回合数，
 *         如 {@code SUNNY|8}；炽热/潮湿/沙沙/冰冷岩石）</li>
 * </ul>
 *
 * <p><b>保命类</b>（仅在「被招式命中且会倒下」时判定）</p>
 * <ul>
 *     <li>{@link #FOCUS_SASH}：满 HP 时受致死伤害保留 1 HP，触发后消耗（气势披带）</li>
 *     <li>{@link #FOCUS_BAND}：按概率保留 1 HP，不消耗（param 为概率百分比，气势头带）</li>
 * </ul>
 *
 * <p><b>讲究系</b></p>
 * <ul>
 *     <li>{@link #CHOICE}：param 为 修正项|倍率。{@code SPECIAL|1.5} 时特殊招式伤害提升 50%
 *         （讲究眼镜），{@code SPEED|1.5} 时实际速度提升 50%（讲究围巾）。两者都会把携带者
 *         本场战斗锁死在第一个使用过的招式上。</li>
 * </ul>
 *
 * <p><b>防御类</b></p>
 * <ul>
 *     <li>{@link #EVOLITE}：未最终进化时防御/特防提升（param 为倍率）</li>
 * </ul>
 */
public enum HeldItemEffect {
    DAMAGE_TYPE,
    SUPER_EFFECTIVE,
    END_TURN_HEAL,
    LIFE_STEAL,
    FIRST_STRIKE,
    EVOLITE,

    PHYSICAL_DAMAGE,
    SPECIAL_DAMAGE,
    LIFE_ORB,
    SPEED_MULTIPLIER,
    MOVE_LAST,
    IGNORE_IMMUNITY,
    GROUND_IMMUNE,
    POISON_HEAL,
    END_TURN_STATUS,
    WEATHER_DURATION,
    FOCUS_SASH,
    FOCUS_BAND,
    CHOICE
}
