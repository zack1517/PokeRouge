package org.example.view;

import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

/**
 * 自定义战斗模式选择页：由启动页「自定义战斗」进入，无卡片悬浮式布局。
 *
 * <p>与启动页同一套视觉体系（背景插画 + 暗角遮罩，标题与胶囊入口直接悬浮其上，
 * 不再使用半透明中央卡片）：按钮结构、悬停/选中动效、入场动画与键盘导航由
 * {@link FloatingMenu} 复用（与启动页完全一致），样式集中在 {@code /css/start-menu.css}，
 * 本类只负责结构与接线。</p>
 *
 * <p>四种模式均已接入真实战斗：点击后经 {@link Mode} 回调交给控制器，先在
 * {@link CustomBattleSetupView} 组建满级满状态队伍（1 vs 1 / 2 vs 2 / 自定义数量为固定只数，
 * 小队对战为 1~6 只任意），再与随机生成的同数量满级对手整队轮战。
 * 「返回」（或 Esc）回到启动页。</p>
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
    private static final String MAIN_BACKGROUND = "/images/background/bg_startpage.jpg";

    /** 共享样式表（胶囊按钮/图标/暗角/标题，与启动页同一份）。 */
    private static final String STYLE_SHEET = "/css/start-menu.css";

    /** 五个入口的 24 单位视口单色描边图标（lucide 风格手绘简化版）。 */
    private static final String ICON_DUEL_1V1 = "M12 11 A3.5 3.5 0 1 0 12 4 A3.5 3.5 0 1 0 12 11"
            + " M6 20 C6 16.5 8.5 14.5 12 14.5 C15.5 14.5 18 16.5 18 20";
    private static final String ICON_DUEL_2V2 = "M9 10.5 A3 3 0 1 0 9 4.5 A3 3 0 1 0 9 10.5"
            + " M3 19.5 C3 16.6 5.4 14.6 9 14.6 C12.6 14.6 15 16.6 15 19.5"
            + " M16 5 A3 3 0 0 1 16 11 M17.3 14.7 C19.9 15.3 21 17 21 19.5";
    private static final String ICON_SQUAD = "M5 21 L5 3.5"
            + " M5 4 C7.5 2.8 10 2.8 12 4 C14 5.2 16.5 5.2 19 4"
            + " L19 12.5 C16.5 13.7 14 13.7 12 12.5 C10 11.3 7.5 11.3 5 12.5";
    private static final String ICON_CUSTOM_COUNT = "M4 9 L20 9 M4 15 L20 15 M10 3 L8 21 M16 3 L14 21";
    private static final String ICON_BACK = "M19 12 L5 12 M11 18 L5 12 L11 6";

    private final Runnable onBack;
    private final Consumer<Mode> onSelectMode;

    public CustomBattleView(Runnable onBack, Consumer<Mode> onSelectMode) {
        this.onBack = onBack;
        this.onSelectMode = onSelectMode;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);

        // 暗角遮罩：叠在背景图之上、内容层之下（与启动页同款，弱化背景保证悬浮元素可读）
        Region vignette = new Region();
        vignette.getStyleClass().add("start-vignette");
        vignette.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        vignette.setMouseTransparent(true);

        FloatingMenu menu = new FloatingMenu();

        Label title = new Label("自定义战斗");
        title.getStyleClass().add("subpage-title");

        // 对战模式入口：结构/动效与启动页一致，行为交由控制器路由（先配置队伍，再进入真实战斗）
        menu.addPill("crimson", "1 vs 1", "SINGLE DUEL", ICON_DUEL_1V1,
                () -> onSelectMode.accept(Mode.ONE_V_ONE));
        menu.addPill("crimson", "2 vs 2", "DOUBLE DUEL", ICON_DUEL_2V2,
                () -> onSelectMode.accept(Mode.TWO_V_TWO));
        menu.addPill("crimson", "小队对战", "SQUAD BATTLE", ICON_SQUAD,
                () -> onSelectMode.accept(Mode.SQUAD));
        menu.addPill("crimson", "自定义数量战斗", "CUSTOM COUNT", ICON_CUSTOM_COUNT,
                () -> onSelectMode.accept(Mode.CUSTOM_COUNT));
        menu.addPill("slate", "返回", "BACK", ICON_BACK, onBack);

        BorderPane layout = new BorderPane();
        layout.setCenter(buildColumn(title, menu));
        root.getChildren().addAll(vignette, layout);

        Scene scene = UiScale.scene(root);
        var css = CustomBattleView.class.getResource(STYLE_SHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        menu.installKeyboard(scene, onBack, null); // Esc 返回启动页；其余按键本页不处理
        menu.selectFirst();
        menu.playEntrance();
        return scene;
    }

    /** 中央悬浮列：标题 + 五个胶囊入口（按内容收拢后垂直居中，与启动页同款布局方式）。 */
    private static Region buildColumn(Label title, FloatingMenu menu) {
        VBox column = new VBox(16, title, menu.node());
        column.setAlignment(Pos.CENTER);
        // 关键：BorderPane 会把 center 子节点拉满可用高度，不设上限时整列会被垂直撑开；
        // maxHeight 用内容首选高封顶后，整列按内容收拢并垂直居中。
        column.setMaxHeight(Region.USE_PREF_SIZE);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        return column;
    }
}
