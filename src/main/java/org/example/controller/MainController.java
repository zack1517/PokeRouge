package org.example.controller;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.example.GameSession;
import org.example.battle.BattleService;
import org.example.battle.BattleServices;
import org.example.config.AppConfig;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.util.LogUtil;
import org.example.view.MainView;
import org.example.view.StarterSelectionView;
import org.example.integration.PokemonBattleAdapter;

import java.util.Optional;

/**
 * 主控制器：负责窗口生命周期、训练家会话，以及 主菜单 ⇄ 战斗 的场景切换。
 *
 * <p>玩家（队伍/背包）在应用生命周期内唯一持有，每次战斗后返回主菜单时重新构建
 * 主视图以反映最新状态。</p>
 */
public class MainController {

    private final Stage stage;
    private GameSession session;
    private Player player;

    public MainController(Stage stage) {
        this.stage = stage;
    }

    /** 游戏第一屏：使用新 pokemon 系统选择初始宝可梦。 */
    public void showStarterSelection() {
        stage.setScene(new StarterSelectionView(this::startWithStarter).createScene());
    }

    private void startWithStarter(String trainerName, org.example.pokemon.domain.Pokemon starter) {
        this.player = PokemonBattleAdapter.createBattlePlayer(trainerName, starter);
        this.session = new GameSession(player);
        showMainMenu();
    }

