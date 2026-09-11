package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一段路线的生成结果：本段行动点上限 + 可自由选择的路线节点 + 本段的必然节点预告。
 *
 * <p>由 {@link NodeGenerator} 产出，由 {@link RogueTurnManager} 在段开始时装载进
 * {@link RunData}。</p>
 */
public class SegmentPlan {

    private final int segment;
    private final int apLimit;
    private final List<Option> routeOptions;
    private final Option mandatoryOption;

    public SegmentPlan(int segment, int apLimit, List<Option> routeOptions, Option mandatoryOption) {
        this.segment = segment;
        this.apLimit = Math.max(1, apLimit);
        this.routeOptions = routeOptions == null ? new ArrayList<>() : new ArrayList<>(routeOptions);
        this.mandatoryOption = mandatoryOption;
    }

    /** 段号（1 起）。 */
    public int getSegment() {
        return segment;
    }

    /** 本段开始时会重置到的行动点上限。 */
    public int getApLimit() {
        return apLimit;
    }

    /** 可以自由选择进入的路线节点（常驻节点必出，商店 / 特殊事件按概率出现）。 */
    public List<Option> getRouteOptions() {
        return Collections.unmodifiableList(routeOptions);
    }

    /** 本段行动点耗尽后必然触发的节点（道馆战）；仅用于界面预告，不参与行动点选择。 */
    public Option getMandatoryOption() {
        return mandatoryOption;
    }
}
