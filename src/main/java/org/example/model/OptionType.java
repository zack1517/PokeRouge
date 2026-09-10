package org.example.model;

/**
 * 路线节点类型（对应《需求文档》§4.2「节点类型总表」）。
 *
 * <p>每类节点携带三项静态属性：</p>
 * <ul>
 *   <li>{@link #isResident()} 常驻性 —— 常驻节点（路人 / 野外精灵 / 医院）在每段路线中
 *       固定出现，非常驻节点（商店 / 特殊事件）按概率出现；</li>
 *   <li>{@link #isMandatory()} 必然性 —— 必然节点（道馆战 / 四天王连打 / 冠军战）
 *       由推进规则触发而非随机生成，且不占用行动点；</li>
 *   <li>{@link #getApCost()} 默认行动点消耗（§4.1「行动点消耗表」）。</li>
 * </ul>
 *
 * <p>实际消耗记录在 {@link Option#getCost()} 上，个别节点会偏离默认值：野外精灵有概率
 * 免费（0 点），特殊事件按具体事件取 0/1/2 点（神兽偶遇 1 点、火箭队线必然神兽 0 点、
 * 火箭队节点 2 点，见 {@link RouteConfig}）。</p>
 */
public enum OptionType {

    /** 路人：常驻，行动点 2；胜利获得金币，失败仅扣金币。 */
    TRAINER("路人", true, false, 2),
    /** 野外精灵：常驻，行动点 1（有概率消耗为 0 点）；可捕获，失败仅扣金币。 */
    WILD("野外精灵", true, false, 1),
    /** 医院：常驻，行动点 1；治疗濒死 / 受伤的宝可梦。 */
    HOSPITAL("医院", true, false, 1),
    /** 商店：随机出现，行动点 1；金币购买道具，商品随进展增多。 */
    SHOP("商店", false, false, 1),
    /** 特殊事件：低概率出现，消耗随事件而定（默认 0 点，由生成方写入实际消耗）。 */
    SPECIAL("特殊事件", false, false, 0),
    /** 装备补给：随机获得一件可携带装备（由控制器结算）；低概率出现，行动点 1。 */
    REWARD("装备补给", false, false, 1),
    /** 火箭队队员：所有时期均可能出现的随机节点，行动点 2；高收益高风险，战败本轮直接结束。 */
    ROCKET("火箭队", false, false, 2),
    /** 火箭队抓捕神兽：后期开启剧情线后的可自主选择事件，行动点 2；与首领交手，战败本轮直接结束。 */
    ROCKET_CAPTURE("火箭队抓捕神兽", false, false, 2),
    /** 神兽偶遇：仅游戏后期出现且每局至多一次，行动点 1（火箭队线必然触发的那次为 0 点）；可捕获。 */
    LEGENDARY("神兽偶遇", false, false, 1),
    /** 道馆战：每段路线行动点耗尽时必然触发，不占用行动点。 */
    GYM("道馆战", false, true, 0),
    /** 四天王连打：冠军战前必然触发，不占用行动点，可失败一次。 */
    ELITE_FOUR("四天王连打", false, true, 0),
    /** 冠军战：路线终点必然触发，不占用行动点，不可失败。 */
    CHAMPION("冠军战", false, true, 0),
    /** 首领侵略战：开启火箭队剧情线却未提前击败首领时，击败冠军后必然触发；不可失败。 */
    ROCKET_INVASION("首领侵略战", false, true, 0);

    private final String displayName;
    private final boolean resident;
    private final boolean mandatory;
    private final int apCost;

    OptionType(String displayName, boolean resident, boolean mandatory, int apCost) {
        this.displayName = displayName;
        this.resident = resident;
        this.mandatory = mandatory;
        this.apCost = apCost;
    }

    /** 节点中文名（用于界面与节点标题）。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 是否常驻节点：每段路线固定出现。 */
    public boolean isResident() {
        return resident;
    }

    /** 是否必然节点：由推进规则强制触发，不参与随机生成、不占用行动点。 */
    public boolean isMandatory() {
        return mandatory;
    }

    /** 该类型节点的默认行动点消耗（实际消耗见 {@link Option#getCost()}）。 */
    public int getApCost() {
        return apCost;
    }

    /** 是否是「进入战斗」的节点（其余节点为当场结算的直接效果）。 */
    public boolean isBattle() {
        return this == TRAINER || this == WILD || this == ROCKET || this == ROCKET_CAPTURE
                || this == LEGENDARY || this == GYM || this == ELITE_FOUR
                || this == CHAMPION || this == ROCKET_INVASION;
    }

    /**
     * 战败后是否不中断本轮：{@code true} 表示仅扣金币继续（路人 / 野外精灵 / 神兽偶遇，§4.3）；
     * {@code false} 表示按重试规则处理（道馆 / 四天王可失败一次，冠军与火箭队不可失败）。
     */
    public boolean defeatOnlyCostsGold() {
        return this == TRAINER || this == WILD || this == LEGENDARY;
    }

    /**
     * 战败是否直接结束本轮远征（火箭队线的高风险节点：§5.2 火箭队节点不可失败，
     * 抓捕神兽与首领侵略战同样不可失败）。必然节点的「可失败一次」由
     * {@link RoutePhase#allowsOneRetry()} 单独判定。
     */
    public boolean defeatEndsRun() {
        return this == ROCKET || this == ROCKET_CAPTURE || this == ROCKET_INVASION;
    }
}
