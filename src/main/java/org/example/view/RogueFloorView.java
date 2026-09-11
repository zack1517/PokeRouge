package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.example.GameSession;
import org.example.model.Option;
import org.example.model.RunData;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

import java.util.function.Consumer;

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
        Label title = new Label("第 " + data.getCurrentFloor() + " 层 · 肉鸽事件");
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #222;");

        Label summary = new Label("剩余点数：" + data.getCurrentPoints());
        summary.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #555;");

        // 半透明 header：标题叠在地图背景上可读，同时背景明显透出（与主菜单一致）
        VBox header = new VBox(2, title, summary);
        header.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6); -fx-background-radius: 10;"
                + " -fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 10;"
                + " -fx-padding: 8 14 10 14;");
        root.getChildren().add(header);

        if (session.isRoguePointsExhausted()) {
            Label bossHint = new Label(data.getCurrentPoints() <= 0
                    ? "点数耗尽，BOSS 战正在自动展开！其余事件已被隐藏。"
                    : "剩余点数已买不起任何事件，本层 BOSS 战即将展开！");
            bossHint.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #8b3a00;");
            bossHint.setWrapText(true);
            root.getChildren().addAll(bossHint, buildBackButton());
            return UiScale.scene(root);
        }

        if (data.getAvailableOptions() == null || data.getAvailableOptions().isEmpty()) {
            Label empty = new Label("本层事件已全部处理，准备进入下一层...");
            empty.setStyle("-fx-font-size: 14px; -fx-font-family: 'Microsoft YaHei';");
            root.getChildren().addAll(empty, buildBackButton());
            return UiScale.scene(root);
        }

        VBox optionsBox = new VBox(12);
        for (Option option : data.getAvailableOptions()) {
            if (option.isHiddenEvent()) {
                Label hidden = new Label("隐藏事件：已被揭晓，等待新的暗面...");
                hidden.setWrapText(true);
                hidden.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #666;");
                optionsBox.getChildren().add(hidden);
                continue;
            }

            Button btn = new Button(option.getName() + "  (消耗 " + option.getCost() + " 点)");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-padding: 12 18; -fx-alignment: CENTER_LEFT;");
            btn.setWrapText(true);
            btn.setOnAction(e -> onOptionSelected.accept(option));

            Label desc = new Label(option.getDescription());
            desc.setWrapText(true);
            desc.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #444;");

            VBox entry = new VBox(4, btn, desc);
            entry.setStyle("-fx-background-color: rgba(255,255,255,0.75); -fx-padding: 8; -fx-background-radius: 8;");
            optionsBox.getChildren().add(entry);
        }

        ScrollPane scroll = new ScrollPane(optionsBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        root.getChildren().addAll(scroll, buildBackButton());
        return UiScale.scene(root);
    }

    /** 返回主菜单按钮：保留本轮楼层进度，再次点击「进入层内事件」可继续。 */
    private Button buildBackButton() {
        Button back = new Button("返回主菜单");
        back.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-padding: 8 14;");
        back.setOnAction(e -> onBack.run());
        return back;
    }
}
