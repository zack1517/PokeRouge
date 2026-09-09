package com.bao01.flow;

import org.example.data.GameData;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Player;
import org.example.model.Pokemon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 流程控制器（门面）：一段 Roguelike Run 的推进状态机。
 *
 * <p>对应《游戏流程接口设计》3.4。采用实例有状态，由主循环 / UI 持有；
 * 方法返回 {@code List<String>} 叙事日志，便于界面直接展示。
 *
 * <p>推进模型（按用户确认的“每次战后重新抽一组”语义实现）：
 * <ol>
 *   <li>常规段：段内反复进入可选节点（战斗/医院/商店），每次战后从常驻+随机事件里
 *       重抽剩余可选节点，直至行动点耗尽 → 触发道馆战 → 通过后段号+1、AP 重置；</li>
 *   <li>终局（通过第 {@code FlowConfig#ROUTE_SEGMENTS} 段道馆后）：四天王 → Boss 冠军
 *       →（视火箭队剧情线）首领侵略战，按 §6.2 判定 NORMAL / TRUE / RUN_OVER 结局。</li>
 * </ol>
 *
 * <p>战斗边界：本类不直接操作战斗内部状态，一律经 {@link BattleAdapter} 发起，
 * 结算由 UI / 对战模块在对局结束后调用 {@link #settleFight(NodeType, FightResult)}。
 */
public final class FlowController {

    private final Player player;
    private final RandomSource rnd;
    private final BattleAdapter battle;
    private final ActionPoints ap;

    private StoryFlags story;
    private CaptureLedger ledger;
    private int gold;

    /** 当前可选节点（常驻 + 随机事件）。 */
    private final List<RouteOption> options = new ArrayList<>();

    /** true=常规段路线（道馆推进）；false=终局（四天王/冠军/侵略）。 */
    private boolean inRoute = true;

    // 里程碑重试/完成标记
    private boolean gymRetried;          // 本段道馆是否已失败一次（再次失败结束 Run）
    private boolean eliteCleared;        // 四天王是否已击败
    private boolean eliteRetried;        // 四天王是否已失败一次
    private boolean championCleared;
    private boolean invasionCleared;

    /** 当前正在等待结算的战斗节点（null=未在战斗中）。 */
    private NodeType pendingFight;

    /** 最近一次进入商店时生成的商品缓存（buy 消费对象）。 */
    private List<ShopOffer> shopStock = List.of();

    private Ending ending;               // null=进行中

    // ------------------------------------------------------------------
    // 构造 / 开局 / 恢复
    // ------------------------------------------------------------------

    private FlowController(Player starter, RandomSource rnd, BattleAdapter battle) {
        this.player = starter;
        this.rnd = rnd;
        this.battle = battle;
        this.ap = ActionPoints.opening();
        this.story = StoryFlags.initial();
        this.ledger = CaptureLedger.empty();
        this.gold = FlowConfig.goldStart();
    }

    /** 开局：携带初始队伍进入第 1 段，重置 AP，生成第 1 段可选节点池。 */
    public static FlowController startRun(Player starter, RandomSource rnd, BattleAdapter battle) {
        if (starter == null) {
            throw new IllegalArgumentException("初始队伍不能为 null");
        }
        FlowController c = new FlowController(starter, rnd, battle);
        c.refreshOptions();
        return c;
    }

    /** 开局（保留 cfg 形参，与《接口文档》3.4 签名一致；数值均走静态 {@code FlowConfig}）。 */
    public static FlowController startRun(Player starter, FlowConfig cfg, RandomSource rnd, BattleAdapter battle) {
        return startRun(starter, rnd, battle);
    }

    /** 从 Run 快照恢复（对应 §7 扩展点；过程态战斗不随快照保存）。 */
    public static FlowController restored(Player player, RunSummary snapshot, BattleAdapter battle) {
        if (snapshot == null) {
            throw new IllegalArgumentException("RunSummary 快照不能为 null");
        }
        if (player == null) {
            throw new IllegalArgumentException("玩家队伍不能为 null");
        }
        FlowController c = new FlowController(player, RandomSource.system(), battle);
        c.ap.resetForSegment(snapshot.segmentNo());
        c.gold = snapshot.gold();
        c.story = snapshot.story();
        c.ledger = snapshot.ledger();
        c.options.clear();
        c.options.addAll(snapshot.options());
        c.inRoute = snapshot.nextMilestone() != NodeType.ELITE_FOUR
                && snapshot.nextMilestone() != NodeType.CHAMPION
                && snapshot.nextMilestone() != NodeType.INVASION;
        return c;
    }

    // ------------------------------------------------------------------
    // 状态查询
    // ------------------------------------------------------------------

    public RunSummary summary() {
        return new RunSummary(segmentNo(), apLeft(), ap.apCap(), gold, story, ledger,
                options(), nextMilestone());
    }

    public int gold() {
        return gold;
    }

    public int apLeft() {
        return ap.apLeft();
    }

    public int segmentNo() {
        return ap.segmentNo();
    }

    public StoryFlags story() {
        return story;
    }

    public CaptureLedger ledger() {
        return ledger;
    }

    public boolean isOver() {
        return ending != null;
    }

    public Ending ending() {
        return ending;
    }

    /** 本段当前可选节点（按序可进入）。 */
    public List<RouteOption> options() {
        return List.copyOf(options);
    }

    /**
     * 下一个必然节点：常规段且行动点耗尽/无可负担选项时为道馆战；
     * 终局依次为 四天王 → 冠军 → 首领侵略（视剧情线）；已结束返回 null。
     */
    public NodeType nextMilestone() {
        if (ending != null) {
            return null;
        }
        if (inRoute) {
            return canEnterAny() ? null : NodeType.GYM;
        }
        if (!eliteCleared) {
            return NodeType.ELITE_FOUR;
        }
        if (!championCleared) {
            return NodeType.CHAMPION;
        }
        if (!invasionCleared && story.rocketEntered() && !story.rocketBossDefeated()) {
            return NodeType.INVASION;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 节点选择
    // ------------------------------------------------------------------

    /**
     * 进入可选节点（下标对应 {@link #options()}）：扣 AP → 执行该节点逻辑。
     * 战斗节点在此扣除 AP 并经 {@link BattleAdapter} 开局，胜负由 UI 对局结束后调
     * {@link #settleFight(NodeType, FightResult)} 结算。
     */
    public List<String> enter(int optionIndex) {
        List<String> log = new ArrayList<>();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            log.add("（无效选项：下标越界）");
            return log;
        }
        if (pendingFight != null) {
            log.add("上一场「" + pendingFight.label() + "」尚未结算，无法进入新节点。");
            return log;
        }
        RouteOption opt = options.get(optionIndex);
        boolean freeForced = opt.forced() && opt.apCost() == 0;

        if (freeForced) {
            log.add("触发「" + opt.display() + "」（必然节点，不消耗 AP）。");
        } else {
            if (!ap.canAfford(opt)) {
                log.add("行动点不足：「" + opt.display() + "」需要 " + opt.apCost()
                        + " AP，当前剩余 " + apLeft() + " AP，无法进入。");
                return log;
            }
            log.addAll(ap.enter(opt));
        }

        options.remove(optionIndex);
        shopStock = List.of();
        log.addAll(routeNode(opt.type()));
        return log;
    }

    /** 触发当前必然节点（nextMilestone 非空时）：道馆/四天王/冠军/侵略 的开局。 */
    public List<String> startMilestone() {
        List<String> log = new ArrayList<>();
        NodeType m = nextMilestone();
        if (m == null) {
            log.add(ending == null ? "当前没有需要触发的必然节点。" : "Run 已结束（" + ending + "）。");
            return log;
        }
        if (pendingFight != null) {
            log.add("上一场「" + pendingFight.label() + "」尚未结算。");
            return log;
        }
        log.add("行动点耗尽，触发「" + m.label() + "」！");
        log.addAll(beginBattle(m));
        return log;
    }

    /** 按节点类型路由：非战斗节点直接执行，战斗节点开局并挂起结算。 */
    private List<String> routeNode(NodeType type) {
        switch (type.cat()) {
            case SERVICE:
                if (type == NodeType.HOSPITAL) {
                    return visitHospital();
                }
                if (type == NodeType.SHOP) {
                    shopStock = generateShopStock();
                    List<String> log = new ArrayList<>();
                    log.add("你走进了商店，可在本节点购买 " + shopStock.size()
                            + " 种商品（shopStock / buy）。");
                    return log;
                }
                return List.of();
            case COMBAT:
                return beginBattle(type);
            case STORY:      // 火箭队抓捕神兽：进入即与首领交战（不可失败）
                return beginBattle(type);
            default:
                return List.of();
        }
    }

    /** 开局一场战斗：标记待结算节点。剧情标记（火箭队线/神兽一次）在此落定。 */
    private List<String> beginBattle(NodeType type) {
        pendingFight = type;
        if (type == NodeType.ROCKET && !story.rocketEntered()) {
            story = story.withRocketEntered();
        }
        if (type == NodeType.LEGEND && !story.legendaryEncountered()) {
            story = story.withLegendaryEncountered();
        }
        List<Pokemon> foes = buildFoeTeam(type);
        boolean canFlee = type == NodeType.WILD || type == NodeType.LEGEND;
        Pokemon lead = foes.isEmpty() ? null : foes.get(0);
        List<String> log = new ArrayList<>();
        log.add("与「" + (lead == null ? "?" : lead.getName()) + "」(Lv."
                + (lead == null ? "?" : lead.getLevel()) + ") 的对战开始！");
        log.addAll(battle.startBattle(player, foes, canFlee));
        return log;
    }

    // ------------------------------------------------------------------
    // 节点逻辑：医院 / 商店
    // ------------------------------------------------------------------

    /** 医院：治疗全部濒死/受伤成员（收费 {@code FlowConfig#hospitalCost}，默认 0）。 */
    public List<String> visitHospital() {
        List<String> log = new ArrayList<>();
        if (gold < FlowConfig.hospitalCost()) {
            log.add("金币不足，无法支付医院费用（" + FlowConfig.hospitalCost() + "）。");
            return log;
        }
        gold -= FlowConfig.hospitalCost();
        player.healParty();
        log.add("医院对全体成员进行了治疗，队伍全员恢复到满状态。");
        return log;
    }

    /** 商店：返回本段商品（随段号变多；未进商店时为空表）。 */
    public List<ShopOffer> shopStock() {
        return List.copyOf(shopStock);
    }

    /** 购买下标商品；金币不足返回提示日志，不扣款。 */
    public List<String> buy(int shopIndex) {
        List<String> log = new ArrayList<>();
        if (shopIndex < 0 || shopIndex >= shopStock.size()) {
            log.add("（无效商品：下标越界）");
            return log;
        }
        ShopOffer offer = shopStock.get(shopIndex);
        if (gold < offer.price()) {
            log.add("金币不足：需要 " + offer.price() + "，当前 " + gold + "。");
            return log;
        }
        Item item = GameData.instance().item(offer.itemId());
        if (item == null) {
            log.add("（未注册道具：" + offer.itemId() + "）");
            return log;
        }
        gold -= offer.price();
        player.getBag().add(item, 1);
        log.add("购买了「" + item.getName() + "」×1，花费 " + offer.price()
                + " 金币，剩余 " + gold + "。");
        return log;
    }

    /** 生成当前商店商品（从数据源 HEAL 道具池随机取 {@code FlowConfig#shopSize} 件）。 */
    private List<ShopOffer> generateShopStock() {
        List<Item> healItems = new ArrayList<>();
        for (Item it : GameData.instance().allItems()) {
            if (it.getCategory() == ItemCategory.HEAL) {
                healItems.add(it);
            }
        }
        // 未定价（售价 -1）的道具不出售。
        healItems.removeIf(it -> FlowConfig.itemPrice(it.getId()) < 0);
        if (healItems.isEmpty()) {
            return List.of();
        }
        Collections.shuffle(healItems, new java.util.Random(rnd.nextInt(Integer.MAX_VALUE)));
        List<ShopOffer> stock = new ArrayList<>();
        int size = FlowConfig.shopSize(segmentNo());
        for (int i = 0; i < Math.min(size, healItems.size()); i++) {
            Item it = healItems.get(i);
            stock.add(new ShopOffer(it.getId(), FlowConfig.itemPrice(it.getId()),
                    "回复 " + (int) it.getEffect() + " HP"));
        }
        return stock;
    }

    // ------------------------------------------------------------------
    // 敌方队伍构造（按段等级、按节点类型选物种）
    // ------------------------------------------------------------------

    /** 构造某节点的敌方队伍（等级 = FlowConfig.enemyLevel(segmentNo) 附近，终局更强）。 */
    public List<Pokemon> buildFoeTeam(NodeType type) {
        int base = FlowConfig.enemyLevel(segmentNo());
        int count;
        int boost;
        switch (type) {
            case WILD:         count = 1; boost = 0; break;
            case TRAINER:      count = 2; boost = 1; break;
            case ROCKET:       count = 2; boost = 3; break;
            case LEGEND:       count = 1; boost = 6; break;
            case ROCKET_BOSS:  count = 3; boost = 4; break;
            case GYM:          count = 2; boost = 0; break;
            case ELITE_FOUR:   count = 3; boost = 3; break;
            case CHAMPION:     count = 4; boost = 5; break;
            case INVASION:     count = 4; boost = 7; break;
            default:           count = 1; boost = 0;
        }
        List<String> species = new ArrayList<>();
        for (org.example.model.Species sp : GameData.instance().allSpecies()) {
            species.add(sp.getId());
        }
        Collections.sort(species);
        if (species.isEmpty()) {
            return List.of();
        }
        int level = base + boost;
        List<Pokemon> foes = new ArrayList<>();
        int start = rnd.nextInt(species.size());
        for (int i = 0; i < count; i++) {
            String id = species.get((start + i) % species.size());
            GameData.instance().createPokemon(id, level).ifPresent(foes::add);
        }
        return foes;
    }

    // ------------------------------------------------------------------
    // 战斗结算
    // ------------------------------------------------------------------

    /**
     * 战斗结束后由 UI 调用：按节点类型执行奖惩、濒死处理、自回血、
     * 剧情线推进、必然节点触发与结局判定。
     */
    public List<String> settleFight(NodeType fought, FightResult outcome) {
        List<String> log = new ArrayList<>();
        if (pendingFight == null) {
            log.add("（当前没有待结算的战斗。）");
            return log;
        }
        if (fought != pendingFight) {
            log.add("（结算节点不匹配：待结算为「" + pendingFight.label() + "」，传入为「"
                    + fought.label() + "」）");
            return log;
        }
        pendingFight = null;
        boolean won = outcome == FightResult.PLAYER_WON;
        boolean fled = outcome == FightResult.PLAYER_FLED;

        log.addAll(settleByType(fought, won, fled));
        if (ending != null) {
            log.add("Run 结束：" + describeEnding());
            return log;
        }
        if (!fled || won) {
            log.addAll(healOneFifth());
        }
        if (!inRoute) {
            return log;
        }
        // 每次战斗结算后重新抽一组可选节点（用户确认的模型 A）。
        refreshOptions();
        if (!canEnterAny()) {
            log.add("已无可以进入的可选节点，本段将触发「" + nextMilestone().label() + "」。");
        }
        return log;
    }

    /** 按节点类型执行胜败奖惩与剧情推进。 */
    private List<String> settleByType(NodeType t, boolean won, boolean fled) {
        List<String> log = new ArrayList<>();
        if (fled) {
            // 逃跑视为节点结束，无奖惩（仅野生/神兽可逃）。
            log.add("你逃离了对战，节点结束。");
            return log;
        }
        switch (t.cat()) {
            case COMBAT:
                log.addAll(settleCombat(t, won));
                break;
            case STORY:      // 火箭队抓捕神兽首领战
                if (won) {
                    story = story.withRocketBossDefeated().withMasterBallHeld();
                    gold = addClamp(gold, FlowConfig.rocketReward());
                    log.add("你击败了持有神兽的火箭队首领，夺得「大师球」并获得 "
                            + FlowConfig.rocketReward() + " 金币！");
                    log.add("大师球在手，神兽将在后续路线中必然现身（AP=0）。");
                } else {
                    ending = Ending.RUN_OVER;
                    log.add("抓捕事件首领战败北…… Run 结束。");
                }
                break;
            case MILESTONE:
                log.addAll(settleMilestone(t, won));
                break;
            default:
                break;
        }
        return log;
    }

    private List<String> settleCombat(NodeType t, boolean won) {
        List<String> log = new ArrayList<>();
        if (won) {
            if (t == NodeType.TRAINER) {
                gold = addClamp(gold, FlowConfig.trainerReward());
                log.add("击败路人，获得 " + FlowConfig.trainerReward() + " 金币。");
            } else if (t == NodeType.ROCKET) {
                gold = addClamp(gold, FlowConfig.rocketReward());
                log.add("击败火箭队，获得 " + FlowConfig.rocketReward() + " 金币！");
                if (rnd.chance(FlowConfig.rocketItemDropChance())) {
                    Item drop = GameData.instance().item("i_super_potion");
                    if (drop != null) {
                        player.getBag().add(drop, 1);
                        log.add("火箭队掉落了「好伤药」×1。");
                    }
                }
            } else if (t == NodeType.WILD) {
                log.add("野生宝可梦被击败（可尝试捕获或离开）。");
            } else if (t == NodeType.LEGEND) {
                log.add("神兽被击败！这次珍贵的偶遇结束了。");
            }
            return log;
        }
        // 战败
        if (t == NodeType.ROCKET) {
            ending = Ending.RUN_OVER;
            log.add("火箭队战败北——不可失败战斗，Run 结束。");
        } else {
            int fine = FlowConfig.trainerFailPenalty();
            gold = Math.max(0, gold - fine);
            log.add("对战失利，损失 " + fine + " 金币（当前 " + gold + "）。Run 继续。");
        }
        return log;
    }

    private List<String> settleMilestone(NodeType t, boolean won) {
        List<String> log = new ArrayList<>();
        switch (t) {
            case GYM:
                if (won) {
                    log.add("道馆战胜利！");
                    if (segmentNo() >= FlowConfig.ROUTE_SEGMENTS) {
                        inRoute = false;
                        log.add("你走完了 " + FlowConfig.ROUTE_SEGMENTS
                                + " 段常规路线，抵达终局——「四天王连打」已在眼前。");
                    } else {
                        log.addAll(ap.advanceSegment());
                        refreshOptions();
                    }
                } else {
                    if (gymRetried) {
                        ending = Ending.RUN_OVER;
                        log.add("道馆战再次败北，Run 结束。");
                    } else {
                        gymRetried = true;
                        int fine = FlowConfig.gymFailPenalty();
                        gold = Math.max(0, gold - fine);
                        log.add("道馆战失利，损失 " + fine + " 金币（当前 " + gold
                                + "）。你可以再挑战一次，但再次失败将结束 Run。");
                    }
                }
                break;
            case ELITE_FOUR:
                if (won) {
                    eliteCleared = true;
                    log.add("四天王连打通过！最终 Boss 冠军战开启。");
                } else {
                    if (eliteRetried) {
                        ending = Ending.RUN_OVER;
                        log.add("四天王再次败北，Run 结束。");
                    } else {
                        eliteRetried = true;
                        int fine = FlowConfig.eliteFailPenalty();
                        gold = Math.max(0, gold - fine);
                        log.add("四天王失利，损失 " + fine + " 金币（当前 " + gold
                                + "）。可再战一次，再次失败将结束 Run。");
                    }
                }
                break;
            case CHAMPION:
                if (won) {
                    championCleared = true;
                    log.add("你击败了 Boss 冠军！");
                    if (story.rocketEntered() && !story.rocketBossDefeated()) {
                        log.add("——但首领带着神兽发动了侵略战！");
                    } else {
                        ending = Ending.NORMAL_CLEAR;
                    }
                } else {
                    ending = Ending.RUN_OVER;
                    log.add("冠军战败北——不可失败，Run 结束。");
                }
                break;
            case INVASION:
                if (won) {
                    ending = Ending.TRUE_CLEAR;
                    log.add("你在侵略战中击败了持神兽的首领，真结局达成！");
                } else {
                    ending = Ending.RUN_OVER;
                    log.add("首领侵略战败北，Run 结束。");
                }
                break;
            default:
                break;
        }
        return log;
    }

    /** 不可失败战斗（火箭队战/抓捕首领战/冠军战/侵略战）。 */
    private boolean isFatalLoss(NodeType t) {
        return t == NodeType.ROCKET || t == NodeType.ROCKET_BOSS
                || t == NodeType.CHAMPION || t == NodeType.INVASION;
    }

    // ------------------------------------------------------------------
    // 捕获 / 治疗 / 状态刷新 / 存档
    // ------------------------------------------------------------------

    /** 捕获尝试：对目标（战败的野生/神兽）执行一次捕获判定，成功记入抓捕账本。 */
    public List<String> attemptCatch(Pokemon target) {
        List<String> log = new ArrayList<>();
        if (target == null || target.isFainted()) {
            log.add("（目标不存在或已濒死，无法捕获）");
            return log;
        }
        if (rnd.chance(FlowConfig.catchChance())) {
            ledger = ledger.record(target.getName());
            log.add("捕获成功！「" + target.getName() + "」已被记为第 "
                    + ledger.countOf(target.getName()) + " 次捕获。");
            log.add("（入队 / 同类合并 / 性格选择交由养成模块处理。）");
        } else {
            log.add("捕获失败，「" + target.getName() + "」逃走了。");
        }
        return log;
    }

    /** 每通过一个节点后：队伍中未濒死成员自动恢复 maxHp/5（最小 1）；濒死不恢复。 */
    private List<String> healOneFifth() {
        List<String> log = new ArrayList<>();
        int healed = 0;
        for (Pokemon p : player.getParty()) {
            if (p.isFainted()) {
                continue;
            }
            int amount = Math.max(1, p.getMaxHp() / 5);
            int before = p.getCurrentHp();
            p.heal(amount);
            if (p.getCurrentHp() > before) {
                healed++;
            }
        }
        if (healed > 0) {
            log.add("节点通过，未濒死成员自动恢复了部分 HP（maxHp/5）。");
        }
        return log;
    }

    /** 重新抽一组当前段的可选节点（段首 / 通过道馆进入下一段时）。 */
    private void refreshOptions() {
        options.clear();
        options.addAll(NodePool.roll(segmentNo(), story, rnd));
    }

    /** 是否还有可进入的可选节点（含免 AP 的强制事件）。 */
    private boolean canEnterAny() {
        for (RouteOption o : options) {
            if (o.forced() && o.apCost() == 0) {
                return true;
            }
            if (ap.canAfford(o)) {
                return true;
            }
        }
        return false;
    }

    private String describeEnding() {
        switch (ending) {
            case NORMAL_CLEAR:
                return "普通通关！";
            case TRUE_CLEAR:
                return "真结局通关！";
            default:
                return "本次 Run 失败。";
        }
    }

    private static int addClamp(int value, int delta) {
        return Math.max(0, value + delta);
    }

    /** Run 快照 → 文本（建议随 World 一并落盘，见 §7）。 */
    public String toText() {
        return "RUN|seg=" + segmentNo() + "|ap=" + apLeft() + "/" + ap.apCap()
                + "|gold=" + gold + "|next=" + nextMilestone()
                + "|ending=" + (ending == null ? "PLAYING" : ending);
    }
}