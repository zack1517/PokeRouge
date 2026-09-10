package org.example.controller;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Stage;
import org.example.GameSession;
import org.example.battle.BattleDataPort;
import org.example.battle.BattleService;
import org.example.battle.BattleServices;
import org.example.config.AppConfig;
import org.example.model.Option;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.RunData;
import org.example.model.Trainer;
import org.example.util.LogUtil;
import org.example.util.MusicPlayer;
import org.example.view.CustomBattleSetupView;
import org.example.view.CustomBattleView;
import org.example.view.MainView;
import org.example.view.RogueFloorView;
import org.example.view.StarterSelectionView;
import org.example.view.StartView;
import org.example.integration.PokemonBattleAdapter;
import org.example.integration.WildEncounter;

import java.util.List;
import java.util.Optional;

/**
 * 主控制器：负责窗口生命周期、训练家会话，以及 主菜单 ⇄ 战斗/肉鸽楼层 的场景切换。
 *
 * <p>玩家（队伍/背包）在应用生命周期内唯一持有，每次战斗后返回主菜单时重新构建
 * 主视图以反映最新状态。</p>
 *
 * <p>肉鸽流程：主菜单「进入层内事件」→ 楼层选项页（点数/事件）→ WILD/ENEMY 走真实战斗、
 * HOSPITAL/RANDOM 当场结算 → 点数耗尽与楼层 BOSS 决战 → 胜利推进下一层（地图段号同步）。</p>
 */
public class MainController {

    private final Stage stage;
    private GameSession session;
    private Player player;

    public MainController(Stage stage) {
        this.stage = stage;
    }

    /**
     * 组装层统一入口：创建野生战引擎并注入数据端口与成长端口。
     *
     * <p>成长端口由外部成长模块实现（经验 / 升级 / 学招 / 进化判定），战斗模块自身不承担成长规则。</p>
     */
    private static BattleService newWildBattle(Player player, Pokemon wild) {
        BattleDataPort dataPort = PokemonBattleAdapter.battleDataPort();
        return BattleServices.newBattle(player, wild, dataPort,
                PokemonBattleAdapter.battleGrowthPort(dataPort));
    }

    /** 组装层统一入口：创建训练师轮战引擎并注入数据端口与成长端口。 */
    private static BattleService newTrainerBattle(Player player, Trainer trainer) {
        BattleDataPort dataPort = PokemonBattleAdapter.battleDataPort();
        return BattleServices.newTrainerBattle(player, trainer, dataPort,
                PokemonBattleAdapter.battleGrowthPort(dataPort));
    }

    /** 游戏第一屏：启动页（「开始游戏」进入初始宝可梦选择；「自定义战斗」进入模式选择页；其余各项为预留入口）。 */
    public void showStartScreen() {
        MusicPlayer.playBgm(AppConfig.BGM_START); // 主界面 BGM（循环、同曲不打断；文件缺失静默降级）
        stage.setScene(new StartView(this::showStarterSelection, this::showCustomBattle).createScene());
    }

    /** 自定义战斗模式选择页：由启动页「自定义战斗」进入；四种模式均已接入真实战斗。 */
    public void showCustomBattle() {
        stage.setScene(new CustomBattleView(this::showStartScreen, this::showCustomBattleSetup).createScene());
    }

    /**
     * 自定义战斗·队伍配置入口：按模式决定双方出战数量
     * （1 vs 1 → 1 只；2 vs 2 → 2 只；小队对战 → 1~6 只任意；自定义数量 → 先选数量）。
     */
    private void showCustomBattleSetup(CustomBattleView.Mode mode) {
        switch (mode) {
            case ONE_V_ONE -> showCustomBattleSetup("1 vs 1", 1);
            case TWO_V_TWO -> showCustomBattleSetup("2 vs 2", 2);
            case SQUAD -> showCustomBattleSetup("小队对战", 0);
            case CUSTOM_COUNT -> askCustomBattleCount();
        }
    }

    /** 显示队伍配置页：requiredCount > 0 须恰好选满，为 0 按小队规则 1~6 只任意；「返回」回模式选择页。 */
    private void showCustomBattleSetup(String title, int requiredCount) {
        stage.setScene(new CustomBattleSetupView(title, requiredCount, this::showCustomBattle, this::startCustomBattle)
                .createScene());
    }

