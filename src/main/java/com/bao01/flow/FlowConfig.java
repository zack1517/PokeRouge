package com.bao01.flow;

/**
 * 流程与经济的全局数值常量（数值平衡统一在此调整）。
 *
 * <p>对应《游戏流程接口设计》3.1：AP 上限 / 消耗、随机事件概率、经济收支、敌人等级曲线
 * 全部集中在 {@code FlowConfig}，便于评审调整与后续配置化。
 */
public final class FlowConfig {

    private FlowConfig() {
    }

    // ---------- 行动点：每段上限（数值 [待配置]） ----------
    /** 第 1 段的 AP 上限。 */
    public static final int SEG_AP_BASE = 6;
    /** 每推进一段，AP 上限的增量。 */
    public static final int SEG_AP_STEP = 1;
    /** AP 上限封顶，避免后期无限膨胀。 */
    public static final int SEG_AP_MAX = 12;

    /** 行动点耗尽后再无可进入的可选节点，强制进入本段必然节点（默认道馆战）。 */
    public static final int AP_FLOOR = 0;

    /** 野外偶遇节点消耗降为 0 的概率。 */
    public static final double WILD_ZERO_AP_CHANCE = 0.2;

    // ---------- 随机事件节点：出现条件数值（[待配置]） ----------
    /** 商店出现概率的起始值（随机节点，随段号提升，见 {@link #shopSpawnChance(int)}）。 */
    public static final double SHOP_SPAWN_BASE = 0.15;
    /** 商店概率每段递增步长。 */
    public static final double SHOP_SPAWN_STEP = 0.03;
    /** 商店概率封顶。 */
    public static final double SHOP_SPAWN_MAX = 0.6;
    /** 火箭队节点概率（各时期均可能）。 */
    public static final double ROCKET_SPAWN_CHANCE = 0.15;
    /** 神兽偶遇（正常触发）概率：仅后期段、每 Run 一次。 */
    public static final double LEGEND_SPAWN_CHANCE = 0.3;
    /** 「火箭队抓捕神兽」剧情事件出现概率：后期段、已进过火箭队节点时。 */
    public static final double ROCKET_BOSS_EVENT_CHANCE = 0.4;
    /** 从第几段起算「后期」（神兽/抓捕剧情线才开始出现）。 */
    public static final int LATE_SEGMENT_FROM = 6;

    // ---------- 行动点：节点默认消耗 ----------
    /** 路人节点消耗。 */
    public static final int AP_TRAINER = 2;
    /** 野外精灵节点消耗。 */
    public static final int AP_WILD = 1;
    /** 医院节点消耗。 */
    public static final int AP_HOSPITAL = 1;
    /** 商店节点消耗。 */
    public static final int AP_SHOP = 1;
    /** 火箭队节点消耗（与路人一致）。 */
    public static final int AP_ROCKET = 2;
    /** 神兽偶遇（正常触发）节点消耗。 */
    public static final int AP_LEGEND = 1;

    /**
     * 每段 AP 上限：第 1 段为 {@link #SEG_AP_BASE}，之后每段递增
     * {@link #SEG_AP_STEP}，封顶 {@link #SEG_AP_MAX}；段号从 1 计，小于 1 按 1 处理。
     */
    public static int segmentApCap(int segmentNo) {
        int seg = Math.max(1, segmentNo);
        return Math.min(SEG_AP_BASE + (seg - 1) * SEG_AP_STEP, SEG_AP_MAX);
    }

    /** 野外偶遇消耗降为 0 的概率。 */
    public static double wildZeroApChance() {
        return WILD_ZERO_AP_CHANCE;
    }

    /** 商店节点概率：随段号逐步提升，封顶 {@link #SHOP_SPAWN_MAX}。 */
    public static double shopSpawnChance(int segmentNo) {
        int seg = Math.max(1, segmentNo);
        return Math.min(SHOP_SPAWN_BASE + (seg - 1) * SHOP_SPAWN_STEP, SHOP_SPAWN_MAX);
    }

    /** 火箭队节点概率（各时期均可能，[待配置]）。 */
    public static double rocketSpawnChance() {
        return ROCKET_SPAWN_CHANCE;
    }

