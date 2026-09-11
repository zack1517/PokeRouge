package org.example.view;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import org.example.util.ImageBackgrounds;
import org.example.util.LogUtil;
import org.example.util.MusicPlayer;
import org.example.util.UiScale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏启动页（第一屏）：无卡片悬浮式主菜单。
 *
 * <p>样式改编自「临时/主界面设计代码」（Web/React 版主界面）：背景插画铺满整页，
 * 彩色 Logo 与五个胶囊入口按钮直接悬浮其上（不再使用半透明中央卡片），
 * 可读性由元素自身的描边、投影与底色承担；样式集中在
 * {@code /css/start-menu.css}，本类只负责结构与动效。</p>
 *
 * <p>功能接线与原版完全一致（不受样式改编影响）：「开始游戏」进入初始宝可梦选择流程；
 * 「自定义战斗」进入模式选择页（均由 {@code MainController} 接线）；
 * 「宝可梦图鉴」「成就系统」「设置选项」为预留入口，当前仅打印日志占位。</p>
 *
 * <p>交互细节：鼠标悬停与方向键 ↑/↓ 切换选中项（黄描边蓝底胶囊 + 放大 1.05），
 * Enter 触发选中项、Esc 取消选中、M 切换主界面 BGM 静音；
 * 入场动效为 Logo 落下淡入 + 按钮错峰上浮淡入，Logo 随后进入无限轻浮动。</p>
 */
public final class StartView {

    /** 启动页背景（源 临时/03-素材与图片处理/background/bg_startpage.png，中心预裁 1280×853）。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_startpage.jpg";

    /** 彩色 Logo（与 临时/03-素材与图片处理/logo/logo.png 同一文件）。 */
    private static final String LOGO_IMAGE = "/images/logo/logo.png";

    /** 启动页样式表（classpath）。 */
    private static final String STYLE_SHEET = "/css/start-menu.css";

    /** Logo 显示宽度（设计画布 px；原图 2784×1632 等比缩放）。 */
    private static final double LOGO_WIDTH = 250;

    /** 胶囊按钮统一宽高（设计画布 px）。 */
    private static final double MENU_WIDTH = 216;

    private static final double MENU_HEIGHT = 40;

    /** 按钮垂直间距。 */
    private static final double MENU_GAP = 8;

    /** 整列（Logo+菜单）垂直偏移量：底部信息条移除后的构图微调（设计画布 px，负值上移）。 */
    private static final double COLUMN_OFFSET_Y = -10;

    /** 图标圆底直径。 */
    private static final double ICON_WRAP_SIZE = 26;

    /** 按钮内容区左右内边距之和（与 CSS 中 -fx-padding 的 12+12 对应）。 */
    private static final double MENU_PADDING_H = 24;

    /** 悬停上浮位移（负值向上）。 */
    private static final double HOVER_LIFT = -2;

    /** 选中放大倍数。 */
    private static final double SELECTED_SCALE = 1.05;

    /** 按钮入场起始位移。 */
    private static final double ENTRANCE_RISE = 11;

    /** Logo 入场起始位移（从上方落下）。 */
    private static final double LOGO_DROP = -13;

    /** Logo 无限浮动幅度（负值向上）。 */
    private static final double FLOAT_AMPLITUDE = -4.5;

    /** 主界面 BGM 音量（与 MusicPlayer 默认一致，M 键静音后按此恢复）。 */
    private static final double VOLUME = 0.5;

    /** 选中态伪类（由 CSS {@code .menu-pill:selected} 承载选中视觉）。 */
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /** 五个入口的 24 单位视口单色描边图标（lucide 风格手绘简化版）。 */
    private static final String ICON_PLAY = "M7 4 L20 12 L7 20 Z";
    private static final String ICON_BOOK = "M12 6.5 C9.6 4.8 6.6 4.2 4.2 4.2 L4.2 18.2 C6.6 18.2 9.6 18.8 12 20.5"
            + " C14.4 18.8 17.4 18.2 19.8 18.2 L19.8 4.2 C17.4 4.2 14.4 4.8 12 6.5 Z M12 6.5 L12 20.5";
    private static final String ICON_SWORDS = "M4 20.5 L16 8.5 M12.3 9.8 L14.7 12.2 M20 20.5 L8 8.5 M11.7 9.8 L9.3 12.2";
    private static final String ICON_TROPHY = "M8 4 L16 4 L16 9 C16 11.2 14.2 13 12 13 C9.8 13 8 11.2 8 9 Z"
            + " M8 5.5 L5.5 5.5 L5.5 7.5 C5.5 9 6.5 10 8 10 M16 5.5 L18.5 5.5 L18.5 7.5 C18.5 9 17.5 10 16 10"
            + " M12 13 L12 16.5 M8.5 16.5 L15.5 16.5 L15.5 19 L8.5 19 Z";
    private static final String ICON_SETTINGS = "M20 7 L11 7 M14 17 L5 17"
            + " M17 20 A3 3 0 1 0 17 14 A3 3 0 1 0 17 20 M7 10 A3 3 0 1 0 7 4 A3 3 0 1 0 7 10";

