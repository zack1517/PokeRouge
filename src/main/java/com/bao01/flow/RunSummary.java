package com.bao01.flow;

import java.util.List;

/**
 * 对外只读快照（供 UI / 存档 / 养成消费，对应《游戏流程接口设计》2.6）。
 */
public record RunSummary(
        int segmentNo,
        int apLeft,
        int apCap,
        int gold,
        StoryFlags story,
        CaptureLedger ledger,
        List<RouteOption> options,
        NodeType nextMilestone) {

    public RunSummary {
        story = story == null ? StoryFlags.initial() : story;
        ledger = ledger == null ? CaptureLedger.empty() : ledger;
        options = options == null ? List.of() : List.copyOf(options);
    }
}
