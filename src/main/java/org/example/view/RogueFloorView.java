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

import java.util.function.Consumer;

public class RogueFloorView {
    private final GameSession session;
    private final Consumer<Option> onOptionSelected;

    public RogueFloorView(GameSession session, Consumer<Option> onOptionSelected) {
        this.session = session;
        this.onOptionSelected = onOptionSelected;
    }

    public Scene createScene() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_LEFT);

        RunData data = session.getRogueRunData();
        Label title = new Label("第 " + data.getCurrentFloor() + " 层 · 肉鸽事件");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-font-family: 'Microsoft YaHei';");

        Label summary = new Label("剩余点数：" + data.getCurrentPoints());
        summary.setStyle("-fx-font-size: 14px; -fx-font-family: 'Microsoft YaHei';");

        root.getChildren().addAll(title, summary);

        if (data.getCurrentPoints() <= 0) {
            Label bossHint = new Label("点数耗尽，BOSS 战正在自动展开！其余事件已被隐藏。");
            bossHint.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #8b3a00;");
            root.getChildren().add(bossHint);
            return new Scene(root, 760, 520);
        }

        if (data.getAvailableOptions() == null || data.getAvailableOptions().isEmpty()) {
            Label empty = new Label("本层事件已全部处理，准备进入下一层...");
            empty.setStyle("-fx-font-size: 14px; -fx-font-family: 'Microsoft YaHei';");
            root.getChildren().add(empty);
            return new Scene(root, 760, 520);
        }

        VBox optionsBox = new VBox(12);
        for (Option option : data.getAvailableOptions()) {
            if ("隐藏事件".equals(option.getName())) {
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
        root.getChildren().add(scroll);
        return new Scene(root, 760, 520);
    }
}
