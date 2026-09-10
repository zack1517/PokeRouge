package com.bao01.flow;

import java.util.List;

/**
 * 对外只读快照（供 UI / 存档 / 养成消费，对应《游戏流程接口设计》2.6）。
 *
 * <p>本快照是「一段 Run 的完整可落盘状态」：{@link FlowController#summary()} 生成，
 * {@link FlowController#restored(org.example.model.Player, RunSummary, BattleAdapter)}
 * 按本快照逐字段还原。除队伍 / 背包（由存档模块单独保存）外，Run 的全部推进状态都在这里，
 * 因此「存档 → 读档 → 继续推进」与原 Run 完全等价。
 *
 * <p>字段分组：
 * <ul>
 *   <li>推进：{@code segmentNo} / {@code apLeft} / {@code apCap} / {@code inRoute} / {@code nextMilestone}；</li>
 *   <li>经济与剧情：{@code gold} / {@code story} / {@code ledger}；</li>
 *   <li>分段内瞬态：{@code options}（本段剩余可选节点）、{@code shopStock}（当前商店货架）；</li>
 *   <li>里程碑记账：{@code gymRetried} / {@code eliteCleared} / {@code eliteRetried} /
 *       {@code championCleared} / {@code invasionCleared}（决定「再败一次即结束 Run」等规则）；</li>
 *   <li>结局：{@code ending}（{@code null} = 进行中）。</li>
 * </ul>
 *
 * <p>不包含「正在等待结算的战斗」（{@link FlowController} 的 {@code pendingFight}）：
 * 对局过程态不落盘，读档后从节点入口重新开始该节点。
 */
public record RunSummary(
        int segmentNo,
        int apLeft,
        int apCap,
        int gold,
        StoryFlags story,
        CaptureLedger ledger,
        List<RouteOption> options,
        NodeType nextMilestone,
        boolean inRoute,
        boolean gymRetried,
        boolean eliteCleared,
        boolean eliteRetried,
        boolean championCleared,
        boolean invasionCleared,
        Ending ending,
        List<ShopOffer> shopStock) {

    public RunSummary {
        story = story == null ? StoryFlags.initial() : story;
        ledger = ledger == null ? CaptureLedger.empty() : ledger;
        options = options == null ? List.of() : List.copyOf(options);
        shopStock = shopStock == null ? List.of() : List.copyOf(shopStock);
    }

    /** 是否已进入终局（false 之外即 四天王 / 冠军 / 侵略战 阶段）。 */
    public boolean inFinalPhase() {
        return !inRoute;
    }

    /** Run 是否已结束。 */
    public boolean isOver() {
        return ending != null;
    }
}
