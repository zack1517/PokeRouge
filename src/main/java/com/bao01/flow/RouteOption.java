package com.bao01.flow;

/** 当前段的一个可选节点实例。 */
public record RouteOption(NodeType type, String display, int apCost, boolean forced) {

    /**
     * 创建普通可选节点（非强制）。
     *
     * @param type    节点种类
     * @param display 展示文案（如「路人 小刚」「野生的 焰尾狐」）
     * @param apCost  进入需消耗 AP（0 表示该实例免 AP）
     */
    public static RouteOption of(NodeType type, String display, int apCost) {
        return new RouteOption(type, display, apCost, false);
    }

    /**
     * 创建必然触发节点（如 AP 耗尽后的道馆战、四天王、冠军、侵略战）。
     * 必然节点默认不消耗 AP。
     */
    public static RouteOption forced(NodeType type, String display) {
        return new RouteOption(type, display, 0, true);
    }

    public RouteOption {
        if (type == null) {
            throw new IllegalArgumentException("节点种类不能为 null");
        }
        if (display == null || display.isBlank()) {
            throw new IllegalArgumentException("节点展示文案不能为空");
        }
        if (apCost < 0) {
            throw new IllegalArgumentException("AP 成本不能为负：" + apCost);
        }
    }
}
