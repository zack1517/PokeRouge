package org.example;

import org.example.growth.GrowthProgress;
import org.example.model.Option;
import org.example.model.OptionType;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.PokemonInstance;
import org.example.model.RogueTurnManager;
import org.example.model.RunData;

import java.util.List;

/**
 * 根包级会话对象：负责持有当前玩家状态，并充当 UI/控制器与底层模型之间的编排中心。
 * <p>此对象不关心具体玩法细节，只负责管理玩家会话、队伍切换和恢复等总流程，
 * 真正的数据/规则实现仍由子包提供。</p>
 * <p>地图段与地图背景属“对局进度”：段号的唯一来源是 {@link RunData#getSegment()}，
 * 当前段背景与段号绑定 —— 同段内多次返回主菜单（多次重建视图）保持同一张图，
 * 只有换段才重抽（见 {@link #enterRogueSegment(int)} / {@link #enterNextSegment()}）。</p>
 * <p>肉鸽路线接口（{@link #startRogueRun()} 等）由 {@link RogueTurnManager} 承载段号 / 节点 /
 * 行动点 / 金币逻辑，已接入主流程：主菜单「进入路线节点」→ {@link org.example.view.RogueFloorView}
 * 节点页 → 真实战斗（战斗节点先经 {@link #enterRogueNode(Option)} 扣行动点，再由控制器接管战斗）。</p>
 */
public class GameSession {

    /** 地图背景候选（classpath；换段时随机取一）。 */
    private static final String[] MAP_BACKGROUNDS = {
            "/images/background/bg_map1.jpeg",
            "/images/background/bg_map2.jpeg",
            "/images/background/bg_map3.jpeg"};

    private final Player player;

    /** 局外成长进度（图鉴：种族捕捉次数 / 对战次数 / 个体值加成），跨单轮远征存活。 */
    private final GrowthProgress growthProgress;

    /** 肉鸽流程管理器（旧版恢复：楼层/事件/点数的会话入口）。 */
    private final RogueTurnManager rogueTurnManager = new RogueTurnManager();

    /** 当前段已确定的地图背景（null = 尚未抽取）；与段号同步变化。 */
    private String mapBackground;

    public GameSession(Player player) {
        this(player, GrowthProgress.instance());
    }

