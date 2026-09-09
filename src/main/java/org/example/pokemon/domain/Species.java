package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 宝可梦种族，描述一种宝可梦的静态基础数据。
 */
public class Species implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final List<ElementType> types;
    private final BaseStats baseStats;
    private final int evolutionLevel;
    private final String evolutionTarget;
    private final int baseExpYield;
    private final double captureRate;
    private final String category;
    private final String description;
    private final List<LearnableMove> learnableMoves;

    public Species(String id, String name, List<ElementType> types, BaseStats baseStats,
                   int evolutionLevel, String evolutionTarget, int baseExpYield,
                   double captureRate, String category, String description) {
        this.id = id;
        this.name = name;
        // 防御性拷贝，避免外部修改传入的列表
        this.types = new ArrayList<>(types);
        this.baseStats = baseStats;
        this.evolutionLevel = evolutionLevel;
        this.evolutionTarget = evolutionTarget;
        this.baseExpYield = baseExpYield;
        this.captureRate = captureRate;
        this.category = category;
        this.description = description;
        this.learnableMoves = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * 返回该种族的属性列表（不可变视图）。
     *
     * @return 属性列表
     */
    public List<ElementType> getTypes() {
        return Collections.unmodifiableList(types);
    }

    public BaseStats getBaseStats() {
        return baseStats;
    }

    public int getEvolutionLevel() {
        return evolutionLevel;
    }

    public String getEvolutionTarget() {
        return evolutionTarget;
    }

    /** 兼容旧版命名：进化目标 id。 */
    public String getEvolvesToId() {
        return evolutionTarget;
    }

    /** 兼容旧版命名：向外部调用方返回进化等级。 */
    public int getEvolveLevel() {
        return evolutionLevel;
    }

    public int getBaseExpYield() {
        return baseExpYield;
    }

    public double getCaptureRate() {
        return captureRate;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 返回该种族的升级学招表（不可变视图）。
     *
     * @return 学招列表
     */
    public List<LearnableMove> getLearnableMoves() {
        return Collections.unmodifiableList(learnableMoves);
    }

    /**
     * 兼容旧版 API：返回该种族的“出生技能/默认技能”列表。
     */
    public List<String> getMoveIds() {
        return learnableMoves.stream().map(LearnableMove::getMoveId).distinct().toList();
    }

    /**
     * 兼容旧版 API：返回按等级升序排序的习得技能列表。
     */
    public List<java.util.Map.Entry<Integer, String>> getLearnSchedule() {
        return learnableMoves.stream()
                .sorted(java.util.Comparator.comparingInt(LearnableMove::getLevel))
                .map(lm -> java.util.Map.entry(lm.getLevel(), lm.getMoveId()))
                .toList();
    }

    /**
     * 兼容旧版 API：在指定等级是否会学到新技能。
     */
    public String moveLearnedAt(int level) {
        return learnableMoves.stream()
                .filter(lm -> lm.getLevel() == level)
                .map(LearnableMove::getMoveId)
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断该种族在指定等级时是否可以进化。
     *
     * @param level 当前等级
     * @return 可进化返回 true，否则返回 false
     */
    public boolean canEvolveAt(int level) {
        return level >= evolutionLevel && evolutionTarget != null;
    }

    /**
     * 向升级学招表添加一条可学习技能记录。
     *
     * @param move 可学习技能
     */
    public void addLearnableMove(LearnableMove move) {
        learnableMoves.add(move);
    }
}