    /** 神兽偶遇（正常触发）概率（仅后期段、每 Run 一次，[待配置]）。 */
    public static double legendSpawnChance() {
        return LEGEND_SPAWN_CHANCE;
    }

    /** 「火箭队抓捕神兽」剧情事件出现概率（后期段且进过火箭队节点时，[待配置]）。 */
    public static double rocketBossEventChance() {
        return ROCKET_BOSS_EVENT_CHANCE;
    }

    /** 是否到达「后期」段（神兽偶遇 / 抓捕剧情线开始出现的段号起）。 */
    public static boolean isLateSegment(int segmentNo) {
        return segmentNo >= LATE_SEGMENT_FROM;
    }

    // ---------- 路线长度 ----------
    /** 第 1 段~第 N 段为常规路线段，每段行动点耗尽触发道馆战；通过第 N 段道馆后进入终局。 */
    public static final int ROUTE_SEGMENTS = 8;

    // ---------- 经济：开局与收入（数值 [待配置]） ----------
    public static final int GOLD_START = 500;
    public static final int REWARD_TRAINER = 120;
    public static final int REWARD_ROCKET = 400;
    public static final double ROCKET_ITEM_DROP_CHANCE = 0.3;

    // ---------- 经济：失败惩罚（数值 [待配置]） ----------
    public static final int PENALTY_TRAINER = 30;
    public static final int PENALTY_GYM = 200;
    public static final int PENALTY_ELITE = 300;

    // ---------- 服务：医院/商店/抓捕（数值 [待配置]） ----------
    public static final int HOSPITAL_COST = 0;
    public static final double CATCH_BASE_CHANCE = 0.5;

    // ---------- 成长曲线 ----------
    public static final int ENEMY_LEVEL_BASE = 8;
    public static final int ENEMY_LEVEL_STEP = 4;

    public static int goldStart() {
        return GOLD_START;
    }

    public static int trainerReward() {
        return REWARD_TRAINER;
    }

    public static int rocketReward() {
        return REWARD_ROCKET;
    }

    public static double rocketItemDropChance() {
        return ROCKET_ITEM_DROP_CHANCE;
    }

    public static int gymFailPenalty() {
        return PENALTY_GYM;
    }

    public static int eliteFailPenalty() {
        return PENALTY_ELITE;
    }

    public static int trainerFailPenalty() {
        return PENALTY_TRAINER;
    }

    public static int hospitalCost() {
        return HOSPITAL_COST;
    }

    public static double catchChance() {
        return CATCH_BASE_CHANCE;
    }

    /** 敌人基础等级：随段号抬升。 */
    public static int enemyLevel(int segmentNo) {
        int seg = Math.max(1, segmentNo);
        return ENEMY_LEVEL_BASE + (seg - 1) * ENEMY_LEVEL_STEP;
    }

    /** 商店商品数量：随段号小幅增长。 */
    public static int shopSize(int segmentNo) {
        return Math.min(3 + (Math.max(1, segmentNo) - 1) / 2, 6);
    }

    /**
     * 商店售价（数值 [待配置]），键为 {@code GameData} 中的道具 id；
     * 未知道具 / 不出售返回 -1。
     */
    public static int itemPrice(String itemId) {
        if (itemId == null) {
            return -1;
        }
        if (itemId.equals("i_potion")) {
            return 150;
        }
        if (itemId.equals("i_super_potion")) {
            return 350;
        }
        return -1;
    }

    /**
     * 节点默认 AP 消耗（规则见《README》行动点机制表）。
     *
     * <p>神兽偶遇在「火箭队线必然触发」时由实例改写为 0 AP；
     * 道馆 / 四天王 / Boss / 侵略 / 抓捕事件均为必然节点，默认 0 AP。
     */
    public static int apCost(NodeType type) {
        if (type == null) {
            return 0;
        }
        switch (type) {
            case TRAINER:
                return AP_TRAINER;
            case WILD:
                return AP_WILD;
            case HOSPITAL:
                return AP_HOSPITAL;
            case SHOP:
                return AP_SHOP;
            case ROCKET:
                return AP_ROCKET;
            case LEGEND:
                return AP_LEGEND;
            default:
                return 0; // ROCKET_BOSS / GYM / ELITE_FOUR / CHAMPION / INVASION
        }
    }
}