    /** 显示主菜单（重新构建，反映最新的队伍/背包）。 */
    public void showMainMenu() {
        MainView view = new MainView(player, new MainView.Actions() {
            @Override
            public void onStartBattle() {
                startRandomBattle();
            }

            @Override
            public void onStartRogueFloor() {
                startRogueFloor();
            }

            @Override
            public void onSetActive(int index) {
                session.setActive(index);
                showMainMenu();
            }

            @Override
            public void onHealAll() {
                session.healAll();
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
        }, session.mapBackgroundPath(), session.getSegment());
        stage.setScene(view.createScene());
    }

    /** 进入一场新的随机遭遇战。 */
    public void startRandomBattle() {
        if (!session.hasHealthyPokemon()) {
            infoAlert("没有能战斗的精灵", "队伍已全部倒下，先去治疗队伍吧。");
            return;
        }
        // 尊重玩家在主页设好的先发精灵；仅当当前出战精灵已倒下（或被清空）时才重置为首只健康精灵
        Pokemon active = session.getActive();
        if (active == null || active.isFainted()) {
            session.leadWithFirstHealthy();
        }
        Pokemon lead = session.getActive();
        int level = BattleServices.wildLevelAround(lead.getLevel());
        Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(level);
        if (wild.isEmpty()) {
            infoAlert("数据异常", "没有可遭遇的野生精灵（数据缺失）。");
            return;
        }
        try {
            BattleService engine = BattleServices.newBattle(player, wild.get());
            BattleController battle = new BattleController(engine, this::showMainMenu, session.getSegment());
            stage.setScene(battle.createScene());
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
        }
    }

    public void startRogueFloor() {
        if (session == null || player == null) {
            return;
        }
        if (player.getParty().isEmpty()) {
            infoAlert("队伍为空", "先选择并加入宝可梦再开始肉鸽层内事件。");
            return;
        }
        session.startRogueRun();
        showRogueFloorScene();
    }

    private void showRogueFloorScene() {
        stage.setScene(new org.example.view.RogueFloorView(session, this::handleRogueOption).createScene());
    }

    private void handleRogueOption(org.example.model.Option option) {
        if (session == null || option == null) {
            showMainMenu();
            return;
        }

        if (!session.selectRogueOption(option)) {
            showMainMenu();
            return;
        }

        if (session.isRogueRunFinished()) {
            infoAlert("游戏结束", "你在第 " + session.getRogueRunData().getCurrentFloor() + " 层被击败。");
            showMainMenu();
            return;
        }
        if (session.getRogueRunData().getCurrentPoints() <= 0) {
            startBossBattle();
            return;
        }
        if ("隐藏事件".equals(option.getName())) {
            showRogueFloorScene();
            return;
        }

        switch (option.getType()) {
            case WILD -> startWildEventBattle();
            case ENEMY -> startEnemyEventBattle();
            case HOSPITAL, RANDOM -> showRogueFloorScene();
            default -> showRogueFloorScene();
        }
    }

    private void startWildEventBattle() {
        if (!session.hasHealthyPokemon()) {
            infoAlert("没有能战斗的精灵", "队伍已全部倒下，返回主菜单。");
            showMainMenu();
            return;
        }
        Pokemon lead = session.getActive();
        if (lead == null || lead.isFainted()) {
            session.leadWithFirstHealthy();
            lead = session.getActive();
        }
        int level = org.example.battle.BattleServices.wildLevelAround(lead.getLevel());
        Optional<Pokemon> wild = org.example.battle.BattleServices.randomWild(level);
        if (wild.isEmpty()) {
            infoAlert("遭遇异常", "当前没有可用野生精灵。");
            showRogueFloorScene();
            return;
        }
        org.example.battle.BattleService engine = org.example.battle.BattleServices.newBattle(player, wild.get());
        org.example.controller.BattleController battle = new org.example.controller.BattleController(engine, () -> {
            if (session.getRogueRunData().getCurrentPoints() <= 0) {
                startBossBattle();
                return;
            }
            if (engine.getStatus() == org.example.battle.BattleService.Status.PLAYER_WIN) {
                System.out.println("野怪战斗胜利，继续本层探索。");
            }
            showRogueFloorScene();
        }, session.getSegment());
        stage.setScene(battle.createScene());
    }

    private void startEnemyEventBattle() {
        if (!session.hasHealthyPokemon()) {
            infoAlert("没有能战斗的精灵", "队伍已全部倒下，返回主菜单。");
            showMainMenu();
            return;
        }
        Pokemon lead = session.getActive();
        if (lead == null || lead.isFainted()) {
            session.leadWithFirstHealthy();
            lead = session.getActive();
        }
        int level = Math.max(2, lead.getLevel() + 2 + (int) (Math.random() * 4));
        java.util.List<String> pool = org.example.data.GameData.instance().wildPool();
        if (pool.isEmpty()) {
            infoAlert("训练家对战异常", "没有可用的敌方精灵。");
            showRogueFloorScene();
            return;
        }
        Optional<Pokemon> enemy = org.example.data.GameData.instance().createPokemon(
                pool.get((int) (Math.random() * pool.size())),
                level);
        if (enemy.isEmpty()) {
            infoAlert("训练家对战异常", "没有可用的敌方精灵。");
            showRogueFloorScene();
            return;
        }
        org.example.battle.BattleService engine = org.example.battle.BattleServices.newBattle(player, enemy.get());
        org.example.controller.BattleController battle = new org.example.controller.BattleController(engine, () -> {
            if (session.getRogueRunData().getCurrentPoints() <= 0) {
                startBossBattle();
                return;
            }
            if (engine.getStatus() == org.example.battle.BattleService.Status.PLAYER_WIN) {
                System.out.println("训练家战斗胜利，继续本层探索。");
            }
            showRogueFloorScene();
        }, session.getSegment());
        stage.setScene(battle.createScene());
    }

    private void startBossBattle() {
        if (!session.hasHealthyPokemon()) {
            infoAlert("没有能战斗的精灵", "队伍已全部倒下，返回主菜单。");
            showMainMenu();
            return;
        }
        Pokemon lead = session.getActive();
        if (lead == null || lead.isFainted()) {
            session.leadWithFirstHealthy();
            lead = session.getActive();
        }
        int level = Math.max(5, lead.getLevel() + 4 + (session.getRogueRunData().getCurrentFloor() * 2));
        java.util.List<String> pool = org.example.data.GameData.instance().wildPool();
        String bossId = pool.isEmpty() ? "s_fire_cat" : pool.get((int) (Math.random() * pool.size()));
        Optional<Pokemon> boss = org.example.data.GameData.instance().createPokemon(bossId, level);
        if (boss.isEmpty()) {
            infoAlert("BOSS 对战异常", "没有可用的Boss精灵。");
            showMainMenu();
            return;
        }
        org.example.battle.BattleService engine = org.example.battle.BattleServices.newBattle(player, boss.get());
        org.example.controller.BattleController battle = new org.example.controller.BattleController(engine, () -> {
            if (engine.getStatus() == org.example.battle.BattleService.Status.PLAYER_WIN) {
                int nextFloor = session.getRogueRunData().getCurrentFloor() + 1;
                session.enterRogueFloor(nextFloor);
                infoAlert("BOSS 战胜利", "你已通过第 " + (nextFloor - 1) + " 层，进入第 " + nextFloor + " 层。");
                showRogueFloorScene();
            } else {
                session.getRogueRunData().setGameOver(true);
                infoAlert("BOSS 战失败", "队伍被击败，游戏结束。");
                showMainMenu();
            }
        }, session.getSegment());
        stage.setScene(battle.createScene());
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
