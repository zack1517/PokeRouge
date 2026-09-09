package com.bao01.view;

import com.bao01.controller.BattleController;
import com.bao01.model.Battle;
import com.bao01.model.BattleAction;
import com.bao01.model.Item;
import com.bao01.model.Move;
import com.bao01.model.Pokemon;
import com.bao01.model.Team;
import com.bao01.model.Weather;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * 单打对战界面（1v1，最多 6 只轮换）。
 *
 * <p>布局：顶部为对局模式与重新开局；中部上方是敌方信息条与战斗日志，
 * 下方为我方信息条；底部为行动区——每回合在「对战 / 道具 / 宝可梦 / 逃跑」
 * 四类行动中切换按钮面板。
 */
public class BattleView {

    private final BattleController controller = new BattleController();

    private final BorderPane root = new BorderPane();
    private final Label foeName = new Label();
    private final Label foeSub = new Label();
    private final ProgressBar foeBar = new ProgressBar(1);
    private final Label foeHpText = new Label();
    private final Label myName = new Label();
    private final Label mySub = new Label();
    private final ProgressBar myBar = new ProgressBar(1);
    private final Label myHpText = new Label();
    private final Label weatherLabel = new Label();
    private final TextArea log = new TextArea();
    private final VBox actions = new VBox(6);

    public BattleView() {
        buildUi();
        newBattle(BattleController.Mode.WILD);
    }

    public BorderPane getRoot() {
        return root;
    }

    // ---------- 布局构建 ----------

    private void buildUi() {
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color: #f4f7fb;");

        root.setTop(buildTopBar());
        root.setCenter(buildCenter());
        root.setBottom(wrapActionPanel());
    }

    private Node buildTopBar() {
        HBox bar = new HBox(8);
        bar.setPadding(new Insets(4, 2, 8, 2));
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("宝可梦 · 单打对战");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        HBox.setHgrow(title, Priority.ALWAYS);

        Label modeTitle = new Label("对战对象：");
        Label mode = new Label();
        mode.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a6fd1;");

        Button save = new Button("💾 存档");
        Button load = new Button("📂 读档");
        Button wild = new Button("遇野生宝可梦");
        Button trainer = new Button("挑战训练家");
        Button weather = new Button("🌦 天气测试");
        save.setOnAction(e -> doSave());
        load.setOnAction(e -> doLoad());
        wild.setOnAction(e -> newBattle(BattleController.Mode.WILD));
        trainer.setOnAction(e -> newBattle(BattleController.Mode.TRAINER));
        weather.setOnAction(e -> doWeatherTest());
        for (Button b : new Button[]{save, load, wild, trainer, weather}) {
            b.setStyle(primaryStyle());
        }

        bar.getChildren().addAll(title, modeTitle, mode, save, load, wild, trainer, weather);
        this.modeLabel = mode;
        return bar;
    }

    /** 💾 存档：把当前对局快照写入默认存档文件。 */
    private void doSave() {
        log.clear();
        append(controller.save());
        refreshUi();
        showMainMenu();
    }

    /** 📂 读档：从默认存档文件恢复对局并继续。 */
    private void doLoad() {
        log.clear();
        append(controller.load());
        refreshUi();
        showMainMenu();
    }

    /** 🌦 天气测试：进入可稳定触发沙暴/下雪/求雨/晴天的测试对局。 */
    private void doWeatherTest() {
        log.clear();
        append(controller.startWeatherTest());
        refreshUi();
        showMainMenu();
    }

    private Label modeLabel;

