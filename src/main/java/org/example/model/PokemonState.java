package org.example.model;

import java.io.Serializable;

/**
 * 存档状态快照（玩家队伍 + 时间戳）。
 * <p>契约：接口文档 v1.0 §3.3。提供存档模块使用。</p>
 */
public class PokemonState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Player player;
    private final long timestamp;

    public PokemonState(Player player, long timestamp) {
        this.player = player;
        this.timestamp = timestamp;
    }

    public Player getPlayer() { return player; }
    public long getTimestamp() { return timestamp; }
}
