package org.example.config;

/**
 * 应用级常量配置。
 * <p>窗口标题、尺寸等目前为骨架示例值，待选题确认后按实际内容调整（[待确认]）。</p>
 */
public final class AppConfig {

    private AppConfig() {
    }

    /** 窗口标题。 */
    public static final String APP_TITLE = "JavaFX";

    /** 是否允许用户调整窗口大小。 */
    public static final boolean RESIZABLE = false;

    /** 窗口初始宽高。 */
    public static final double WINDOW_WIDTH = 640;
    public static final double WINDOW_HEIGHT = 480;

    /** 退出确认弹窗文案。 */
    public static final String EXIT_CONFIRM_TITLE = "退出程序";
    public static final String EXIT_CONFIRM_HEADER = null;
    public static final String EXIT_CONFIRM_CONTENT = "您是否要退出游戏？";
}
