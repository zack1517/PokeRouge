package org.example.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次远征（肉鸽路线）的运行时状态。
 *
 * <p>字段含义对应《需求文档》§4：</p>
 * <ul>
 *   <li>{@link #getSegment() 段号} —— 段号是「现在走到第几段」的唯一来源（1 起；0 表示
 *       尚未开始本轮远征），每段开始时行动点按 {@link RouteConfig#apLimitForSegment(int)}
 *       重置；</li>
 *   <li>{@link #getAp() 剩余行动点} / {@link #getApMax() 本段上限} —— 自主规划路线的额度，
 *       耗尽后必然触发道馆战；</li>
 *   <li>{@link #getPhase() 阶段} —— 决定当前可走节点与必然节点（道馆 / 四天王 / 冠军）；</li>
 *   <li>{@link #getGold() 金币} —— 商店流通货币，节点胜负的唯一奖惩对象；</li>
 *   <li>{@link #getRetryUsed() 已用重试次数} —— 道馆 / 四天王可失败一次（§4.3）。</li>
 * </ul>
 */
public class RunData {

    /** 段号起点。 */
    public static final int FIRST_SEGMENT = 1;

    private List<PokemonInstance> team = new ArrayList<>();
    private int segment;
    private int ap;
    private int apMax = RouteConfig.apLimitForSegment(FIRST_SEGMENT);
    private RoutePhase phase = RoutePhase.EXPLORING;
    private int retryUsed;
    private int gold = RouteConfig.STARTING_GOLD;
    private boolean isGameOver;
    private boolean cleared;
    private List<Option> availableOptions = new ArrayList<>();
    private Option mandatoryOption;
    private boolean rocketLineUnlocked;
    private boolean rocketBossDefeated;
    private boolean legendaryMet;
    private boolean pendingLegendary;
    private boolean aggressionTriggered;

    public RunData() {
    }

    public RunData(List<PokemonInstance> team, int segment, int ap, boolean isGameOver) {
        this.team = team == null ? new ArrayList<>() : new ArrayList<>(team);
        this.segment = segment;
        this.ap = ap;
        this.apMax = segment <= 0 ? RouteConfig.apLimitForSegment(FIRST_SEGMENT)
                : RouteConfig.apLimitForSegment(segment);
        this.isGameOver = isGameOver;
    }

    public List<PokemonInstance> getTeam() {
        return team;
    }

    public void setTeam(List<PokemonInstance> team) {
        this.team = team == null ? new ArrayList<>() : new ArrayList<>(team);
    }

    /** 当前段号（1 起；0 表示尚未开始）。 */
    public int getSegment() {
        return segment;
    }

    public void setSegment(int segment) {
        this.segment = segment;
    }

    /** 本轮远征是否已经开始。 */
    public boolean isNotStarted() {
        return segment < FIRST_SEGMENT;
    }

    /** 当前剩余行动点。 */
    public int getAp() {
        return ap;
    }

    public void setAp(int ap) {
        this.ap = Math.max(0, ap);
    }

    /** 本段行动点上限（随段推进提升）。 */
    public int getApMax() {
        return apMax;
    }

    /** 上限至少为 1，避免存档或外部传入 0 导致界面显示异常。 */
    public void setApMax(int apMax) {
        this.apMax = Math.max(1, apMax);
    }

    /** 当前推进阶段。 */
    public RoutePhase getPhase() {
        return phase == null ? RoutePhase.EXPLORING : phase;
    }

    public void setPhase(RoutePhase phase) {
        this.phase = phase == null ? RoutePhase.EXPLORING : phase;
    }

    /** 本段内已使用过的「失败一次」机会次数（道馆 / 四天王各允许 1 次）。 */
    public int getRetryUsed() {
        return retryUsed;
    }

    public void setRetryUsed(int retryUsed) {
        this.retryUsed = Math.max(0, retryUsed);
    }

    /** 当前阶段战败后是否还能重试（§4.3）。 */
    public boolean canRetry() {
        return getPhase().allowsOneRetry() && retryUsed < 1;
    }

    /** 消耗一次重试机会。 */
    public void useRetry() {
        retryUsed++;
    }

    /** 金币余额，不会为负。 */
    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = Math.max(0, gold);
    }

    /** 获得金币。 */
    public void addGold(int amount) {
        if (amount > 0) {
            this.gold += amount;
        }
    }

    /**
     * 扣除金币。
     *
     * @return 金币足够并完成扣除返回 {@code true}；余额不足时不扣除并返回 {@code false}
     */
    public boolean spendGold(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (gold < amount) {
            return false;
        }
        gold -= amount;
        return true;
    }

    /** 按惩罚规则扣金币，余额不足时扣到 0（不会变负数）。 */
    public void payGoldPenalty(int amount) {
        if (amount > 0) {
            gold = Math.max(0, gold - amount);
        }
    }

    public boolean isGameOver() {
        return isGameOver;
    }

    public void setGameOver(boolean gameOver) {
        isGameOver = gameOver;
    }

    /** 是否已通关（打完冠军战）。 */
    public boolean isCleared() {
        return cleared;
    }

    public void setCleared(boolean cleared) {
        this.cleared = cleared;
    }

    /** 本段可自由选择的路线节点。 */
    public List<Option> getAvailableOptions() {
        return availableOptions;
    }

    public void setAvailableOptions(List<Option> availableOptions) {
        this.availableOptions = availableOptions == null ? new ArrayList<>() : new ArrayList<>(availableOptions);
    }

    /** 当前待攻略的必然节点（道馆 / 四天王 / 冠军）；处于路线探索阶段时为 {@code null}。 */
    public Option getMandatoryOption() {
        return mandatoryOption;
    }

    public void setMandatoryOption(Option mandatoryOption) {
        this.mandatoryOption = mandatoryOption;
    }

    /**
     * 本段是否还有可走的路线节点。
     *
     * <p>一次性节点必须未走过、常驻节点（{@link Option#isRepeatable()}）走过也可再走，
     * 两者都以「下一次进入的行动点消耗 ≤ 剩余行动点」为准。</p>
     */
    public boolean hasSelectableOption() {
        for (Option option : availableOptions) {
            if (option == null) {
                continue;
            }
            if (option.isConsumed() && !option.isRepeatable()) {
                continue;
            }
            if (option.apCostForNextEntry() <= ap) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 火箭队剧情线与神兽偶遇（《需求文档》§五）
    // ------------------------------------------------------------------

    /** 是否进入过任意一次火箭队节点——进入即开启后期「火箭队抓捕神兽」剧情线（§5.2）。 */
    public boolean isRocketLineUnlocked() {
        return rocketLineUnlocked;
    }

    public void setRocketLineUnlocked(boolean rocketLineUnlocked) {
        this.rocketLineUnlocked = rocketLineUnlocked;
    }

    /** 是否已击败火箭队首领（完成抓捕神兽事件并获得大师球，§5.3）。 */
    public boolean isRocketBossDefeated() {
        return rocketBossDefeated;
    }

    public void setRocketBossDefeated(boolean rocketBossDefeated) {
        this.rocketBossDefeated = rocketBossDefeated;
    }

    /** 本局是否已经触发过神兽偶遇（每局至多一次，含大师球必然触发的那次，§5.1）。 */
    public boolean isLegendaryMet() {
        return legendaryMet;
    }

    public void setLegendaryMet(boolean legendaryMet) {
        this.legendaryMet = legendaryMet;
    }

    /** 是否有一次「必然触发」的神兽偶遇待进入（火箭队线击败首领后置位，消耗为 0 点）。 */
    public boolean isPendingLegendary() {
        return pendingLegendary;
    }

    public void setPendingLegendary(boolean pendingLegendary) {
        this.pendingLegendary = pendingLegendary;
    }

    /** 首领侵略战是否已触发（用于结局文案判定）。 */
    public boolean isAggressionTriggered() {
        return aggressionTriggered;
    }

    public void setAggressionTriggered(boolean aggressionTriggered) {
        this.aggressionTriggered = aggressionTriggered;
    }
}
