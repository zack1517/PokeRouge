package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 技能，包含威力、命中率、PP 等基础数据，以及命中后可能施加的异常状态。
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

    /** 异常状态触发概率百分比（0~100）。 */
    public int getInflictionChance() {
        return inflictionChance;
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
