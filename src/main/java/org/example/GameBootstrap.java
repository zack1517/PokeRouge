package org.example;

import org.example.config.AppConfig;
import org.example.data.GameData;
import org.example.model.Bag;
import org.example.model.Player;

/**
 * 根包启动器：负责组装一次游戏会话的初始状态，并统一对外暴露入口。
 * <p>这里扮演“总编排器”的角色：UI/控制器和各模块在本层协同，而具体实体与数据由
 * 子包实现。这样保证整个项目有一个清晰的启动入口，而不是让分支模块各自自己跑。</p>
 */
public final class GameBootstrap {

    private GameBootstrap() {
    }

    public static Player createStarterPlayer() {
        Player player = new Player(AppConfig.PLAYER_NAME);
        GameData data = GameData.instance();
        for (String speciesId : data.starterPool()) {
            data.createPokemon(speciesId, 5).ifPresent(player::addPokemon);
        }
        Bag bag = player.getBag();
        bag.add(data.item("i_potion"), 5);
        bag.add(data.item("i_super_potion"), 2);
        bag.add(data.item("i_poke_ball"), 6);
        bag.add(data.item("i_great_ball"), 3);
        return player;
    }
}
