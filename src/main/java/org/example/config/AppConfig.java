package org.example.config;

/**
 * 应用级常量配置。
 * <p>窗口标题、尺寸等目前为骨架示例值，待选题确认后按实际内容调整（[待确认]）。</p>
 */
public final class AppConfig {

    private AppConfig() {
    }

    /** 窗口标题。 */
    public static final String APP_TITLE = "PokeRouge";

    /** 默认训练家名字。 */
    public static final String PLAYER_NAME = "小红";

    /** 是否允许用户调整窗口大小。 */
    public static final boolean RESIZABLE = false;

    /** 界面设计分辨率（逻辑画布，3:2 = 640×426.67）：各页面布局与字号一律按此设计（UiScale 统一缩放）。 */
    public static final double WINDOW_WIDTH = 640;

    /** = 宽 × 2/3，精确 3:2；窗口实际 = 960×640（×WINDOW_SCALE）。 */
    public static final double WINDOW_HEIGHT = WINDOW_WIDTH * 2.0 / 3.0;

    /**
     * 全局界面缩放系数：窗口实际尺寸 = 设计分辨率 × 系数（UiScale.scene 统一施加，见其 javadoc）。
     * <p>等比放大/缩小整体界面只需调此常量，无需改动任何 View 内布局/字号数值。</p>
     */
    public static final double WINDOW_SCALE = 1.5;

    /** 退出确认弹窗文案。 */
    public static final String EXIT_CONFIRM_TITLE = "退出程序";
    public static final String EXIT_CONFIRM_HEADER = null;
    public static final String EXIT_CONFIRM_CONTENT = "您是否要退出游戏？";
}
