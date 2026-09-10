package org.example.util;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * 页面背景工具：按 classpath 图片给页面根节点铺铺满背景（cover，不拉伸变形）。
 *
 * <p>⚠️ 页面根节点若带 css 底色类（如 {@code pr-page} 的 {@code -fx-background-color}），
 * css 会在挂载时覆盖此处设置，故用图背景的 fxml 根不应再挂纯底色类；
 * 图片缺失/加载失败时回落为统一浅底色，不阻断页面（资源是否必需由
 * {@code AppConfig.REQUIRED_RESOURCES} fail-fast 把关）。</p>
 *
 * <p>⚠️ JavaFX 限制（实测）：背景图比铺贴区宽时 cover 按图片左上角锚定，
 * 不会居中裁切；故背景资源须预裁为与窗口同比例（3:2，见临时/bg_crop.py），
 * 此处的 cover 仅作保险，比例一致时零裁切。</p>
 */
public final class ImageBackgrounds {

    private ImageBackgrounds() {
    }

    /** 兜底底色（原 {@code .pr-page} 浅灰）。 */
    private static final Color FALLBACK = Color.web("#f4f4f4");

    /** 图片缓存：同图只解码一次（页面 render 会高频重入）。 */
    private static final Map<String, Image> CACHE = new HashMap<>();

    /** 同步解码 classpath 图片并铺满背景（仅 {@link Region} 支持背景；非 Region 根节点忽略）。 */
    public static void apply(Parent root, String classpathImage) {
        if (!(root instanceof Region region)) {
            return;
        }
        Background bg;
        Image image = load(classpathImage);
        if (image == null || image.isError()) {
            bg = new Background(new BackgroundFill(
                    FALLBACK, CornerRadii.EMPTY, Insets.EMPTY));
        } else {
            bg = new Background(new BackgroundImage(image,
                    BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO,
                            false, false, false, true))); // cover（保险；图已预裁同比例）
        }
        region.setBackground(bg);
    }

    /** 同步解码并缓存 classpath 图片；图缺失或解码失败返回 null（调用方自行兑底）。 */
    public static Image load(String classpathImage) {
        if (classpathImage == null) {
            return null;
        }
        return CACHE.computeIfAbsent(classpathImage, path -> {
            java.net.URL url = ImageBackgrounds.class.getResource(path);
            if (url == null) {
                return null; // 图缺失：走兜底底色（由 REQUIRED_RESOURCES 负责 fail-fast 提醒）
            }
            return new Image(url.toString(), false); // 同步解码：立即获知加载错误
        });
    }
}
