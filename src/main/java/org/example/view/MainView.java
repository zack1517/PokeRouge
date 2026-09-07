package org.example.view;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import org.example.config.AppConfig;

/**
 * 主视图：负责构建 Stage 内的界面层级 Scene -> Pane -> Node。
 * <p>当前仅为骨架占位内容，游戏/业务界面后续在此组装（[待确认]）。</p>
 */
public class MainView {

    /** 创建应用主场景。 */
    public Scene createScene() {
        Pane root = new Pane();

        Label placeholder = new Label("JavaFX 工程骨架已就绪（v0.0.1-skeleton）");
        placeholder.setLayoutX(20);
        placeholder.setLayoutY(20);
        root.getChildren().add(placeholder);

        return new Scene(root, AppConfig.WINDOW_WIDTH, AppConfig.WINDOW_HEIGHT);
    }
}
