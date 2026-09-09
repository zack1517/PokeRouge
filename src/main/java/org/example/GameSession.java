package org.example;

import org.example.model.Player;
import org.example.model.Pokemon;

/**
 * 根包级会话对象：负责持有当前玩家状态，并充当 UI/控制器与底层模型之间的编排中心。
 * <p>此对象不关心具体玩法细节，只负责管理玩家会话、队伍切换和恢复等总流程，
 * 真正的数据/规则实现仍由子包提供。</p>
 * <p>地图段与地图背景属“对局进度”（肉鸽流程系统接入前的本地占位）：当前段背景与段号绑定 ——
 * 同段内多次返回主菜单（多次重建视图）保持同一张图，只有 {@link #enterNextSegment()} 换段才重抽。
 * 流程系统接入后由它调用 enterNextSegment 驱动段号，届时可移除段恒 1 的限制。</p>
 */
public class GameSession {

    /** 地图背景候选（classpath；换段时随机取一）。 */
    private static final String[] MAP_BACKGROUNDS = {
            "/images/background/bg_map1.jpeg",
            "/images/background/bg_map2.jpeg",
            "/images/background/bg_map3.jpeg"};

    private final Player player;

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
}
