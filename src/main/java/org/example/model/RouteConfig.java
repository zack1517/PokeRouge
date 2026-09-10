package org.example.model;

/**
 * 路线数值配置：把《需求文档》§4「游戏流程与节点设计」里所有可调数值集中到一处
 * （对应 §八「数值可配置」：行动点上限随进度、商店商品成长、敌人强度等集中配置）。
 *
 * <p>本类只放常量与纯函数，无状态；段号一律从 1 起。所有数值均为「待配置」的初版取值，
 * 调整平衡只需改这里，不必触碰生成规则与界面。</p>
 */
public final class RouteConfig {

    private RouteConfig() {
    }

    // ------------------------------------------------------------------
    // 段与行动点（§4.1）
    // ------------------------------------------------------------------

    /** 一条路线的总段数：走完第 N 段后依次进入四天王连打与冠军战（§4.2 必然节点）。 */
    public static final int TOTAL_SEGMENTS = 5;

    /** 第 1 段的行动点上限。 */
    public static final int BASE_AP_LIMIT = 8;

    /** 每推进一段增加的行动点上限（上限随进度逐步提升）。 */
    public static final int AP_LIMIT_PER_SEGMENT = 2;

    /** 某一段的行动点上限；段开始时行动点重置为该值。 */
    public static int apLimitForSegment(int segment) {
        return BASE_AP_LIMIT + (Math.max(1, segment) - 1) * AP_LIMIT_PER_SEGMENT;
    }

    /** 起始金币。 */
    public static final int STARTING_GOLD = 120;

    // ------------------------------------------------------------------
    // 节点生成（§4.2）
    // ------------------------------------------------------------------

    /** 野外精灵节点「本次遭遇不消耗行动点」的概率（百分比，§4.1 野外精灵有概率 0 点）。 */
    public static final int WILD_FREE_PERCENT = 30;

    /** 商店节点出现的概率（百分比，随机节点）。 */
    public static final int SHOP_PERCENT = 55;

    /** 特殊事件节点出现的概率（百分比，低概率节点）。 */
    public static final int SPECIAL_PERCENT = 30;

    /** 特殊事件节点的默认行动点消耗（神兽偶遇 1 点；火箭队线与火箭队节点在第二步接入）。 */
    public static final int SPECIAL_AP_COST = 1;

    /**
     * 每段最多生成的路线节点数（不含必然节点）：
     * 常驻的路人 / 野外精灵 / 医院必占 3 个，商店与特殊事件再按概率追加。
     */
    public static final int MAX_ROUTE_NODES = 5;

    // ------------------------------------------------------------------
    // 金币奖惩（§4.2 节点说明 + §4.3 失败与惩罚规则）
    // ------------------------------------------------------------------

    /** 路人战胜利的金币奖励。 */
    public static int trainerWinGold(int segment) {
        return 45 + Math.max(1, segment) * 15;
    }

    /** 野外精灵战胜利的金币奖励。 */
    public static int wildWinGold(int segment) {
        return 20 + Math.max(1, segment) * 8;
    }

    /** 特殊事件的金币奖励。 */
    public static int specialGold(int segment) {
        return 30 + Math.max(1, segment) * 10;
    }

    /** 道馆战胜利的金币奖励。 */
    public static int gymWinGold(int segment) {
        return 150 + Math.max(1, segment) * 50;
    }

    /** 四天王连打胜利的金币奖励。 */
    public static int eliteFourWinGold(int segment) {
        return 250 + Math.max(1, segment) * 60;
    }

    /** 冠军战胜利的金币奖励。 */
    public static int championWinGold(int segment) {
        return 400 + Math.max(1, segment) * 80;
    }

    /** 普通节点（路人 / 野外精灵）战败的金币惩罚；不会把金币扣成负数。 */
    public static int defeatGoldPenalty(int segment) {
        return 25 + Math.max(1, segment) * 10;
    }

