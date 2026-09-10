package org.example;

import org.example.model.Option;
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
 * <p>地图段与地图背景属“对局进度”（肉鸽流程系统接入前的本地占位）：当前段背景与段号绑定 ——
 * 同段内多次返回主菜单（多次重建视图）保持同一张图，只有 {@link #enterNextSegment()} 换段才重抽。
 * 流程系统接入后由它调用 enterNextSegment 驱动段号，届时可移除段恒 1 的限制。</p>
 * <p>肉鸽流程接口（{@link #startRogueRun()} 等）由 {@link RogueTurnManager} 承载楼层/事件/点数逻辑，
 * 已接入主流程：主菜单「进入层内事件」→ {@link org.example.view.RogueFloorView} 选项页 → 真实战斗
 * （WILD/ENEMY 先经 {@link #consumeRogueOption(Option)} 扣点，再由控制器接管战斗）。</p>
 */
public class GameSession {

    /** 地图背景候选（classpath；换段时随机取一）。 */
    private static final String[] MAP_BACKGROUNDS = {
            "/images/background/bg_map1.jpeg",
            "/images/background/bg_map2.jpeg",
            "/images/background/bg_map3.jpeg"};

    private final Player player;

    /** 肉鸽流程管理器（旧版恢复：楼层/事件/点数的会话入口）。 */
    private final RogueTurnManager rogueTurnManager = new RogueTurnManager();

    /** 当前地图段号（从第 1 段起；流程系统接入前不自动推进，见类 javadoc）。 */
    private int segment = 1;

    /** 当前段已确定的地图背景（null = 尚未抽取）；与段号同步变化。 */
    private String mapBackground;

    public GameSession(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    /** 当前地图段号（第 1 段起）。 */
    public int getSegment() {
        return segment;
    }

    /**
     * 进入下一段：段号 +1 并立即重抽地图背景（不会与上一段同图）。
     * 同段内不调用 → 背景保持不变（“一轮战斗后未进下一阶段不换背景”的语义来源）。
     * 由肉鸽流程系统在推进段时调用（TODO 接入前无调用方）。
     */
    public void enterNextSegment() {
        segment++;
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

    public void healAll() {
        player.healParty();
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

    // ==================== 肉鸽流程接口（旧版恢复，供 RogueFloorView 等肉鸽 UI 使用） ====================

    /** 以当前队伍开始一轮肉鸽：从第 1 层起，队伍快照进 RogueTurnManager。 */
    public void startRogueRun() {
        if (player == null || player.getParty().isEmpty()) {
            return;
        }
        List<PokemonInstance> team = player.getParty().stream()
                .map(PokemonInstance::new)
                .toList();
        rogueTurnManager.startRun(team, 1);
    }

    /** 进入指定楼层，生成该层事件选项。 */
    public void enterRogueFloor(int floor) {
        rogueTurnManager.enterFloor(floor);
    }

    /** 选择肉鸽事件选项；返回 false 表示本轮已结束（点数耗尽等）。 */
    public boolean selectRogueOption(Option option) {
        if (rogueTurnManager.isGameOver()) {
            return false;
        }
        rogueTurnManager.selectOption(option);
        return !rogueTurnManager.isGameOver();
    }

    /**
     * 只扣除选项点数并更新楼层选项（不结算事件效果、不自动触发 BOSS）：
     * WILD/ENEMY 等战斗型事件由 UI 扣点后接管为真实战斗。
     *
     * @return 扣点成功返回 {@code true}；隐藏事件、非法选项或点数不足返回 {@code false}
     */
    public boolean consumeRogueOption(Option option) {
        return rogueTurnManager.consumeOption(option);
    }

    /** 执行选项的事件效果结算（HOSPITAL/RANDOM 等直接效果事件由 UI 扣点后调用）。 */
    public void resolveRogueOptionEffect(Option option) {
        rogueTurnManager.resolveOptionEffect(option);
    }

    /** 标记本轮肉鸽结束（如 UI 接管的真实战斗失败时），供结算流程与视图判定。 */
    public void endRogueRun() {
        rogueTurnManager.getRunData().setGameOver(true);
    }

    /** 强制进入 BOSS 战（点数耗尽时由流程触发）。 */
    public void triggerRogueBossFight() {
        rogueTurnManager.triggerBossFight();
    }

    /** 当前肉鸽轮次数据（楼层、点数、可用选项等）。 */
    public RunData getRogueRunData() {
        return rogueTurnManager.getRunData();
    }

    /** 当前可选的肉鸽事件列表。 */
    public List<Option> getRogueOptions() {
        return rogueTurnManager.getAvailableOptions();
    }

    /** 本轮肉鸽是否已结束。 */
    public boolean isRogueRunFinished() {
        return rogueTurnManager.isGameOver();
    }
}
