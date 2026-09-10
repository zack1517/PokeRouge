package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 存档状态，保存玩家数据及其保存时间。
 */
public class PokemonState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Player player;
    private final long timestamp;

    public PokemonState(Player player, long timestamp) {
        this.player = player;
        this.timestamp = timestamp;
    }

    public Player getPlayer() {
        return player;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