    /** 道馆 / 四天王首次战败的金币惩罚（重试代价显著更高，§4.3）。 */
    public static int mandatoryDefeatGoldPenalty(int segment) {
        return 90 + Math.max(1, segment) * 30;
    }

    // ------------------------------------------------------------------
    // 节点自回血（§4.4）
    // ------------------------------------------------------------------

    /** 每通过一个节点，未濒死宝可梦恢复最大 HP 的分子。 */
    public static final int NODE_HEAL_NUMERATOR = 1;

    /** 每通过一个节点，未濒死宝可梦恢复最大 HP 的分母。 */
    public static final int NODE_HEAL_DENOMINATOR = 5;

    /** 单个节点通过后，一只未濒死宝可梦的回复量（濒死宝可梦不享受，需医院或道具）。 */
    public static int nodeHealAmount(int maxHp) {
        return Math.max(0, maxHp) * NODE_HEAL_NUMERATOR / NODE_HEAL_DENOMINATOR;
    }

    // ------------------------------------------------------------------
    // 商店（§4.2 商店：随机出现，随游戏进展商品种类与数量越多）
    // ------------------------------------------------------------------

    /** 第 1 段上架的商品数量。 */
    public static final int BASE_SHOP_STOCK = 3;

    /** 每推进一段额外增加的商品数量。 */
    public static final int SHOP_STOCK_PER_SEGMENT = 1;

    /** 单次商店的商品数量上限。 */
    public static final int MAX_SHOP_STOCK = 6;

    /** 某一段商店的商品数量（数量随进展增加）。 */
    public static int shopStockSize(int segment) {
        int size = BASE_SHOP_STOCK + (Math.max(1, segment) - 1) * SHOP_STOCK_PER_SEGMENT;
        return Math.min(MAX_SHOP_STOCK, size);
    }

    /** 每推进一段的物价涨幅（百分比）。 */
    public static final int SHOP_PRICE_INFLATION_PERCENT = 15;

    /** 按段数折算后的售价。 */
    public static int shopPrice(int basePrice, int segment) {
        int steps = Math.max(1, segment) - 1;
        return (int) Math.round(basePrice * (1.0 + SHOP_PRICE_INFLATION_PERCENT * steps / 100.0));
    }

    // ------------------------------------------------------------------
    // 敌人强度（随段推进增强）
    // ------------------------------------------------------------------

    /** 野外精灵相对玩家先发的等级加成。 */
    public static int wildLevelBonus(int segment) {
        return Math.max(1, segment) - 1;
    }

    /** 路人训练家相对玩家先发的等级加成。 */
    public static int trainerLevelBonus(int segment) {
        return Math.max(1, segment) + 1;
    }

    /** 道馆战相对玩家先发的等级加成。 */
    public static int gymLevelBonus(int segment) {
        return 2 + Math.max(1, segment) * 2;
    }

    /** 四天王连打相对玩家先发的等级加成。 */
    public static int eliteFourLevelBonus(int segment) {
        return 4 + Math.max(1, segment) * 2;
    }

    /** 冠军战相对玩家先发的等级加成。 */
    public static int championLevelBonus(int segment) {
        return 6 + Math.max(1, segment) * 3;
    }

    /** 道馆战的对手宝可梦数量。 */
    public static int gymPartySize(int segment) {
        return Math.min(3, 1 + Math.max(1, segment) / 2);
    }

    /** 四天王连打的对手宝可梦数量。 */
    public static int eliteFourPartySize(int segment) {
        return Math.min(4, 2 + Math.max(1, segment) / 2);
    }

    /** 冠军战的对手宝可梦数量。 */
    public static int championPartySize(int segment) {
        return Math.min(6, 3 + Math.max(1, segment) / 2);
    }

    // ------------------------------------------------------------------
    // 特殊事件：神兽偶遇与火箭队剧情线（§五）
    // ------------------------------------------------------------------

