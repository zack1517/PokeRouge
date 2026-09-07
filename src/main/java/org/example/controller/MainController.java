package org.example.controller;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.example.config.AppConfig;
import org.example.util.LogUtil;

import java.util.Optional;

/**
 * 主控制器：负责窗口生命周期与用户交互逻辑，是视图与模型之间的桥梁。
 */
public class MainController {

    private final Stage stage;

    public MainController(Stage stage) {
        this.stage = stage;
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
        alert.setHeaderText(AppConfig.EXIT_CONFIRM_HEADER);
        alert.setContentText(AppConfig.EXIT_CONFIRM_CONTENT);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