    /** 「自定义数量」：先选择双方出战数量（1~6），再进入队伍配置页；取消则留在模式选择页。 */
    private void askCustomBattleCount() {
        ChoiceDialog<Integer> dialog = new ChoiceDialog<>(3, 1, 2, 3, 4, 5, 6);
        dialog.setTitle("自定义数量战斗");
        dialog.setHeaderText(null);
        dialog.setContentText("选择双方出战数量（1~6 只）：");
        dialog.showAndWait().ifPresent(count -> showCustomBattleSetup("自定义数量（" + count + "）", count));
    }

    /**
     * 自定义战斗开战：选定队伍交给战斗系统（首只首发，战斗中可经「精灵」菜单换人），
     * 对手为随机生成的同数量同级整队（暂无完整 AI 训练家逻辑）；战斗结束回模式选择页。
     *
     * <p>独立模式：使用独立对战玩家，不影响主流程的 {@link #player} 与 {@link #session}。</p>
     */
    private void startCustomBattle(List<org.example.pokemon.domain.Pokemon> squad) {
        if (squad == null || squad.isEmpty()) {
            showCustomBattle();
            return;
        }
        Player squadPlayer = PokemonBattleAdapter.createBattlePlayer("自定义训练家", squad);
        Trainer foe = PokemonBattleAdapter.createSquadTrainer("自定义对手", squad.size(), squad.get(0).getLevel());
        try {
            BattleService engine = newTrainerBattle(squadPlayer, foe);
            MusicPlayer.stop(); // 对局界面暂无 BGM（与肉鸽战斗保持一致）
            int segment = session != null ? session.getSegment() : 1; // 独立模式：未走主线时用默认段号
            stage.setScene(new BattleController(engine, this::showCustomBattle, segment).createScene());
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
            showCustomBattle();
        }
    }

