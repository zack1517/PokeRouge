package org.example;

import org.example.model.Player;
import org.example.model.Pokemon;

/**
 * 根包级会话对象：负责持有当前玩家状态，并充当 UI/控制器与底层模型之间的编排中心。
 * <p>此对象不关心具体玩法细节，只负责管理玩家会话、队伍切换和恢复等总流程，
 * 真正的数据/规则实现仍由子包提供。</p>
 */
public class GameSession {

    private final Player player;

    public GameSession(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
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
