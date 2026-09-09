package com.bao01.flow;

/**
 * 节点种类（只表达「是什么」）。
 *
 * <p>AP 消耗、随机为 0、必然触发等实例级修饰不写死在枚举里，
 * 而由 {@link RouteOption} 在实例上承载（{@code apCost} / {@code forced}），
 * 便于未来新增「免 AP / 强制」等变体而不改本枚举。
 */
public enum NodeType {

    // ---------- 常驻战斗 ----------
    TRAINER("路人", Cat.COMBAT),
    WILD("野外精灵", Cat.COMBAT),

    // ---------- 随机战斗 ----------
    ROCKET("火箭队", Cat.COMBAT),
    LEGEND("神兽偶遇", Cat.COMBAT),

    // ---------- 服务 ----------
    HOSPITAL("医院", Cat.SERVICE),
    SHOP("商店", Cat.SERVICE),

    // ---------- 剧情事件 ----------
    ROCKET_BOSS("火箭队抓捕神兽", Cat.STORY),

    // ---------- 必然里程碑 ----------
    GYM("道馆战", Cat.MILESTONE),
    ELITE_FOUR("四天王连打", Cat.MILESTONE),
    CHAMPION("Boss 冠军战", Cat.MILESTONE),
    INVASION("首领侵略战", Cat.MILESTONE);

    private final String label;
    private final Cat cat;

    NodeType(String label, Cat cat) {
        this.label = label;
        this.cat = cat;
    }

    /** 中文展示名。 */
    public String label() {
        return label;
    }

    /** 所属节点类别。 */
    public Cat cat() {
        return cat;
    }
}
