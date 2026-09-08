package org.example.model;

/**
 * 技能。
 * <p>一个精灵最多携带 4 个技能。技能具有元素属性、类别（物/特）、威力与 PP（使用次数）。</p>
 */
public class Move {

    private final String id;
    private final String name;
    private final ElementType type;
    private final MoveCategory category;
    /** 威力；0 表示变化类技能（当前不做特殊效果，仅保留扩展位）。 */
    private final int power;
    /** 命中率 0~100；预留扩展（当前恒命中）。 */
    private final int accuracy;
    /** 最大 PP（使用次数）。 */
    private final int maxPp;

    public Move(String id, String name, ElementType type, MoveCategory category, int power, int accuracy, int maxPp) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.category = category;
        this.power = power;
        this.accuracy = accuracy;
        this.maxPp = maxPp;
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
}