    private final Runnable onStartGame;

    private final Runnable onCustomBattle;

    /** 五个胶囊按钮（顺序 = 视觉顺序，供键盘导航索引）。 */
    private final List<Button> menuButtons = new ArrayList<>();

    /** 每按钮悬停位移动画缓存（重复触发前先 stop，避免同属性动画竞争）。 */
    private final Map<Button, TranslateTransition> hoverLifts = new HashMap<>();

    /** 每按钮选中缩放动画缓存（同上）。 */
    private final Map<Button, ScaleTransition> selectedScales = new HashMap<>();

    /** 入场动画作用容器（Logo 外层）与无限浮动容器（Logo 内层）。 */
    private VBox logoEntrance;
    private VBox logoFloat;

    /** 当前选中项下标（-1 = 未选中；默认选中第一项「开始游戏」）。 */
    private int activeIndex = 0;

    /** 入场动画进行中（期间忽略悬停位移，避免与入场位移争抢 translateY）。 */
    private boolean entrancePlaying = true;

    /** 静音状态跨场景保持：MusicPlayer 为全局单例，重进主界面时需恢复静音。 */
    private static boolean muted;

    public StartView(Runnable onStartGame, Runnable onCustomBattle) {
        this.onStartGame = onStartGame;
        this.onCustomBattle = onCustomBattle;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);

        // 暗角遮罩：叠在背景图之上、内容层之下（弱化背景保证悬浮元素可读）
        Region vignette = new Region();
        vignette.getStyleClass().add("start-vignette");
        vignette.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        vignette.setMouseTransparent(true);

        BorderPane layout = new BorderPane();
        layout.setCenter(buildMenuColumn());

        root.getChildren().addAll(vignette, layout);

        Scene scene = UiScale.scene(root);
        var css = StartView.class.getResource(STYLE_SHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        installKeyboard(scene);
        initializeSelection();
        if (muted) {
            MusicPlayer.setVolume(0); // 回到主界面时保持静音状态（BGM 循环中不重播）
        }
        playEntranceAnimations();
        return scene;
    }

    // ------------------------------------------------------------------
    // 布局：Logo + 胶囊菜单
    // ------------------------------------------------------------------

    /** 中央悬浮列：彩色 Logo（含入场/浮动双层容器）+ 五个胶囊入口。 */
    private Region buildMenuColumn() {
        Image logoImage = ImageBackgrounds.load(LOGO_IMAGE);
        ImageView logo = new ImageView(logoImage);
        logo.setFitWidth(LOGO_WIDTH);
        logo.setPreserveRatio(true);
        logo.setSmooth(true); // 原图 2784 宽大比例缩小，关闭平滑会有锯齿
        logo.getStyleClass().add("start-logo");

        // 双层容器：外层承载入场（translateY/fade），内层承载无限浮动，互不争抢属性
        logoFloat = new VBox(logo);
        logoFloat.setAlignment(Pos.CENTER);
        logoEntrance = new VBox(logoFloat);
        logoEntrance.setAlignment(Pos.CENTER);

        VBox menu = new VBox(MENU_GAP);
        menu.setAlignment(Pos.CENTER);
        menu.setFillWidth(false);
        menu.getChildren().addAll(
                buildMenuButton("yellow", "开始游戏", "START", ICON_PLAY, onStartGame),
                buildMenuButton("blue", "宝可梦图鉴", "POKEDEX", ICON_BOOK,
                        () -> LogUtil.info("[StartView] 宝可梦图鉴：图鉴展示功能待实现（预留入口）")),
                buildMenuButton("crimson", "自定义战斗", "CUSTOM RUN", ICON_SWORDS, onCustomBattle),
                buildMenuButton("violet", "成就系统", "ACHIEVEMENTS", ICON_TROPHY,
                        () -> LogUtil.info("[StartView] 成就系统：成就展示与管理功能待实现（预留入口）")),
                buildMenuButton("slate", "设置选项", "SETTINGS", ICON_SETTINGS,
                        () -> LogUtil.info("[StartView] 设置选项：音效、画面等配置功能待实现（预留入口）")));

        VBox column = new VBox(12, logoEntrance, menu);
        column.setAlignment(Pos.CENTER);
        // 关键：BorderPane 会把 center 子节点拉满可用高度，不设上限时整列会被垂直撑开；
        // maxHeight 用内容首选高封顶后，整列按内容收拢并垂直居中。
        column.setMaxHeight(Region.USE_PREF_SIZE);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        column.setTranslateY(COLUMN_OFFSET_Y); // 底部信息条移除后整列微调，校准构图
        return column;
    }