    /** 「游戏后期」的起始段号：神兽偶遇与火箭队抓捕事件只在此段及之后出现（§5.1）。 */
    public static final int LATE_GAME_SEGMENT = 3;

    /** 该段是否已进入游戏后期。 */
    public static boolean isLateGame(int segment) {
        return Math.max(1, segment) >= LATE_GAME_SEGMENT;
    }

    /** 火箭队队员节点出现的概率（百分比）；各时期均可能出现（§5.2）。 */
    public static final int ROCKET_PERCENT = 35;

    /** 火箭队抓捕神兽事件出现的概率（百分比）；需后期且已开启剧情线。 */
    public static final int ROCKET_CAPTURE_PERCENT = 60;

    /** 神兽偶遇出现的概率（百分比）；仅后期且每局至多一次（§5.1）。 */
    public static final int LEGENDARY_PERCENT = 30;

    /** 火箭队队员节点的行动点消耗（与路人一致，§4.1）。 */
    public static final int ROCKET_AP_COST = 2;

    /** 火箭队抓捕神兽事件的行动点消耗（与火箭队节点一致）。 */
    public static final int ROCKET_CAPTURE_AP_COST = 2;

    /** 神兽偶遇的行动点消耗；火箭队线必然触发的那次为 0 点（§4.1）。 */
    public static final int LEGENDARY_AP_COST = 1;

    /** 火箭队队员战胜利的金币奖励（高收益，§5.2）。 */
    public static int rocketWinGold(int segment) {
        return 120 + Math.max(1, segment) * 45;
    }

    /** 火箭队队员战掉落特殊道具的概率（百分比）。 */
    public static final int ROCKET_DROP_PERCENT = 60;

    /** 火箭队节点掉落的特殊道具。 */
    public static final String ROCKET_DROP_ITEM_ID = "i_ultra_ball";

    /** 击败火箭队首领（抓捕神兽事件）的额外奖励道具——大师球。 */
    public static final String ROCKET_BOSS_REWARD_ITEM_ID = "i_master_ball";

    /** 火箭队抓捕神兽事件（首领战）胜利的金币奖励。 */
    public static int rocketCaptureWinGold(int segment) {
        return 320 + Math.max(1, segment) * 90;
    }

    /** 神兽偶遇战斗胜利的金币奖励。 */
    public static int legendaryWinGold(int segment) {
        return 90 + Math.max(1, segment) * 35;
    }

    /** 首领侵略战胜利的金币奖励（通关前的最后一战）。 */
    public static int bossAggressionWinGold(int segment) {
        return 520 + Math.max(1, segment) * 120;
    }

    /** 火箭队队员战相对玩家先发的等级加成（难度显著高于常规节点，§5.2）。 */
    public static int rocketLevelBonus(int segment) {
        return 3 + Math.max(1, segment) * 2;
    }

    /** 火箭队首领战相对玩家先发的等级加成。 */
    public static int rocketBossLevelBonus(int segment) {
        return 5 + Math.max(1, segment) * 3;
    }

    /** 神兽偶遇相对玩家先发的等级加成（神兽强度高于普通野生精灵）。 */
    public static int legendaryLevelBonus(int segment) {
        return 4 + Math.max(1, segment) * 3;
    }

    /** 首领侵略战相对玩家先发的等级加成。 */
    public static int bossAggressionLevelBonus(int segment) {
        return 7 + Math.max(1, segment) * 3;
    }

    /** 火箭队队员战的对手宝可梦数量。 */
    public static int rocketPartySize(int segment) {
        return Math.min(4, 1 + Math.max(1, segment) / 2);
    }

    /** 火箭队首领战的对手宝可梦数量。 */
    public static int rocketBossPartySize(int segment) {
        return Math.min(6, 2 + Math.max(1, segment) / 2);
    }

    /** 首领侵略战的对手宝可梦数量。 */
    public static int bossAggressionPartySize(int segment) {
        return Math.min(6, 3 + Math.max(1, segment) / 2);
    }
}
