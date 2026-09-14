package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 技能，包含威力、命中率、PP 等基础数据，以及命中后可能施加的异常状态、附带的专属效果
 * （天气/场地/守住/寄生种子/睡觉）与能力等级变化。
 */
public class Move implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final ElementType type;
    private final MoveCategory category;
    private final int power;
    private final int accuracy;
    private final int maxPp;
    private final int priority;
    /** 命中后可能施加的异常状态名（无则为空串），如 POISON/PARALYSIS。 */
    private final String inflicts;
    /** 异常状态触发概率百分比（0~100）。 */
    private final int inflictionChance;
    /** 招式附带效果（天气/场地/守住等）；无特殊效果为 {@link MoveEffect#NONE}。 */
    private final MoveEffect effect;
    /** 招式附带的能力等级变化（自身/对方、多项）；无则为空列表。 */
    private final List<StatChange> statChanges;

    public Move(String id, String name, ElementType type, MoveCategory category,
                int power, int accuracy, int maxPp, int priority) {
        this(id, name, type, category, power, accuracy, maxPp, priority, "", 0);
    }

    /**
     * 完整构造（含异常状态）。
     *
     * @param inflicts        命中后可能施加的异常状态名（如 POISON；无则为空串或 null）
     * @param inflictionChance 触发概率百分比 0~100
     */
    public Move(String id, String name, ElementType type, MoveCategory category,
                int power, int accuracy, int maxPp, int priority,
                String inflicts, int inflictionChance) {
        this(id, name, type, category, power, accuracy, maxPp, priority,
                inflicts, inflictionChance, MoveEffect.NONE, List.of());
    }

    /**
     * 最完整构造（含效果与能力等级变化）。
     *
     * @param effect      招式附带效果（无则传 {@code null} 或 {@link MoveEffect#NONE}）
     * @param statChanges 招式附带的能力等级变化（无则传 {@code null} 或空列表）
     */
    public Move(String id, String name, ElementType type, MoveCategory category,
                int power, int accuracy, int maxPp, int priority,
                String inflicts, int inflictionChance,
                MoveEffect effect, List<StatChange> statChanges) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.category = category;
        this.power = power;
        this.accuracy = accuracy;
        this.maxPp = maxPp;
        this.priority = priority;
        this.inflicts = inflicts == null ? "" : inflicts.trim();
        this.inflictionChance = Math.max(0, Math.min(100, inflictionChance));
        this.effect = effect == null ? MoveEffect.NONE : effect;
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

    public int getPriority() {
        return priority;
    }

    /** 命中后可能施加的异常状态名（无则为空串）。 */
    public String getInflicts() {
        return inflicts;
    }

    /** 是否附带异常状态。 */
    public boolean hasInfliction() {
        return inflicts != null && !inflicts.isEmpty();
    }

    /** 异常状态触发概率百分比（0~100）。 */
    public int getInflictionChance() {
        return inflictionChance;
    }

    /** 招式附带效果（无则为 {@link MoveEffect#NONE}）。 */
    public MoveEffect getEffect() {
        return effect;
    }

    /** 招式附带的能力等级变化（多项，顺序即施加顺序）；无则为空列表。 */
    public List<StatChange> getStatChanges() {
        return statChanges;
    }

    /** 是否附带能力等级变化。 */
    public boolean hasStatChanges() {
        return !statChanges.isEmpty();
    }

    /**
     * 判断该技能是否为物理技能。
     *
     * @return 物理技能返回 true，否则返回 false
     */
    public boolean isPhysical() {
        return category == MoveCategory.PHYSICAL;
    }

    /**
     * 判断该技能是否为特殊技能。
     *
     * @return 特殊技能返回 true，否则返回 false
     */
    public boolean isSpecial() {
        return category == MoveCategory.SPECIAL;
    }

    /**
     * 判断该技能是否为变化技能。
     *
     * @return 变化技能返回 true，否则返回 false
     */
    public boolean isStatus() {
        return category == MoveCategory.STATUS;
    }
}
