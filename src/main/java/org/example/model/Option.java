package org.example.model;

/**
 * 路线节点（《需求文档》§4.2）。
 *
 * <p>一个 {@code Option} 描述玩家在路线图上看到的一个可选节点：名称、类型、行动点消耗
 * （字段名沿用 {@code cost}）、描述文本，以及是否已被走过。</p>
 *
 * <p>{@link #isConsumed()} 取代了旧版用「名字等于『隐藏事件』」做状态标记的写法：走完的
 * 节点保持类型与名称不变，只打上已消耗标记，界面据此渲染成灰色不可点。</p>
 */
public class Option {
    private String name;
    private OptionType type;
    private int cost;
    private String description;
    private boolean consumed;

    public Option() {
    }

    public Option(String name, OptionType type, int cost, String description) {
        this.name = name;
        this.type = type;
        this.cost = cost;
        this.description = description;
    }

    /** 创建消耗等于该节点类型默认行动点消耗的节点。 */
    public static Option of(OptionType type, String name, String description) {
        return new Option(name, type, type == null ? 0 : type.getApCost(), description);
    }

    /** 创建消耗为 0 点的节点（如已走完的节点、必然节点）。 */
    public static Option free(OptionType type, String name, String description) {
        return new Option(name, type, 0, description);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OptionType getType() {
        return type;
    }

    public void setType(OptionType type) {
        this.type = type;
    }

    /** 进入该节点需要消耗的行动点；必然节点与已走完的节点为 0。 */
    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** 该节点是否已经走过；走完的节点不再可进入，但保留在原位供玩家查看。 */
    public boolean isConsumed() {
        return consumed;
    }

    public void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }

    /** 标记为已走过。 */
    public void markConsumed() {
        this.consumed = true;
    }

    /** 节点类型中文名；类型为空时退化为节点名称。 */
    public String getTypeDisplayName() {
        return type == null ? name : type.getDisplayName();
    }

    /** 该类型节点进入战斗时是否可捕获野生宝可梦（野外精灵与神兽偶遇均可捕获）。 */
    public boolean allowsCapture() {
        return type == OptionType.WILD || type == OptionType.LEGENDARY;
    }

    @Override
    public String toString() {
        return "Option{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", cost=" + cost +
                ", consumed=" + consumed +
                ", description='" + description + '\'' +
                '}';
    }
}