    /**
     * 以指定成长进度构造会话（便于隔离测试）。
     *
     * @param player         玩家
     * @param growthProgress 局外成长进度，不可为 {@code null}
     */
    public GameSession(Player player, GrowthProgress growthProgress) {
        this.player = player;
        this.growthProgress = java.util.Objects.requireNonNull(growthProgress, "growthProgress");
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 局外成长进度：图鉴数据接口，可在主菜单等非战斗界面查询。
     *
     * <p>可查：某种族的累计捕捉次数、累计对战次数、个体值加成，以及全局个体值加成
     * （后续遭遇的所有精灵都会获得该加成）。</p>
     *
     * @return 成长进度，永不为 {@code null}
     */
    public GrowthProgress getGrowthProgress() {
        return growthProgress;
    }

    /**
     * 当前地图段号（第 1 段起）。
     *
     * <p>段号的唯一来源是 {@link RunData#getSegment()}：未开始本轮远征时视为第 1 段，
     * 避免主菜单在开局前显示「第 0 段」。</p>
     */
    public int getSegment() {
        int current = rogueTurnManager.getRunData().getSegment();
        return current < RunData.FIRST_SEGMENT ? RunData.FIRST_SEGMENT : current;
    }

    /**
     * 直接设置地图段号（读档还原用）。与 {@link #enterNextSegment()} 不同：不推进、不重抽背景，
     * 仅把段号置为指定值（小于 1 视为 1），也不会重置行动点与节点。
     */
    public void setSegment(int segment) {
        rogueTurnManager.getRunData().setSegment(Math.max(RunData.FIRST_SEGMENT, segment));
    }

    /** 当前段已确定的地图背景；{@code null} 表示尚未抽取（读档时若存档未记录背景则为 null）。 */
    public String getMapBackground() {
        return mapBackground;
    }

    /** 直接设置当前段地图背景（读档还原用），{@code null} 表示回到「尚未抽取」状态。 */
    public void setMapBackground(String mapBackground) {
        this.mapBackground = mapBackground;
    }

    /**
     * 进入下一段：段号 +1 并立即重抽地图背景（不会与上一段同图）。
     * 同段内不调用 → 背景保持不变（“一轮战斗后未进下一阶段不换背景”的语义来源）。
     *
     * <p>注意：本方法只负责「地图段号 + 背景」，行动点重置与节点重抽由
     * {@link #enterRogueSegment(int)} 统一完成；正常推进请用后者。</p>
     */
    public void enterNextSegment() {
        setSegment(getSegment() + 1);
        mapBackground = nextMapBackground();
    }

    /**
     * 当前段的地图背景 classpath：段内惰性随机一次并缓存，后续调用返回同一张
     * （主菜单每次重建都调用本方法，保证同段背景稳定）。
     */
    public String mapBackgroundPath() {
        if (mapBackground == null) {
            mapBackground = nextMapBackground();
        }
        return mapBackground;
    }

    /** 从候选图随机取一；已有当前图时避开（避免换段后与上一段同图，3 张候选足够重抽）。 */
    private String nextMapBackground() {
        String pick;
        do {
            pick = MAP_BACKGROUNDS[(int) (Math.random() * MAP_BACKGROUNDS.length)];
        } while (MAP_BACKGROUNDS.length > 1 && pick.equals(mapBackground));
        return pick;
    }

    public void setActive(int index) {
        player.setActive(index);
    }

    public boolean hasHealthyPokemon() {
        return player.hasHealthyPokemon();
    }

    public Pokemon getActive() {
        return player.getActive();
    }

    public void leadWithFirstHealthy() {
        player.leadWithFirstHealthy();
    }

    public boolean isPlayerReadyForBattle() {
        return hasHealthyPokemon();
    }

    // ==================== 肉鸽路线接口（节点 / 行动点 / 金币，供路线 UI 使用） ====================

    /**
     * 以当前队伍开始一轮远征：从第 1 段起，队伍快照进 {@link RogueTurnManager}，
     * 行动点重置为本段上限、金币重置为初始值、地图背景按新段重抽。
     */
    public void startRogueRun() {
        if (player == null || player.getParty().isEmpty()) {
            return;
        }
        List<PokemonInstance> team = player.getParty().stream()
                .map(PokemonInstance::new)
                .toList();
        rogueTurnManager.startRun(team);
        mapBackground = nextMapBackground();
    }

    /**
     * 把当前队伍重新快照进肉鸽轮次。
     *
     * <p>{@code RunData.team} 只在开局时快照一次，而战斗直接读写唯一持有的 {@link Player}，
     * 因此肉鸽进行中新捕捉/新入队的精灵不会出现在旧快照里。事件结算（尤其是急救站的
     * 全队恢复）与 BOSS 战力评估都读取该快照，故结算前必须重新同步，否则新精灵会被漏掉。</p>
     */
    public void syncRogueTeam() {
        if (player == null || player.getParty().isEmpty()) {
            return;
        }
        rogueTurnManager.setTeam(player.getParty().stream()
                .map(PokemonInstance::new)
                .toList());
    }

    /**
     * 进入指定段：重新生成路线节点、行动点重置为该段上限、阶段回到路线探索，
     * 并在段号变化时重抽地图背景。这是正常推进段的统一入口
     * （道馆战胜利后由控制器调用）。
     */
    public void enterRogueSegment(int segment) {
        int previous = getSegment();
        rogueTurnManager.enterSegment(segment);
        if (getSegment() != previous) {
            mapBackground = nextMapBackground();
        }
    }

    /**
     * 进入某个路线节点：校验并扣除行动点、把节点标记为已走。
     * 战斗型节点（路人 / 野外精灵 / 道馆 / 四天王 / 冠军）由 UI 扣点后接管为真实战斗。
     *
     * @return 扣点成功返回 {@code true}；节点已走过、不属于本段或行动点不足返回 {@code false}
     */
    public boolean enterRogueNode(Option option) {
        syncRogueTeam();
        return rogueTurnManager.consumeNode(option);
    }

    /** 结算节点中不需战斗的当场效果（医院治疗全队、特殊事件发放金币）。 */
    public void resolveRogueOptionEffect(Option option) {
        syncRogueTeam();
        rogueTurnManager.resolveImmediateEffect(option);
    }

    /** 节点自回血：未濒死的宝可梦恢复最大 HP 的 1/5（§4.4）。 */
    public void applyRogueNodeHeal() {
        syncRogueTeam();
        rogueTurnManager.applyNodeHeal();
    }

    /**
     * 节点结束后检查是否该进入本段必然节点（道馆战）。
     *
     * @return 触发了道馆战返回 {@code true}
     */
    public boolean advanceRogueNode() {
        return rogueTurnManager.advanceAfterNode();
    }

    /**
     * 刷新本段路线节点：每走完一个路线节点后重新随机生成一批节点（行动点、阶段与金币不变）。
     * 常驻节点（路人 / 野外精灵 / 医院）每次都必然入列，因此刷新后仍可再次进入。
     */
    public void refreshRogueRoute() {
        rogueTurnManager.refreshRoute();
    }

    /** 发放节点胜利的金币奖励，返回实际发放数额。 */
    public int awardRogueWinGold(OptionType type) {
        return rogueTurnManager.awardWinGold(type);
    }

    /** 按失败规则扣金币，返回实际扣除数额（金币不足时扣到 0）。 */
    public int applyRogueDefeatPenalty(OptionType type) {
        return rogueTurnManager.applyDefeatPenalty(type);
    }

    /** 必然节点（道馆战）当前待攻略节点；路线探索阶段为 {@code null}。 */
    public Option getRogueMandatoryOption() {
        return rogueTurnManager.getMandatoryOption();
    }

    /**
     * 必然节点胜利推进：道馆 → 下一段 / 四天王，四天王 → 冠军，
     * 冠军 → 通关（或已开启火箭队剧情线时的首领侵略战），首领侵略战 → 通关。
     *
     * <p>必然节点的胜利金币与阶段流转都在本方法内一次结算，控制器<b>不要</b>再单独调用
     * {@link #awardRogueWinGold(OptionType)}，否则金币会重复发放。段号变化时会重抽地图背景
     * （与 {@link #enterRogueSegment(int)} 一致）。</p>
     */
    public void resolveRogueMandatoryVictory() {
        int previous = getSegment();
        rogueTurnManager.resolveMandatoryVictory();
        if (getSegment() != previous) {
            mapBackground = nextMapBackground();
        }
    }

    /**
     * 必然节点战败处理：道馆 / 四天王可失败一次（重试再败结束），冠军不可失败。
     *
     * @return 还能继续挑战返回 {@code true}；本轮远征结束返回 {@code false}
     */
    public boolean resolveRogueMandatoryDefeat() {
        return rogueTurnManager.resolveMandatoryDefeat();
    }

    /** 标记本轮远征结束（如 UI 接管的真实战斗失败时），供结算流程与视图判定。 */
    public void endRogueRun() {
        rogueTurnManager.getRunData().setGameOver(true);
    }

    /** 当前肉鸽轮次数据（段号、行动点、阶段、金币、可用节点等）。 */
    public RunData getRogueRunData() {
        return rogueTurnManager.getRunData();
    }

    /**
     * 当前可选的路线节点列表（含已走过的节点：一次性节点由 {@link Option#isConsumed()} 标记且
     * 不可再进，常驻节点走过后仍可重复进入）。
     */
    public List<Option> getRogueOptions() {
        return rogueTurnManager.getAvailableOptions();
    }

    /** 本轮远征是否已结束（通关或战败）。 */
    public boolean isRogueRunFinished() {
        return rogueTurnManager.isRunFinished();
    }

    /** 本轮远征是否已通关。 */
    public boolean isRogueRunCleared() {
        return rogueTurnManager.isCleared();
    }

    // ==================== 火箭队剧情线与神兽偶遇（《需求文档》§五） ====================

    /** 是否已进入过任意一次火箭队节点（开启后期「火箭队抓捕神兽」剧情线）。 */
    public boolean isRogueRocketLineUnlocked() {
        return rogueTurnManager.isRocketLineUnlocked();
    }

    /** 是否已击败火箭队首领（完成抓捕神兽事件并获得大师球）。 */
    public boolean isRogueRocketBossDefeated() {
        return rogueTurnManager.isRocketBossDefeated();
    }

    /** 本局是否已触发过神兽偶遇（每局至多一次）。 */
    public boolean isRogueLegendaryMet() {
        return rogueTurnManager.isLegendaryMet();
    }

    /** 是否有一次 0 点的神兽偶遇待进入。 */
    public boolean isRoguePendingLegendary() {
        return rogueTurnManager.isPendingLegendary();
    }

    /** 首领侵略战是否已触发。 */
    public boolean isRogueAggressionTriggered() {
        return rogueTurnManager.getRunData().isAggressionTriggered();
    }

    /**
     * 火箭队抓捕神兽事件胜利结算（§5.3）：标记首领已被击败，并把一次 0 点、
     * 必然出现的神兽偶遇追加到本段路线中。
     *
     * @return 追加的神兽偶遇节点
     */
    public Option resolveRogueRocketBossVictory() {
        return rogueTurnManager.resolveRocketBossVictory();
    }
}
