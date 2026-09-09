package org.example;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.config.AppConfig;
import org.example.controller.MainController;

/**
 * JavaFX 应用主类。
 * <p>遵循生命周期：init() -> start() -> stop()。
 * 界面层级：Stage -> Scene -> Pane -> Node。</p>
 */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        // 1. 窗口级配置
        stage.setTitle(AppConfig.APP_TITLE);
        stage.setResizable(AppConfig.RESIZABLE);

        // 2. 主控制器负责会话与场景切换
        MainController controller = new MainController(stage);
        controller.bindStageEvents();
        controller.showStarterSelection();

        // 3. 显示窗口
        stage.show();
    }
}
