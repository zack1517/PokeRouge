package org.example.controller;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.example.battle.BattleService;
import org.example.battle.BattleServices;
import org.example.config.AppConfig;
import org.example.data.GameData;
import org.example.model.Bag;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.util.LogUtil;
import org.example.view.MainView;

import java.util.Optional;

/**
 * 主控制器：负责窗口生命周期、训练家会话，以及 主菜单 ⇄ 战斗 的场景切换。
 *
 * <p>玩家（队伍/背包）在应用生命周期内唯一持有，每次战斗后返回主菜单时重新构建
 * 主视图以反映最新状态。</p>
 */
public class MainController {

    private final Stage stage;
    private final Player player;

    public MainController(Stage stage) {
        this.stage = stage;
        this.player = createStarterPlayer();
    }

    /** 组装玩家开局状态：初始精灵与背包补给。 */
    private Player createStarterPlayer() {
        Player p = new Player(AppConfig.PLAYER_NAME);
        GameData data = GameData.instance();
        data.createPokemon("s_fire_cat", 5).ifPresent(p::addToParty);
        data.createPokemon("s_leaf_chick", 5).ifPresent(p::addToParty);
        Bag bag = p.getBag();
        bag.add(data.item("i_potion"), 5);
        bag.add(data.item("i_super_potion"), 2);
        bag.add(data.item("i_poke_ball"), 6);
        bag.add(data.item("i_great_ball"), 3);
        return p;
    }

    /** 显示主菜单（重新构建，反映最新的队伍/背包）。 */
    public void showMainMenu() {
        MainView view = new MainView(player, new MainView.Actions() {
            @Override
            public void onStartBattle() {
                startRandomBattle();
            }

            @Override
            public void onSetActive(int index) {
                player.setActive(index);
                showMainMenu();
            }

            @Override
            public void onHealAll() {
                player.healParty();
                showMainMenu();
            }

            @Override
            public void onExit() {
                if (confirmExit()) {
                    Platform.exit();
                } else {
                    showMainMenu();
                }
            }
        });
        stage.setScene(view.createScene());
    }

    /** 进入一场新的随机遭遇战。 */
    public void startRandomBattle() {
        if (!player.hasHealthyPokemon()) {
            infoAlert("没有能战斗的精灵", "队伍已全部倒下，先去治疗队伍吧。");
            return;
        }
        player.leadWithFirstHealthy();
        Pokemon lead = player.getActive();
        int level = BattleServices.wildLevelAround(lead.getLevel());
        Optional<Pokemon> wild = BattleServices.randomWild(level);
        if (wild.isEmpty()) {
            infoAlert("数据异常", "没有可遭遇的野生精灵（数据缺失）。");
            return;
        }
        try {
            BattleService engine = BattleServices.newBattle(player, wild.get());
            BattleController battle = new BattleController(engine, this::showMainMenu);
            stage.setScene(battle.createScene());
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
        }
    }

    /** 绑定窗口事件：关闭确认与生命周期日志。 */
    public void bindStageEvents() {
        // 窗口关闭时先确认
        stage.setOnCloseRequest(event -> {
            event.consume();
            if (confirmExit()) {
                Platform.exit();
            }
        });

        stage.setOnHiding(e -> LogUtil.info("setOnHiding...."));
        stage.setOnHidden(e -> LogUtil.info("setOnHidden...."));
        stage.setOnShowing(e -> LogUtil.info("setOnShowing....."));
        stage.setOnShown(e -> LogUtil.info("setOnShown....."));
    }

    private boolean confirmExit() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(AppConfig.EXIT_CONFIRM_TITLE);
        alert.setHeaderText(null);
        alert.setContentText(AppConfig.EXIT_CONFIRM_CONTENT);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void infoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