    /**
     * 单个胶囊入口按钮：图标圆底 + 中文主标签 + 英文副标签 + 右侧 {@code >>>} 指示。
     * 视觉全部由 CSS（{@code .menu-pill} / {@code .menu-icon-wrap} 等）承担，
     * 行为上仍是标准 {@link Button}，点击回调与原版一致。
     *
     * @param tone   图标圆底色调（yellow/blue/crimson/violet/slate，与 CSS 对应）
     * @param label  中文主标签
     * @param sub    英文副标签
     * @param icon   24 单位视口的 SVG 路径数据
     * @param action 点击行为（与原入口按钮一一对应）
     */
    private Button buildMenuButton(String tone, String label, String sub, String icon, Runnable action) {
        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().addAll("menu-icon-wrap", "menu-tone-" + tone);
        iconWrap.setMinSize(ICON_WRAP_SIZE, ICON_WRAP_SIZE);
        iconWrap.setPrefSize(ICON_WRAP_SIZE, ICON_WRAP_SIZE);
        iconWrap.setMaxSize(ICON_WRAP_SIZE, ICON_WRAP_SIZE);
        SVGPath iconPath = new SVGPath();
        iconPath.setContent(icon);
        iconPath.getStyleClass().add("menu-icon");
        iconWrap.getChildren().add(iconPath);

        Label title = new Label(label);
        title.getStyleClass().add("menu-title");
        Label subLabel = new Label(sub);
        subLabel.getStyleClass().add("menu-sub");
        VBox texts = new VBox(1, title, subLabel);
        texts.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label caret = new Label(">>>");
        caret.getStyleClass().add("menu-caret");

        HBox content = new HBox(8, iconWrap, texts, spacer, caret);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(Double.MAX_VALUE);

        Button button = new Button();
        button.getStyleClass().add("menu-pill");
        button.setGraphic(content);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setMinSize(MENU_WIDTH, MENU_HEIGHT);
        button.setPrefSize(MENU_WIDTH, MENU_HEIGHT);
        button.setMaxSize(MENU_WIDTH, MENU_HEIGHT);
        // graphic 撑满按钮内容区（Button 默认按 graphic 首选宽布局，需显式绑定到按钮宽减内边距）
        content.prefWidthProperty().bind(button.widthProperty().subtract(MENU_PADDING_H));

        final int index = menuButtons.size();
        menuButtons.add(button);

        button.setOnAction(e -> action.run());
        button.setOnMouseEntered(e -> {
            lift(button, HOVER_LIFT);
            setActive(index);
        });
        button.setOnMouseExited(e -> lift(button, 0));
        return button;
    }

    // ------------------------------------------------------------------
    // 键盘操作（↑↓ 切换 / Enter 确认 / Esc 取消 / M 静音）
    // ------------------------------------------------------------------

