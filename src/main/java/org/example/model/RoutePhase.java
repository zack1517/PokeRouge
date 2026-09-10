package org.example.model;

/**
 * 路线推进阶段：决定「当前还能走哪些节点」以及行动点耗尽后必然触发哪一个节点
 * （《需求文档》§4.2 的必然节点链：道馆战 → 四天王连打 → 冠军战）。
 *
 * <p>一次完整路线的阶段流转：</p>
 * <pre>
 * EXPLORING（自由消耗行动点走常驻 / 随机节点）
 *   └─ 行动点耗尽 → GYM（道馆战）→ 胜利且段号未到 TOTAL_SEGMENTS：回到 EXPLORING（下一段）
 *                                  └─ 胜利且已是最后一段 → ELITE_FOUR
 * ELITE_FOUR（四天王连打，可失败一次）→ 胜利 → CHAMPION
 * CHAMPION（冠军战，不可失败）→ 胜利 → 未开启火箭队剧情线：CLEARED（通关）
 *                                    └─ 已开启剧情线且未提前击败首领：ROCKET_INVASION（首领侵略战）
 * ROCKET_INVASION（首领侵略战，不可失败）→ 胜利 → CLEARED（通关）
 * </pre>
 *
 * <p>战败导致的结束不改变阶段，而是由 {@link RunData#isGameOver()} 单独标记。</p>
 */
public enum RoutePhase {

    /** 段内探索：按行动点自主选择路线节点。 */
    EXPLORING("路线探索"),
    /** 道馆战：本段行动点耗尽后必然触发。 */
    GYM("道馆战"),
    /** 四天王连打：冠军战前的必然节点。 */
    ELITE_FOUR("四天王连打"),
    /** 冠军战：路线终点的必然节点。 */
    CHAMPION("冠军战"),
    /** 首领侵略战：开启火箭队剧情线却未提前击败首领时，击败冠军后必然触发（§5.3）。 */
    ROCKET_INVASION("首领侵略战"),
    /** 已通关：整条路线打完，本轮远征成功结束。 */
    CLEARED("已通关");

    private final String displayName;

    RoutePhase(String displayName) {
        this.displayName = displayName;
    }

    /** 阶段中文名（用于界面展示）。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 该阶段是否处于「必然节点」——此时不再有可自由选择的路线节点。 */
    public boolean isMandatoryBattle() {
        return this == GYM || this == ELITE_FOUR || this == CHAMPION || this == ROCKET_INVASION;
    }

    /** 该阶段的必然节点类型；{@link #EXPLORING} 与 {@link #CLEARED} 返回 {@code null}。 */
    public OptionType toOptionType() {
        return switch (this) {
            case GYM -> OptionType.GYM;
            case ELITE_FOUR -> OptionType.ELITE_FOUR;
            case CHAMPION -> OptionType.CHAMPION;
            case ROCKET_INVASION -> OptionType.ROCKET_INVASION;
            case EXPLORING, CLEARED -> null;
        };
    }

    /**
     * 该阶段是否允许失败一次（§4.3：道馆战 / 四天王可失败一次；
     * 冠军战与首领侵略战不可失败，§5.2 / §5.3）。
     */
    public boolean allowsOneRetry() {
        return this == GYM || this == ELITE_FOUR;
    }
}
