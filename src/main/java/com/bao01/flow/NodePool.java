package com.bao01.flow;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点生成器：为一次「节点选择」产出一组 {@link RouteOption}。
 *
 * <p>对应《README》节点类型与《游戏流程接口设计》§5 的 generateOptions。
 * 结构：
 * <ul>
 *   <li><b>常驻节点</b>（每次刷新必有）：野外偶遇、路人、医院；</li>
 *   <li><b>随机事件</b>（按概率 / 段号 / 剧情线状态追加）：商店、火箭队、
 *       神兽偶遇（仅后期、每 Run 一次）、火箭队抓捕神兽剧情事件（自选进入）。</li>
 * </ul>
 * 所有随机决策经 {@link RandomSource}，便于测试固定序列复现。
 * 本类不处理必然节点（GYM / 四天王 / 冠军 / 侵略），它们由控制器在段末触发。
 */
public final class NodePool {

    private NodePool() {
    }

    /**
     * 刷新一组可选节点。
     *
     * @param segmentNo 当前段号（1 起，决定商店概率与「后期」判定）
     * @param story     剧情线状态（决定神兽 / 抓捕事件的可见性）
     * @param rnd       随机源
     * @return 常驻 + 命中的随机事件节点（已洗牌，不重复同类型节点）
     */
    public static List<RouteOption> roll(int segmentNo, StoryFlags story, RandomSource rnd) {
        if (story == null || rnd == null) {
            throw new IllegalArgumentException("story / rnd 不能为 null");
        }
        List<RouteOption> options = new ArrayList<>();

        // ---------- 常驻节点 ----------
        options.add(RouteOption.of(NodeType.WILD, NodeType.WILD.label(), wildApCost(rnd)));
        options.add(RouteOption.of(NodeType.TRAINER, NodeType.TRAINER.label(), FlowConfig.apCost(NodeType.TRAINER)));
        options.add(RouteOption.of(NodeType.HOSPITAL, NodeType.HOSPITAL.label(), FlowConfig.apCost(NodeType.HOSPITAL)));

        // ---------- 随机事件：商店 ----------
        if (rnd.chance(FlowConfig.shopSpawnChance(segmentNo))) {
            options.add(RouteOption.of(NodeType.SHOP, NodeType.SHOP.label(), FlowConfig.apCost(NodeType.SHOP)));
        }

        // ---------- 随机事件：火箭队 ----------
        if (rnd.chance(FlowConfig.rocketSpawnChance())) {
            options.add(RouteOption.of(NodeType.ROCKET, NodeType.ROCKET.label(), FlowConfig.apCost(NodeType.ROCKET)));
        }

        // ---------- 随机事件：神兽偶遇（仅后期） ----------
        if (FlowConfig.isLateSegment(segmentNo) && !story.legendaryEncountered()) {
            if (story.masterBallHeld()) {
                // 大师球获得后必然触发一次，AP=0、forced=true
                options.add(RouteOption.forced(NodeType.LEGEND, "神兽（大师球必然触发）"));
            } else if (rnd.chance(FlowConfig.legendSpawnChance())) {
                options.add(RouteOption.of(NodeType.LEGEND, NodeType.LEGEND.label(), FlowConfig.apCost(NodeType.LEGEND)));
            }
        }

        // ---------- 随机事件：火箭队抓捕神兽（剧情可选项） ----------
        if (story.rocketEntered() && !story.rocketBossDefeated()
                && FlowConfig.isLateSegment(segmentNo)
                && rnd.chance(FlowConfig.rocketBossEventChance())) {
            options.add(RouteOption.of(NodeType.ROCKET_BOSS, NodeType.ROCKET_BOSS.label(), 0));
        }

        // ---------- 洗牌，避免常驻永远排在前面 ----------
        shuffle(options, rnd);
        return List.copyOf(options);
    }

    /** 野外偶遇 AP：常规 1，命中概率时本次为 0。 */
    private static int wildApCost(RandomSource rnd) {
        return rnd.chance(FlowConfig.wildZeroApChance()) ? 0 : FlowConfig.apCost(NodeType.WILD);
    }

    /** Fisher-Yates 洗牌（不额外引入 java.util.Random）。 */
    private static void shuffle(List<RouteOption> options, RandomSource rnd) {
        for (int i = options.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            RouteOption tmp = options.get(i);
            options.set(i, options.get(j));
            options.set(j, tmp);
        }
    }
}