    /** 初始宝可梦选择页：使用新 pokemon 系统选择初始宝可梦（由启动页「开始游戏」进入）。 */
    public void showStarterSelection() {
        // 离开主界面即停 BGM：其它界面暂未配置音乐；
        // 后续各界面各有 BGM 时，改为在对应界面入口调 MusicPlayer.playBgm（自动停旧播新）
        MusicPlayer.stop();
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
            public void onStartRogueFloor() {
                startRogueFloor();
            }

            @Override
            public void onSetActive(int index) {
                session.setActive(index);
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

    // ------------------------------------------------------------------
    // 肉鸽楼层流程：「进入层内事件」入口 ⇄ 真实战斗 ⇄ BOSS
    // ------------------------------------------------------------------

    /** 进入肉鸽楼层事件：无进行中的一轮则新开，有则继续当前进度。 */
    public void startRogueFloor() {
        if (session == null || player == null) {
            return;
        }
        if (player.getParty().isEmpty()) {
            infoAlert("队伍为空", "先选择并加入宝可梦再开始肉鸽层内事件。");
            return;
        }
        RunData data = session.getRogueRunData();
        boolean inProgress = data.getCurrentFloor() > 0 && !data.isGameOver() && data.getCurrentPoints() > 0;
        if (!inProgress) {
            session.startRogueRun(); // 新开一轮：从第 1 层起，队伍快照进 RogueTurnManager
        } else {
            session.syncRogueTeam(); // 继续当前轮：把中途新入队的精灵同步进快照
        }
        showRogueFloorScene();
    }

    /** 显示肉鸽楼层事件页（每次重绘反映最新点数/选项）。 */
    private void showRogueFloorScene() {
        stage.setScene(new RogueFloorView(session, this::handleRogueOption, this::showMainMenu).createScene());
    }

    /**
     * 楼层事件统一入口：先扣点（不结算效果），战斗型事件（WILD/ENEMY）接管为真实战斗，
     * 直接效果事件（HOSPITAL/RANDOM）当场结算后统一推进。
     */
    private void handleRogueOption(Option option) {
        if (option == null || session == null) {
            return;
        }
        if (!session.consumeRogueOption(option)) {
            showRogueFloorScene(); // 隐藏事件 / 点数不足：重绘提示
            return;
        }
        switch (option.getType()) {
            case WILD -> startRogueWildBattle();
            case ENEMY -> startRogueEnemyBattle();
            case HOSPITAL, RANDOM -> {
                session.resolveRogueOptionEffect(option);
                afterRogueStep();
            }
        }
    }

    /** 一次楼层事件（含战斗）结束后的统一推进：已结束→主菜单；点数耗尽→BOSS；否则重绘。 */
    private void afterRogueStep() {
        if (session.isRogueRunFinished()) {
            infoAlert("本轮结束", "队伍倒下了……肉鸽远征到此为止。");
            showMainMenu();
            return;
        }
        if (session.getRogueRunData().getCurrentPoints() <= 0) {
            startRogueBossBattle(); // 点数耗尽：真实 BOSS 战
            return;
        }
        showRogueFloorScene();
    }

    /** 战斗前队伍就绪检查：无健康精灵直接判负结束；当前先发倒下则换首只健康精灵。 */
    private boolean ensureRogueBattleReady() {
        if (!session.hasHealthyPokemon()) {
            session.endRogueRun();
            infoAlert("本轮结束", "队伍已全部倒下，肉鸽远征结束。");
            showMainMenu();
            return false;
        }
        Pokemon active = session.getActive();
        if (active == null || active.isFainted()) {
            session.leadWithFirstHealthy();
        }
        return true;
    }

    /** 肉鸽战斗结束回调：战败结束本轮，其余（胜/捕捉/逃跑）继续楼层推进。 */
    private Runnable rogueBattleFinished(BattleService engine) {
        return () -> {
            if (engine.getStatus() == BattleService.Status.PLAYER_LOSE) {
                session.endRogueRun();
                infoAlert("本轮结束", "队伍倒下了……肉鸽远征到此为止。");
                showMainMenu();
                return;
            }
            afterRogueStep();
        };
    }

    /** WILD 事件：生成与先发等级相当的野生精灵，进入真实遭遇战。 */
    private void startRogueWildBattle() {
        if (!ensureRogueBattleReady()) {
            return;
        }
        int level = WildEncounter.levelAround(session.getActive().getLevel());
        Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(level);
        if (wild.isEmpty()) {
            infoAlert("数据异常", "没有可遭遇的野生精灵（数据缺失）。");
            showRogueFloorScene();
            return;
        }
        try {
            BattleService engine = newWildBattle(player, wild.get());
            stage.setScene(new BattleController(engine, rogueBattleFinished(engine), session.getSegment())
                    .createScene());
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
            showRogueFloorScene();
        }
    }

    /** ENEMY 事件：拦路训练家 1~2 只轮战（不可逃/不可捕），等级略高于先发。 */
    private void startRogueEnemyBattle() {
        if (!ensureRogueBattleReady()) {
            return;
        }
        int level = WildEncounter.levelAround(session.getActive().getLevel() + 2);
        Trainer trainer = new Trainer("拦路训练家");
        int count = 1 + (int) (Math.random() * 2);
        for (int i = 0; i < count; i++) {
            PokemonBattleAdapter.createWildPokemon(level).ifPresent(trainer::addPokemon);
        }
        if (trainer.getParty().isEmpty()) {
            infoAlert("数据异常", "没有可遭遇的精灵（数据缺失）。");
            showRogueFloorScene();
            return;
        }
        try {
            BattleService engine = newTrainerBattle(player, trainer);
            stage.setScene(new BattleController(engine, rogueBattleFinished(engine), session.getSegment())
                    .createScene());
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
            showRogueFloorScene();
        }
    }

    /** 点数耗尽：与同层 BOSS 展开真实决斗；胜利推进下一层+下一段，战败/逃跑结束本轮。 */
    private void startRogueBossBattle() {
        if (!ensureRogueBattleReady()) {
            return;
        }
        RunData data = session.getRogueRunData();
        int bossLevel = Math.max(5, session.getActive().getLevel() + 4 + data.getCurrentFloor() * 2);
        Optional<Pokemon> boss = PokemonBattleAdapter.createWildPokemon(bossLevel);
        if (boss.isEmpty()) {
            infoAlert("数据异常", "BOSS 数据缺失，无法开战。");
            showRogueFloorScene();
            return;
        }
        try {
            BattleService engine = newWildBattle(player, boss.get());
            stage.setScene(new BattleController(engine, () -> {
                BattleService.Status status = engine.getStatus();
                if (status == BattleService.Status.PLAYER_WIN || status == BattleService.Status.CAUGHT) {
                    int cleared = data.getCurrentFloor();
                    infoAlert("BOSS 战胜利", "第 " + cleared + " 层 BOSS 已被击败！进入下一层。");
                    session.enterNextSegment(); // 段号与层号同步推进：主菜单地图背景随层刷新
                    session.enterRogueFloor(cleared + 1);
                    showRogueFloorScene();
                } else {
                    session.endRogueRun();
                    infoAlert("本轮结束", "BOSS 战失败……肉鸽远征到此为止。");
                    showMainMenu();
                }
            }, session.getSegment()).createScene());
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
            showRogueFloorScene();
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
