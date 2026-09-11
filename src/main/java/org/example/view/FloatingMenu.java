package org.example.view;

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
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 悬浮胶囊菜单组：与启动页一致的胶囊按钮结构（左图标圆底 + 居中主/副标签 + 右箭头）、
 * 悬停上浮、选中放大、入场错峰动画与 ↑/↓ 键盘导航。
 *
 * <p>供启动页与其它悬浮式页面（如自定义战斗模式选择页）复用，保证按钮样式、
 * 交互动效完全一致；纯视觉由 {@code /css/start-menu.css} 承担，使用方需在场景中挂载该样式表。</p>
 */
public final class FloatingMenu {

    /** 胶囊按钮统一宽高（设计画布 px）。 */
    private static final double MENU_WIDTH = 216;
    private static final double MENU_HEIGHT = 38;

    /** 按钮垂直间距（六个入口挤占画布后的收紧值）。 */
    private static final double MENU_GAP = 5;

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

    /** 入场动画时长与错峰步长（与启动页保持一致）。 */
    private static final double ENTRANCE_FADE_MS = 620;
    private static final double ENTRANCE_DELAY_BASE_MS = 120;
    private static final double ENTRANCE_DELAY_STEP_MS = 95;

    /** 选中态伪类（由 CSS {@code .menu-pill:selected} 承载选中视觉）。 */
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final VBox pane = new VBox(MENU_GAP);

    /** 胶囊按钮（顺序 = 视觉顺序，供键盘导航索引）。 */
    private final List<Button> buttons = new ArrayList<>();

    /** 每按钮悬停位移动画缓存（重复触发前先 stop，避免同属性动画竞争）。 */
    private final Map<Button, TranslateTransition> hoverLifts = new HashMap<>();

    /** 每按钮选中缩放动画缓存（同上）。 */
    private final Map<Button, ScaleTransition> selectedScales = new HashMap<>();

    /** 当前选中项下标（-1 = 未选中）。 */
    private int activeIndex = -1;

    /** 入场动画进行中（期间忽略悬停位移，避免与入场位移争抢 translateY）。 */
    private boolean entrancePlaying = true;

    public FloatingMenu() {
        pane.setAlignment(Pos.CENTER);
        pane.setFillWidth(false);
    }

    /** 菜单容器（放入页面垂直列中即可）。 */
    public VBox node() {
        return pane;
    }

    /**
     * 添加一个胶囊入口，并按加入顺序纳入键盘导航。
     *
     * @param tone   图标圆底色调（yellow/blue/crimson/violet/slate，与 CSS 对应）
     * @param label  中文主标签
     * @param sub    英文副标签
     * @param icon   24 单位视口的 SVG 路径数据
     * @param action 点击行为
     * @return 按钮引用（调用方可追加禁用、提示等配置）
     */
    public Button addPill(String tone, String label, String sub, String icon, Runnable action) {
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
        texts.setAlignment(Pos.CENTER); // 主/副标签两行相互居中
        texts.setFillWidth(false);      // 较短的副标签不被拉宽，保持居中堆叠

        Label caret = new Label(">>>");
        caret.getStyleClass().add("menu-caret");

        // 三层叠放：图标靠左、文本块严格居中于按钮、指示符靠右
        StackPane content = new StackPane(iconWrap, texts, caret);
        StackPane.setAlignment(iconWrap, Pos.CENTER_LEFT);
        StackPane.setAlignment(caret, Pos.CENTER_RIGHT);
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

        final int index = buttons.size();
        buttons.add(button);

        button.setOnAction(e -> action.run());
        button.setOnMouseEntered(e -> {
            lift(button, HOVER_LIFT);
            setActive(index);
        });
        button.setOnMouseExited(e -> lift(button, 0));

        pane.getChildren().add(button);
        return button;
    }

    /** 初始选中第一项（进入页面即高亮；直接置位，不播放入场缩放）。 */
    public void selectFirst() {
        if (buttons.isEmpty()) {
            return;
        }
        activeIndex = 0;
        Button first = buttons.get(0);
        first.pseudoClassStateChanged(SELECTED, true);
        first.setScaleX(SELECTED_SCALE);
        first.setScaleY(SELECTED_SCALE);
    }

    /** 取消选中（Esc 的默认语义）。 */
    public void clearSelection() {
        setActive(-1);
    }

    /**
     * 安装键盘操作：↑/↓ 循环切换选中项（跳过置灰项）、Enter 触发选中项、
     * Esc 执行指定动作（如返回上一页）、其余按键交给可选回调。
     *
     * @param escAction      Esc 行为（通常传返回动作或 {@link #clearSelection}）
     * @param otherKeyAction 其余按键的回调（传 null 表示不处理）
     */
    public void installKeyboard(Scene scene, Runnable escAction, Consumer<KeyCode> otherKeyAction) {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case UP -> moveActive(-1);
                case DOWN -> moveActive(1);
                case ENTER -> fireActive();
                case ESCAPE -> escAction.run();
                default -> {
                    if (otherKeyAction != null) {
                        otherKeyAction.accept(event.getCode());
                    }
                    return; // 非菜单按键不消费，留给其它监听者
                }
            }
            event.consume();
        });
    }

    /** 入场动效：按钮错峰上浮淡入（与启动页一致）；全部入座后放行悬停位移。 */
    public void playEntrance() {
        Interpolator spline = Interpolator.SPLINE(0.16, 1, 0.3, 1);
        entrancePlaying = true;
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            button.setOpacity(0);
            button.setTranslateY(ENTRANCE_RISE);

            FadeTransition fade = new FadeTransition(Duration.millis(ENTRANCE_FADE_MS), button);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setInterpolator(spline);

            TranslateTransition rise = new TranslateTransition(Duration.millis(ENTRANCE_FADE_MS), button);
            rise.setFromY(ENTRANCE_RISE);
            rise.setToY(0);
            rise.setInterpolator(spline);

            SequentialTransition sequence = new SequentialTransition(
                    new PauseTransition(Duration.millis(ENTRANCE_DELAY_BASE_MS + i * ENTRANCE_DELAY_STEP_MS)),
                    new ParallelTransition(fade, rise));
            if (i == buttons.size() - 1) {
                sequence.setOnFinished(e -> entrancePlaying = false); // 最后一个按钮入座后放行悬停位移
            }
            sequence.play();
        }
    }

    /** 循环移动选中项（取模公式；跳过置灰项，未选中时按首/尾项衔接）。 */
    private void moveActive(int delta) {
        int size = buttons.size();
        if (size == 0) {
            return;
        }
        int index = activeIndex;
        for (int step = 0; step < size; step++) {
            index = (index + delta + size) % size;
            if (!buttons.get(index).isDisabled()) {
                setActive(index);
                return;
            }
        }
    }

    /** Enter 触发当前选中项（无选中项时不动作）。 */
    private void fireActive() {
        if (activeIndex >= 0 && activeIndex < buttons.size()) {
            buttons.get(activeIndex).fire();
        }
    }

    /** 切换选中项：旧项还原、新项进入选中态（伪类 + 缩放动画）。 */
    private void setActive(int index) {
        if (index == activeIndex) {
            return;
        }
        activeIndex = index;
        for (int i = 0; i < buttons.size(); i++) {
            setSelected(buttons.get(i), i == index);
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
}
