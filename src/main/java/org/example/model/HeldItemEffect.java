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
 *
 * <p><b>树果类</b>（均为一次性消耗品，触发即从携带栏移除）</p>
 * <ul>
 *     <li>{@link #CURE_STATUS}：携带者陷入参数所列异常时立即治愈。param 为
 *         {@code 状态英文名[|状态英文名…]}，填 {@code ALL} 表示全部异常（含混乱）——
 *         樱子果 {@code PARALYSIS}、桃桃果 {@code POISON|BADLY_POISON}、莓莓果 {@code BURN}、
 *         零余果 {@code SLEEP}、利木果 {@code FREEZE}、柿仔果 {@code CONFUSION}、木子果 {@code ALL}</li>
 *     <li>{@link #HEAL_HP}：HP 不高于「阈值比例」时回复。param 为 {@code 阈值比例|回复量}，
 *         回复量小于 1 视为最大 HP 比例、不小于 1 视为固定点数——橙橙果 {@code 0.5|10}、
 *         文柚果 {@code 0.5|0.25}、勿花果等 {@code 0.25|0.125}</li>
 *     <li>{@link #HEAL_PP}：携带者招式 PP 归零时回复该招式 PP（param 为回复点数，苹野果 {@code 10}）</li>
 *     <li>{@link #RESIST_TYPE}：受到参数所指属性且「效果拔群」的招式时伤害乘倍率
 *         （param 为 {@code 属性|倍率[|ALWAYS]}，属性减伤树果统一 {@code 0.5}；
 *         第 3 段填 {@code ALWAYS} 时不看克制关系，用于一般属性的灯浆果）</li>
 * </ul>
 *
 * <p><b>招式标记与判定类</b>（依赖 {@link MoveFlag} 招式标记、会心与畏缩判定）</p>
 * <ul>
 *     <li>{@link #PUNCH_BOOST}：携带者使用拳类招式（{@link MoveFlag#PUNCH}）时威力提升，
 *         且该招式不再视为接触（param 为倍率，拳击手套 {@code 1.1}）</li>
 *     <li>{@link #CONTACT_PUNISH}：携带者被接触类招式（{@link MoveFlag#CONTACT}）命中后，
 *         攻击方按最大 HP 比例反伤（param 为比例，凸凸头盔 {@code 0.1667}）</li>
 *     <li>{@link #POWDER_IMMUNE}：携带者免疫粉末类招式（{@link MoveFlag#POWDER}）
 *         与沙暴/冰雹的回合末伤害（防尘护目镜）</li>
 *     <li>{@link #CRIT_BOOST}：携带者的会心等级提升（param 为提升等级，锐利之爪 {@code 1}）</li>
 *     <li>{@link #FLINCH_CHANCE}：携带者的招式造成伤害后按概率使目标畏缩
 *         （param 为概率百分比，王者之证 {@code 10}）</li>
 *     <li>{@link #CONSECUTIVE_BOOST}：连续使用同一招式时每次提升威力，上限 {@value
 *         org.example.battle.BattleEngine#CONSECUTIVE_MAX_MULTIPLIER} 倍
 *         （param 为每层增幅，节拍器 {@code 0.2}）</li>
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
    CHOICE,

    CURE_STATUS,
    HEAL_HP,
    HEAL_PP,
    RESIST_TYPE,

    PUNCH_BOOST,
    CONTACT_PUNISH,
    POWDER_IMMUNE,
    CRIT_BOOST,
    FLINCH_CHANCE,
    CONSECUTIVE_BOOST
}
