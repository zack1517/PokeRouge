package org.example.util;

import javafx.scene.image.Image;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 精灵立绘加载工具（战斗双方立绘与图鉴弹窗共用）。
 *
 * <p>图片按精灵中文名从 classpath {@value #POKEMON_IMAGE_DIR} 加载（文件名须与种族名一致，
 * 如「皮卡丘.png」）。结果带静态缓存：value 为 {@code null} 表示已确认无图（内建精灵），
 * 避免重复加载。</p>
 */
public final class SpriteLoader {

    /** 立绘图片目录（classpath）。 */
    public static final String POKEMON_IMAGE_DIR = "/images/pokemon/";

    /** 立绘图片缓存：精灵名 → 图片；value 为 null 表示已确认无图。 */
    private static final Map<String, Image> SPRITE_CACHE = new HashMap<>();

    private SpriteLoader() {
        // 工具类，禁止实例化
    }

    /**
     * 按精灵名加载立绘图片；无图或加载失败返回 {@code null}（调用方回退占位文本）。
     */
    public static Image load(String pokemonName) {
        if (pokemonName == null || pokemonName.isBlank()) {
            return null;
        }
        if (SPRITE_CACHE.containsKey(pokemonName)) {
            return SPRITE_CACHE.get(pokemonName);
        }
        String path = POKEMON_IMAGE_DIR + pokemonName + ".png";
        try (InputStream in = SpriteLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                SPRITE_CACHE.put(pokemonName, null);
                return null;
            }
            Image image = new Image(in);
            SPRITE_CACHE.put(pokemonName, image);
            return image;
        } catch (Exception e) {
            SPRITE_CACHE.put(pokemonName, null);
            return null;
        }
    }
}
