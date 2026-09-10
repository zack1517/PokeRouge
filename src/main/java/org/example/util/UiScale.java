package org.example.util;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.transform.Scale;
import org.example.config.AppConfig;

/**
 * 全局界面等比缩放（2026-09-09 定版，配合 UI 重设计）。
 *
 * <p>所有页面一律按设计分辨率（{@code AppConfig.WINDOW_WIDTH/HEIGHT}，640×426.67，3:2）排版，
 * 经本工具把内容根钉死为设计尺寸并挂入 {@link Pane} 外壳，随后对整棵内容施加
 * {@code WINDOW_SCALE} 等比缩放；窗口实际尺寸 = 设计分辨率 × 缩放系数。</p>
 *
 * <p>各 View 的 {@code createScene()} 只需把 {@code new Scene(root, W, H)} 替换为
 * {@link #scene(Region)} 即可整体放大/缩小，后续调整缩放只需改 {@code WINDOW_SCALE}
 * 一个常量，无需改动任何布局/字号数值（对应 UI 验收「界面等比放大」需求）。</p>
 */
public final class UiScale {

    private UiScale() {
    }

    /**
     * 按全局缩放系数构建场景。
     *
     * @param content 页面内容根（须为 {@link Region}，各 View 均为 BorderPane）
     * @throws IllegalArgumentException 内容根为 null
     */
    public static Scene scene(Region content) {
        if (content == null) {
            throw new IllegalArgumentException("[fail-fast] UiScale.scene 内容根为 null");
        }
        double w = AppConfig.WINDOW_WIDTH;
        double h = AppConfig.WINDOW_HEIGHT;
        content.setMinSize(w, h);
        content.setPrefSize(w, h);
        content.setMaxSize(w, h);
        content.resize(w, h); // Pane 外壳不负责布局子节点，需手动钉一次设计尺寸
        content.getTransforms().clear(); // 防御：页面根不带历史变换
        content.getTransforms().add(new Scale(AppConfig.WINDOW_SCALE, AppConfig.WINDOW_SCALE));
        Pane shell = new Pane(content);
        return new Scene(shell, w * AppConfig.WINDOW_SCALE, h * AppConfig.WINDOW_SCALE);
    }
}
