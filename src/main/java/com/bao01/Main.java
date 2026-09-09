package com.bao01;

import com.bao01.view.BattleView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 应用入口。
 *
 * <p>JavaFX 生命周期：{@code init()} → {@link #start(Stage)} → {@code stop()}；
 * 界面层级：Stage（舞台）→ Scene（场景）→ 根节点（BorderPane）→ 子节点。
 *
 * <p>当前版本：宝可梦基础对战（1v1 回合制，速度决定先手权）。
 * 界面由 {@link BattleView} 程序化构建，控制器在 controller 包内。
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        BattleView battleView = new BattleView();

        Scene scene = new Scene(battleView.getRoot(), 860, 640);
        stage.setTitle("bao01 · 宝可梦基础对战");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
