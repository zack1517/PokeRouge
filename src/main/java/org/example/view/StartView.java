package org.example.view;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.util.ImageBackgrounds;
import org.example.util.LogUtil;
import org.example.util.MusicPlayer;
import org.example.util.UiScale;

/**
 * 游戏启动页（第一屏）：无卡片悬浮式主菜单。
 *
 * <p>样式改编自「临时/主界面设计代码」（Web/React 版主界面）：背景插画铺满整页，
 * 彩色 Logo 与七个胶囊入口按钮直接悬浮其上（不再使用半透明中央卡片），
 * 可读性由元素自身的描边、投影与底色承担；样式集中在
 * {@code /css/start-menu.css}，本类只负责结构与动效。</p>
 *
 * <p>功能接线与 dev 版一致（不受样式改编影响）：「开始游戏」进入初始宝可梦选择流程、
 * 「继续游戏」进入存档位选择页（无存档时置灰）、「宝可梦图鉴」进入图鉴页、
 * 「道具图鉴」进入全量道具一览、「自定义战斗」进入模式选择页（均由 {@code MainController} 接线）；
 * 「成就系统」「设置选项」为预留入口，当前仅打印日志占位。</p>
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

    /** Logo 显示宽度（设计画布 px；原图 2784×1632 等比缩放；七个入口的版面平衡值）。 */
    private static final double LOGO_WIDTH = 235;

    /** 整列（Logo+菜单）垂直偏移量：底栏移除且扩充至七个入口后的构图校准（设计画布 px，负值上移）。 */
    private static final double COLUMN_OFFSET_Y = 0;

    /** Logo 入场起始位移（从上方落下）。 */
    private static final double LOGO_DROP = -13;

    /** Logo 无限浮动幅度（负值向上）。 */
    private static final double FLOAT_AMPLITUDE = -4.5;

    /** 主界面 BGM 音量（与 MusicPlayer 默认一致，M 键静音后按此恢复）。 */
    private static final double VOLUME = 0.5;

    /** 七个入口的 24 单位视口单色描边图标（lucide 风格手绘简化版）。 */
    private static final String ICON_PLAY = "M7 4 L20 12 L7 20 Z";
    private static final String ICON_BOOK = "M12 6.5 C9.6 4.8 6.6 4.2 4.2 4.2 L4.2 18.2 C6.6 18.2 9.6 18.8 12 20.5"
            + " C14.4 18.8 17.4 18.2 19.8 18.2 L19.8 4.2 C17.4 4.2 14.4 4.8 12 6.5 Z M12 6.5 L12 20.5";

    /** 「道具图鉴」图标：行囊（背包本体 + 顶部提手 + 扣带）。 */
    private static final String ICON_BAG = "M6.5 8.5 L17.5 8.5 L18.5 20 L5.5 20 Z"
            + " M9.5 8.5 L9.5 6 A2.5 2.5 0 0 1 14.5 6 L14.5 8.5 M6 13 L18 13";
    private static final String ICON_SWORDS = "M4 20.5 L16 8.5 M12.3 9.8 L14.7 12.2 M20 20.5 L8 8.5 M11.7 9.8 L9.3 12.2";
    private static final String ICON_TROPHY = "M8 4 L16 4 L16 9 C16 11.2 14.2 13 12 13 C9.8 13 8 11.2 8 9 Z"
            + " M8 5.5 L5.5 5.5 L5.5 7.5 C5.5 9 6.5 10 8 10 M16 5.5 L18.5 5.5 L18.5 7.5 C18.5 9 17.5 10 16 10"
            + " M12 13 L12 16.5 M8.5 16.5 L15.5 16.5 L15.5 19 L8.5 19 Z";
    private static final String ICON_SETTINGS = "M20 7 L11 7 M14 17 L5 17"
            + " M17 20 A3 3 0 1 0 17 14 A3 3 0 1 0 17 20 M7 10 A3 3 0 1 0 7 4 A3 3 0 1 0 7 10";

    /** 「继续游戏」图标：读档（history 风格，回流箭头 + 表盘）。 */
    private static final String ICON_HISTORY = "M3 12 A9 9 0 1 0 12 3 A9.75 9.75 0 0 0 5.26 5.74 L3 8"
            + " M3 3 L3 8 L8 8 M12 7 L12 12 L16 14";

    private final Runnable onStartGame;

    /** 「继续游戏」回调（进入存档位选择页；无存档置灰时不触发）。 */
    private final Runnable onContinueGame;

    /** 是否存在可继续的存档（来自 dev 版存档系统）：false 时「继续游戏」置灰。 */
    private final boolean canContinue;

    private final Runnable onCustomBattle;
    /** 「宝可梦图鉴」回调。 */
    private final Runnable onPokedex;

    /** 「道具图鉴」回调（打开全量道具 / 装备一览）。 */
    private final Runnable onItemDex;

    /** 入场动画作用容器（Logo 外层）与无限浮动容器（Logo 内层）。 */
    private VBox logoEntrance;
    private VBox logoFloat;

    /** 静音状态跨场景保持：MusicPlayer 为全局单例，重进主界面时需恢复静音。 */
    private static boolean muted;

    /**
     * @param onStartGame    「开始游戏」回调
     * @param onContinueGame 「继续游戏」回调
     * @param canContinue    是否存在可继续的存档
     * @param onCustomBattle 「自定义战斗」回调
     * @param onPokedex      「宝可梦图鉴」回调
     * @param onItemDex      「道具图鉴」回调
     */
    public StartView(Runnable onStartGame, Runnable onContinueGame, boolean canContinue,
                     Runnable onCustomBattle, Runnable onPokedex, Runnable onItemDex) {
        this.onStartGame = onStartGame;
        this.onContinueGame = onContinueGame;
        this.canContinue = canContinue;
        this.onCustomBattle = onCustomBattle;
        this.onPokedex = onPokedex;
        this.onItemDex = onItemDex;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);

        // 暗角遮罩：叠在背景图之上、内容层之下（弱化背景保证悬浮元素可读）
        Region vignette = new Region();
        vignette.getStyleClass().add("start-vignette");
        vignette.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        vignette.setMouseTransparent(true);

        FloatingMenu menu = new FloatingMenu();
        BorderPane layout = new BorderPane();
        layout.setCenter(buildMenuColumn(menu));

        root.getChildren().addAll(vignette, layout);

        Scene scene = UiScale.scene(root);
        var css = StartView.class.getResource(STYLE_SHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        menu.installKeyboard(scene, menu::clearSelection, code -> {
            if (code == KeyCode.M) {
                toggleMute(); // Esc 取消选中；M 主界面静音由本页专属处理
            }
        });
        menu.selectFirst();
        if (muted) {
            MusicPlayer.setVolume(0); // 回到主界面时保持静音状态（BGM 循环中不重播）
        }
        playEntranceAnimations(menu);
        return scene;
    }

    // ------------------------------------------------------------------
    // 布局：Logo + 胶囊菜单
    // ------------------------------------------------------------------

    /** 中央悬浮列：彩色 Logo（含入场/浮动双层容器）+ 六个胶囊入口（装入共享菜单组）。 */
    private Region buildMenuColumn(FloatingMenu menu) {
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

        menu.addPill("yellow", "开始游戏", "START", ICON_PLAY, onStartGame);
        buildContinueButton(menu);
        // 「宝可梦图鉴」进入图鉴页（PokedexView：宝可梦库 + 局外成长进度驱动）
        menu.addPill("blue", "宝可梦图鉴", "POKEDEX", ICON_BOOK, () -> {
            if (onPokedex != null) {
                onPokedex.run();
            }
        });
        // 「道具图鉴」进入全量道具页（ItemDexView：93 件道具 / 装备一览，启动页无存档时只读）
        menu.addPill("teal", "道具图鉴", "ITEM DEX", ICON_BAG, () -> {
            if (onItemDex != null) {
                onItemDex.run();
            }
        });
        menu.addPill("crimson", "自定义战斗", "CUSTOM RUN", ICON_SWORDS, onCustomBattle);
        menu.addPill("violet", "成就系统", "ACHIEVEMENTS", ICON_TROPHY,
                () -> LogUtil.info("[StartView] 成就系统：成就展示与管理功能待实现（预留入口）"));
        menu.addPill("slate", "设置选项", "SETTINGS", ICON_SETTINGS,
                () -> LogUtil.info("[StartView] 设置选项：音效、画面等配置功能待实现（预留入口）"));

        VBox column = new VBox(10, logoEntrance, menu.node()); // Logo 与菜单间距（七入口版收紧为 10）
        column.setAlignment(Pos.CENTER);
        // 关键：BorderPane 会把 center 子节点拉满可用高度，不设上限时整列会被垂直撑开；
        // maxHeight 用内容首选高封顶后，整列按内容收拢并垂直居中。
        column.setMaxHeight(Region.USE_PREF_SIZE);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        column.setTranslateY(COLUMN_OFFSET_Y); // 底部信息条移除后整列微调，校准构图
        return column;
    }

    /** 「继续游戏」入口：有存档时可进入存档位选择页，无存档时置灰并提示（与 dev 版行为一致）。 */
    private void buildContinueButton(FloatingMenu menu) {
        Button button = menu.addPill("yellow", "继续游戏", "CONTINUE", ICON_HISTORY, () -> {
            if (onContinueGame != null) {
                onContinueGame.run();
            }
        });
        if (!canContinue) {
            button.setDisable(true);
            button.setTooltip(new Tooltip("还没有任何存档，先开始游戏吧"));
        }
    }

    /** M 键静音开关（作用于主界面 BGM；静音状态跨场景保持）。 */
    private static void toggleMute() {
        muted = !muted;
        MusicPlayer.setVolume(muted ? 0 : VOLUME);
    }

    // ------------------------------------------------------------------
    // 入场动画（Logo 本页专属；按钮部分由共享菜单组承担）
    // ------------------------------------------------------------------

    /** 入场动效：Logo 落下淡入（本页专属）+ 按钮错峰上浮淡入（由共享菜单组承担）；Logo 随后进入无限轻浮动。 */
    private void playEntranceAnimations(FloatingMenu menu) {
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

        menu.playEntrance(); // 七个入口的错峰上浮淡入（共享动效，与启动页一致）
    }
}
