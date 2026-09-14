package org.example.util;

import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 * 圆角几何裁切工具：给节点安装随尺寸自适应的圆角矩形 clip。
 *
 * <p>用途（蓝环照片卡封装）：{@code -fx-background-image} 不随 {@code -fx-background-radius}
 * 裁切——贴图直角会溢出圆角并遮挡描边环；而素材自带烘焙圆角又无法适配卡面随窗口变化的
 * 尺寸（非等比拉伸下四角圆角变形，永远对不上 {@code -fx-border-radius} 的正圆弧）。
 * 改用与本节点描边环同半径的几何 clip 后，四角在任意窗口尺寸下精确贴合
 * （配合直角版素材 /images/ui/bg_info_*.png 使用）。</p>
 */
public final class RoundClip {

    private RoundClip() {
    }

    /**
     * 给节点安装圆角矩形 clip（四角同半径，随节点宽高自适应；超出圆角的渲染被裁掉）。
     *
     * @param node   目标节点（Region）
     * @param radius 圆角半径（须与 {@code -fx-background-radius} / {@code -fx-border-radius} 同值）
     */
    public static void install(Region node, double radius) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2); // Rectangle 弧宽高取直径，与 CSS 半径换算 2 倍
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(node.widthProperty());
        clip.heightProperty().bind(node.heightProperty());
        node.setClip(clip);
    }
}
