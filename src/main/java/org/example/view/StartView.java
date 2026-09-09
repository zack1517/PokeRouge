package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.config.AppConfig;
import org.example.util.ImageBackgrounds;
import org.example.util.LogUtil;
import org.example.util.UiScale;

/**
 * 游戏启动页（第一屏）：整页铺 bg_main 主画面背景，中央半透明卡片承载四个入口按钮。
 * <p>「开始游戏」进入初始宝可梦选择流程（由 {@code MainController} 接线）；
 * 「继续游戏」「成就系统」「设置选项」为预留入口，当前仅打印日志占位。</p>
 */
public final class StartView {

    /** 启动页背景（与 {@link StarterSelectionView} 同款主画面）。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_main.jpeg";

    /** 四个入口按钮统一宽度（设计分辨率 px，随 UiScale 等比缩放）。 */
    private static final double BUTTON_WIDTH = 180;

    private final Runnable onStartGame;

    public StartView(Runnable onStartGame) {
        this.onStartGame = onStartGame;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);
        root.setPadding(new Insets(12));

        Label title = new Label(AppConfig.APP_TITLE);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 24px; -fx-font-weight: bold;");

        Button startGame = menuButton("开始游戏");
        startGame.setOnAction(e -> onStartGame.run());

        Button continueGame = menuButton("继续游戏");
        continueGame.setOnAction(e -> LogUtil.info("[StartView] 继续游戏：加载存档功能待实现（预留入口）"));

        Button achievements = menuButton("成就系统");
        achievements.setOnAction(e -> LogUtil.info("[StartView] 成就系统：成就展示与管理功能待实现（预留入口）"));

        Button settings = menuButton("设置选项");
        settings.setOnAction(e -> LogUtil.info("[StartView] 设置选项：音效、画面等配置功能待实现（预留入口）"));

        // 半透明白卡承载标题与四个入口（与 StarterSelectionView 的卡片同款：0.65 白底 + 浅灰描边）
        VBox card = new VBox(10, title, startGame, continueGame, achievements, settings);
        card.setMaxWidth(260);
        // 关键：BorderPane 会把 center 子节点拉满可用高度，不设上限时卡片背景将撑满整窗高；
        // maxHeight 用内容首选高封顶后，卡片按内容收拢并垂直居中。
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPadding(new Insets(18));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.65);"
                + "-fx-border-color: #c9c9c9; -fx-border-radius: 10; -fx-background-radius: 10;");
        root.setCenter(card);
        return UiScale.scene(root);
    }

    /** 入口按钮统一风格：微软雅黑 14px，四键等宽以便垂直对齐。 */
    private static Button menuButton(String text) {
        Button button = new Button(text);
        button.setMaxWidth(BUTTON_WIDTH);
        button.setPrefWidth(BUTTON_WIDTH);
        button.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-padding: 6 18;");
        return button;
    }
}
