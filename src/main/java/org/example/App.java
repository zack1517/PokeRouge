package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.config.AppConfig;
import org.example.controller.MainController;
import org.example.view.MainView;

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

        // 2. 组装视图（Scene -> Pane -> Node）
        MainView mainView = new MainView();
        Scene scene = mainView.createScene();
        stage.setScene(scene);

        // 3. 绑定控制器（交互与窗口事件）
        MainController controller = new MainController(stage);
        controller.bindStageEvents();

        // 4. 显示窗口
        stage.show();
    }
}
