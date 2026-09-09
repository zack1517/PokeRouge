package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.config.AppConfig;
import org.example.model.Move;
import org.example.model.MoveSlot;
import org.example.model.Pokemon;
import org.example.model.Terrain;
import org.example.model.Weather;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * 战斗场景布局（纯界面，无业务逻辑）。
 *
 * <p>结构：上=双方精灵信息面板，中=战斗日志，下=行动按钮区。
 * 控制器通过本视图暴露的引用更新内容、绑定事件。</p>
 */
public class BattleView {

    /** 面板按钮点击时的回调（由控制器绑定）。 */
    public interface Actions {
        /** 选择技能。 */
        void onMoveSelected(MoveSlot slot);

        /** 选择道具（索引为背包可用堆叠的下标）。 */
        void onItemSelected(int stackIndex);

        /** 切换到队伍中第 partyIndex 只精灵（消耗本回合行动）。 */
        void onSwitchSelected(int partyIndex);

        /** 逃跑。 */
        void onRun();

        /** 返回主菜单。 */
        void onExit();
    }

    // ---- 玩家信息 ----
    private final Label playerName = new Label();
    private final Label playerInfo = new Label();
    private final ProgressBar playerHpBar = new ProgressBar();
    private final Label playerHpText = new Label();
    private final ProgressBar playerExpBar = new ProgressBar();
    private final Label playerExpText = new Label();

    // ---- 野怪信息 ----
    private final Label wildName = new Label();
    private final Label wildInfo = new Label();
    private final ProgressBar wildHpBar = new ProgressBar();
    private final Label wildHpText = new Label();

    // ---- 中部：天气/场地状态 + 日志 ----
    private final Label fieldStatus = new Label();
    private final TextArea logArea = new TextArea();

    // ---- 底部：行动按钮容器（重建） ----
    private final VBox actionBox = new VBox(8);

    private final Actions actions;

    public BattleView(Actions actions) {
        this.actions = actions;
    }

    /** 构建战斗场景。 */
    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setTop(buildTop());
        root.setCenter(buildCenter());
        root.setBottom(buildBottom());

