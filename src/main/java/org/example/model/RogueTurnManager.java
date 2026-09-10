package org.example.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 路线推进状态机：负责行动点（AP）、路线节点与必然节点的规则判定。
 *
 * <p>职责边界：本类只做**规则判定与数值结算**（AP 扣除、金币奖惩、医院治疗、节点自回血、
 * 必然节点触发与阶段流转），不进行任何战斗模拟——真实战斗由控制器接管：
 * {@link #consumeNode(Option)} 扣点后由 UI 拉起战斗，战斗结束后控制器回调
 * {@link #awardWinGold(OptionType)} 或 {@link #applyDefeatPenalty(OptionType)}，
 * 再调用 {@link #applyNodeHeal()} 与 {@link #advanceAfterNode()}。</p>
 *
 * <p>一次推进的典型调用序列：</p>
 * <pre>
 * startRun(team)                       // 进入第 1 段，AP 重置为本段上限
 *   loop:
 *     consumeNode(option)              // 校验并扣 AP、把节点标记为已走
 *     （战斗节点：控制器战斗 → awardWinGold / applyDefeatPenalty）
 *     resolveImmediateEffect(option)   // 医院治疗 / 特殊事件结算
 *     applyNodeHeal()                  // 节点自回血：未濒死宝可梦回复最大 HP 的 1/5
 *     advanceAfterNode()               // AP 耗尽或无节点可走 → 触发道馆战
 *   mandatory:
 *     resolveMandatoryVictory()        // 道馆胜利 → 下一段；四天王 → 冠军；冠军 → 通关
 *     resolveMandatoryDefeat()         // 可失败一次；重试再败则 Run 结束
 * </pre>
 */
public class RogueTurnManager {

    private final RunData runData;
    private final NodeGenerator generator;

    public RogueTurnManager() {
        this(new RunData(), new NodeGenerator());
    }

    public RogueTurnManager(RunData runData, NodeGenerator generator) {
        this.runData = runData == null ? new RunData() : runData;
        this.generator = generator == null ? new NodeGenerator() : generator;
    }

    public RunData getRunData() {
        return runData;
    }

    public NodeGenerator getGenerator() {
        return generator;
    }

    // ------------------------------------------------------------------
    // 开轮与段推进
    // ------------------------------------------------------------------

    /** 开始一次新远征：写入队伍、重置金币与剧情线状态，并从第 1 段起步。 */
    public void startRun(List<PokemonInstance> team) {
        setTeam(team);
        runData.setGold(RouteConfig.STARTING_GOLD);
        runData.setGameOver(false);
        runData.setCleared(false);
        runData.setRocketLineUnlocked(false);
        runData.setRocketBossDefeated(false);
        runData.setLegendaryMet(false);
        runData.setPendingLegendary(false);
        runData.setAggressionTriggered(false);
        enterSegment(RunData.FIRST_SEGMENT);
    }

    /**
     * 进入某一段：重新生成路线节点（按剧情线状态决定特殊事件），行动点重置为该段上限，
     * 阶段回到路线探索，「失败一次」的机会也一并重置（§4.1：每段路线开始时重置行动点至该段上限）。
     */
    public void enterSegment(int segment) {
        SegmentPlan plan = generatePlan(segment);
        runData.setSegment(plan.getSegment());
        runData.setApMax(plan.getApLimit());
        runData.setAp(plan.getApLimit());
        runData.setAvailableOptions(new ArrayList<>(plan.getRouteOptions()));
        runData.setPhase(RoutePhase.EXPLORING);
        runData.setRetryUsed(0);
        runData.setMandatoryOption(null);
        runData.setGameOver(false);
        runData.setCleared(false);
    }

    /**
     * 刷新本段路线节点（§4.1：<b>每走完一个路线节点，本段剩余节点重新随机生成</b>）：
     * 按当前段号与剧情线 / 神兽状态重新抽一批节点替换旧列表，
     * <b>行动点、阶段、金币与剧情线标记都不变</b>——刷新只换节点，不重置本段进度。
     *
     * <p>由于常驻节点（路人 / 野外精灵 / 医院）每次都必然入列，刷新后玩家仍能再次进入
     * 这三类节点，行动点依旧是唯一的限制资源。</p>
     */
    public void refreshRoute() {
        SegmentPlan plan = generatePlan(runData.getSegment());
        runData.setAvailableOptions(new ArrayList<>(plan.getRouteOptions()));
    }

    /** 按当前段号与剧情线状态生成本段方案（开段与刷新共用，保证两处规则一致）。 */
    private SegmentPlan generatePlan(int segment) {
        return generator.generateSegment(segment, runData.isRocketLineUnlocked(),
                runData.isRocketBossDefeated(), runData.isLegendaryMet(), runData.isPendingLegendary());
    }

    public void setTeam(List<PokemonInstance> team) {
        runData.setTeam(team);
    }

    // ------------------------------------------------------------------
    // 路线节点
    // ------------------------------------------------------------------

    public List<Option> getAvailableOptions() {
        return runData.getAvailableOptions();
    }

    /** 当前待攻略的必然节点；路线探索阶段返回 {@code null}。 */
    public Option getMandatoryOption() {
        return runData.getMandatoryOption();
    }

    public boolean hasAvailableOptions() {
        return !runData.getAvailableOptions().isEmpty();
    }

    /** 本段是否还有下一次进入行动点足够、且（若为一次性节点）尚未走过的路线节点。 */
    public boolean hasSelectableOption() {
        return runData.hasSelectableOption();
    }

    /** 是否处于必然节点阶段（道馆战 / 四天王连打 / 冠军战）。 */
    public boolean isAtMandatoryNode() {
        return runData.getPhase().isMandatoryBattle();
    }

    /**
     * 进入某个路线节点：校验节点合法、可进入（一次性节点未走过）且行动点够用，然后扣除
     * 行动点并把该节点标记为已走过（保留在原位供玩家查看）。
     *
     * <p>常驻节点（路人 / 野外精灵 / 医院）走过后<b>仍可重复进入</b>，行动点是唯一限制；
     * 一次性节点走过后保持灰色不可点。</p>
     *
     * <p>进入节点的同时结算剧情线标记（《需求文档》§5）：进入过火箭队节点即开启后期
     * 「火箭队抓捕神兽」剧情线；神兽偶遇每局至多一次，进入即算已触发。</p>
     *
     * @return 成功返回 true；节点为 null / 一次性节点已走过 / 不属于本段 / 行动点不足时
     *         返回 false 且不改变任何状态
     */
    public boolean consumeNode(Option chosen) {
        if (chosen == null) {
            return false;
        }
        if (chosen.isConsumed() && !chosen.isRepeatable()) {
            return false;
        }
        if (!runData.getAvailableOptions().contains(chosen)) {
            return false;
        }
        int nextAp = runData.getAp() - chosen.apCostForNextEntry();
        if (nextAp < 0) {
            return false;
        }
        runData.setAp(nextAp);
        chosen.markConsumed();
        markStorylineOnEnter(chosen.getType());
        return true;
    }

    /** 进入节点时的剧情线标记结算（§5.1 / §5.2）。 */
    private void markStorylineOnEnter(OptionType type) {
        if (type == null) {
            return;
        }
        switch (type) {
            case ROCKET -> runData.setRocketLineUnlocked(true);
            case LEGENDARY -> {
                runData.setLegendaryMet(true);
                runData.setPendingLegendary(false);
            }
            default -> {
                // 其余节点不影响剧情线
            }
        }
    }

    /**
     * 结算节点的当场效果（不需战斗的部分）：
     * 医院治疗全队、特殊事件发放金币；商店由控制器打开购买界面，战斗节点由控制器拉起战斗。
     */
    public void resolveImmediateEffect(Option option) {
        if (option == null || option.getType() == null) {
            return;
        }
        switch (option.getType()) {
            case HOSPITAL -> healPartyFully();
            case SPECIAL -> runData.addGold(goldRewardFor(OptionType.SPECIAL));
            default -> {
                // 战斗节点由控制器接管；商店由控制器打开购买界面
            }
        }
    }

    /** 医院：全队完全恢复（HP 回满、PP 补满、清除异常，濒死宝可梦复活）。 */
    public void healPartyFully() {
        for (PokemonInstance member : runData.getTeam()) {
            if (member != null && member.getPokemon() != null) {
                member.getPokemon().fullRestore();
            }
        }
    }

    /**
     * 节点自回血（§4.4）：每通过一个节点，未濒死的宝可梦恢复最大 HP 的 1/5；
     * 濒死宝可梦不享受，只能靠医院或道具恢复。
     */
    public void applyNodeHeal() {
        for (PokemonInstance member : runData.getTeam()) {
            if (member == null || member.getPokemon() == null) {
                continue;
            }
            Pokemon pokemon = member.getPokemon();
            if (!pokemon.isFainted()) {
                pokemon.heal(RouteConfig.nodeHealAmount(pokemon.getMaxHp()));
            }
        }
    }

    /**
     * 进入节点并把「不需战斗」的部分一次结算完（扣点 → 当场效果 → 自回血 → 刷新节点 → 阶段推进）。
     * 战斗类节点请改用 {@link #consumeNode(Option)}，以便控制器在战斗结束后再回调
     * {@link #applyNodeHeal()}、{@link #refreshRoute()} 与 {@link #advanceAfterNode()}。
     *
     * @return 扣点成功返回 true
     */
    public boolean resolveNode(Option chosen) {
        if (!consumeNode(chosen)) {
            return false;
        }
        resolveImmediateEffect(chosen);
        applyNodeHeal();
        refreshRoute();
        advanceAfterNode();
        return true;
    }

    /**
     * 节点结束后检查是否该进入必然节点：本段已无节点可走（行动点不足且没有 0 点节点，
     * 例如火箭队线必然触发的神兽偶遇）时触发道馆战（§4.1：行动点耗尽后无法再进入本段
     * 随机节点，直接触发道馆战）。
     *
     * @return 触发了必然节点返回 true
     */
    public boolean advanceAfterNode() {
        if (runData.isGameOver() || runData.isCleared()) {
            return false;
        }
        if (runData.getPhase() != RoutePhase.EXPLORING) {
            return false;
        }
        if (runData.hasSelectableOption()) {
            return false;
        }
        triggerMandatoryNode();
        return true;
    }

    // ------------------------------------------------------------------
    // 必然节点
    // ------------------------------------------------------------------

    /** 触发当前段应到的必然节点：路线探索阶段 → 道馆战。 */
    public Option triggerMandatoryNode() {
        if (runData.getPhase() != RoutePhase.EXPLORING) {
            return runData.getMandatoryOption();
        }
        return enterPhase(RoutePhase.GYM);
    }

    /** 切换到指定必然节点阶段，并生成对应节点。 */
    public Option enterPhase(RoutePhase phase) {
        runData.setPhase(phase);
        runData.setRetryUsed(0);
        Option node = generator.createMandatoryOption(phase, runData.getSegment());
        runData.setMandatoryOption(node);
        return node;
    }

    /**
     * 必然节点胜利后的推进：
     * 道馆战胜利 → 未到末段则进入下一段，已到末段则进入四天王连打；
     * 四天王连打胜利 → 冠军战；
     * 冠军战胜利 → 开启过火箭队剧情线且未提前击败首领则进入首领侵略战，否则通关；
     * 首领侵略战胜利 → 通关。
     */
    public void resolveMandatoryVictory() {
        RoutePhase phase = runData.getPhase();
        switch (phase) {
            case GYM -> {
                runData.addGold(goldRewardFor(OptionType.GYM));
                if (runData.getSegment() < RouteConfig.TOTAL_SEGMENTS) {
                    enterSegment(runData.getSegment() + 1);
                } else {
                    enterPhase(RoutePhase.ELITE_FOUR);
                }
            }
            case ELITE_FOUR -> {
                runData.addGold(goldRewardFor(OptionType.ELITE_FOUR));
                enterPhase(RoutePhase.CHAMPION);
            }
            case CHAMPION -> {
                runData.addGold(goldRewardFor(OptionType.CHAMPION));
                if (runData.isRocketLineUnlocked() && !runData.isRocketBossDefeated()) {
                    // §5.3：进入过火箭队节点却没走抓捕事件 → 击败冠军后触发首领侵略战
                    runData.setAggressionTriggered(true);
                    enterPhase(RoutePhase.ROCKET_INVASION);
                } else {
                    clearRun();
                }
            }
            case ROCKET_INVASION -> {
                runData.addGold(goldRewardFor(OptionType.ROCKET_INVASION));
                clearRun();
            }
            default -> {
                // 非必然节点阶段，无需推进
            }
        }
    }

    /** 结束本轮远征为「通关」。 */
    private void clearRun() {
        runData.setPhase(RoutePhase.CLEARED);
        runData.setCleared(true);
        runData.setMandatoryOption(null);
    }

    /**
     * 火箭队抓捕神兽事件（§5.3）胜利结算：获得大师球（由控制器发放道具），
     * 并把一次 0 点、必然出现的神兽偶遇追加到本段路线中。
     *
     * @return 追加的神兽偶遇节点
     */
    public Option resolveRocketBossVictory() {
        runData.setRocketBossDefeated(true);
        runData.setPendingLegendary(true);
        Option legendary = generator.createLegendary(runData.getSegment(), true);
        runData.getAvailableOptions().add(legendary);
        return legendary;
    }

    /**
     * 必然节点战败后的处理（§4.3）：道馆战 / 四天王可失败一次（大量扣金币），
     * 重试再败 Run 结束；冠军战不可失败，直接结束。
     *
     * @return 还能继续（已消耗这次失败机会并扣金币）返回 true；Run 结束返回 false
     */
    public boolean resolveMandatoryDefeat() {
        RoutePhase phase = runData.getPhase();
        if (phase.allowsOneRetry() && runData.canRetry()) {
            runData.useRetry();
            runData.payGoldPenalty(RouteConfig.mandatoryDefeatGoldPenalty(runData.getSegment()));
            if (runData.getMandatoryOption() == null) {
                enterPhase(phase);
            }
            return true;
        }
        runData.setGameOver(true);
        return false;
    }

    // ------------------------------------------------------------------
    // 金币
    // ------------------------------------------------------------------

    /** 该类型节点胜利的金币奖励（不含惩罚）。 */
    public int goldRewardFor(OptionType type) {
        if (type == null) {
            return 0;
        }
        int segment = runData.getSegment();
        return switch (type) {
            case TRAINER -> RouteConfig.trainerWinGold(segment);
            case WILD -> RouteConfig.wildWinGold(segment);
            case SPECIAL -> RouteConfig.specialGold(segment);
            case ROCKET -> RouteConfig.rocketWinGold(segment);
            case ROCKET_CAPTURE -> RouteConfig.rocketCaptureWinGold(segment);
            case LEGENDARY -> RouteConfig.legendaryWinGold(segment);
            case GYM -> RouteConfig.gymWinGold(segment);
            case ELITE_FOUR -> RouteConfig.eliteFourWinGold(segment);
            case CHAMPION -> RouteConfig.championWinGold(segment);
            case ROCKET_INVASION -> RouteConfig.bossAggressionWinGold(segment);
            case HOSPITAL, SHOP -> 0;
        };
    }

    /** 发放胜利奖励并返回实际发放的金币数。 */
    public int awardWinGold(OptionType type) {
        int reward = goldRewardFor(type);
        runData.addGold(reward);
        return reward;
    }

    /**
     * 战败扣金币（§4.3：路人 / 野外精灵战败仅扣金币、不中断 Run；道馆 / 四天王重试时
     * 大量扣金币）。金币不足时扣到 0，不会变负数。
     *
     * @return 实际扣除的金币数
     */
    public int applyDefeatPenalty(OptionType type) {
        if (type == null) {
            return 0;
        }
        int segment = runData.getSegment();
        int penalty = type.isMandatory()
                ? RouteConfig.mandatoryDefeatGoldPenalty(segment)
                : RouteConfig.defeatGoldPenalty(segment);
        int before = runData.getGold();
        runData.payGoldPenalty(penalty);
        return before - runData.getGold();
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public boolean isGameOver() {
        return runData.isGameOver();
    }

    /** 本轮远征是否已通关。 */
    public boolean isCleared() {
        return runData.isCleared();
    }

    /** 是否已开启火箭队剧情线（进入过任意一次火箭队节点，§5.2）。 */
    public boolean isRocketLineUnlocked() {
        return runData.isRocketLineUnlocked();
    }

    /** 是否已击败火箭队首领（完成抓捕神兽事件，§5.3）。 */
    public boolean isRocketBossDefeated() {
        return runData.isRocketBossDefeated();
    }

    /** 本局是否已触发过神兽偶遇（每局至多一次，§5.1）。 */
    public boolean isLegendaryMet() {
        return runData.isLegendaryMet();
    }

    /** 是否有一次 0 点的神兽偶遇待进入。 */
    public boolean isPendingLegendary() {
        return runData.isPendingLegendary();
    }

    /** 本轮远征是否已彻底结束（通关或战败）。 */
    public boolean isRunFinished() {
        return runData.isGameOver() || runData.isCleared();
    }
}