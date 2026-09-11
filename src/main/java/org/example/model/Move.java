package org.example.model;

import java.util.List;

/**
 * 技能。
 * <p>一个精灵最多携带 4 个技能。技能具有元素属性、类别（物/特）、威力、命中率、PP（使用次数）
 * 与先制度；变化类技能可附带天气/场地或专属效果（守住/寄生种子/睡觉，见 {@link MoveEffect}）、
 * 按概率施加异常状态（{@link StatusCondition}，见 {@link #getInflicts()}），或增减双方的能力等级
 * （{@link StatChange}，见 {@link #getStatChanges()}）。</p>
 */
public class Move {

    private final String id;
    private final String name;
    private final ElementType type;
    private final MoveCategory category;
    /** 威力；0 表示变化类技能（具体效果由 {@link #effect} 描述）。 */
    private final int power;
    /** 命中率 0~100；-1 表示必中。判定规则见 <b>BattleEngine</b>：-1 与 100 恒命中且不消耗随机数。 */
    private final int accuracy;
    /** 最大 PP（使用次数）。 */
    private final int maxPp;
    /** 先制度：数值越大越先出手（相同才比较速度）；0 为普通技能。 */
    private final int priority;
    /** 技能附带效果（天气/场地等）；无特殊效果为 {@link MoveEffect#NONE}。 */
    private final MoveEffect effect;
    /** 命中后可能施加的异常状态；无则为 {@link StatusCondition#NONE}。 */
    private final StatusCondition inflicts;
    /** 施加异常状态的触发概率（0~100，百分比）；{@link #inflicts} 为 NONE 时无意义。 */
    private final int inflictionChance;
    /** 招式附带的能力等级变化（自身/对方、多项）；无则为空列表。 */
    private final List<StatChange> statChanges;

    public Move(String id, String name, ElementType type, MoveCategory category, int power, int accuracy, int maxPp) {
        this(id, name, type, category, power, accuracy, maxPp, 0, MoveEffect.NONE);
    }

    public Move(String id, String name, ElementType type, MoveCategory category, int power, int accuracy,
                int maxPp, MoveEffect effect) {
        this(id, name, type, category, power, accuracy, maxPp, 0, effect);
    }

    /** 完整构造：命中率 0~100，-1 表示必中；priority 为先制度（越大越先出手）。 */
    public Move(String id, String name, ElementType type, MoveCategory category, int power, int accuracy,
                int maxPp, int priority, MoveEffect effect) {
        this(id, name, type, category, power, accuracy, maxPp, priority, effect, StatusCondition.NONE, 0);
    }

    /**
     * 完整构造（含异常状态）：命中后按 {@code inflictionChance} 的概率对目标施加 {@code inflicts}。
     *
     * @param inflicts        命中后可能施加的异常状态（无则 {@link StatusCondition#NONE}）
     * @param inflictionChance 触发概率百分比 0~100（100 表示必定触发）
     */
    public Move(String id, String name, ElementType type, MoveCategory category, int power, int accuracy,
                int maxPp, int priority, MoveEffect effect,
                StatusCondition inflicts, int inflictionChance) {
        this(id, name, type, category, power, accuracy, maxPp, priority, effect,
                inflicts, inflictionChance, List.of());
    }

    /**
     * 最完整构造（含能力等级变化）：除异常状态外，可声明对自身/对方的能力等级增减。
     *
     * @param statChanges 招式附带的能力等级变化（无则传 {@code null} 或空列表）
     */
    public Move(String id, String name, ElementType type, MoveCategory category, int power, int accuracy,
                int maxPp, int priority, MoveEffect effect,
                StatusCondition inflicts, int inflictionChance, List<StatChange> statChanges) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.category = category;
        this.power = power;
        this.accuracy = accuracy < 0 ? -1 : Math.min(100, accuracy);
        this.maxPp = maxPp;
        this.priority = priority;
        this.effect = effect == null ? MoveEffect.NONE : effect;
        this.inflicts = inflicts == null ? StatusCondition.NONE : inflicts;
        this.inflictionChance = Math.max(0, Math.min(100, inflictionChance));
        this.statChanges = statChanges == null ? List.of() : List.copyOf(statChanges);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ElementType getType() {
        return type;
    }

    public MoveCategory getCategory() {
        return category;
    }

    public int getPower() {
        return power;
    }

    public int getAccuracy() {
        return accuracy;
    }

    public int getMaxPp() {
        return maxPp;
    }

    /** 先制度：数值越大越先出手（相同才回退速度判定）；0 为普通技能。 */
    public int getPriority() {
        return priority;
    }

    /** 技能附带效果（无则为 {@link MoveEffect#NONE}）。 */
    public MoveEffect getEffect() {
        return effect;
    }

    /** 命中后可能施加的异常状态；无则为 {@link StatusCondition#NONE}。 */
    public StatusCondition getInflicts() {
        return inflicts;
    }

    /** 是否附带异常状态。 */
    public boolean hasInfliction() {
        return inflicts != StatusCondition.NONE;
    }

    /** 异常状态的触发概率百分比（0~100）。 */
    public int getInflictionChance() {
        return inflictionChance;
    }

    /** 招式附带的能力等级变化（多项，顺序即施加顺序）；无则为空列表。 */
    public List<StatChange> getStatChanges() {
        return statChanges;
    }

    /** 是否附带能力等级变化。 */
    public boolean hasStatChanges() {
        return !statChanges.isEmpty();
    }

    /** 是否为变化类技能（{@link MoveCategory#STATUS}）。 */
    public boolean isStatus() {
        return category == MoveCategory.STATUS;
    }

    /** 是否为物理技能（{@link MoveCategory#PHYSICAL}）。 */
    public boolean isPhysical() {
        return category == MoveCategory.PHYSICAL;
    }

    /** 是否为特殊技能（{@link MoveCategory#SPECIAL}）。 */
    public boolean isSpecial() {
        return category == MoveCategory.SPECIAL;
    }
}
