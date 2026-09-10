package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.GameSession;
import org.example.model.Option;
import org.example.model.RouteConfig;
import org.example.model.RoutePhase;
import org.example.model.RunData;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

import java.util.function.Consumer;

/**
 * 路线节点页：显示当前段、行动点与金币，并按《需求文档》§4.2 列出本段节点供玩家消耗行动点进入。
 *
 * <p>三类呈现：</p>
 * <ul>
 *   <li>可进入的路线节点 —— 按钮形式，标题带行动点消耗；</li>
 *   <li>已走过的节点 —— 灰色只读文字（由 {@link Option#isConsumed()} 标记，取代旧版
 *       「名字等于『隐藏事件』」的哨兵写法）；</li>
 *   <li>行动点耗尽 / 无节点可走 —— 提示必然节点（道馆战）即将展开，并给出「挑战道馆」按钮。</li>
 * </ul>
 */
public class RogueFloorView {

    private final GameSession session;
    private final Consumer<Option> onOptionSelected;
    private final Runnable onBack;

    public RogueFloorView(GameSession session, Consumer<Option> onOptionSelected, Runnable onBack) {
        this.session = session;
        this.onOptionSelected = onOptionSelected;
        this.onBack = onBack;
    }

    public Scene createScene() {
        VBox root = new VBox(16);
        ImageBackgrounds.apply(root, session.mapBackgroundPath()); // 与主菜单同款地图背景
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.TOP_LEFT);

        RunData data = session.getRogueRunData();
        Label title = new Label("第 " + session.getSegment() + " / " + RouteConfig.TOTAL_SEGMENTS
                + " 段 · " + data.getPhase().getDisplayName());
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #222;");

        Label summary = new Label("行动点：" + data.getAp() + " / " + data.getApMax()
                + "　　金币：" + data.getGold() + "  🪙");
        summary.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px; -fx-text-fill: #555;");

        // 半透明 header：标题叠在地图背景上可读，同时背景明显透出（与主菜单一致）
        VBox header = new VBox(2, title, summary);
        header.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6); -fx-background-radius: 10;"
                + " -fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 10;"
                + " -fx-padding: 8 14 10 14;");
        root.getChildren().add(header);

        if (session.isRogueRunFinished()) {
            Label done = new Label(session.isRogueRunCleared()
                    ? "本轮远征已通关！"
                    : "本轮远征已结束……");
            done.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #8b3a00;");
            root.getChildren().addAll(done, buildBackButton());
            return UiScale.scene(root);
        }

        if (data.getPhase().isMandatoryBattle()) {
            root.getChildren().addAll(buildMandatoryBox(data), buildBackButton());
            return UiScale.scene(root);
        }

        if (!data.hasSelectableOption()) {
            Label bossHint = new Label("行动点已用尽，无法再进入本段节点 —— 道馆战即将展开！");
            bossHint.setWrapText(true);
            bossHint.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #8b3a00;");
            Button gym = new Button("挑战道馆");
            gym.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-padding: 10 18;");
            gym.setOnAction(e -> onOptionSelected.accept(data.getMandatoryOption()));
            root.getChildren().addAll(bossHint, gym, buildBackButton());
            return UiScale.scene(root);
        }

        VBox optionsBox = new VBox(12);
        for (Option option : data.getAvailableOptions()) {
            optionsBox.getChildren().add(buildOptionEntry(option, data));
        }
        optionsBox.getChildren().add(buildMandatoryPreview(data));

        ScrollPane scroll = new ScrollPane(optionsBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        root.getChildren().addAll(scroll, buildBackButton());
        return UiScale.scene(root);
    }

    /** 单个节点：可进入时是按钮，已走过时是灰色只读条目。 */
    private VBox buildOptionEntry(Option option, RunData data) {
        if (option.isConsumed()) {
            Label used = new Label("［已走过］" + option.getName());
            used.setWrapText(true);
            used.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #777;");
            VBox usedEntry = new VBox(used);
            usedEntry.setStyle("-fx-background-color: rgba(235,235,235,0.7); -fx-padding: 8; -fx-background-radius: 8;");
            return usedEntry;
        }

        boolean affordable = option.getCost() <= data.getAp();
        String costText = option.getCost() == 0 ? "不消耗行动点" : "消耗 " + option.getCost() + " 点行动点";
        Button btn = new Button(option.getTypeDisplayName() + " · " + option.getName() + "  (" + costText + ")");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setWrapText(true);
        btn.setDisable(!affordable);
        btn.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-padding: 12 18; -fx-alignment: CENTER_LEFT;");
        btn.setOnAction(e -> onOptionSelected.accept(option));

        Label desc = new Label(option.getDescription());
        desc.setWrapText(true);
        desc.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #444;");

        VBox entry = new VBox(4, btn, desc);
        entry.setStyle("-fx-background-color: rgba(255,255,255,0.75); -fx-padding: 8; -fx-background-radius: 8;");
        return entry;
    }

    /** 必然节点预告：让玩家在规划路线时知道行动点耗尽的后果。 */
    private Label buildMandatoryPreview(RunData data) {
        Label preview = new Label("⚠ 行动点耗尽或本段无可走节点时，必然触发：" + mandatoryName(data));
        preview.setWrapText(true);
        preview.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #8b3a00;");
        return preview;
    }

    /** 必然节点阶段：显示节点信息与挑战按钮。 */
    private VBox buildMandatoryBox(RunData data) {
        Option mandatory = data.getMandatoryOption();
        String name = mandatory == null ? data.getPhase().getDisplayName() : mandatory.getName();
        String description = mandatory == null
                ? data.getPhase().getDisplayName()
                : mandatory.getDescription();

        Label hint = new Label("必然节点：" + name);
        hint.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #8b3a00;");

        Label desc = new Label(description);
        desc.setWrapText(true);
        desc.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #444;");

        Label retry = new Label(retryText(data));
        retry.setWrapText(true);
        retry.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #555;");

        Button fight = new Button("开始挑战");
        fight.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-padding: 10 18;");
        fight.setOnAction(e -> onOptionSelected.accept(data.getMandatoryOption()));

        HBox row = new HBox(10, fight);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, hint, desc, retry, row);
        box.setStyle("-fx-background-color: rgba(255,255,255,0.78); -fx-padding: 12; -fx-background-radius: 8;");
        return box;
    }

    private String retryText(RunData data) {
        if (data.getPhase().allowsOneRetry()) {
            return data.canRetry() ? "本节点可失败一次：战败只扣金币并可再挑战一次。"
                    : "已用过本段的失败机会：再战败本轮远征即结束。";
        }
        return "本节点不可失败：战败本轮远征立即结束。";
    }

    private String mandatoryName(RunData data) {
        Option mandatory = data.getMandatoryOption();
        return mandatory == null ? RoutePhase.GYM.getDisplayName() : mandatory.getName();
    }

    /** 返回主菜单按钮：保留本轮进程，再次点击「进入路线节点」可继续。 */
    private Button buildBackButton() {
        Button back = new Button("返回主菜单");
        back.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-padding: 8 14;");
        back.setOnAction(e -> onBack.run());
        return back;
    }
}