        Scene scene = new Scene(root, AppConfig.WINDOW_WIDTH, AppConfig.WINDOW_HEIGHT);
        return scene;
    }

    // ------------------------------------------------------------------
    // 构建
    // ------------------------------------------------------------------

    private Parent buildTop() {
        HBox top = new HBox(16);
        top.setPadding(new Insets(0, 0, 8, 0));
        top.setAlignment(Pos.TOP_CENTER);
        top.getChildren().addAll(
                buildInfoPanel("我方", playerName, playerInfo, playerHpBar, playerHpText,
                        playerExpBar, playerExpText),
                buildInfoPanel("野生", wildName, wildInfo, wildHpBar, wildHpText, null, null));
        return top;
    }

    private VBox buildInfoPanel(String title, Label name, Label info, ProgressBar bar,
                                Label hpText, ProgressBar expBar, Label expText) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        info.setWrapText(true);
        info.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        bar.setPrefWidth(230);
        hpText.setStyle("-fx-font-weight: bold;");

        VBox panel = new VBox(4, titleLabel, name, info, bar, hpText);
        if (expBar != null) {
            expBar.setPrefWidth(230);
            expBar.setStyle("-fx-accent: #5a8af0;");
            expText.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
            panel.getChildren().addAll(expBar, expText);
        }
        panel.setPrefWidth(270);
        panel.setStyle("-fx-border-color: #bbb; -fx-border-width: 1;"
                + " -fx-border-radius: 6; -fx-padding: 8; -fx-background-radius: 6; -fx-background-color: #f4f4f8;");
        return panel;
    }

    private Parent buildCenter() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px;");
        fieldStatus.setWrapText(true);
        fieldStatus.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px;"
                + "-fx-font-weight: bold; -fx-text-fill: #2a6cb6;");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        VBox center = new VBox(4, fieldStatus, logArea);
        VBox.setMargin(fieldStatus, new Insets(0, 0, 4, 0));
        return center;
    }

    private Parent buildBottom() {
        actionBox.setPadding(new Insets(8, 0, 0, 0));
        actionBox.setAlignment(Pos.CENTER);
        return actionBox;
    }

    // ------------------------------------------------------------------
    // 更新
    // ------------------------------------------------------------------

    /** 刷新双方状态面板。 */
    public void refreshPokemon(Pokemon player, Pokemon wild) {
        playerName.setText(player.getName());
        playerInfo.setText(typeOf(player) + "  Lv." + player.getLevel());
        refreshHp(playerHpBar, playerHpText, player);
        refreshExp(playerExpBar, playerExpText, player);

        wildName.setText(wild.getName());
        wildInfo.setText(typeOf(wild) + "  Lv." + wild.getLevel());
        refreshHp(wildHpBar, wildHpText, wild);
    }

    /** 刷新天气/场地状态栏（无天气/场地时清空）。 */
    public void refreshFieldStatus(Weather weather, Terrain terrain) {
        StringBuilder sb = new StringBuilder();
        if (weather.isActive()) {
            sb.append("天气：").append(weather.getDisplayName());
        }
        if (terrain.isActive()) {
            if (sb.length() > 0) {
                sb.append("    ");
            }
            sb.append("场地：").append(terrain.getDisplayName());
        }
        fieldStatus.setText(sb.toString());
    }

    /** 显示战斗日志（覆盖整个日志框）。 */
    public void showLog(List<String> lines) {
        logArea.clear();
        for (String line : lines) {
            logArea.appendText(line + System.lineSeparator());
        }
    }

    // ---- 底部按钮组 ----

    /** 主菜单按钮：技能 / 背包 / 精灵 / 逃跑。 */
    public void showMainMenu(Runnable onSkills, Runnable onBag, boolean canSwitch, Runnable onSwitch) {
        actionBox.getChildren().clear();
        Button skillBtn = styledButton("技能");
        skillBtn.setOnAction(e -> onSkills.run());
        Button bagBtn = styledButton("背包");
        bagBtn.setOnAction(e -> onBag.run());
        Button switchBtn = styledButton("精灵");
        switchBtn.setDisable(!canSwitch);
        switchBtn.setOnAction(e -> onSwitch.run());
        Button runBtn = styledButton("逃跑");
        runBtn.setOnAction(e -> actions.onRun());

        HBox row = new HBox(10, skillBtn, bagBtn, switchBtn, runBtn);
        row.setAlignment(Pos.CENTER);
        actionBox.getChildren().add(row);
    }

    /** 显示技能选择（最多 4 个）：2×2 网格，技能名与 PP 同行，避免单行放不下被截断。 */
    public void showMoveMenu(List<MoveSlot> slots, Runnable onBack) {
        actionBox.getChildren().clear();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < 4; i++) {
            MoveSlot slot = i < slots.size() ? slots.get(i) : null;
            Button b;
            if (slot == null) {
                b = styledButton("—");
                b.setDisable(true);
            } else {
                String pp = slot.exhausted()
                        ? "PP 不足"
                        : "PP " + slot.getPp() + "/" + slot.getMove().getMaxPp();
                b = styledButton(slot.getMove().getName() + "    " + pp + "\n"
                        + slot.getMove().getType().getDisplayName()
                        + " · " + moveBrief(slot.getMove()));
                b.setDisable(slot.exhausted());
                b.setOnAction(e -> actions.onMoveSelected(slot));
            }
            b.setMaxWidth(300);
            grid.add(b, i % 2, i / 2);
        }
        Button back = styledButton("返回");
        back.setOnAction(e -> onBack.run());
        VBox v = new VBox(8, grid, back);
        v.setAlignment(Pos.CENTER);
        actionBox.getChildren().add(v);
    }

    /** 背包中的一个可点击道具条目。 */
    public record ItemButton(String text, boolean disabled) {
    }    /** 显示背包可用道具。点击某个条目时调用 onPick(index)。 */
    public void showBagMenu(List<ItemButton> items, java.util.function.IntConsumer onPick, Runnable onBack) {
        actionBox.getChildren().clear();
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i < items.size(); i++) {
            ItemButton it = items.get(i);
            int index = i;
            Button b = styledButton(it.text());
            b.setDisable(it.disabled());
            b.setOnAction(e -> onPick.accept(index));
            row.getChildren().add(b);
        }
        Button back = styledButton("返回");
        back.setOnAction(e -> onBack.run());
        VBox v = new VBox(8, row, back);
        v.setAlignment(Pos.CENTER);
        actionBox.getChildren().add(v);
    }

    /** 显示我方队伍：点击某只健康且非当前出战的精灵执行切换。 */
    public void showPartyMenu(List<Pokemon> party, int activeIndex, IntConsumer onPick, Runnable onBack) {
        actionBox.getChildren().clear();
        FlowPane grid = new FlowPane(8, 8);
        grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < party.size(); i++) {
            Pokemon p = party.get(i);
            int index = i;
            String hp = p.isFainted() ? "已倒下" : "HP " + p.getCurrentHp() + " / " + p.getMaxHp();
            String prefix = i == activeIndex ? "★ " : "";
            Button b = styledButton(prefix + p.getName() + "  Lv." + p.getLevel() + "\n" + hp);
            b.setDisable(p.isFainted() || i == activeIndex);
            b.setOnAction(e -> onPick.accept(index));
            grid.getChildren().add(b);
        }
        Button back = styledButton("返回");
        back.setOnAction(e -> onBack.run());
        VBox v = new VBox(8, grid, back);
        v.setAlignment(Pos.CENTER);
        actionBox.getChildren().add(v);
    }

    /**
     * 显示「学会新技能」抉择：精灵已掌握 4 个技能，玩家点选一个当前技能将其遗忘并学习
     * 新技能，或点击「放弃学习」跳过。
     *
     * @param prompt    说明文字（含精灵与新技能名）
     * @param slots     当前已掌握的技能（必为 4 个）
     * @param onForget  选中槽位下标（0~3）时回调
     * @param onDecline 选择放弃学习时回调
     */
    public void showLearnMoveMenu(String prompt, List<MoveSlot> slots,
                                  IntConsumer onForget, Runnable onDecline) {
        actionBox.getChildren().clear();
        Label title = new Label(prompt);
        title.setWrapText(true);
        title.setMaxWidth(600);
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px;"
                + "-fx-font-weight: bold;");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < slots.size(); i++) {
            MoveSlot slot = slots.get(i);
            int index = i;
            String pp = slot.exhausted()
                    ? "PP 不足"
                    : "PP " + slot.getPp() + "/" + slot.getMove().getMaxPp();
            Button b = styledButton(slot.getMove().getName() + "    " + pp + "\n"
                    + slot.getMove().getType().getDisplayName() + " · " + moveBrief(slot.getMove()));
            b.setOnAction(e -> onForget.accept(index));
            grid.add(b, i % 2, i / 2);
        }
        Button decline = styledButton("放弃学习");
        decline.setOnAction(e -> onDecline.run());
        VBox v = new VBox(8, title, grid, decline);
        v.setAlignment(Pos.CENTER);
        actionBox.getChildren().add(v);
    }

    public void clearActions() {
        actionBox.getChildren().clear();
    }

    /** 战斗结束：清空按钮，只显示结果与返回按钮。 */
    public void showResult(String summary) {
        actionBox.getChildren().clear();
        Label result = new Label(summary);
        result.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 15px; -fx-font-weight: bold;");
        Button back = styledButton("返回主菜单");
        back.setOnAction(e -> actions.onExit());
        VBox v = new VBox(10, result, back);
        v.setAlignment(Pos.CENTER);
        actionBox.getChildren().add(v);
    }

    private Button styledButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px;"
                + "-fx-padding: 8 16; -fx-background-radius: 6;");
        return b;
    }

    private static String typeOf(Pokemon p) {
        List<String> types = p.getSpecies().getTypes().stream()
                .map(t -> t.getDisplayName()).toList();
        return String.join(" / ", types);
    }

    /** 技能威力/类别简写：变化类显示「变化」，其余显示「威力 X」。 */
    private static String moveBrief(Move move) {
        return move.isStatus() ? "变化" : "威力 " + move.getPower();
    }

    private static void refreshHp(ProgressBar bar, Label text, Pokemon p) {
        double ratio = p.getMaxHp() <= 0 ? 0 : (double) p.getCurrentHp() / p.getMaxHp();
        bar.setProgress(Math.max(0, ratio));
        text.setText("HP " + p.getCurrentHp() + " / " + p.getMaxHp());
        String color;
        if (ratio > 0.5) {
            color = "limegreen";
        } else if (ratio > 0.2) {
            color = "#f0a020";
        } else {
            color = "#e04040";
        }
        bar.setStyle("-fx-accent: " + color + ";");
    }

    /** 刷新经验条：进度 = 当前经验 / 升到下一级所需；满级显示 MAX。 */
    private static void refreshExp(ProgressBar bar, Label text, Pokemon p) {
        long need = p.expToNextLevel();
        if (need <= 0) {
            bar.setProgress(1.0);
            text.setText("EXP MAX");
            return;
        }
        long cur = p.getExp();
        bar.setProgress(Math.max(0.0, Math.min(1.0, (double) cur / need)));
        text.setText("EXP " + cur + " / " + need);
    }
}