    private Node buildCenter() {
        VBox center = new VBox(6);
        center.setPadding(new Insets(2));

        HBox foeRow = bannerRow();
        foeRow.getChildren().addAll(buildInfoColumn(foeName, foeSub, foeHpText, foeBar, "#c0392b"));
        center.getChildren().add(foeRow);

        HBox weatherRow = new HBox(8);
        weatherRow.setAlignment(Pos.CENTER);
        weatherLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        weatherRow.getChildren().add(weatherLabel);
        center.getChildren().add(weatherRow);

        ScrollPane scroll = new ScrollPane(log);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(190);
        log.setEditable(false);
        log.setWrapText(true);
        log.setStyle("-fx-font-family: 'Microsoft YaHei', 'SimHei'; -fx-font-size: 13px;"
                + "-fx-control-inner-background: #ffffff;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        center.getChildren().add(scroll);

        HBox myRow = bannerRow();
        myRow.getChildren().addAll(buildInfoColumn(myName, mySub, myHpText, myBar, "#2980b9"));
        center.getChildren().add(myRow);
        return center;
    }

    private HBox bannerRow() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6));
        row.setStyle("-fx-background-color: white; -fx-background-radius: 8;"
                + "-fx-border-color: #d7dee8; -fx-border-radius: 8;");
        return row;
    }

    private Node buildInfoColumn(Label name, Label sub, Label hpText, ProgressBar bar, String color) {
        VBox box = new VBox(2);
        box.setPadding(new Insets(2));
        box.setPrefWidth(300);

        name.setFont(Font.font("System", FontWeight.BOLD, 18));
        name.setStyle("-fx-text-fill: " + color + ";");

        sub.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");

        HBox hpRow = new HBox(8);
        hpRow.setAlignment(Pos.CENTER_LEFT);
        bar.setPrefWidth(180);
        hpText.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        hpRow.getChildren().addAll(bar, hpText);
        box.getChildren().addAll(name, sub, hpRow);
        return box;
    }

    private Node wrapActionPanel() {
        VBox wrap = new VBox(6);
        wrap.setPadding(new Insets(8, 2, 2, 2));
        actions.setAlignment(Pos.CENTER);
        wrap.getChildren().add(actions);
        return wrap;
    }

    // ---------- 状态刷新 ----------

    private void newBattle(BattleController.Mode mode) {
        List<String> opening = controller.start(mode);
        log.clear();
        append(opening);
        refreshUi();
        showMainMenu();
    }

    private void refreshUi() {
        Battle b = controller.battle();
        if (b == null) {
            return;
        }
        modeLabel.setText(b.opponent().getLabel());
        updateBanner(foeName, foeSub, foeHpText, foeBar, b.foeActive(), true);
        updateBanner(myName, mySub, myHpText, myBar, b.playerActive(), false);
        updateWeather(b);
    }

    /** 更新天气状态栏文案（无天气时显示提示）。 */
    private void updateWeather(Battle b) {
        Weather w = b.weather();
        if (w == Weather.NONE || b.weatherTurnsLeft() <= 0) {
            weatherLabel.setText("☀ 天气：无");
            weatherLabel.setStyle("-fx-text-fill:#888;");
            return;
        }
        String text = switch (w) {
            case RAIN -> "🌧 雨天";
            case SUN -> "☀ 晴天";
            case SAND -> "🌪 沙暴";
            case SNOW -> "❄ 雪";
            case NONE -> "无";
        };
        weatherLabel.setText(text + "（剩余 " + b.weatherTurnsLeft() + " 回合）");
        weatherLabel.setStyle(switch (w) {
            case RAIN -> "-fx-text-fill:#2f6f9f;";
            case SUN -> "-fx-text-fill:#e67e22;";
            case SAND -> "-fx-text-fill:#b7950b;";
            case SNOW -> "-fx-text-fill:#5dade2;";
            case NONE -> "-fx-text-fill:#888;";
        });
    }

    private void updateBanner(Label name, Label sub, Label hpText, ProgressBar bar,
                              Pokemon p, boolean foe) {
        if (p == null) {
            return;
        }
        String role = foe ? "对方" : "我方";
        name.setText(p.getName());
        sub.setText(role + " · Lv." + p.getLevel() + " · " + p.getType().getLabel()
                + " · " + (foe ? "速度 " + p.speed() : "") + stageText(p));

        int cur = Math.max(0, p.currentHp());
        int max = p.maxHp();
        hpText.setText(cur + " / " + max);
        double ratio = p.hpRatio();
        bar.setProgress(Math.max(0, ratio));
        if (p.isFainted()) {
            bar.setStyle("-fx-accent: #7f8c8d;");
        } else if (ratio > 0.5) {
            bar.setStyle("-fx-accent: #27ae60;");
        } else if (ratio > 0.2) {
            bar.setStyle("-fx-accent: #e67e22;");
        } else {
            bar.setStyle("-fx-accent: #e74c3c;");
        }
    }

    private String stageText(Pokemon p) {
        StringBuilder sb = new StringBuilder();
        for (com.bao01.model.Stat s : com.bao01.model.Stat.values()) {
            if (s.isHp()) {
                continue;
            }
            int stage = p.stageOf(s);
            if (stage != 0) {
                sb.append(' ').append(s.getCode()).append(stage > 0 ? "+" : "").append(stage);
            }
        }
        return sb.toString();
    }

    private void append(List<String> lines) {
        for (String line : lines) {
            log.appendText(line + "\n");
        }
        log.setScrollTop(Double.MAX_VALUE);
    }

    // ---------- 行动区 ----------

    private void setActions(Node... nodes) {
        actions.getChildren().clear();
        for (Node n : nodes) {
            actions.getChildren().add(n);
        }
    }

    private void setActionRows(List<Node> rows) {
        actions.getChildren().clear();
        actions.getChildren().addAll(rows);
    }

    /** 将按钮按每行 4 个放入居中的 HBox 行。 */
    private List<Node> buildRows(List<? extends Button> buttons) {
        List<Node> rows = new java.util.ArrayList<>();
        List<Button> current = new java.util.ArrayList<>();
        for (Button b : buttons) {
            current.add(b);
            if (current.size() == 4) {
                rows.add(btnRow(current.toArray(new Button[0])));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            rows.add(btnRow(current.toArray(new Button[0])));
        }
        return rows;
    }

    private void showMainMenu() {
        Battle b = controller.battle();
        if (b == null) {
            return;
        }
        if (b.isOver()) {
            showEnd(b.outcome());
            return;
        }
        if (b.isPlayerPendingSendout()) {
            showSendOut();
            return;
        }
        if (b.isPlayerKoSwitchPending()) {
            showKoSwitchChoice();
            return;
        }
        Button atk = menuButton("⚔ 对战");
        Button item = menuButton("🧪 道具");
        Button sw = menuButton("🔁 宝可梦");
        Button flee = menuButton("🏃 逃跑");
        atk.setOnAction(e -> showMoves());
        item.setOnAction(e -> showItems());
        sw.setOnAction(e -> showSwitch());
        flee.setDisable(!b.canFlee());
        flee.setOnAction(e -> confirmFlee());
        setActions(btnRow(atk, item, sw, flee));
    }

    private void showMoves() {
        Pokemon p = controller.battle().playerActive();
        List<Button> row = new java.util.ArrayList<>();
        for (int i = 0; i < p.moveCount(); i++) {
            Move m = p.move(i);
            String label = m.getName() + "  " + m.getType().getLabel();
            if (m.isDamaging()) {
                label += " 威力" + m.getPower();
            } else if (m.isWeatherMove() && m.getWeather() != null && m.getWeather() != Weather.NONE) {
                label += " 变化·" + m.getWeather().getLabel();
            } else {
                label += " 变化";
            }
            Button bt = menuButton(label);
            bt.setPrefWidth(190);
            bt.setTooltip(new Tooltip(moveTooltip(m, i)));
            bt.setStyle(primaryStyle());
            int idx = i;
            bt.setOnAction(e -> submitAction(new BattleAction.Attack(idx)));
            row.add(bt);
        }
        Button back = menuButton("← 返回");
        back.setOnAction(e -> showMainMenu());
        row.add(back);
        setActionRows(buildRows(row));
    }

    private String moveTooltip(Move m, int i) {
        StringBuilder sb = new StringBuilder("类别：").append(m.getCategory().getLabel());
        sb.append("  优先度：").append(m.getPriority());
        sb.append("  命中：").append((int) (m.getAccuracy() * 100)).append('%');
        if (m.isWeatherMove()) {
            Weather w = m.getWeather();
            if (w != null && w != Weather.NONE) {
                sb.append('\n').append("效果：引发").append(w.getLabel())
                        .append("（持续 5 回合，可覆盖其他天气）");
            }
        } else if (!m.isDamaging() && m.getEffect() != null) {
            Move.Effect eff = m.getEffect();
            sb.append('\n').append("效果：").append(eff.target() == Move.EffectTarget.SELF ? "自身" : "对方")
                    .append(' ').append(eff.stat().getCode())
                    .append(eff.stageDelta() > 0 ? "+" : "").append(eff.stageDelta()).append(" 级");
        }
        return sb.toString();
    }

    private void showItems() {
        List<Item> items = controller.usableItems();
        if (items.isEmpty()) {
            Button back = menuButton("背包里没有可用的道具");
            back.setDisable(true);
            Button ret = menuButton("← 返回");
            ret.setOnAction(e -> showMainMenu());
            setActionRows(buildRows(List.of(back, ret)));
            return;
        }
        List<Button> row = new java.util.ArrayList<>();
        for (Item item : items) {
            int count = controller.battle().player().bag().count(item);
            Button bt = menuButton(item.getName() + " ×" + count);
            bt.setStyle(primaryStyle());
            bt.setOnAction(e -> submitAction(new BattleAction.UseItem(item)));
            row.add(bt);
        }
        Button back = menuButton("← 返回");
        back.setOnAction(e -> showMainMenu());
        row.add(back);
        setActionRows(buildRows(row));
    }

    private void showSwitch() {
        Team t = controller.battle().player();
        List<Button> row = new java.util.ArrayList<>();
        for (int i = 0; i < t.size(); i++) {
            Pokemon p = t.member(i);
            boolean active = i == t.activeIndex();
            boolean alive = !p.isFainted();
            String label = (active ? "★ " : "") + p.getName() + "  HP "
                    + p.currentHp() + "/" + p.maxHp();
            Button bt = menuButton(label);
            bt.setDisable(active || !alive);
            if (!active && alive) {
                bt.setStyle(primaryStyle());
                int idx = i;
                bt.setOnAction(e -> submitAction(new BattleAction.SwitchTo(idx)));
            }
            row.add(bt);
        }
        Button back = menuButton("← 返回");
        back.setOnAction(e -> showMainMenu());
        row.add(back);
        setActionRows(buildRows(row));
    }

    private void showSendOut() {
        Team t = controller.battle().player();
        List<Button> row = new java.util.ArrayList<>();
        Button hint = menuButton("请选择上场的宝可梦：");
        hint.setDisable(true);
        row.add(hint);
        for (int i = 0; i < t.size(); i++) {
            Pokemon p = t.member(i);
            if (p.isFainted() || i == t.activeIndex()) {
                continue;
            }
            Button bt = menuButton(p.getName() + "  HP " + p.currentHp() + "/" + p.maxHp());
            bt.setStyle(primaryStyle());
            int idx = i;
            bt.setOnAction(e -> {
                List<String> logs = controller.sendOut(idx);
                append(logs);
                refreshUi();
                showMainMenu();
            });
            row.add(bt);
        }
        setActionRows(buildRows(row));
    }

    /** 击倒对方宝可梦后的免费轮换选择：可派替补（不占用回合），也可继续战斗。 */
    private void showKoSwitchChoice() {
        Battle b = controller.battle();
        Team t = b.player();
        List<Button> row = new java.util.ArrayList<>();
        Button hint = menuButton("🎯 对方宝可梦倒下了！是否轮换己方宝可梦？");
        hint.setDisable(true);
        row.add(hint);
        for (int i = 0; i < t.size(); i++) {
            Pokemon p = t.member(i);
            if (p.isFainted() || i == t.activeIndex()) {
                continue;
            }
            Button bt = menuButton(p.getName() + "  HP " + p.currentHp() + "/" + p.maxHp());
            bt.setStyle(primaryStyle());
            int idx = i;
            bt.setOnAction(e -> {
                List<String> logs = controller.koSwitch(idx);
                append(logs);
                refreshUi();
                showMainMenu();
            });
            row.add(bt);
        }
        Button keep = menuButton("⚔ 继续战斗");
        keep.setStyle("-fx-background-color:#27ae60; -fx-text-fill:white; -fx-font-weight:bold;");
        keep.setOnAction(e -> {
            List<String> logs = controller.koSwitchKeep();
            append(logs);
            refreshUi();
            showMainMenu();
        });
        row.add(keep);
        setActionRows(buildRows(row));
    }

    private void confirmFlee() {
        Button yes = menuButton("🏃 确认逃跑");
        yes.setStyle("-fx-background-color:#c0392b; -fx-text-fill:white; -fx-font-weight:bold;");
        yes.setOnAction(e -> submitAction(new BattleAction.Flee()));
        Button back = menuButton("← 取消");
        back.setOnAction(e -> showMainMenu());
        setActionRows(buildRows(List.of(yes, back)));
    }

    private void showEnd(Battle.Outcome outcome) {
        String text = switch (outcome) {
            case PLAYER_WON -> "🎉 你获胜了！";
            case FOE_WON -> "💀 你输了……再接再厉！";
            case PLAYER_FLED -> "🏃 你逃出了对战。";
            case IN_PROGRESS -> "";
        };
        Label result = new Label(text);
        result.setFont(Font.font("System", FontWeight.BOLD, 18));
        HBox head = new HBox(result);
        head.setAlignment(Pos.CENTER);

        Button again = menuButton("再来一局（野生）");
        again.setStyle("-fx-background-color:#2980b9; -fx-text-fill:white; -fx-font-weight:bold;");
        again.setOnAction(e -> newBattle(BattleController.Mode.WILD));
        Button againTrainer = menuButton("再来一局（训练家）");
        againTrainer.setStyle("-fx-background-color:#8e44ad; -fx-text-fill:white; -fx-font-weight:bold;");
        againTrainer.setOnAction(e -> newBattle(BattleController.Mode.TRAINER));

        List<Node> rows = new java.util.ArrayList<>();
        rows.add(head);
        rows.addAll(buildRows(List.of(again, againTrainer)));
        setActionRows(rows);
    }

    private void submitAction(BattleAction action) {
        List<String> logs = controller.act(action);
        append(logs);
        refreshUi();
        showMainMenu();
    }

    // ---------- 小部件 ----------

    private HBox btnRow(Button... buttons) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER);
        row.getChildren().addAll(buttons);
        return row;
    }

    private Button menuButton(String text) {
        Button b = new Button(text);
        b.setPrefWidth(180);
        b.setPrefHeight(34);
        b.setFont(Font.font("System", FontWeight.NORMAL, 13));
        return b;
    }

    private String primaryStyle() {
        return "-fx-background-color:#ffffff; -fx-border-color:#aebbd0;"
                + "-fx-border-radius:6; -fx-background-radius:6;";
    }
}
