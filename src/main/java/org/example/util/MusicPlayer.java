package org.example.util;

import java.net.URL;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/**
 * 背景音乐播放器（全局单例）：循环播放 classpath 音频文件。
 *
 * <p>与 {@link ImageBackgrounds} 同族：资源缺失 / 加载失败时静默降级并打日志，不阻断游戏。
 * 同一路径重复调用不重启播放（幂等）；切换曲目时自动停旧播新，供各界面配置各自的 BGM。</p>
 *
 * <p>⚠️ 运行需 javafx-media 模块（pom 已声明）；Windows 上支持 mp3/wav。</p>
 */
public final class MusicPlayer {

    private MusicPlayer() {
    }

    /** 默认音量（0~1）；后续「设置选项」可经 {@link #setVolume(double)} 调整。 */
    private static final double DEFAULT_VOLUME = 0.5;

    private static MediaPlayer current;
    private static String currentPath;

    /**
     * 循环播放指定 classpath 音频。
     * <p>幂等：同一路径已在播放时不打断；文件缺失/解码失败时静默降级（保留当前播放状态）。</p>
     */
    public static void playBgm(String classpathAudio) {
        if (classpathAudio == null || classpathAudio.equals(currentPath)) {
            return; // null 忽略；同曲不打断
        }
        URL url = MusicPlayer.class.getResource(classpathAudio);
        if (url == null) {
            LogUtil.info("[MusicPlayer] 音频文件缺失，已静默：" + classpathAudio
                    + "（将音乐文件放入 src/main/resources 对应路径后重启即生效）");
            return;
        }
        stop();
        try {
            Media media = new Media(url.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            player.setCycleCount(MediaPlayer.INDEFINITE); // 单曲循环
            player.setVolume(DEFAULT_VOLUME);
            player.setOnError(() -> LogUtil.info("[MusicPlayer] 播放出错，已静默：" + player.getError()));
            player.play();
            current = player;
            currentPath = classpathAudio;
        } catch (RuntimeException ex) {
            LogUtil.info("[MusicPlayer] 音频加载失败，已静默：" + ex.getMessage());
        }
    }

    /** 停止当前背景音乐并释放资源（无播放时安全无操作）。 */
    public static void stop() {
        if (current != null) {
            current.stop();
            current.dispose();
            current = null;
            currentPath = null;
        }
    }

    /** 调整当前背景音乐音量（0~1），供后续「设置选项」接线。 */
    public static void setVolume(double volume) {
        if (current != null) {
            current.setVolume(Math.max(0, Math.min(1, volume)));
        }
    }
}
