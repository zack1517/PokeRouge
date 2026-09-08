package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 精灵种族（统一格式的精灵定义）。
 * <p>由数据文件/内建注册表提供，描述一种精灵：双属性、六项种族值、捕获率、默认技能集、
 * 进化目标（可能没有）与按等级习得技能表。</p>
 */
public class Species {

    private final String id;
    private final String name;
    private final ElementType type1;
    private final ElementType type2;
    private final Stats baseStats;
    /** 捕获率 0~255，越高越容易抓。 */
    private final int catchRate;
    /** 默认技能 id 列表（出生即会的技能，≤4）。 */
    private final List<String> moveIds;
    /** 进化目标种族 id（无则为 null）。 */
    private final String evolvesToId;
    /** 进化所需等级（0 = 无进化）。 */
    private final int evolveLevel;
    /** 等级 → 该等级新习得的技能 id。 */
    private final Map<Integer, String> learnAt;
    /** 契约补充：可学习技能表（§2.9）。{@link #learnAt} 之外额外追加的条目。 */
    private final List<LearnableMove> extraLearnable = new ArrayList<>();

    public Species(String id, String name, ElementType type1, ElementType type2,
                   Stats baseStats, int catchRate, List<String> moveIds,
                   String evolvesToId, int evolveLevel, Map<Integer, String> learnAt) {
        this.id = id;
        this.name = name;
        this.type1 = Objects.requireNonNull(type1, "type1");
        this.type2 = type2;
        this.baseStats = Objects.requireNonNull(baseStats);
        this.catchRate = catchRate;
        this.moveIds = Collections.unmodifiableList(new ArrayList<>(moveIds));
        this.evolvesToId = evolvesToId;
        this.evolveLevel = evolveLevel;
        this.learnAt = learnAt == null || learnAt.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(learnAt));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ElementType getType1() {
        return type1;
    }

    public ElementType getType2() {
        return type2;
    }

    /** 双属性列表（1 或 2 个）。 */
    public List<ElementType> getTypes() {
        return type2 == null ? List.of(type1) : List.of(type1, type2);
    }

    public Stats getBaseStats() {
        return baseStats;
    }

    public int getCatchRate() {
        return catchRate;
    }

    public List<String> getMoveIds() {
        return moveIds;
    }

    public String getEvolvesToId() {
        return evolvesToId;
    }

    public int getEvolveLevel() {
        return evolveLevel;
    }

    /** 是否可以在指定等级进化（需定义了进化目标）。 */
    public boolean canEvolveAt(int level) {
        return evolvesToId != null && evolveLevel > 0 && level >= evolveLevel;
    }

    /** 指定等级是否会新习得技能（返回技能 id，无则为 null）。 */
    public String moveLearnedAt(int level) {
        return learnAt.get(level);
    }

    /**
     * 按等级升序的习得技能表（用于批量按当前等级解锁技能）。
     *
     * @return 不可变的 “等级 → 技能 id” 升序快照
     */
    public List<Map.Entry<Integer, String>> getLearnSchedule() {
        if (learnAt.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<Integer, String>> sorted = new ArrayList<>(learnAt.entrySet());
        sorted.sort(Comparator.comparingInt(Map.Entry::getKey));
        return Collections.unmodifiableList(sorted);
    }

    /**
     * 契约补充：可学习技能表（§2.9）。
     * <p>由出生技能（1 级习得）与「等级 → 技能」习得表合并而来，另含 {@link #addLearnableMove}
     * 追加的条目；用于外部按等级查询。</p>
     */
    public List<LearnableMove> getLearnableMoves() {
        List<LearnableMove> all = new ArrayList<>();
        for (String moveId : moveIds) {
            all.add(new LearnableMove(moveId, 1));
        }
        for (Map.Entry<Integer, String> e : getLearnSchedule()) {
            all.add(new LearnableMove(e.getValue(), e.getKey()));
        }
        all.addAll(extraLearnable);
        return Collections.unmodifiableList(all);
    }

    /** 契约补充：追加一个可学习技能条目（§2.9）。 */
    public void addLearnableMove(LearnableMove move) {
        extraLearnable.add(move);
    }

    @Override
    public String toString() {
        return name + "(" + id + ")";
    }
}
