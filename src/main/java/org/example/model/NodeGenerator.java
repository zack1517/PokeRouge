package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 路线节点生成器：按《需求文档》§4.2「节点类型总表」为每一段生成节点。
 *
 * <p>生成规则：</p>
 * <ul>
 *   <li><b>常驻节点</b>（{@link OptionType#isResident()}）每段固定出现：路人、野外精灵、医院；</li>
 *   <li><b>随机节点</b>（商店 / 特殊事件）按 {@link RouteConfig} 中的概率各自独立判定；</li>
 *   <li><b>必然节点</b>（道馆战 / 四天王连打 / 冠军战）不参与随机生成，由推进阶段决定，
 *       见 {@link #createMandatoryOption(RoutePhase, int)}。</li>
 * </ul>
 *
 * <p>节点消耗默认取自 {@link OptionType#getApCost()}，只有野外精灵会按概率免单
 * （§4.1：野外精灵有概率消耗为 0 点）。</p>
 */
public class NodeGenerator {

    /** 节点描述里用于提示「本次免单」的后缀。 */
    public static final String FREE_HINT = "（本次遭遇不消耗行动点）";

    private final Random random;

    public NodeGenerator() {
        this(new Random());
    }

    public NodeGenerator(Random random) {
        this.random = random == null ? new Random() : random;
    }

    /** 生成某一段的路线节点与必然节点预告。 */
    public SegmentPlan generateSegment(int segment) {
        return generateSegment(segment, false, false, false, false);
    }

    /**
     * 生成某一段的路线节点与必然节点预告，并按剧情线状态决定特殊事件槽位放哪一个事件
     * （《需求文档》§五）：
     *
     * <ol>
     *   <li>{@code pendingLegendary} 为真：必然放入一次 0 点的神兽偶遇（火箭队线击败首领后）；</li>
     *   <li>后期 + 已开启火箭队线 + 尚未击败首领：按 {@link RouteConfig#ROCKET_CAPTURE_PERCENT}
     *       尝试放入「火箭队抓捕神兽」（玩家可自主选择是否进入）；</li>
     *   <li>后期 + 本局尚未遇到过神兽：按 {@link RouteConfig#LEGENDARY_PERCENT} 尝试放入神兽偶遇；</li>
     *   <li>按 {@link RouteConfig#ROCKET_PERCENT} 尝试放入火箭队队员节点（各时期均有概率）；</li>
     *   <li>以上均未命中时，按 {@link RouteConfig#SPECIAL_PERCENT} 放入通用「特殊事件」占位节点。</li>
     * </ol>
     *
     * @param segment             段号（1 起）
     * @param rocketLineUnlocked  是否已进入过火箭队节点（开启剧情线）
     * @param rocketBossDefeated  是否已击败火箭队首领（剧情线已收束，不再出现抓捕事件）
     * @param legendaryMet        本局是否已触发过神兽偶遇
     * @param pendingLegendary    是否有必然触发的神兽偶遇待进入
     */
    public SegmentPlan generateSegment(int segment, boolean rocketLineUnlocked, boolean rocketBossDefeated,
                                       boolean legendaryMet, boolean pendingLegendary) {
        int seg = Math.max(1, segment);
        List<Option> options = new ArrayList<>();

        options.add(createTrainer());
        options.add(createWild());
        options.add(createHospital());

        if (roll(RouteConfig.SHOP_PERCENT)) {
            options.add(createShop());
        }
        Option special = rollSpecialEvent(seg, rocketLineUnlocked, rocketBossDefeated,
                legendaryMet, pendingLegendary);
        if (special != null) {
            options.add(special);
        }

        if (options.size() > RouteConfig.MAX_ROUTE_NODES) {
            options = new ArrayList<>(options.subList(0, RouteConfig.MAX_ROUTE_NODES));
        }
        Collections.shuffle(options, random);

        return new SegmentPlan(seg, RouteConfig.apLimitForSegment(seg), options,
                createMandatoryOption(RoutePhase.GYM, seg));
    }

    /**
     * 按剧情线状态决定本段特殊事件槽位的节点；没有命中任何事件时返回 {@code null}。
     * 每段最多只有一个特殊事件槽位（见 {@link RouteConfig#MAX_ROUTE_NODES}）。
     */
    public Option rollSpecialEvent(int segment, boolean rocketLineUnlocked, boolean rocketBossDefeated,
                                   boolean legendaryMet, boolean pendingLegendary) {
        int seg = Math.max(1, segment);
        if (pendingLegendary) {
            return createLegendary(seg, true);
        }
        if (RouteConfig.isLateGame(seg) && rocketLineUnlocked && !rocketBossDefeated
                && roll(RouteConfig.ROCKET_CAPTURE_PERCENT)) {
            return createRocketCapture();
        }
        if (RouteConfig.isLateGame(seg) && !legendaryMet
                && roll(RouteConfig.LEGENDARY_PERCENT)) {
            return createLegendary(seg, false);
        }
        if (roll(RouteConfig.ROCKET_PERCENT)) {
            return createRocket();
        }
        if (roll(RouteConfig.SPECIAL_PERCENT)) {
            return createSpecial();
        }
        return null;
    }

    /** 路人：常驻节点，行动点 2，胜利获得金币、失败仅扣金币。 */
    public Option createTrainer() {
        return new Option("路人训练家", OptionType.TRAINER, OptionType.TRAINER.getApCost(),
                "与路人训练家对战，胜利获得金币；失败仅扣金币");
    }

    /** 野外精灵：常驻节点，行动点 1（有概率消耗为 0 点），可捕获。 */
    public Option createWild() {
        boolean free = roll(RouteConfig.WILD_FREE_PERCENT);
        String description = free
                ? "遭遇野生宝可梦，可战斗并捕获；" + FREE_HINT
                : "遭遇野生宝可梦，可战斗并捕获；失败仅扣金币";
        return new Option("野生宝可梦出没", OptionType.WILD, free ? 0 : OptionType.WILD.getApCost(),
                description);
    }

    /** 医院：常驻节点，行动点 1，治疗濒死 / 受伤的宝可梦。 */
    public Option createHospital() {
        return new Option("宝可梦医院", OptionType.HOSPITAL, OptionType.HOSPITAL.getApCost(),
                "治疗全队伤病与 PP、复活濒死宝可梦；恢复途径只有医院与道具");
    }

    /** 商店：随机节点，行动点 1，段数越靠后商品种类与数量越多。 */
    public Option createShop() {
        return new Option("商店", OptionType.SHOP, OptionType.SHOP.getApCost(),
                "用金币购买回复品、道具与技能机；随进度商品种类与数量增多");
    }

    /** 特殊事件：随机节点，低概率出现；具体事件类型由后续剧情线决定。 */
    public Option createSpecial() {
        return new Option("神秘事件", OptionType.SPECIAL, RouteConfig.SPECIAL_AP_COST,
                "旅途中遇到的低概率特殊事件，类型随剧情进展变化");
    }

    /**
     * 火箭队队员节点（§5.2）：各时期均可能出现的随机节点，行动点 2；
     * 难度显著高于常规节点，胜利获得大量金币并有概率掉落特殊道具，战败本轮直接结束。
     */
    public Option createRocket() {
        return new Option("火箭队出没", OptionType.ROCKET, RouteConfig.ROCKET_AP_COST,
                "与火箭队队员交手：胜利获得大量金币并有概率掉落特殊道具；不可失败，战败本轮结束");
    }

    /**
     * 火箭队抓捕神兽事件（§5.3）：后期开启剧情线后可自主选择是否进入；
     * 进入即与火箭队首领交手，胜利获得大师球并必然触发一次神兽偶遇。
     */
    public Option createRocketCapture() {
        return new Option("火箭队抓捕神兽", OptionType.ROCKET_CAPTURE, RouteConfig.ROCKET_CAPTURE_AP_COST,
                "火箭队正在抓捕神兽：进入则与首领交手，胜利获得大师球并必然触发一次神兽偶遇；"
                        + "不进入则正常推进。不可失败");
    }

    /**
     * 神兽偶遇（§5.1）：仅游戏后期出现且每局至多一次，可捕获，失败仅扣金币。
     *
     * @param segment 当前段号，用于描述文本
     * @param free    是否为火箭队线「必然触发」的那一次（不消耗行动点）
     */
    public Option createLegendary(int segment, boolean free) {
        int seg = Math.max(1, segment);
        String description = free
                ? "大师球的气息引来了神兽；可战斗并捕获，" + FREE_HINT
                : "稀有的神兽出现在路线前方；可战斗并捕获，失败仅扣金币";
        return new Option("神兽偶遇（第 " + seg + " 段）", OptionType.LEGENDARY,
                free ? 0 : RouteConfig.LEGENDARY_AP_COST, description);
    }

    /**
     * 生成必然节点：不占用行动点，由阶段推进强制触发。
     *
     * @param phase   必然节点所属阶段（{@link RoutePhase#EXPLORING} / {@link RoutePhase#CLEARED} 无必然节点）
     * @param segment 当前段号，用于描述文本
     * @return 必然节点；该阶段没有对应节点时返回 {@code null}
     */
    public Option createMandatoryOption(RoutePhase phase, int segment) {
        if (phase == null) {
            return null;
        }
        int seg = Math.max(1, segment);
        return switch (phase) {
            case GYM -> Option.free(OptionType.GYM, "第 " + seg + " 段道馆战",
                    "行动点耗尽后必然触发，是本段的进度门槛；战败可再挑战一次");
            case ELITE_FOUR -> Option.free(OptionType.ELITE_FOUR, "四天王连打",
                    "冠军战前的必然节点；与道馆同级强度，可失败一次");
            case CHAMPION -> Option.free(OptionType.CHAMPION, "冠军战",
                    "路线终点的最终 Boss；不可失败，胜利即通关");
            case ROCKET_INVASION -> Option.free(OptionType.ROCKET_INVASION, "首领侵略战",
                    "开启过火箭队剧情线却未提前击败首领：击败冠军后必然触发；不可失败");
            case EXPLORING, CLEARED -> null;
        };
    }

    /** 按百分比概率判定（0 以下恒 false，100 及以上恒 true）。 */
    private boolean roll(int percent) {
        if (percent <= 0) {
            return false;
        }
        if (percent >= 100) {
            return true;
        }
        return random.nextInt(100) < percent;
    }
}