    /**
     * 安装全局键盘操作。与设计稿一致：↑↓ 循环切换选中项、Enter 触发选中项、
     * Esc 取消选中、M 切换静音；鼠标点击等原有交互不受影响。
     */
    private void installKeyboard(Scene scene) {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case UP -> {
                    moveActive(-1);
                    event.consume();
                }
                case DOWN -> {
                    moveActive(1);
                    event.consume();
                }
                case ENTER -> {
                    fireActive();
                    event.consume();
                }
                case ESCAPE -> {
                    setActive(-1);
                    event.consume();
                }
                case M -> {
                    toggleMute();
                    event.consume();
                }
                default -> {
                }
            }
        });
    }

    /** 循环移动选中项（设计稿同款取模公式；未选中时按第一/第四项衔接）。 */
    private void moveActive(int delta) {
        int size = menuButtons.size();
        if (size == 0) {
            return;
        }
        setActive((activeIndex + delta + size) % size);
    }

    /** Enter 触发当前选中项（无选中项时不动作）。 */
    private void fireActive() {
        if (activeIndex >= 0 && activeIndex < menuButtons.size()) {
            menuButtons.get(activeIndex).fire();
        }
    }

    /** M 键静音开关（作用于主界面 BGM；静音状态跨场景保持）。 */
    private static void toggleMute() {
        muted = !muted;
        MusicPlayer.setVolume(muted ? 0 : VOLUME);
    }

    // ------------------------------------------------------------------
    // 选中 / 悬停动效
    // ------------------------------------------------------------------

    /** 初始选中第一项（与设计稿一致：进入即高亮「开始游戏」；直接置位不播放入场缩放）。 */
    private void initializeSelection() {
        if (menuButtons.isEmpty()) {
            return;
        }
        Button first = menuButtons.get(0);
        first.pseudoClassStateChanged(SELECTED, true);
        first.setScaleX(SELECTED_SCALE);
        first.setScaleY(SELECTED_SCALE);
    }

    /** 切换选中项：旧项还原、新项进入选中态（伪类 + 缩放动画）。 */
    private void setActive(int index) {
        if (index == activeIndex) {
            return;
        }
        activeIndex = index;
        for (int i = 0; i < menuButtons.size(); i++) {
            setSelected(menuButtons.get(i), i == index);
        }
    }

    /** 单个按钮的选中视觉：CSS 伪类切换 + 缩放过渡。 */
    private void setSelected(Button button, boolean selected) {
        button.pseudoClassStateChanged(SELECTED, selected);
        ScaleTransition scale = selectedScales.computeIfAbsent(button, b -> {
            ScaleTransition transition = new ScaleTransition(Duration.millis(200), b);
            transition.setInterpolator(Interpolator.EASE_BOTH);
            return transition;
        });
        scale.stop();
        scale.setFromX(button.getScaleX());
        scale.setFromY(button.getScaleY());
        scale.setToX(selected ? SELECTED_SCALE : 1.0);
        scale.setToY(selected ? SELECTED_SCALE : 1.0);
        scale.play();
    }

    /** 悬停上浮/回落（入场动画期间跳过，避免与入场位移争抢 translateY）。 */
    private void lift(Button button, double toY) {
        if (entrancePlaying) {
            return;
        }
        TranslateTransition transition = hoverLifts.computeIfAbsent(button, b -> {
            TranslateTransition t = new TranslateTransition(Duration.millis(180), b);
            t.setInterpolator(Interpolator.EASE_BOTH);
            return t;
        });
        transition.stop();
        transition.setFromY(button.getTranslateY());
        transition.setToY(toY);
        transition.play();
    }

    // ------------------------------------------------------------------
    // 入场动画
    // ------------------------------------------------------------------

    /** 入场动效：Logo 落下淡入 + 按钮错峰上浮淡入；Logo 随后进入无限轻浮动。 */
    private void playEntranceAnimations() {
        Interpolator spline = Interpolator.SPLINE(0.16, 1, 0.3, 1);
        Duration logoIn = Duration.millis(900);

        logoEntrance.setOpacity(0);
        logoEntrance.setTranslateY(LOGO_DROP);
        FadeTransition logoFade = new FadeTransition(logoIn, logoEntrance);
        logoFade.setFromValue(0);
        logoFade.setToValue(1);
        logoFade.setInterpolator(spline);
        TranslateTransition logoDrop = new TranslateTransition(logoIn, logoEntrance);
        logoDrop.setFromY(LOGO_DROP);
        logoDrop.setToY(0);
        logoDrop.setInterpolator(spline);
        new ParallelTransition(logoFade, logoDrop).play();

        // 无限轻浮动（内层容器，与外层入场位移互不干扰）
        TranslateTransition floating = new TranslateTransition(Duration.millis(5500), logoFloat);
        floating.setFromY(0);
        floating.setToY(FLOAT_AMPLITUDE);
        floating.setInterpolator(Interpolator.EASE_BOTH);
        floating.setCycleCount(Animation.INDEFINITE);
        floating.setAutoReverse(true);
        floating.play();

        entrancePlaying = true;
        for (int i = 0; i < menuButtons.size(); i++) {
            Button button = menuButtons.get(i);
            button.setOpacity(0);
            button.setTranslateY(ENTRANCE_RISE);

            FadeTransition fade = new FadeTransition(Duration.millis(620), button);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setInterpolator(spline);
            TranslateTransition rise = new TranslateTransition(Duration.millis(620), button);
            rise.setFromY(ENTRANCE_RISE);
            rise.setToY(0);
            rise.setInterpolator(spline);

            SequentialTransition sequence = new SequentialTransition(
                    new PauseTransition(Duration.millis(120 + i * 95)),
                    new ParallelTransition(fade, rise));
            if (i == menuButtons.size() - 1) {
                sequence.setOnFinished(e -> entrancePlaying = false); // 最后一个按钮入座后放行悬停位移
            }
            sequence.play();
        }
    }
}
