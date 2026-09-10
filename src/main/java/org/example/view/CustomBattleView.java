package org.example.view;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

/**
 * 自定义战斗模式选择页：由启动页「自定义战斗」进入，中央半透明卡片承载模式入口与返回按钮。
 * <p>四种模式均已接入真实战斗：点击后经 {@link Mode} 回调交给控制器，先在
 * {@link CustomBattleSetupView} 组建满级满状态队伍（1 vs 1 / 2 vs 2 / 自定义数量为固定只数，
 * 小队对战为 1~6 只任意），再与随机生成的同数量满级对手整队轮战。「返回」回到启动页。</p>
 */
public final class CustomBattleView {

    /** 战斗模式：决定出战数量语义（队伍在 {@link CustomBattleSetupView} 中配置）。 */
    public enum Mode {
        /** 1 vs 1：双方各 1 只。 */
        ONE_V_ONE,
        /** 2 vs 2：双方各 2 只。 */
        TWO_V_TWO,
        /** 小队对战：双方各 1~6 只任意。 */
        SQUAD,
        /** 自定义数量：进入队伍配置前先选择双方数量（1~6）。 */
        CUSTOM_COUNT
    }

    /** 页面背景（与启动页同款主画面）。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_main.jpeg";

    /** 入口按钮统一宽度（设计分辨率 px，随 UiScale 等比缩放）。 */
    private static final double BUTTON_WIDTH = 180;

    private final Runnable onBack;
    private final Consumer<Mode> onSelectMode;

    public CustomBattleView(Runnable onBack, Consumer<Mode> onSelectMode) {
        this.onBack = onBack;
        this.onSelectMode = onSelectMode;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);
        root.setPadding(new Insets(12));

        Label title = new Label("自定义战斗");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 24px; -fx-font-weight: bold;");

        // 对战模式入口：四种模式均交给控制器路由（先配置队伍，再进入真实战斗）
        Button duel1v1 = modeButton("1 vs 1");
        duel1v1.setOnAction(e -> onSelectMode.accept(Mode.ONE_V_ONE));

        Button duel2v2 = modeButton("2 vs 2");
        duel2v2.setOnAction(e -> onSelectMode.accept(Mode.TWO_V_TWO));

        Button squad = modeButton("小队对战");
        squad.setOnAction(e -> onSelectMode.accept(Mode.SQUAD));

        Button customCount = modeButton("自定义数量战斗");
        customCount.setOnAction(e -> onSelectMode.accept(Mode.CUSTOM_COUNT));

        Button back = modeButton("返回");
        back.setOnAction(e -> onBack.run());

        // 半透明白卡承载标题与按钮（与启动页同款：0.65 白底 + 浅灰描边）
        VBox card = new VBox(10, title, duel1v1, duel2v2, squad, customCount, back);
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

    /** 入口按钮统一风格：微软雅黑 14px，等宽以便垂直对齐（与启动页一致）。 */
    private static Button modeButton(String text) {
        Button button = new Button(text);
        button.setMaxWidth(BUTTON_WIDTH);
        button.setPrefWidth(BUTTON_WIDTH);
        button.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-padding: 6 18;");
        return button;
    }
}
