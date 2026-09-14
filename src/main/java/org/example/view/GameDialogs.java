package org.example.view;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.DialogPane;
import javafx.scene.paint.Color;

/**
 * 统一小弹窗工具：信息 / 警告 / 确认 / 选择，全部套用游戏主视觉
 * （白卡 + 深蓝描边环 + 金色胶囊按钮 + 微软雅黑）。
 *
 * <p>实现为原生 {@link Alert} / {@link ChoiceDialog} 换皮：内容结构（header / content /
 * 按钮栏）与布局骨架保持不动，只追加样式表（start-menu.css 的 .game-dialog 段）与样式类，
 * 因此弹窗尺寸与迁移前基本一致。默认的信息 / 警告图标被移除，统一为纯文字卡面。</p>
 *
 * <p>按钮语义：主操作 = 金色渐变胶囊（{@code game-pill-primary}，与 .slot-action 同族）；
 * 次要操作 = 白底深蓝描边胶囊（{@code game-pill-secondary}）；破坏性操作（删除 / 覆盖）
 * = 红渐变胶囊 + 白字（{@code game-pill-danger}，与 .slot-delete 同语义）。</p>
 */
public final class GameDialogs {

    /** 与启动页 / 悬浮子页共享的样式表（弹窗样式段见其中 .game-dialog 组）。 */
    private static final String STYLE_SHEET = "/css/start-menu.css";

    /** 外框描边色（#123c63）；同时用作弹窗窗口 Scene 底色，圆角外露区与描边同色。 */
    private static final Color FRAME_COLOR = Color.web("#123c63");

    private GameDialogs() {
    }

    // ------------------------------------------------------------------
    // 信息 / 警告：单按钮告知
    // ------------------------------------------------------------------

    /** 信息提示（成功 / 中性结果）：正文只有一段，无加粗提示行。 */
    public static void info(String title, String message) {
        info(title, null, message);
    }

    /** 信息提示：{@code header} 为顶部加粗提示行（可为 {@code null}）。 */
    public static void info(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        DialogPane pane = alert.getDialogPane();
        ButtonType ok = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().setAll(ok);
        applyStyle(pane, false);
        styleButton(pane, ok, "game-pill-primary");
        alert.showAndWait();
    }

    /** 警告提示（操作未生效）：标题行转橙金以示区分。 */
    public static void warn(String title, String message) {
        warn(title, null, message);
    }

    /** 警告提示：{@code header} 为顶部加粗提示行（可为 {@code null}）。 */
    public static void warn(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        DialogPane pane = alert.getDialogPane();
        ButtonType ok = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().setAll(ok);
        applyStyle(pane, true);
        styleButton(pane, ok, "game-pill-primary");
        alert.showAndWait();
    }

    // ------------------------------------------------------------------
    // 确认：两个按钮
    // ------------------------------------------------------------------

    /** 确认询问（非破坏性）：确认按钮为金色胶囊。 */
    public static boolean confirm(String title, String message, String okText, String cancelText) {
        return confirm(title, null, message, okText, cancelText, false);
    }

    /**
     * 确认询问。
     *
     * @param header    顶部加粗提示行（可为 {@code null}）
     * @param okText    确认按钮文案
     * @param cancelText 取消按钮文案
     * @param dangerous 是否破坏性操作（删除 / 覆盖等）：确认按钮换成红渐变胶囊
     * @return 是否点了确认按钮（直接关窗时返回 {@code false}）
     */
    public static boolean confirm(String title, String header, String message,
                                  String okText, String cancelText, boolean dangerous) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(cancelText, ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(ok, cancel);
        DialogPane pane = alert.getDialogPane();
        applyStyle(pane, false);
        styleButton(pane, ok, dangerous ? "game-pill-danger" : "game-pill-primary");
        styleButton(pane, cancel, "game-pill-secondary");
        return alert.showAndWait().filter(ok::equals).isPresent();
    }

    // ------------------------------------------------------------------
    // 选择：下拉单选
    // ------------------------------------------------------------------

    /**
     * 单选选择（下拉列表）：{@link ChoiceDialog} 换皮，返回选中项，取消时为
     * {@link Optional#empty()}。
     *
     * <p>ChoiceDialog 内部按 {@code ButtonBar.ButtonData.OK_DONE} 判定结果，因此可安全
     * 替换为中文按钮（经 javap 核对 17.0.20 字节码确认）。</p>
     *
     * @param defaultValue 默认选中项（须存在于 {@code choices} 中）
     */
    public static <T> Optional<T> choose(String title, String header, String message,
                                         List<T> choices, T defaultValue) {
        ChoiceDialog<T> dialog = new ChoiceDialog<>(defaultValue, choices);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(message);
        DialogPane pane = dialog.getDialogPane();
        ButtonType ok = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(ok, cancel);
        applyStyle(pane, false);
        styleButton(pane, ok, "game-pill-primary");
        styleButton(pane, cancel, "game-pill-secondary");
        return dialog.showAndWait();
    }

    // ------------------------------------------------------------------
    // 内部：换皮
    // ------------------------------------------------------------------

    /**
     * 给自定义 {@link DialogPane} 应用统一弹窗皮肤（供本类各 API 复用；自定义
     * {@link javafx.scene.control.Dialog} 亦可直接调用）。
     */
    public static void applyStyle(DialogPane pane) {
        applyStyle(pane, false);
    }

    private static void applyStyle(DialogPane pane, boolean warn) {
        var css = GameDialogs.class.getResource(STYLE_SHEET);
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().add("game-dialog");
        if (warn) {
            pane.getStyleClass().add("game-dialog-warn");
        }
        // 默认信息 / 警告图标移除：统一为纯文字卡面（与主弹窗无图标的观感一致）
        pane.setGraphic(null);
        // 弹窗 Scene 在 Dialog 构造期（HeavyweightDialog）即已建立，须立即处理（实测 sceneProperty
        // 监听器不会触发）；监听器保留，覆盖未来可能的 detach / re-attach 场景
        styleScene(pane.getScene(), css);
        pane.sceneProperty().addListener((obs, oldScene, scene) -> styleScene(scene, css));
    }

    /**
     * Scene 级换皮：窗口底色 = 描边色（DialogPane 圆角外露的四角与深蓝描边同色，不露系统白底）；
     * scene 级样式表同时补齐（ComboBox 下拉弹层是独立窗口，只认 scene 级样式表）。
     */
    private static void styleScene(Scene scene, URL css) {
        if (scene == null) {
            return;
        }
        scene.setFill(FRAME_COLOR);
        if (css != null) {
            String url = css.toExternalForm();
            if (!scene.getStylesheets().contains(url)) {
                scene.getStylesheets().add(url);
            }
        }
    }

    /** 给按钮栏中的按钮追加样式类（按钮须已随按钮类型创建）。 */
    private static void styleButton(DialogPane pane, ButtonType type, String styleClass) {
        Node node = pane.lookupButton(type);
        if (node instanceof Button button) {
            button.getStyleClass().add(styleClass);
        }
    }
}
