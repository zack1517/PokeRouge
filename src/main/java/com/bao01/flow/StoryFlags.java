package com.bao01.flow;

/**
 * 剧情线状态（每 Run 一份）。
 *
 * <p>对应《游戏流程接口设计》2.3。不可变 record，更新返回副本；
 * 神兽 / 火箭队 / 侵略线的出现条件（见 §6）都以此字段判定。
 */
public record StoryFlags(
        boolean rocketEntered,          // 是否进入过任意一次火箭队节点（开启后期抓捕线）
        boolean legendaryEncountered,   // 神兽偶遇是否已发生（每 Run 限一次）
        boolean masterBallHeld,         // 是否已获得大师球（火箭队首领战后）
        boolean rocketBossDefeated,     // 抓捕事件首领是否已被击败（击败后不再触发侵略战）
        boolean championDefeated,       // 冠军是否已击败（触发侵略战的时点）
        boolean invasionDone) {         // 首领侵略战是否已结束

    /** 初始剧情线状态：全部为否。 */
    public static StoryFlags initial() {
        return new StoryFlags(false, false, false, false, false, false);
    }

    public StoryFlags withRocketEntered() {
        return new StoryFlags(true, legendaryEncountered, masterBallHeld,
                rocketBossDefeated, championDefeated, invasionDone);
    }

    public StoryFlags withLegendaryEncountered() {
        return new StoryFlags(rocketEntered, true, masterBallHeld,
                rocketBossDefeated, championDefeated, invasionDone);
    }

    public StoryFlags withMasterBallHeld() {
        return new StoryFlags(rocketEntered, legendaryEncountered, true,
                rocketBossDefeated, championDefeated, invasionDone);
    }

    public StoryFlags withRocketBossDefeated() {
        return new StoryFlags(rocketEntered, legendaryEncountered, masterBallHeld,
                true, championDefeated, invasionDone);
    }

    public StoryFlags withChampionDefeated() {
        return new StoryFlags(rocketEntered, legendaryEncountered, masterBallHeld,
                rocketBossDefeated, true, invasionDone);
    }

    public StoryFlags withInvasionDone() {
        return new StoryFlags(rocketEntered, legendaryEncountered, masterBallHeld,
                rocketBossDefeated, championDefeated, true);
    }
}
