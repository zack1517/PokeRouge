package org.example.model;

/**
 * 路线节点（《需求文档》§4.2）。
 *
 * <p>一个 {@code Option} 描述玩家在路线图上看到的一个可选节点：名称、类型、行动点消耗
 * （字段名沿用 {@code cost}）、描述文本，以及是否已被走过。</p>
 *
 * <p>{@link #isConsumed()} 取代了旧版用「名字等于『隐藏事件』」做状态标记的写法：走完的
 * 节点保持类型与名称不变，只打上已走过标记；一次性节点据此渲染成灰色不可点。</p>
 *
 * <p>常驻节点（{@link OptionType#isResident()}：路人 / 野外精灵 / 医院）是例外：它们走完后
 * <b>可以重复进入</b>（{@link #isRepeatable()}），行动点是唯一限制。为此这里还记录
 * {@link #getVisitCount() 曾走过的次数}，并区分「首次进入」与「再次进入」的行动点消耗
 * （见 {@link #apCostForNextEntry()}，免单优惠只对首次进入生效）。</p>
 */
public class Option {
    private String name;
    private OptionType type;
    private int cost;
    private String description;
    /** 已走过次数；大于 0 即视为 {@link #isConsumed()}。 */
    private int visits;

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

    /** 创建消耗为 0 点的节点（免单的野外精灵 / 大师球气息引来的神兽 / 必然节点）。 */
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

    /** 该节点首次进入需要消耗的行动点；必然节点为 0。 */
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

    /** 该节点是否已经走过；一次性节点走完后不可再进入，常驻节点仍可重复进入。 */
    public boolean isConsumed() {
        return visits > 0;
    }

    public void setConsumed(boolean consumed) {
        this.visits = consumed ? Math.max(1, visits) : 0;
    }

    /** 记录一次进入。 */
    public void markConsumed() {
        this.visits++;
    }

    /** 该节点被走过的次数（常驻节点可大于 1；一次性节点至多 1）。 */
    public int getVisitCount() {
        return visits;
    }

    /** 该节点是否属于可重复进入的常驻节点（路人 / 野外精灵 / 医院）。 */
    public boolean isRepeatable() {
        return type != null && type.isResident();
    }

    /**
     * 下一次进入该节点实际扣除的行动点。
     *
     * <p>常驻节点首次进入照 {@link #getCost()} 结算（可能有「免单」优惠），再次进入时按
     * 类型默认消耗计——否则耗点为 0 的常驻节点会被无限白刷。一次性节点始终返回
     * {@link #getCost()}。</p>
     */
    public int apCostForNextEntry() {
        if (visits > 0 && isRepeatable() && type != null) {
            return type.getApCost();
        }
        return cost;
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
                ", consumed=" + isConsumed() +
                ", visits=" + visits +
                ", description='" + description + '\'' +
                '}';
    }
}
