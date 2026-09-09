package com.bao01.flow;

import java.util.ArrayList;
import java.util.List;

/**
 * 行动点（AP）记账：一次 Run 从第 1 段开始，逐段推进。
 *
 * <p>规则（《README》行动点机制）：
 * <ul>
 *   <li>每段开始时 AP 重置为该段上限（{@code FlowConfig#segmentApCap}）；</li>
 *   <li>进入节点前必须先 {@link #canAfford(RouteOption)}；AP 不足 / 已耗尽则拒绝并返回提示，不扣 AP；</li>
 *   <li>AP 耗尽（{@link #exhausted()}）后不再允许进入任何可选节点，由外层
 *       {@code FlowController} 强制触发本段必然节点（道馆战）；</li>
 *   <li>必然节点不消耗 AP，通过后 {@link #advanceSegment()} 进入下一段并重置 AP。</li>
 * </ul>
 */
public final class ActionPoints {

    /** 起始段号（从 1 计）。 */
    public static final int FIRST_SEGMENT = 1;

    private int segmentNo;
    private int apCap;
    private int apLeft;

    private ActionPoints(int segmentNo) {
        resetForSegment(segmentNo);
    }

    /** 开局：携带初始队伍进入第 1 段，AP 重置为第 1 段上限。 */
    public static ActionPoints opening() {
        return new ActionPoints(FIRST_SEGMENT);
    }

    /** 段号（从 1 计）。 */
    public int segmentNo() {
        return segmentNo;
    }

    /** 当前段 AP 上限。 */
    public int apCap() {
        return apCap;
    }

    /** 当前剩余 AP。 */
    public int apLeft() {
        return apLeft;
    }

    /** AP 是否已耗尽（剩余 0）。耗尽后不可再进入可选节点。 */
    public boolean exhausted() {
        return apLeft <= FlowConfig.AP_FLOOR;
    }

    /** 本段是否还能进入某节点：未耗尽且能负担其 AP 成本。 */
    public boolean canAfford(RouteOption option) {
        return option != null && !exhausted() && option.apCost() <= apLeft;
    }

    /**
     * 进入某可选节点并扣减 AP；AP 不足或已耗尽时拒绝（不扣 AP），返回提示日志。
     */
    public List<String> enter(RouteOption option) {
        List<String> log = new ArrayList<>();
        if (option == null) {
            log.add("（无效节点：不能进入空选项）");
            return log;
        }
        if (!canAfford(option)) {
            log.add("行动点不足：" + option.display() + " 需要 " + option.apCost()
                    + " AP，当前剩余 " + apLeft + " AP，无法进入。");
            return log;
        }
        apLeft -= option.apCost();
        log.add("进入「" + option.display() + "」，消耗 " + option.apCost() + " AP，剩余 "
                + apLeft + "/" + apCap + " AP。");
        if (exhausted()) {
            log.add("行动点已耗尽，本段将强制进入必然节点（道馆战）。");
        }
        return log;
    }

    /**
     * 重置到下一段：段号 +1，AP 重置为该段上限。
     */
    public List<String> advanceSegment() {
        return resetForSegment(segmentNo + 1);
    }

    /** 重置到指定段：AP 重置为该段上限。 */
    public List<String> resetForSegment(int newSegmentNo) {
        this.segmentNo = Math.max(FIRST_SEGMENT, newSegmentNo);
        this.apCap = FlowConfig.segmentApCap(this.segmentNo);
        this.apLeft = this.apCap;
        List<String> log = new ArrayList<>();
        log.add("进入第 " + this.segmentNo + " 段，AP 重置为上限 " + this.apCap + "。");
        return log;
    }

    @Override
    public String toString() {
        return "第 " + segmentNo + " 段  AP " + apLeft + "/" + apCap;
    }
}
