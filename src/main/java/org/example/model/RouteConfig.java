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

    /** 装备补给节点出现的概率（百分比，低概率节点；占用与火箭队 / 神兽偶遇共用的特殊事件槽位）。 */
    public static final int EQUIPMENT_PERCENT = 30;

    /** 宝可梦交换事件出现的概率（百分比，低概率节点；与特殊事件共享同一事件槽位）。 */
    public static final int TRADE_PERCENT = 30;

    /**
     * 糖果补给节点出现的概率（百分比，低概率节点；在特殊事件槽位的优先级链里排在装备补给之后）。
     * 命中后按 {@link #candyCountForSegment(int)} 发放神奇糖果。
     */
    public static final int CANDY_PERCENT = 30;

    /**
     * 糖果补给节点在第 {@code segment} 段发放的神奇糖果数量：首段 8 个，之后每段 +2
     * （8 / 10 / 12 / 14 / 16）。段号非法时按第 1 段处理。
     */
    public static int candyCountForSegment(int segment) {
        int seg = Math.max(1, segment);
        return 8 + (seg - 1) * 2;
    }

    /** 糖果补给发放的道具 id（神奇糖果，见 {@code items.csv}）。 */
    public static final String CANDY_ITEM_ID = "i_rare_candy";

    /**
     * 每段最多生成的路线节点数（不含必然节点）：
     * 常驻的路人 / 野外精灵 / 医院必占 3 个，商店再按概率追加，共用的特殊事件槽位最多再占 1 个，
     * 末段在火箭队剧情线未收束时还会固定多占 1 个「火箭队抓捕神兽」（见
     * {@link #ROCKET_BOSS_RESIDENT_SEGMENT}），故上限为 6。
     */
    public static final int MAX_ROUTE_NODES = 6;

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

    /**
     * 普通节点战败全灭救援的行动点代价（§4.3）：野生 / 路人训练家战败全灭不结束游戏，
     * 而是消耗该点数行动点恢复全队状态；剩余行动点不足该值时置 0。
     */
    public static final int DEFEAT_RESCUE_AP_COST = 2;

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
    // 商店（§4.2 商店：随机出现；消耗品按段解锁后全量上架，装备全量上架）
    // ------------------------------------------------------------------
    //
    // 两个分区都不再有格位上限：装备全量上架（想买哪件就买哪件），消耗品解锁多少件就上架多少件
    // （第 1 段 7 件 → 第 5 段 16 件）。旧的「第 1 段 3 件、每段 +1、上限 6 件」那套格位常量
    // 已随随机抽签一起删除；消耗品的解锁节奏是逐件数据，写在 ShopStock.CONSUMABLES 的解锁段号上。

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

    /** 野外精灵相对队伍最高等级的等级加成：⌊段号/2⌋（向下取整）。 */
    public static int wildLevelBonus(int segment) {
        return Math.max(1, segment) / 2;
    }

    /** 路人训练家相对队伍最高等级的等级加成：段号 - 1。 */
    public static int trainerLevelBonus(int segment) {
        return Math.max(1, segment) - 1;
    }

    /**
     * 路人训练家队伍数量下限（按段）：1 段 1 只、2 段 1 只、3 段 2 只、4 段 3 只；
     * 第 5 段保持随机 3~4 只。
     */
    public static int trainerPartyMin(int segment) {
        int seg = Math.max(1, segment);
        return switch (seg) {
            case 1, 2 -> 1;
            case 3 -> 2;
            case 4 -> 3;
            default -> 3; // 第 5 段保持随机 3~4
        };
    }

    /**
     * 路人训练家队伍数量上限（按段）：1 段 1 只、2 段 2 只、3 段 2 只、4 段 3 只；
     * 第 5 段保持随机 3~4 只。
     */
    public static int trainerPartyMax(int segment) {
        int seg = Math.max(1, segment);
        return switch (seg) {
            case 1 -> 1;
            case 2, 3 -> 2;
            case 4 -> 3;
            default -> 4; // 第 5 段保持随机 3~4
        };
    }

    /** 道馆战相对队伍最高等级的等级加成（仅第 5 段及以后的道馆主使用；1~4 段走 {@link #gymFixedLevel}）。 */
    public static int gymLevelBonus(int segment) {
        return 2 + Math.max(1, segment) * 2;
    }

    /**
     * 道馆馆主固定等级表（仅 1~4 段）：11 / 17 / 24 / 32。
     * 第 5 段道馆主保持相对队伍最高等级的等级加成（见 {@link #gymLevelBonus}），不走本表；
     * {@code default} 仅为非法段号的防御性兑底。
     */
    public static int gymFixedLevel(int segment) {
        return switch (Math.max(1, segment)) {
            case 1 -> 11;
            case 2 -> 17;
            case 3 -> 24;
            default -> 32;
        };
    }

    /** 道馆战的对手宝可梦数量：1~4 段固定为 2 / 3 / 3 / 4；第 5 段起沿用旧公式。 */
    public static int gymPartySize(int segment) {
        int seg = Math.max(1, segment);
        return switch (seg) {
            case 1 -> 2;
            case 2, 3 -> 3;
            case 4 -> 4;
            default -> Math.min(3, 1 + seg / 2);
        };
    }

    /**
     * 四天王连打相对队伍最高等级的等级加成：固定 0（即队伍最高等级 ±2，无额外加成）。
     *
     * <p>保留段号参数以兼容既有调用口径，当前不再随段数增强。</p>
     */
    public static int eliteFourLevelBonus(int segment) {
        return 0;
    }

    /**
     * 冠军战相对队伍最高等级的等级加成：固定 +3（实际等级再 ±1 浮动，见控制器）。
     *
     * <p>保留段号参数以兼容既有调用口径，当前不再随段数增强。</p>
     */
    public static int championLevelBonus(int segment) {
        return 3;
    }

    /**
     * 四天王连打的对手宝可梦数量：固定 4 只。
     *
     * <p>保留段号参数以兼容既有调用口径。</p>
     */
    public static int eliteFourPartySize(int segment) {
        return 4;
    }

    /**
     * 冠军战的对手宝可梦数量：固定 5 只。
     *
     * <p>保留段号参数以兼容既有调用口径。</p>
     */
    public static int championPartySize(int segment) {
        return 5;
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

    /**
     * 火箭队抓捕神兽事件出现的概率（百分比）；需进入出现窗口
     * （第 4 段行动点消耗过半之后，见 {@link #rocketCaptureAvailable}），且尚未到固定出现的末段。
     */
    public static final int ROCKET_CAPTURE_PERCENT = 60;

    /**
     * 火箭队首领（抓捕神兽）固定出现的起始段号：已开启火箭队剧情线且首领未被击败时，
     * 从该段起该节点不再掷 {@link #ROCKET_CAPTURE_PERCENT}，而是随常驻节点一起固定入列 ——
     * 玩家在打进冠军战前一定有机会与首领交手、拿到大师球并触发那一次必然的神兽偶遇。
     */
    public static final int ROCKET_BOSS_RESIDENT_SEGMENT = TOTAL_SEGMENTS;

    /** 该段是否已到火箭队首领固定出现的段号（剧情线状态由节点生成器另行判定）。 */
    public static boolean isRocketBossResidentSegment(int segment) {
        return Math.max(1, segment) >= ROCKET_BOSS_RESIDENT_SEGMENT;
    }

    /** 火箭队抓捕神兽事件的起始段号：第 4 段行动点消耗过半后才出现（§5.3）。 */
    public static final int ROCKET_CAPTURE_SEGMENT = 4;

    /**
     * 火箭队抓捕神兽事件是否可在当前状态下出现：
     * 第 4 段需行动点消耗过半（当前行动点不超过上限的一半）之后，第 5 段起直接可出现。
     *
     * @param segment 段号（1 起）
     * @param ap      当前剩余行动点
     * @param apMax   本段行动点上限
     */
    public static boolean rocketCaptureAvailable(int segment, int ap, int apMax) {
        int seg = Math.max(1, segment);
        if (seg > ROCKET_CAPTURE_SEGMENT) {
            return true;
        }
        return seg == ROCKET_CAPTURE_SEGMENT && apMax > 0 && ap * 2 <= apMax;
    }

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

    /** 火箭队队员战相对队伍最高等级的等级加成：段号 - 1（与路人训练家同口径，§5.2）。 */
    public static int rocketLevelBonus(int segment) {
        return Math.max(1, segment) - 1;
    }

    /** 火箭队首领战相对队伍最高等级的等级加成：固定 +4（实际等级再 ±1 浮动，见控制器）。 */
    public static final int ROCKET_BOSS_LEVEL_BONUS = 4;

    /** 火箭队首领战的对手宝可梦数量：固定 4 只。 */
    public static final int ROCKET_BOSS_PARTY_SIZE = 4;

    /**
     * 神兽偶遇相对队伍最高等级的等级加成：固定 +6（实际等级再 ±2 浮动，见控制器）。
     *
     * <p>保留段号参数以兼容既有调用口径，当前不再随段数增强。</p>
     */
    public static int legendaryLevelBonus(int segment) {
        return 6;
    }

    /**
     * 首领侵略战相对队伍最高等级的等级加成：固定 +1（实际等级再 ±1 浮动，见控制器）。
     *
     * <p>保留段号参数以兼容既有调用口径，当前不再随段数增强。</p>
     */
    public static int bossAggressionLevelBonus(int segment) {
        return 1;
    }

    /**
     * 火箭队队员战的对手宝可梦数量：1~5 段固定为 2 / 3 / 3 / 4 / 4。
     * {@code default} 为非法段号的防御性兑底。
     */
    public static int rocketPartySize(int segment) {
        int seg = Math.max(1, segment);
        return switch (seg) {
            case 1 -> 2;
            case 2, 3 -> 3;
            default -> 4;
        };
    }

    /**
     * 首领侵略战的对手宝可梦数量：固定 6 只。
     *
     * <p>保留段号参数以兼容既有调用口径。</p>
     */
    public static int bossAggressionPartySize(int segment) {
        return 6;
    }
}
