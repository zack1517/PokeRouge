package com.bao01.app;

import com.bao01.flow.BattleAdapter;
import com.bao01.flow.Ending;
import com.bao01.flow.FightResult;
import com.bao01.flow.FlowController;
import com.bao01.flow.NodeType;
import com.bao01.flow.RandomSource;
import com.bao01.flow.RouteOption;
import com.bao01.flow.RunSummary;
import com.bao01.flow.ShopOffer;
import com.bao01.save.SaveData;
import com.bao01.save.SaveException;
import com.bao01.save.SaveManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.battle.BattleService;
import org.example.battle.BattleServices;
import org.example.controller.BattleController;
import org.example.data.GameData;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.ItemStack;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 「存档系统 + 游戏流程」独立可玩窗口（验收用）。
 *
 * <p>本类是 {@code com.bao01} 侧的一次性验收界面，<b>不修改 {@code org.example} 的任何文件</b>：
 * 它只复用 {@code org.example} 的公共 API（战斗引擎 {@link BattleServices} 与战斗界面
 * {@link BattleController}、精灵 / 道具 / 背包模型），把
 * {@link SaveManager} 的存 / 读档与 {@link FlowController} 的 Run 推进串成一条
 * 可以真正点击的链路。</p>
 *
 * <p>界面分四块：左侧队伍（含「设为先发」）与背包；中间 Run 状态、本段可选节点、
 * 医院 / 商店 / 里程碑入口；右侧日志；底部存档操作按钮。</p>
 *
 * <p><b>战斗桥接</b>：{@link FlowController} 通过 {@link BattleAdapter} 发起战斗，
 * 本窗口在收到请求后切到 {@link BattleController} 的战斗场景；对局结束（含逃跑 / 捕捉）
 * 再切回主场景，并把 {@link BattleService.Status} 映射成 {@link FightResult} 回填
 * {@link FlowController#settleFight(NodeType, FightResult)}。</p>
 *
 * <p>已知简化（见 {@code docs/存档接口设计.md} §6.3）：{@code org.example} 的战斗引擎是
 * 1v1，而流程的「路人 / 道馆 / 四天王…」会构造 2~4 只敌方队伍，本窗口只取第一只开打；
 * 战斗引擎判定为「捕捉成功」时，另走一次 {@link FlowController#attemptCatch(Pokemon)}
 * 记入抓捕账本（捕获率取自流程配置）。</p>
 */
public class RougeApp extends Application {

    private static final double MAIN_WIDTH = 980;
    private static final double MAIN_HEIGHT = 660;
    private static final int LOG_MAX_LINES = 400;

    private final UiBattleAdapter adapter = new UiBattleAdapter();
    private final TextArea logArea = new TextArea();
    private final BorderPane root = new BorderPane();

    private Stage stage;
    private Scene mainScene;
    private Player player;
    private FlowController flow;
    private Path savePath;

    /** 当前等待结算的节点（{@code FlowController} 未暴露 pendingFight，故在此记录）。 */
    private NodeType fightNode;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        this.savePath = SaveManager.defaultPath();

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefColumnCount(28);

        this.mainScene = new Scene(root, MAIN_WIDTH, MAIN_HEIGHT);

        newRun();

        stage.setTitle("PokeRouge · 存档 + 流程独立验收窗口（com.bao01）");
        stage.setScene(mainScene);
        stage.show();

        log("存档路径：" + savePath);
        log("存档格式版本：" + SaveData.FORMAT_VERSION
                + "（1 = 仅队伍/背包，2 = 追加训练家名 + Run 状态）");
        log("提示：先推进几个节点再点「保存游戏」，然后「读取游戏」应回到同一状态。");
    }

    // ------------------------------------------------------------------
    // Run 生命周期
    // ------------------------------------------------------------------

    /** 新开一段 Run（覆盖内存状态，不删除已存盘的存档）。 */
    private void newRun() {
        this.player = createStarterPlayer();
        this.flow = FlowController.startRun(player, RandomSource.system(), adapter);
        this.fightNode = null;
        log("===== 新的 Run 开始（训练家 " + player.getName() + "） =====");
        log("  初始队伍 " + player.getPartySize() + " 只，金币 " + flow.gold()
                + "，本段可选节点 " + flow.options().size() + " 个");
        refresh();
    }

    /** 保存：玩家队伍 / 背包 + 当前 Run 快照一并落盘。 */
    private void save() {
        try {
            RunSummary snapshot = flow.summary();
            SaveData.World world = SaveManager.captureWorld(player, snapshot);
            SaveManager.writeToFile(savePath, world);
            log("【保存成功】" + savePath);
            log("  Run: 第 " + snapshot.segmentNo() + " 段 AP " + snapshot.apLeft() + "/"
                    + snapshot.apCap() + " 金币 " + snapshot.gold()
                    + " 阶段 " + (snapshot.inRoute() ? "常规路线" : "终局")
                    + " 结局 " + Ending.textOf(snapshot.ending()));
            refresh();
        } catch (SaveException e) {
            log("【保存失败】" + e.getMessage());
        }
    }

    /** 读取：从默认路径恢复玩家与 Run。 */
    private void load() {
        try {
            SaveData.World world = SaveManager.readFromFile(savePath);
            Player restored = SaveManager.restorePlayer(world);
            if (!world.hasRun()) {
                log("【读取失败】该存档不含 Run 状态（v" + world.version()
                        + " 旧档），无法恢复流程进度。");
                return;
            }
            this.player = restored;
            this.flow = FlowController.restored(restored, world.run(), adapter);
            this.fightNode = null;
            log("【读取成功】" + savePath + "（v" + world.version() + "）");
            RunSummary s = world.run();
            log("  恢复到：第 " + s.segmentNo() + " 段 AP " + s.apLeft() + "/" + s.apCap()
                    + " 金币 " + s.gold()
                    + " 阶段 " + (s.inRoute() ? "常规路线" : "终局")
                    + " 必然节点 " + NodeType.textOf(s.nextMilestone())
                    + " 结局 " + Ending.textOf(s.ending()));
            refresh();
        } catch (SaveException e) {
            log("【读取失败】" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 界面搭建
    // ------------------------------------------------------------------

    /** 依据当前玩家 / Run 状态重建整个界面。 */
    private void refresh() {
        root.setTop(buildHeader());
        HBox center = new HBox(12, buildPlayerPane(), buildRunPane(), buildLogPane());
        center.setPadding(new Insets(8));
        HBox.setHgrow(center.getChildren().get(1), Priority.ALWAYS);
        root.setCenter(center);
        root.setBottom(buildBottomBar());
    }

    private Region buildHeader() {
        RunSummary s = flow.summary();
        Label title = new Label("训练家 " + player.getName()
                + " ｜ 第 " + s.segmentNo() + " 段"
                + " ｜ AP " + s.apLeft() + "/" + s.apCap()
                + " ｜ 金币 " + s.gold()
                + " ｜ 阶段 " + (s.inRoute() ? "常规路线" : "终局")
                + (s.isOver() ? " ｜ 结局 " + Ending.textOf(s.ending()) : ""));
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox box = new VBox(2, title, new Label("必然节点：" + NodeType.textOf(s.nextMilestone())
                + "　抓捕累计：" + s.ledger().totalCatches() + " 次"));
        box.setPadding(new Insets(8));
        return box;
    }

    private Region buildPlayerPane() {
        VBox pane = new VBox(6);
        pane.setPrefWidth(280);
        pane.getChildren().add(sectionLabel("队伍（点击成员设为先发）"));

        List<Pokemon> party = player.getParty();
        int activeIndex = player.getActiveIndex();
        for (int i = 0; i < party.size(); i++) {
            Pokemon p = party.get(i);
            Button b = new Button((i == activeIndex ? "▶ " : "   ") + describePokemon(p));
            b.setMaxWidth(Double.MAX_VALUE);
            b.setDisable(p.isFainted());
            final int index = i;
            b.setOnAction(e -> {
                player.setActive(index);
                log("先发改为：" + party.get(index).getName());
                refresh();
            });
            pane.getChildren().add(b);
        }

        pane.getChildren().add(sectionLabel("背包"));
        List<ItemStack> stacks = player.getBag().getAll();
        boolean any = false;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            any = true;
            pane.getChildren().add(new Label("  " + stack.getItem().getName()
                    + " ×" + stack.getCount()));
        }
        if (!any) {
            pane.getChildren().add(new Label("  （空）"));
        }
        return wrapScroll(pane);
    }

    private Region buildRunPane() {
        VBox pane = new VBox(6);
        pane.setMinWidth(320);
        RunSummary s = flow.summary();

        if (s.isOver()) {
            pane.getChildren().add(sectionLabel("Run 已结束"));
            pane.getChildren().add(new Label("结局：" + describeEnding(s.ending())));
            pane.getChildren().add(new Label("点「新 Run」重新开始，或「读取游戏」回到存档。"));
            return wrapScroll(pane);
        }

        pane.getChildren().add(sectionLabel("本段可选节点"));
        List<RouteOption> options = flow.options();
        if (options.isEmpty()) {
            pane.getChildren().add(new Label("  （本段已无可选节点）"));
        }
        for (int i = 0; i < options.size(); i++) {
            RouteOption opt = options.get(i);
            Button b = new Button("进入：「" + opt.display() + "」"
                    + (opt.forced() ? "（必然）" : "　消耗 " + opt.apCost() + " AP"));
            b.setMaxWidth(Double.MAX_VALUE);
            final int index = i;
            b.setOnAction(e -> enterOption(index));
            pane.getChildren().add(b);
        }

        NodeType milestone = s.nextMilestone();
        Button milestoneBtn = new Button(milestone == null
                ? "（无必然节点）"
                : "触发必然节点：" + milestone.label());
        milestoneBtn.setMaxWidth(Double.MAX_VALUE);
        milestoneBtn.setDisable(milestone == null);
        milestoneBtn.setOnAction(e -> startMilestone());
        pane.getChildren().add(milestoneBtn);

        pane.getChildren().add(sectionLabel("服务"));
        Button hospital = new Button("前往医院（治疗全队）");
        hospital.setMaxWidth(Double.MAX_VALUE);
        hospital.setOnAction(e -> {
            appendLog(flow.visitHospital());
            refresh();
        });
        pane.getChildren().add(hospital);

        List<ShopOffer> stock = flow.shopStock();
        if (stock.isEmpty()) {
            pane.getChildren().add(new Label("  （当前不在商店节点）"));
        } else {
            pane.getChildren().add(sectionLabel("商店货架"));
            for (int i = 0; i < stock.size(); i++) {
                ShopOffer offer = stock.get(i);
                Item item = GameData.instance().item(offer.itemId());
                String name = item == null ? offer.itemId() : item.getName();
                Button b = new Button("购买 " + name + "（" + offer.price() + " 金币）"
                        + (offer.description() == null || offer.description().isBlank()
                        ? "" : "　" + offer.description()));
                b.setMaxWidth(Double.MAX_VALUE);
                final int index = i;
                b.setOnAction(e -> {
                    appendLog(flow.buy(index));
                    refresh();
                });
                pane.getChildren().add(b);
            }
        }

        pane.getChildren().add(sectionLabel("剧情线"));
        pane.getChildren().add(new Label("  " + describeStory(s)));
        pane.getChildren().add(new Label("  " + describeCatches(s)));
        return wrapScroll(pane);
    }

    private Region buildLogPane() {
        VBox pane = new VBox(4, sectionLabel("日志"), logArea);
        pane.setPrefWidth(320);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        return pane;
    }

    private Region buildBottomBar() {
        Button saveBtn = new Button("保存游戏");
        saveBtn.setOnAction(e -> save());
        Button loadBtn = new Button("读取游戏");
        loadBtn.setDisable(!SaveManager.defaultSaveExists());
        loadBtn.setOnAction(e -> load());
        Button newBtn = new Button("新 Run");
        newBtn.setOnAction(e -> {
            newRun();
            log("（已放弃当前 Run，未改动磁盘存档）");
        });
        Button exitBtn = new Button("退出");
        exitBtn.setOnAction(e -> Platform.exit());

        HBox bar = new HBox(10, saveBtn, loadBtn, newBtn, exitBtn,
                new Label("　存档：" + savePath));
        bar.setPadding(new Insets(8));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ------------------------------------------------------------------
    // 流程操作
    // ------------------------------------------------------------------

    private void enterOption(int index) {
        RouteOption opt = flow.options().get(index);
        log(">> 进入「" + opt.display() + "」");
        appendLog(flow.enter(index));
        afterFlowCall(opt.type());
    }

    private void startMilestone() {
        NodeType m = flow.nextMilestone();
        log(">> 触发必然节点「" + (m == null ? "?" : m.label()) + "」");
        appendLog(flow.startMilestone());
        afterFlowCall(m);
    }

    /** 流程调用之后统一收尾：若流程发起了战斗则切到战斗场景，否则刷新界面。 */
    private void afterFlowCall(NodeType node) {
        if (adapter.requested) {
            this.fightNode = node;
            openBattle();
            return;
        }
        refresh();
    }

    /** 取出适配器缓存的战斗请求，切到 {@link BattleController} 的战斗场景。 */
    private void openBattle() {
        List<Pokemon> foes = adapter.takeFoes();
        adapter.requested = false;
        if (foes.isEmpty()) {
            log("（流程未提供对手，按胜利结算）");
            finishFight(FightResult.PLAYER_WON, null);
            return;
        }
        if (foes.size() > 1) {
            log("（流程给出 " + foes.size() + " 只对手，战斗引擎为 1v1，本次只打第一只："
                    + foes.get(0).getName() + "）");
        }
        Pokemon foe = foes.get(0);
        try {
            BattleService engine = BattleServices.newBattle(player, foe);
            BattleController controller = new BattleController(engine,
                    () -> onBattleExit(engine));
            stage.setScene(controller.createScene());
        } catch (RuntimeException e) {
            log("（战斗无法开始：" + e.getMessage() + "，按胜利结算）");
            finishFight(FightResult.PLAYER_WON, foe);
        }
    }

    /** 战斗场景退出回调：把战斗状态映射成流程结果并结算。 */
    private void onBattleExit(BattleService engine) {
        stage.setScene(mainScene);
        Pokemon foe = engine.getWild();
        FightResult result = switch (engine.getStatus()) {
            case PLAYER_WIN -> FightResult.PLAYER_WON;
            case CAUGHT -> FightResult.PLAYER_WON;
            case PLAYER_LOSE -> FightResult.PLAYER_LOST;
            case FLED -> FightResult.PLAYER_FLED;
            case ONGOING -> null;
        };
        if (result == null) {
            log("（战斗尚未结束就退出了，本场保持待结算；再次进入该节点即可重开）");
            refresh();
            return;
        }
        if (engine.getStatus() == BattleService.Status.CAUGHT) {
            log("战斗引擎判定捕捉成功，转交流程记账：");
            appendLog(flow.attemptCatch(foe));
        }
        finishFight(result, foe);
    }

    private void finishFight(FightResult result, Pokemon foe) {
        NodeType fought = fightNode;
        fightNode = null;
        if (fought == null) {
            log("（没有待结算的节点）");
            refresh();
            return;
        }
        appendLog(flow.settleFight(fought, result));
        refresh();
    }

    // ------------------------------------------------------------------
    // 战斗桥
    // ------------------------------------------------------------------

    /** {@link BattleAdapter} 实现：只登记流程发起的战斗请求，由本窗口接管场景切换。 */
    private final class UiBattleAdapter implements BattleAdapter {

        private List<Pokemon> foes = List.of();
        private boolean requested;

        @Override
        public List<String> startBattle(Player p, List<Pokemon> enemies, boolean canFlee) {
            this.foes = enemies == null ? List.of() : List.copyOf(enemies);
            this.requested = true;
            return List.of("（战斗准备：对手 " + foes.size() + " 只，"
                    + (canFlee ? "可以逃跑" : "不可逃跑") + "）");
        }

        private List<Pokemon> takeFoes() {
            List<Pokemon> copy = foes;
            foes = List.of();
            return copy;
        }
    }

    // ------------------------------------------------------------------
    // 初始队伍
    // ------------------------------------------------------------------

    /** 构造开局玩家：优先取 {@code s_fire_cat} / {@code s_leaf_chick}，缺则按注册表顺序补齐。 */
    private static Player createStarterPlayer() {
        Player p = new Player("小红");
        List<String> wanted = new ArrayList<>(List.of("s_fire_cat", "s_leaf_chick"));
        for (String id : wanted) {
            GameData.instance().createPokemon(id, 5).ifPresent(p::addToParty);
        }
        for (Species s : GameData.instance().allSpecies()) {
            if (p.getPartySize() >= 2) {
                break;
            }
            GameData.instance().createPokemon(s.getId(), 5).ifPresent(p::addToParty);
        }
        if (p.getPartySize() == 0) {
            throw new SaveException("物种注册表为空，无法组建初始队伍");
        }
        p.setActive(0);
        for (Item item : GameData.instance().allItems()) {
            if (item.getCategory() == ItemCategory.HEAL) {
                p.getBag().add(item, 3);
            } else if (item.getCategory() == ItemCategory.POKE_BALL) {
                p.getBag().add(item, 5);
            }
        }
        return p;
    }

    // ------------------------------------------------------------------
    // 文案工具
    // ------------------------------------------------------------------

    private static String describePokemon(Pokemon p) {
        return p.getName() + " Lv." + p.getLevel()
                + " HP " + p.getCurrentHp() + "/" + p.getMaxHp()
                + (p.isFainted() ? "（濒死）" : "");
    }

    private static String describeStory(RunSummary s) {
        var st = s.story();
        return "火箭队线 " + yn(st.rocketEntered())
                + "｜神兽已遇 " + yn(st.legendaryEncountered())
                + "｜大师球 " + yn(st.masterBallHeld())
                + "｜首领已败 " + yn(st.rocketBossDefeated())
                + "｜冠军已败 " + yn(st.championDefeated())
                + "｜侵略已结束 " + yn(st.invasionDone());
    }

    private static String describeCatches(RunSummary s) {
        Map<String, Integer> catches = s.ledger().snapshot();
        if (catches.isEmpty()) {
            return "抓捕记录：（无）";
        }
        StringBuilder sb = new StringBuilder("抓捕记录：");
        boolean first = true;
        for (Map.Entry<String, Integer> e : catches.entrySet()) {
            if (!first) {
                sb.append('、');
            }
            sb.append(e.getKey()).append('×').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static String describeEnding(Ending ending) {
        if (ending == null) {
            return "进行中";
        }
        return switch (ending) {
            case RUN_OVER -> "RUN_OVER（挑战失败）";
            case NORMAL_CLEAR -> "NORMAL_CLEAR（普通通关）";
            case TRUE_CLEAR -> "TRUE_CLEAR（真结局）";
        };
    }

    private static String yn(boolean b) {
        return b ? "是" : "否";
    }

    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-padding: 6 0 0 0;");
        return l;
    }

    private static Region wrapScroll(Region content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setPadding(new Insets(2));
        return sp;
    }

    // ------------------------------------------------------------------
    // 日志
    // ------------------------------------------------------------------

    private void appendLog(List<String> lines) {
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            log(line);
        }
    }

    private void log(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        logArea.appendText(line + System.lineSeparator());
        String text = logArea.getText();
        int count = text.split("\n", -1).length;
        if (count > LOG_MAX_LINES) {
            int cut = text.indexOf('\n');
            if (cut > 0) {
                logArea.setText(text.substring(cut + 1));
            }
        }
        logArea.positionCaret(logArea.getLength());
    }
}
