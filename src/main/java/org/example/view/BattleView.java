package org.example.view;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.example.battle.BattleEvent;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.MoveSlot;
import org.example.model.Pokemon;
import org.example.model.Stats;
import org.example.model.StatusCondition;
import org.example.model.Terrain;
import org.example.model.Weather;
import org.example.util.ImageBackgrounds;
import org.example.util.SpriteLoader;
import org.example.util.UiScale;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * 战斗场景布局（纯界面，无业务逻辑）。
 *
 * <p>2026-09-11 视觉改版：上 2/3 立绘舞台 + 下 1/4 信息区（信息区二次收窄，内部字号/间距同步压缩）——
 * 主背景区（上方 2/3）：中央透出背景图，双方立绘为透明贴图直接悬于背景之上、带上下浮动待机动画 ——
 * 我方立绘位于左下角（宽 0-40%、底部往上 0-35% 区域），敌方立绘位于右上角（宽 60-100%、顶部 0-35% 区域）；
 * 左上：敌信息卡 + 阶段/金币块（敌卡下方）；右下：己方信息卡（与敌卡同款同尺寸）；
 * 下 1/4：大横面板（约界面高度 1/4），左=战斗日志，右=行动区（主菜单 2×2 网格，子菜单同区切换）；
 * 「战斗」→ 技能面板时：左块整组换成 [状态行+2×2 技能格]，右块换成技能信息卡（名称/属性/分类/威力/命中/PP，
 * 悬停技能格实时联动右块）。
 * 双方信息卡同款同尺寸（名称/属性/等级 + HP 条，EXP 行随「卡样式一致」要求移除，待确认后另寻展示位）；
 * 立绘显示精灵图片（classpath /images/pokemon/，文件名与精灵中文名一致；内建精灵无图时回退「精灵名+立绘」占位文本）；
 * 战斗背景由流程按战斗类型传入（2026-09-11 素材按类型分类：野怪 / 路人 / 道馆 / Boss，
 * 见 {@link org.example.GameSession#battleBackgroundFor(org.example.model.OptionType)}）；未指定时默认野外图。</p>
 *
 * <p>2026-09-11 样式改版（整合「临时/react」Web 版战斗页设计）：信息卡为奶油米色芯 + 外蓝内黄双层描边硬阴影（2026-09-12 起双层描边与日志框统一）、
 * 行动按钮与技能 / 道具 / 精灵格为马卡龙四色（黄 / 蓝 / 绿 / 红）硬底按钮、日志与详情卡为金框米色芯（芯色与信息卡同底色）、
 * HP 条为棕底 + 绿 / 黄 / 红渐变填充、属性徽章按属性分色。仅调整配色 / 描边 / 圆角 / 阴影等视觉样式：
 * 全部描边改由背景层内缩模拟（不占 padding），各按钮、文本、贴图（宝可梦立绘区域）的大小与位置均不变；
 * HP 条内部轨道样式在 {@code /css/battle.css}。</p>
 *
 * <p>全局等比缩放由 {@link org.example.util.UiScale} 统一施加（本类布局按 640×426.67 设计，3:2）。</p>
 */
public class BattleView {

    /** 面板按钮点击时的回调（由控制器绑定）。 */
    public interface Actions {
        /** 选择技能。 */
        void onMoveSelected(MoveSlot slot);

        /** 选择道具（索引为背包可用堆叠的下标）。 */
        void onItemSelected(int stackIndex);

        /** 切换到队伍中第 partyIndex 只精灵（消耗本回合行动）。 */
        void onSwitchSelected(int partyIndex);

        /** 逃跑。 */
        void onRun();

        /** 返回主菜单。 */
        void onExit();
    }

    /** 默认战斗背景（未指定类型时的兜底：野外图，对应流程「其余特殊情况一律默认 bg_battle_wild」）。 */
    private static final String DEFAULT_BACKGROUND = "/images/background/bg_battle_wild.jpeg";

    // ---- 双方立绘（精灵图片；无图时回退占位文本）----
    private final ImageView playerSprite = new ImageView();
    private final ImageView enemySprite = new ImageView();
    private final Label playerSpriteFallback = new Label();
    private final Label enemySpriteFallback = new Label();

    // ---- 主背景区（上方 2/3）与立绘区域（按 640×426.67 设计画布计算）----
    /** 主背景区高度（界面高 2/3）：426.67 → 284.45。 */
    private static final double STAGE_HEIGHT = 426.67 * 2 / 3;
    /** 单个立绘区域：宽 40%（左侧 0-40% / 右侧 60-100%）、高为主背景区的 35%。 */
    private static final double SPRITE_AREA_W = 640 * 0.40;
    private static final double SPRITE_AREA_H = STAGE_HEIGHT * 0.35;
    /** 立绘贴图适配尺寸（正方形素材等比缩放；2026-09-11 起由 84 放大一倍 → 168）。 */
    private static final double SPRITE_SIZE = 168;
    /** 待机浮动：上下 4 设计单位、单程 1.8s 往返循环（敌我相位错开）。 */
    private static final double SPRITE_FLOAT_AMPLITUDE = 4;
    private static final int MS_SPRITE_FLOAT = 1800;
    /**
     * 演出层：与内容根同尺寸叠放（见 {@link #createScene()}）、不拦截鼠标，承载飞行道具、光点、
     * 闪光环等临时特效节点。动画一律以<b>设计单位</b>书写（整场景由 {@link UiScale} 统一缩放），
     * 节点落位用 {@code sceneToLocal} 在场景坐标与演出层坐标间换算，不依赖布局时机。
     */
    private final Pane effectLayer = new Pane();

    /** 属性徽章组间距：多属性时每枚属性独立一枚徽章（react TypeBadge 排布）。 */
    private static final int TYPE_BADGE_GAP = 4;

    // ---- 敌方信息（左上卡片） ----
    private final Label wildName = new Label("--");
    private final HBox wildTypeBox = new HBox(TYPE_BADGE_GAP);
    private final Label wildLv = new Label();
    private final ProgressBar wildHpBar = new ProgressBar();
    private final Label wildHpText = new Label();
    private final Label wildStatus = new Label(); // 异常状态徽章（无异常时隐藏）

    // ---- 己方信息（右下卡片，样式与敌方卡一致） ----
    private final Label playerName = new Label("--");
    private final HBox playerTypeBox = new HBox(TYPE_BADGE_GAP);
    private final Label playerLv = new Label();
    private final ProgressBar playerHpBar = new ProgressBar();
    private final Label playerHpText = new Label();
    private final Label playerStatus = new Label(); // 异常状态徽章（无异常时隐藏）

    // ---- 右上角信息块：地图阶段 / 金币（数据来自流程系统，TODO(dev) 接入前为桩文本） ----
    private final Label stageLabel = new Label("第 1 段 · 野外遭遇");
    private final Label moneyLabel = new Label("金币 500");

    // ---- 底栏：场况行 + 日志 + 行动区 ----
    /**
     * 日志可视行数（底栏内容区高约 99 逻辑；日志 13px 字号行高约 18，4 行 + 场况行 18 = 90，超出自动丢弃最旧）。
     * <p>性能红线：勿对动态中文文本启用 JavaFX 描边（Text stroke）——实测每字符矢量光栅化约 50ms 且无缓存，
     * 日志每回合重建即卡顿数秒至十余秒（2026-09-09 量化验证），故战斗文本用普通深色 Label。</p>
     */
    private static final int MAX_LOG_ROWS = 4;
    /** 信息卡奶油米色芯渐变：敌我宝可梦信息卡（{@link #battleCard(boolean)}）与底栏日志框 / 详情卡共用同一底色。 */
    private static final String CARD_CREAM = "linear-gradient(to bottom right, #f5f0e8, #e8dfc8)";
    /** 双层描边外圈（深海军蓝、半透明）：日志框与敌我宝可梦信息卡共用的「外蓝框」。 */
    private static final String FRAME_NAVY = "rgba(6, 22, 42, 0.55)";
    /** 双层描边内圈（宝可梦金、微透）：日志框与敌我宝可梦信息卡共用的「内黄框」。 */
    private static final String FRAME_GOLD = "rgba(255, 203, 5, 0.92)";
    /**
     * 日志态左块外框（与右侧行动区视觉分隔）：金框米色芯（react 战斗日志样式）——
     * 外层深海军蓝细描边 + 宝可梦金边框 + 米色芯（与敌我宝可梦信息卡同底色），全部画在背景层（不改 padding，文本位置不变）。
     * 纵向空间紧凑（内容区高约 99，场况 18 + 日志 4×18 + 间距 2 约 92），故上下 padding 仅 2。
     * 技能面板态由 showMoveMenu 清除本样式（左块那时放技能格，不套日志框）。
     */
    private static final String LEFT_LOG_FRAME_CSS =
            "-fx-background-color: " + FRAME_NAVY + ", " + FRAME_GOLD + ", " + CARD_CREAM + ";"
                    + " -fx-background-insets: -2, 0, 3; -fx-background-radius: 18, 16, 13;"
                    + " -fx-padding: 2 10;"
                    + "-fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.35), 8, 0.4, 0, 4);";
    /** 场况行（天气/场地）文本：深色粗体小字（浅底无描边，同日志区样式基调）。 */
    private final Label fieldStatus = new Label();
    private final VBox logLines = new VBox(0); // 战斗日志行（showLog 重建，保留最近 MAX_LOG_ROWS 行）
    // 左块容器：日志模式 [场况行+日志]；技能面板模式时整组替换为 [状态行+技能格]（showLog 每次渲染恢复）
    private final VBox leftPanel = new VBox(2);
    private final VBox actionBox = new VBox(5);

    // ---- 技能面板：右块技能信息卡（随面板显示，默认/悬停联动刷新文本）----
    private final Label moveInfoName = new Label("—");
    private final Label moveInfoMeta = new Label();
    private final Label moveInfoStats = new Label(); // 威力 / 命中
    private final Label moveInfoPp = new Label(); // PP（不足时红字提示）
    private final Label moveInfoEffect = new Label(); // 效果说明（无效果时隐藏该行）

    // ---- 道具信息卡（背包面板右块） ----
    private final Label itemInfoName = new Label("—");
    private final Label itemInfoDesc = new Label(); // 效果说明（无说明时显示占位提示）

    // ---- 精灵面板：右块精灵信息卡（随面板显示，悬停/默认联动刷新文本）----
    private final Label partyInfoName = new Label("—");
    private final Label partyInfoType = new Label();
    private final Label partyInfoLv = new Label();
    private final ProgressBar partyInfoHpBar = new ProgressBar();
    private final Label partyInfoHpText = new Label();
    private final Label partyInfoStatus = new Label(); // 异常状态（无则显示“无”保持行高稳定）
    private final Label partyInfoStat1 = new Label(); // 能力值行 1：攻击/防御/特攻
    private final Label partyInfoStat2 = new Label(); // 能力值行 2：特防/速度
    private final Label partyInfoExp = new Label(); // 经验进度

    private final Actions actions;

    /** 本次战斗的背景 classpath（由流程按战斗类型决定；{@code null} = 用 {@link #DEFAULT_BACKGROUND}）。 */
    private final String battleBackground;

    /** 未指定背景（测试/自定义战等）：使用默认野外图。 */
    public BattleView(Actions actions) {
        this(actions, null);
    }

    /**
     * @param battleBackground 战斗背景 classpath（由流程按战斗类型决定，见
     *                         {@link org.example.GameSession#battleBackgroundFor(org.example.model.OptionType)}）；
     *                         {@code null} 时使用默认野外图
     */
    public BattleView(Actions actions, String battleBackground) {
        this.actions = actions;
        this.battleBackground = battleBackground;
    }

    /** 构建战斗场景（等比缩放由 {@link UiScale} 统一施加）。 */
    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        ImageBackgrounds.apply(root, battleBackground != null ? battleBackground : DEFAULT_BACKGROUND); // 背景按战斗类型铺满；卡片/色块/面板其上叠加
        root.setTop(buildTop());
        root.setCenter(buildCenter());
        root.setBottom(buildBottom());
        // 立绘层：在主背景区（上方 2/3）内绝对定位双方立绘（我方左下、敌方右上），叠在内容根之上、演出层之下。
        Pane spriteLayer = buildSpriteLayer();
        // 演出层与内容根同尺寸叠放（StackPane 自动拉伸）：铺满整个设计区、不拦截鼠标，
        // 飞行道具/闪光等临时特效节点挂在此层，坐标经 sceneToLocal 换算，不受 UiScale 缩放影响。
        effectLayer.setMouseTransparent(true);
        effectLayer.setPickOnBounds(false);
        Scene scene = UiScale.scene(new StackPane(root, spriteLayer, effectLayer));
        java.net.URL css = BattleView.class.getResource("/css/battle.css"); // HP 条棕底等内部部件样式
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    // ------------------------------------------------------------------
    // 构建
    // ------------------------------------------------------------------

    /** 上部区域：敌信息卡 + 阶段/金币块（左上堆叠；敌立绘已移至主背景区右上角，见 {@link #buildSpriteLayer()}）。 */
    private Parent buildTop() {
        VBox topLeft = new VBox(4, buildEnemyCard(), buildHud());
        topLeft.setAlignment(Pos.TOP_LEFT);
        HBox top = new HBox(10, topLeft, spacer());
        top.setPadding(new Insets(0, 0, 6, 0));
        top.setAlignment(Pos.TOP_LEFT); // 子块贴顶排列
        return top;
    }

    /** 敌信息卡（左上；圆角 / 阴影朝左上，见 {@link #buildStatCard}）。 */
    private VBox buildEnemyCard() {
        return buildStatCard(wildName, wildTypeBox, wildLv, wildHpBar, wildHpText, wildStatus, true);
    }

    /** 中部区域：中央留空展示主背景（立绘悬浮其上，见 {@link #buildSpriteLayer()}）；右下角己方信息卡。 */
    private Parent buildCenter() {
        VBox card = buildStatCard(playerName, playerTypeBox, playerLv, playerHpBar, playerHpText, playerStatus, false);
        // 关键：center 是 StackPane，默认会把卡片拉高到与中部区域同高；宽高都限定为内容自然尺寸，再以 BOTTOM_RIGHT 归位到右下。
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane center = new StackPane(card);
        StackPane.setAlignment(card, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(card, new Insets(0, 6, 8, 0));
        return center;
    }

    /**
     * 立绘层：按主背景区（上方 2/3）定位两个立绘区域 —— 敌方右上角（宽 60-100%）、
     * 我方左下角（宽 0-40%）；区域容器负责定位与居中，并挂上下浮动待机动画。
     * 立绘放大一倍（84→168）后整体下调：敌区自顶部下移 42（上缘留 ≈8 防超顶）、
     * 我区在原 0-35% 位置基础上再下移 6（脚底轻触底栏顶，仍完整留在主背景区内）。
     * 进出场/受击等演出动画作用在内层立绘底座（{@link #spriteBoxOf}）上，与区域浮动互不干扰。
     */
    private Pane buildSpriteLayer() {
        Pane layer = new Pane();
        layer.setMouseTransparent(true);
        enemySpriteBox = spritePane(enemySprite, enemySpriteFallback);
        playerSpriteBox = spritePane(playerSprite, playerSpriteFallback);
        layer.getChildren().addAll(
                spriteArea(enemySpriteBox, 640 * 0.60, 42, false),
                spriteArea(playerSpriteBox, 0, STAGE_HEIGHT - SPRITE_AREA_H + 6, true));
        return layer;
    }

    /** 单个立绘区域：定尺寸容器 + 立绘居中 + 上下浮动待机动画（{@code floatUpPhase} 控制敌我相位错开）。 */
    private static StackPane spriteArea(StackPane box, double x, double y, boolean floatUpPhase) {
        StackPane area = new StackPane(box);
        area.setPrefSize(SPRITE_AREA_W, SPRITE_AREA_H);
        area.setMinSize(SPRITE_AREA_W, SPRITE_AREA_H);
        area.setMaxSize(SPRITE_AREA_W, SPRITE_AREA_H);
        area.setLayoutX(x);
        area.setLayoutY(y);
        area.setMouseTransparent(true);
        TranslateTransition floating = new TranslateTransition(Duration.millis(MS_SPRITE_FLOAT), area);
        floating.setFromY(floatUpPhase ? 0 : -SPRITE_FLOAT_AMPLITUDE);
        floating.setToY(floatUpPhase ? -SPRITE_FLOAT_AMPLITUDE : 0);
        floating.setInterpolator(Interpolator.EASE_BOTH);
        floating.setAutoReverse(true);
        floating.setCycleCount(Animation.INDEFINITE);
        floating.play();
        return area;
    }

    /** 底部区域：大横面板（半透明白，高 107 ≈ 界面高 1/4）—— 左：场况行 + 日志；右：行动区。 */
    private Parent buildBottom() {
        // 左块：场况（天气/场地，无数据时留空）+ 战斗日志（深色文字；白字黑描边方案因 JavaFX 描边渲染性能
        // 问题废弃——动态中文描边每字符 ~50ms 光栅且无缓存，见 MAX_LOG_ROWS 注释）
        fieldStatus.setStyle(YH + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0b2a42;");
        leftPanel.setStyle(LEFT_LOG_FRAME_CSS); // 初始即日志态：带外框（showMoveMenu 切换技能面板时清除）
        leftPanel.getChildren().setAll(fieldStatus, logLines);
        VBox.setVgrow(logLines, Priority.ALWAYS);
        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        leftPanel.setAlignment(Pos.CENTER); // 内容（场况行+日志 / 技能面板组）在左块内垂直居中，不贴顶
        leftPanel.setFillWidth(true);

        // 右块：行动区（主菜单 2×2 网格 / 子菜单 / 学招 / 结算全量重建；技能面板时换为技能信息卡）
        actionBox.setAlignment(Pos.CENTER);

        HBox bottom = new HBox(10, leftPanel, actionBox);
        bottom.setPadding(new Insets(4, 12, 4, 12));
        bottom.setAlignment(Pos.CENTER);
        // 底栏高度 = 界面高 1/4（426.67/4 ≈ 107）；内边距同步收窄为 4，内容区约 99，容纳压缩后的各状态面板
        bottom.setPrefHeight(107);
        bottom.setStyle("-fx-background-color: rgba(255, 255, 255, 0.66);"
                + "-fx-background-radius: 10; -fx-border-radius: 10;");
        return bottom;
    }

    /**
     * 双方同款信息卡：上行 名称/属性徽章组（多属性时每枚属性独立徽章，内容随 {@link #refreshTypeBadges} 重建）/等级，
     * 下行 HP 条与数值，再下行异常状态徽章（无异常时整行隐藏，卡高自动回落）。
     */
    private VBox buildStatCard(Label name, HBox typeBox, Label lv,
                               ProgressBar hpBar, Label hpText, Label status, boolean enemy) {
        VBox card = battleCard(enemy);
        HBox line1 = new HBox(6);
        line1.setAlignment(Pos.CENTER_LEFT);
        name.setStyle(YH + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1a2a3a;");
        typeBox.setAlignment(Pos.CENTER_LEFT);
        lv.setStyle(YH + "-fx-font-size: 13px; -fx-text-fill: #4a5b70;");
        line1.getChildren().addAll(name, typeBox, lv);

        HBox line2 = new HBox(6);
        line2.setAlignment(Pos.CENTER_LEFT);
        hpBar.setPrefWidth(130);
        hpBar.getStyleClass().add("battle-hp"); // 棕底 + 渐变填充（/css/battle.css）
        hpText.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #1a2a3a; -fx-font-weight: bold;");
        line2.getChildren().addAll(hpBar, hpText);

        status.setStyle(statusChip());
        status.setManaged(false);
        status.setVisible(false);

        card.getChildren().addAll(line1, line2, status);
        return card;
    }

    /** 异常状态徽章样式（橙底深字，与属性徽章区分）。 */
    private static String statusChip() {
        return YH + "-fx-background-color: #ffe6c7; -fx-background-radius: 4;"
                + "-fx-padding: 1 8; -fx-font-size: 12px; -fx-text-fill: #a35200;"
                + "-fx-font-weight: bold;";
    }

    /**
     * 异常状态徽章文案：倒下 &gt; 主要异常 &gt; 混乱（两者可同时显示，用空格连接）；无异常返回空串。
     */
    private static String statusBadgeText(Pokemon p) {
        if (p == null) {
            return "";
        }
        if (p.isFainted()) {
            return "已倒下";
        }
        StringBuilder sb = new StringBuilder();
        if (p.getStatus() != StatusCondition.NONE) {
            sb.append(p.getStatus().getDisplayName());
        }
        if (p.isConfused()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(StatusCondition.CONFUSION.getDisplayName());
        }
        return sb.toString();
    }

    /** 把异常状态徽章刷到标签：无异常时隐藏并让出布局空间。 */
    private static void applyStatusBadge(Label target, Pokemon p) {
        String text = statusBadgeText(p);
        target.setText(text);
        boolean visible = !text.isEmpty();
        target.setManaged(visible);
        target.setVisible(visible);
    }

    /**
     * 立绘底座：透明贴图直接悬于背景之上（素材自带透明底，不再套底座色框与边框）。
     * 图片等比适配 {@link #SPRITE_SIZE}；无图时回退「精灵名 + 立绘」占位文本（暗底胶囊保证浅色背景上可读）。
     */
    private StackPane spritePane(ImageView imageView, Label fallback) {
        fallback.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        fallback.setStyle(YH + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: rgba(255,255,255,0.95);"
                + "-fx-background-color: rgba(24, 40, 64, 0.55); -fx-background-radius: 8; -fx-padding: 5 8;");
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(SPRITE_SIZE);
        imageView.setFitHeight(SPRITE_SIZE);
        StackPane box = new StackPane(fallback, imageView);
        // 限定为内容自然尺寸：StackPane 默认把 managed 子节点拉伸到整个区域，
        // 若放任拉伸，受击闪光（包围盒）等特效会按区域大小绘制而非立绘大小。
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return box;
    }

    /**
     * 按精灵名加载立绘图片（委托公共工具 {@link SpriteLoader}，classpath {@code /images/pokemon/}）；
     * 加载失败返回 {@code null}（调用方回退占位文本）。结果带缓存。
     */
    private static Image loadSprite(String pokemonName) {
        return SpriteLoader.load(pokemonName);
    }

    /** 把某只精灵的立绘刷到指定图片区：有图则显示图片；无图则隐藏图片、回退显示精灵名占位。 */
    private static void applySprite(ImageView imageView, Label fallback, Pokemon p) {
        applySpriteByName(imageView, fallback, p == null ? null : p.getName());
    }

    /**
     * 按<b>精灵名</b>刷新立绘（演出事件只携带名字，战斗中精灵对象随换宠而变，故与 {@link #applySprite} 分开）。
     * 有图显示图片；无图回退「精灵名 + 立绘」占位文本。
     */
    private static void applySpriteByName(ImageView imageView, Label fallback, String name) {
        Image image = loadSprite(name);
        if (image != null) {
            imageView.setImage(image);
            imageView.setManaged(true);
            imageView.setVisible(true);
            fallback.setVisible(false);
            fallback.setManaged(false);
        } else {
            imageView.setImage(null);
            imageView.setVisible(false);
            imageView.setManaged(false);
            fallback.setText(name == null || name.isBlank() ? "立 绘" : name + "\n立 绘");
            fallback.setVisible(true);
            fallback.setManaged(true);
        }
    }

    /** 左上角纯文本块（敌卡下方）：当前地图阶段（上）+ 玩家金币（下，金色）。无背景框、小字号，直接叠于背景图上。 */
    private VBox buildHud() {
        stageLabel.setStyle(YH + "-fx-font-size: 10px; -fx-text-fill: #333;"
                + "-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.85), 2, 0.6, 0, 0);"); // 白晕保底可读
        moneyLabel.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #b8860b;"
                + "-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.85), 2, 0.6, 0, 0);");
        VBox hud = new VBox(1, stageLabel, moneyLabel);
        hud.setAlignment(Pos.CENTER_LEFT);
        hud.setMaxHeight(Region.USE_PREF_SIZE);
        hud.setMaxWidth(Region.USE_PREF_SIZE);
        return hud;
    }

    /**
     * 信息卡通用底：奶油米色芯 + 外蓝内黄双层描边（与日志框同款，用 {@link #FRAME_NAVY} / {@link #FRAME_GOLD}）
     * + 对角圆角 + 硬阴影（react 信息卡）。敌卡圆角朝左上（18 4 18 4）、阴影左下；己方卡镜像（4 18 4 18 / 阴影右下）。
     * 描边由背景层内缩模拟（不占 padding → 卡内文本位置不变）：外圈蓝 2px + 内圈黄 3px，均画在卡内，卡尺寸不变。
     */
    private VBox battleCard(boolean enemy) {
        VBox card = new VBox(3);
        String radius = enemy ? "18 4 18 4, 16 2 16 2, 13 0 13 0" : "4 18 4 18, 2 16 2 16, 0 13 0 13";
        String shadow = enemy ? "-4, 4" : "4, 4";
        card.setStyle("-fx-background-color: " + FRAME_NAVY + ", " + FRAME_GOLD + ", " + CARD_CREAM + ";"
                + "-fx-background-radius: " + radius + "; -fx-background-insets: 0, 2, 5;"
                + "-fx-padding: 6 12;"
                + "-fx-effect: dropshadow(gaussian, rgba(42, 58, 92, 0.85), 0, 1, " + shadow + ");");
        return card;
    }

    /** 底栏信息卡紧凑底样式（金框米色芯，芯色 {@link #CARD_CREAM} 与宝可梦信息卡同底色；与 {@link #battleCard(boolean)} 同为 react 体系，纵向 padding 6→3，适配 1/4 底栏 ≈99 内容区）。 */
    private static final String BOTTOM_CARD_CSS =
            "-fx-background-color: " + FRAME_NAVY + ", " + FRAME_GOLD + ", " + CARD_CREAM + ";"
                    + " -fx-background-insets: -2, 0, 3; -fx-background-radius: 18, 16, 13;"
                    + " -fx-padding: 3 10;"
                    + "-fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.35), 8, 0.4, 0, 4);";

    /** 占位弹性空白（把右上角信息块顶到最右）。 */
    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static final String YH = "-fx-font-family: 'Microsoft YaHei'; ";

    // ---- react 战斗页设计色板（2026-09-11 样式改版；描边一律由背景层内缩模拟，不占 padding）----
    /** 深海军蓝：宽按钮描边与浅色属性徽章的深色文字。 */
    private static final String POKE_INK = "#2a3a5c";

    /** 马卡龙按钮配色（背景 / 描边 / 文字 / 硬阴影色；对应 react 黄=战斗、蓝=背包、绿=精灵、红=逃跑）。 */
    private record PokeTone(String bg, String border, String text, String shadow) { }

    private static final PokeTone TONE_YELLOW = new PokeTone("#f8d030", "#b07818", "#3a2000", "#b07818");
    private static final PokeTone TONE_BLUE = new PokeTone("#6890f0", "#2850b0", "#ffffff", "#2850b0");
    private static final PokeTone TONE_GREEN = new PokeTone("#78c850", "#286820", "#ffffff", "#286820");
    private static final PokeTone TONE_RED = new PokeTone("#f85888", "#a01840", "#ffffff", "#a01840");
    /** 马卡龙四色轮换表（技能 / 道具 / 精灵格按格索引取色）。 */
    private static final PokeTone[] TONE_CYCLE = {TONE_YELLOW, TONE_BLUE, TONE_GREEN, TONE_RED};

    /**
     * 马卡龙按钮样式：描边色垫底 + 面色内缩 2px 作芯（不占 padding）+ 向下 3px 硬阴影（react poke-button）。
     * {@code sizeCss} 由调用方给出字号 / 尺寸等不变项。
     */
    private static String toneCss(PokeTone tone, String sizeCss) {
        return YH + sizeCss + "-fx-background-color: " + tone.border() + ", " + tone.bg() + ";"
                + "-fx-background-radius: 7, 5; -fx-background-insets: 0, 2;"
                + "-fx-text-fill: " + tone.text() + ";"
                + "-fx-effect: dropshadow(gaussian, " + tone.shadow() + ", 0, 1, 0, 3);";
    }

    /** 灰态按钮样式（禁用 / PP 不足 / 倒下格：与马卡龙按钮同构，仅换灰阶）。 */
    private static String greyToneCss(String sizeCss) {
        return YH + sizeCss + "-fx-background-color: #b9c2cc, #e9e9e9;"
                + "-fx-background-radius: 7, 5; -fx-background-insets: 0, 2;"
                + "-fx-text-fill: #8a8a8a;";
    }

    /** 属性徽章样式（react TypeBadge：按属性分色；电 / 冰等浅色属性用深字保证可读）。 */
    private static String chipFor(ElementType type) {
        if (type == null) {
            return chip();
        }
        return YH + "-fx-background-color: " + type.getColorCode() + "; -fx-background-radius: 9;"
                + "-fx-padding: 1 8; -fx-font-size: 12px; -fx-text-fill: " + badgeTextColor(type) + ";"
                + "-fx-font-weight: bold;";
    }

    /** 取精灵主属性（无则 null；供单徽章场景，如精灵详情卡）。 */
    private static ElementType primaryTypeOf(Pokemon p) {
        return p == null || p.getSpecies() == null ? null
                : p.getSpecies().getTypes().stream().findFirst().orElse(null);
    }

    /** 浅色属性（亮底）用深海军蓝字，其余用白字。 */
    private static String badgeTextColor(ElementType type) {
        return switch (type) {
            case ELECTRIC, ICE, GROUND, STEEL, NORMAL, ROCK, BUG, FAIRY -> POKE_INK;
            default -> "#ffffff";
        };
    }

    /** 属性徽章样式。 */
    private static String chip() {
        return YH + "-fx-background-color: #e3edf9; -fx-background-radius: 4;"
                + "-fx-padding: 1 8; -fx-font-size: 12px; -fx-text-fill: #2f5d9e;";
    }

    // ------------------------------------------------------------------
    // 更新
    // ------------------------------------------------------------------

    /** 刷新双方状态卡片（含属性徽章组与异常状态徽章）与双方立绘。 */
    public void refreshPokemon(Pokemon player, Pokemon wild) {
        playerName.setText(player.getName());
        refreshTypeBadges(playerTypeBox, player); // 属性逐枚独立显示（各自专属配色）
        playerLv.setText("Lv." + player.getLevel());
        refreshHp(playerHpBar, playerHpText, player);
        applyStatusBadge(playerStatus, player);
        applySprite(playerSprite, playerSpriteFallback, player);

        wildName.setText(wild.getName());
        refreshTypeBadges(wildTypeBox, wild);
        wildLv.setText("Lv." + wild.getLevel());
        refreshHp(wildHpBar, wildHpText, wild);
        applyStatusBadge(wildStatus, wild);
        applySprite(enemySprite, enemySpriteFallback, wild);
    }

    /**
     * 刷新属性徽章组：按精灵属性逐枚重建 —— 多属性（如 草 / 毒）各一枚独立徽章、各自专属底色，
     * 不再合并为单一文本；无属性时清空。
     */
    private static void refreshTypeBadges(HBox box, Pokemon p) {
        box.getChildren().clear();
        if (p == null || p.getSpecies() == null) {
            return;
        }
        for (ElementType type : p.getSpecies().getTypes()) {
            Label chip = new Label(type.getDisplayName());
            chip.setStyle(chipFor(type));
            box.getChildren().add(chip);
        }
    }

    /** 刷新天气/场地状态行（无天气/场地时清空；位于底栏日志上方）。 */
    public void refreshFieldStatus(Weather weather, Terrain terrain) {
        StringBuilder sb = new StringBuilder();
        if (weather.isActive()) {
            sb.append("天气：").append(weather.getDisplayName());
        }
        if (terrain.isActive()) {
            if (sb.length() > 0) {
                sb.append("    ");
            }
            sb.append("场地：").append(terrain.getDisplayName());
        }
        fieldStatus.setText(sb.toString());
    }

    /** 设置右上角信息块内容（阶段/金币；流程数据接入后由控制器调用，TODO(dev)）。 */
    public void setHud(String stageText, int money) {
        if (stageText != null && !stageText.isBlank()) {
            stageLabel.setText(stageText);
        }
        moneyLabel.setText("金币 " + money);
    }

    /** 显示战斗日志：先恢复左块为日志态，再按行重建 Label（仅保留最近 {@link #MAX_LOG_ROWS} 行，最新一行在底部）。 */
    public void showLog(List<String> lines) {
        restoreLeftPanel();
        logLines.getChildren().clear();
        int from = Math.max(0, lines.size() - MAX_LOG_ROWS);
        for (int i = from; i < lines.size(); i++) {
            logLines.getChildren().add(logLine(lines.get(i)));
        }
    }

    /** 战斗日志行：深色文字（与场况行同字体基调）；不换行，超长截断防溢出。 */
    private Label logLine(String line) {
        Label l = new Label(line.length() <= 40 ? line : line.substring(0, 39) + "…");
        l.setStyle(YH + "-fx-font-size: 13px; -fx-text-fill: #0b2a42;");
        l.setWrapText(false);
        return l;
    }

    // ---- 底部右侧行动区 ----

    /** 主菜单：2×2 等大网格（战斗 / 背包 / 精灵 / 逃跑，行优先）。精灵入口恒可点开（可换性判定在队伍菜单内）。 */
    public void showMainMenu(Runnable onSkills, Runnable onBag, Runnable onOpenParty) {
        actionBox.getChildren().clear();
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setAlignment(Pos.CENTER);
        grid.add(gridButton("战斗", false, TONE_YELLOW, e -> onSkills.run()), 0, 0);
        grid.add(gridButton("背包", false, TONE_BLUE, e -> onBag.run()), 1, 0);
        // 无论是否有可换精灵都能点开队伍菜单（倒下/当前出战精灵在菜单内禁用）
        grid.add(gridButton("精灵", false, TONE_GREEN, e -> onOpenParty.run()), 0, 1);
        grid.add(gridButton("逃跑", false, TONE_RED, e -> actions.onRun()), 1, 1);
        actionBox.getChildren().add(grid);
    }

    /** 主菜单 2×2 网格按钮：等大（92×32），马卡龙色 + 深描边硬底阴影（禁用态灰化）。 */
    private Button gridButton(String text, boolean disabled, PokeTone tone,
                              javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button b = new Button(text);
        b.setMaxWidth(200);
        String size = "-fx-font-size: 14px; -fx-pref-width: 92px; -fx-pref-height: 32px;";
        b.setStyle(disabled ? greyToneCss(size) : toneCss(tone, size));
        b.setDisable(disabled);
        b.setOnAction(handler);
        return b;
    }

    /** 显示技能面板（点主菜单「战斗」后）：左块换成 [状态行+2×2 技能格]，右块换成技能信息卡；
     * 光标移到技能格上右块联动显示该技能的名称/类型·分类/威力/命中/PP；点击可用技能即出招（PP 不足灰格可看不可点）。 */
    public void showMoveMenu(List<MoveSlot> slots, Runnable onBack) {
        // ---- 左块：状态行（天气/场地 + 右侧返回按钮）----
        Button back = compactButton("返回");
        back.setOnAction(e -> onBack.run());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox statusRow = new HBox(8, fieldStatus, gap, back);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // ---- 左块：2×2 技能格（名 + PP·类型·威力 两行）----
        GridPane grid = new GridPane();
        // 底栏收窄至 1/4（2026-09-11）：列距 16 保持、行距 12 → 6，四格仍舒展但整体高度适配内容区 ≈99
        grid.setHgap(16);
        grid.setVgap(6);
        grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < 4; i++) {
            MoveSlot slot = i < slots.size() ? slots.get(i) : null;
            if (slot == null) {
                grid.add(skillCell("—", "未习得", true, null, i), i % 2, i / 2);
                continue;
            }
            boolean exhausted = slot.exhausted();
            String pp = exhausted ? "PP 不足" : "PP " + slot.getPp() + "/" + slot.getMove().getMaxPp();
            // 按钮第二行只保留 PP（2026-09-09：威力/属性不再进按钮，集中展示在右侧技能信息卡）
            Button b = skillCell(slot.getMove().getName(), pp, exhausted, slot, i);
            // 悬停联动：右侧信息卡切换为当前技能（PP 不足也可查看，仅不能点击出招）
            b.setOnMouseEntered(e -> updateMoveInfo(slot));
            if (!exhausted) {
                b.setOnAction(e -> actions.onMoveSelected(slot));
            }
            grid.add(b, i % 2, i / 2);
        }

        leftPanel.setStyle(""); // 技能面板态：左块是状态行+技能格，不套日志框（restoreLeftPanel 恢复）
        leftPanel.getChildren().setAll(statusRow, grid);
        actionBox.getChildren().clear();
        actionBox.getChildren().add(buildMoveInfoCard());
        updateMoveInfo(slots.isEmpty() ? null : slots.get(0)); // 打开面板默认展示第一个技能
    }

    /** 恢复底栏左块为日志态（[场况行, 日志 Label 流]）：除技能面板外的状态都经 showLog 回到该布局。 */
    private void restoreLeftPanel() {
        leftPanel.setStyle(LEFT_LOG_FRAME_CSS);
        leftPanel.getChildren().setAll(fieldStatus, logLines);
    }

    /** 技能格（两行：技能名 / PP）；grey=PP 不足格（可悬停查看详情，点击无动作）；null slot 真禁用占位；colorIndex 取马卡龙轮换色。 */
    private Button skillCell(String name, String sub, boolean grey, MoveSlot slot, int colorIndex) {
        Button b = new Button(name + "\n" + sub);
        b.setFocusTraversable(false);
        String size = "-fx-font-size: 11px; -fx-pref-width: 168px; -fx-pref-height: 32px;"
                + "-fx-line-spacing: 1; -fx-padding: 1 4;";
        b.setStyle(grey ? greyToneCss(size) : toneCss(TONE_CYCLE[colorIndex % TONE_CYCLE.length], size));
        b.setDisable(slot == null); // 未习得占位格真禁用（无详情可看）；PP 不足格保留悬停联动
        return b;
    }

    /** 技能信息卡（右块）：技能名 / 类型·分类 / 威力·命中 / PP / 效果（无效果时隐藏效果行）。
     * 卡宽固定为常量（效果行 wrap 上限 170 + 卡 padding 24 = 194）：否则悬停不同技能时文本长短变化会
     * 改变 actionBox pref 宽，牵动左块 2×2 技能格水平位移（2026-09-09 实测悬停短文本技能时网格右移 7px）。 */
    private VBox buildMoveInfoCard() {
        VBox card = battleCard(false);
        card.setStyle(BOTTOM_CARD_CSS); // 1/4 底栏：紧凑底样式
        card.setSpacing(2);
        card.setPrefWidth(194);
        moveInfoName.setStyle(YH + "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a2a3a;");
        moveInfoMeta.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #2f5d9e;");
        moveInfoStats.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #1a2a3a;");
        moveInfoPp.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #1a2a3a;");
        moveInfoEffect.setWrapText(true);
        moveInfoEffect.setMaxWidth(170);
        moveInfoEffect.setStyle(YH + "-fx-font-size: 10px; -fx-text-fill: #5a6a7e;");
        card.getChildren().addAll(moveInfoName, moveInfoMeta, moveInfoStats, moveInfoPp, moveInfoEffect);
        return card;
    }

    /** 联动刷新：把某技能数据刷到右块信息卡（null 清为占位）。 */
    private void updateMoveInfo(MoveSlot slot) {
        if (slot == null) {
            moveInfoName.setText("—");
            moveInfoMeta.setText("未习得");
            moveInfoStats.setText("");
            moveInfoPp.setText("");
            moveInfoEffect.setText("");
            moveInfoEffect.setManaged(false);
            moveInfoEffect.setVisible(false);
            return;
        }
        Move move = slot.getMove();
        boolean exhausted = slot.exhausted();
        moveInfoName.setText(move.getName());
        moveInfoMeta.setText(move.getType().getDisplayName() + " · " + categoryText(move.getCategory()));
        String power = move.isStatus() ? "—" : String.valueOf(move.getPower());
        String acc = move.getAccuracy() <= 0 ? "—" : String.valueOf(move.getAccuracy());
        moveInfoStats.setText("威力 " + power + "    命中 " + acc);
        String ppText = "PP " + slot.getPp() + " / " + slot.getMaxPp();
        moveInfoPp.setText(exhausted ? ppText + "（PP 不足）" : ppText);
        moveInfoPp.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: "
                + (exhausted ? "#c03030;" : "#1a2a3a;"));
        String effect = moveEffectText(move);
        moveInfoEffect.setText(effect);
        boolean hasEffect = !effect.isEmpty();
        moveInfoEffect.setManaged(hasEffect);
        moveInfoEffect.setVisible(hasEffect);
    }

    /** 技能类别中文文案。 */
    private static String categoryText(MoveCategory category) {
        return switch (category) {
            case PHYSICAL -> "物理";
            case SPECIAL -> "特殊";
            case STATUS -> "变化";
        };
    }

    /**
     * 效果行文案：天气/场地类技能显示开启目标；附带异常状态的技能显示「可能使目标陷入X」
     * （必定触发时显示「使目标陷入X」）；无效果返回空串（调用方隐藏该行）。
     */
    private static String moveEffectText(Move move) {
        String status = inflictionText(move);
        MoveEffect effect = move.getEffect();
        if (effect == MoveEffect.NONE) {
            return status;
        }
        String fieldText;
        Weather weather = effect.toWeather();
        if (weather != null) {
            fieldText = "效果：开启" + weather.getDisplayName();
        } else {
            Terrain terrain = effect.toTerrain();
            fieldText = terrain != null ? "效果：开启" + terrain.getDisplayName() : "效果：附加";
        }
        return status.isEmpty() ? fieldText : fieldText + "\n" + status;
    }

    /** 异常状态说明行：按触发概率区分必然/概率文案；无附带异常返回空串。 */
    private static String inflictionText(Move move) {
        if (!move.hasInfliction()) {
            return "";
        }
        StatusCondition condition = move.getInflicts();
        String name = condition.getDisplayName();
        int chance = move.getInflictionChance();
        if (chance >= 100) {
            return "使目标陷入" + name + "状态";
        }
        return "可能使目标陷入" + name + "状态（" + chance + "%）";
    }

    /** 小号紧凑按钮（技能面板状态行右侧「返回」）：黄底棕边（react 返回按钮）。 */
    private Button compactButton(String text) {
        Button b = new Button(text);
        b.setStyle(toneCss(TONE_YELLOW, "-fx-font-size: 11px; -fx-padding: 1 8;"));
        return b;
    }

    /** 背包中的一个可点击道具条目：{@code text} 为格内主文本，{@code detail} 为右卡说明文本。 */
    public record ItemButton(String text, boolean disabled, String detail) {

        /** 简化构造：无说明文本。 */
        public ItemButton(String text, boolean disabled) {
            this(text, disabled, "");
        }
    }

    /**
     * 显示背包面板：左块 [状态行 + 道具格（3 列，条目多于一屏时纵向滚动）]，右块道具信息卡（悬停联动）。
     * 点击可用道具即交回控制器（由控制器决定是直接对敌使用还是先选目标精灵）；
     * 无可用目标的道具格为灰格（可悬停查看说明，点击无动作）。
     */
    public void showBagMenu(List<ItemButton> items, IntConsumer onPick, Runnable onBack) {
        Button back = compactButton("返回");
        back.setOnAction(e -> onBack.run());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox statusRow = new HBox(8, fieldStatus, gap, back);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // 3 列道具格（115px/格，与精灵格同宽）：不撑高底栏，超出部分滚动
        GridPane grid = new GridPane();
        grid.setHgap(13);
        grid.setVgap(8);
        grid.setAlignment(Pos.TOP_CENTER);
        for (int i = 0; i < items.size(); i++) {
            ItemButton entry = items.get(i);
            int index = i;
            Button b = itemCell(entry.text(), entry.disabled(), i);
            b.setOnMouseEntered(e -> updateItemInfo(entry));
            if (!entry.disabled()) {
                b.setOnAction(e -> onPick.accept(index));
            }
            grid.add(b, i % 3, i / 3);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(371);
        scroll.setPrefHeight(66); // 底栏收窄至 1/4：状态行（≈19）+ 滚动区 66，容纳于内容区 ≈99
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        leftPanel.setStyle(""); // 背包面板态：左块是状态行+道具格，不套日志框（showLog 恢复）
        leftPanel.getChildren().setAll(statusRow, scroll);
        actionBox.getChildren().clear();
        actionBox.getChildren().add(buildItemInfoCard());
        updateItemInfo(items.isEmpty() ? null : items.get(0)); // 打开面板默认展示第一个道具
    }

    /** 道具格（一行：名称 ×数量）；grey=当前无可用目标（可悬停查看说明，点击无动作）；colorIndex 取马卡龙轮换色。 */
    private Button itemCell(String text, boolean grey, int colorIndex) {
        Button b = new Button(text);
        b.setFocusTraversable(false);
        String size = "-fx-font-size: 12px; -fx-pref-width: 115px; -fx-pref-height: 28px; -fx-padding: 2 4;";
        b.setStyle(grey ? greyToneCss(size) : toneCss(TONE_CYCLE[colorIndex % TONE_CYCLE.length], size));
        return b;
    }

    /** 道具信息卡（右块，宽度与精灵信息卡一致）：道具名 + 效果说明。 */
    private VBox buildItemInfoCard() {
        VBox card = battleCard(false);
        card.setStyle(BOTTOM_CARD_CSS); // 1/4 底栏：紧凑底样式
        card.setSpacing(2);
        card.setPrefWidth(PARTY_INFO_CARD_WIDTH);
        card.setAlignment(Pos.TOP_LEFT);
        itemInfoName.setStyle(YH + "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a2a3a;");
        itemInfoDesc.setWrapText(true);
        itemInfoDesc.setMaxWidth(210);
        itemInfoDesc.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #1a2a3a;");
        card.getChildren().addAll(itemInfoName, itemInfoDesc);
        return card;
    }

    /** 联动刷新：把某道具的说明刷到右卡（null 清为占位）。 */
    private void updateItemInfo(ItemButton entry) {
        itemInfoName.setText(entry == null ? "—" : entry.text());
        itemInfoDesc.setText(entry == null || entry.detail().isEmpty() ? "选择道具查看说明" : entry.detail());
    }

    /**
     * 精灵面板（点主菜单「精灵」后）：与技能面板同构 —— 左块 [状态行 + 3×2 固定六格]，右块精灵信息卡。
     * 格内只显示 名称/等级/血量；悬停格子右侧联动显示该精灵完整信息（等级/属性/HP/异常状态/能力值/经验），
     * 点击健康且非当前出战的精灵即切换上场（倒下的灰格可看详情不可点，当前出战带 ★ 亦不可点）。
     * 队伍不足 6 只时空槽位置灰占位（固定六格保证布局不随队伍数量变化）。
     * 格宽较技能格窄（115 vs 168）：底栏内容区总宽 616 预算下，右卡需 235 才能完整容纳能力值最长行，
     * 三列网格 3×115+2×13=371 恰等于左块宽度（与技能格 2 列宽格互补）。
     */
    public void showPartyMenu(List<Pokemon> party, int activeIndex, IntConsumer onPick, Runnable onBack) {
        renderPartyGrid(party, activeIndex,
                idx -> !party.get(idx).isFainted() && idx != activeIndex, // 可点：健康且非当前出战
                idx -> party.get(idx).isFainted(),                        // 灰格：倒下
                onPick, onBack, null, "返回");
    }

    /**
     * 放生面板（满队捕捉成功后）：与精灵面板同构的 3×2 六格，全部精灵均可点击放生
     * （含倒下与当前出战），右侧卡悬停联动；顶部提示说明放生后果（装备返还），
     * 「放弃捕捉」按钮回调 {@code onDiscard}。
     */
    public void showReleaseMenu(List<Pokemon> party, int activeIndex,
                                IntConsumer onRelease, Runnable onDiscard, String hint) {
        renderPartyGrid(party, activeIndex,
                idx -> true, // 全部可点：放生任意队内精灵
                idx -> false,
                onRelease, onDiscard, hint, "放弃捕捉");
    }

    /**
     * 目标选择面板（背包用药后）：与精灵面板同构的 3×2 六格，格子可否点击由 {@code selectable} 决定
     * （如伤药只能选未满血且未倒下的精灵），不可选格以灰格呈现（仍可悬停查看详情）。
     * 点中合法目标即回调其队伍下标（由控制器转交 {@code useItem(item, partyIndex)}）。
     *
     * @param party       玩家队伍
     * @param activeIndex 当前出战精灵下标（仅用于 ★ 标记）
     * @param selectable  某下标是否可选作目标
     * @param onPick      选中目标时的回调（参数为队伍下标）
     * @param onBack      返回背包
     * @param hint        状态行提示文案（如「选择使用【伤药】的目标」）；显示时顶替场况文本，null 时状态行显示场况文本
     */
    public void showTargetMenu(List<Pokemon> party, int activeIndex, IntPredicate selectable,
                               IntConsumer onPick, Runnable onBack, String hint) {
        renderPartyGrid(party, activeIndex, selectable, idx -> !selectable.test(idx), onPick, onBack, hint, "返回");
    }

    /**
     * 队伍六格面板通用渲染（精灵面板 / 道具目标面板共用）：
     * 左块 [状态行 + 3×2 固定六格]（有提示文案时提示顶替场况文本显示在状态行内），右块精灵信息卡（悬停联动）。
     *
     * @param selectable 某下标是否可点击选中
     * @param grey       某下标是否灰格呈现（不可点但可查看详情）
     * @param backText   返回按钮文案（精灵/目标面板为「返回」，放生面板为「放弃捕捉」）
     */
    private void renderPartyGrid(List<Pokemon> party, int activeIndex,
                                 IntPredicate selectable, IntPredicate grey,
                                 IntConsumer onPick, Runnable onBack, String hint, String backText) {
        leftPanel.setStyle("");
        Button back = compactButton(backText);
        back.setOnAction(e -> onBack.run());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        // 底栏收窄至 1/4 后纵向仅余 ≈99：有提示文案时（道具目标选择）提示顶替场况文本
        // 显示在同一状态行内，不再另起一行，保证 3×2 格 + 状态行放得下
        Label headLabel = fieldStatus;
        if (hint != null && !hint.isEmpty()) {
            headLabel = new Label(hint);
            headLabel.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #2f5d9e;");
        }
        HBox statusRow = new HBox(8, headLabel, gap, back);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // 3×2 固定六格（行优先）；列距 13 使三列网格恰填满左块（115×3+13×2=371）
        GridPane grid = new GridPane();
        grid.setHgap(13);
        grid.setVgap(6);
        grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < 6; i++) {
            int index = i;
            Pokemon p = index < party.size() ? party.get(index) : null;
            if (p == null) {
                grid.add(partyCell("—", "空位", true, true, index), index % 3, index / 3);
                continue;
            }
            boolean fainted = p.isFainted();
            boolean active = index == activeIndex;
            // 格内第二行（115px 宽，文本紧凑防溢出）：Lv + HP（斜杠不带空格）/ 倒下 + 异常状态摘要
            String badge = statusBadgeText(p);
            String sub = fainted ? "已倒下" : "HP " + p.getCurrentHp() + "/" + p.getMaxHp();
            if (!fainted && !badge.isEmpty()) {
                sub = sub + "  " + badge;
            }
            Button b = partyCell((active ? "★ " : "") + p.getName(), "Lv." + p.getLevel() + "  " + sub,
                    grey.test(index), false, index);
            // 悬停联动：详情卡切换（倒下/出战也可查看）；点击仅合法目标可选中
            b.setOnMouseEntered(e -> updatePartyInfo(p));
            if (selectable.test(index)) {
                b.setOnAction(e -> onPick.accept(index));
            }
            grid.add(b, index % 3, index / 3);
        }

        leftPanel.getChildren().setAll(statusRow, grid);
        actionBox.getChildren().clear();
        actionBox.getChildren().add(buildPartyInfoCard());
        updatePartyInfo(party.isEmpty() ? null : party.get(0)); // 打开面板默认展示第一只
    }

    /** 精灵格（两行：名称 / 等级+血量）：grey=倒下（可悬停查看详情、点击无动作）；空位真禁用（无详情可看）；colorIndex 取马卡龙轮换色。 */
    private Button partyCell(String name, String sub, boolean grey, boolean disabled, int colorIndex) {
        Button b = new Button(name + "\n" + sub);
        b.setFocusTraversable(false);
        String size = "-fx-font-size: 11px; -fx-pref-width: 115px; -fx-pref-height: 32px;"
                + "-fx-line-spacing: 1; -fx-padding: 1 4;";
        b.setStyle(grey ? greyToneCss(size) : toneCss(TONE_CYCLE[colorIndex % TONE_CYCLE.length], size));
        b.setDisable(disabled);
        return b;
    }

    /** 精灵信息卡（右块）固定宽度：内容最长行为能力值行（“攻击 100    防御 100    特攻 100”约 200px 逻辑）
     * + 卡 padding 24 + 余量 → 235。此宽度同时是背包态右空卡宽度（左右分界与精灵面板一致，切换不跳动）。 */
    private static final int PARTY_INFO_CARD_WIDTH = 235;

    /** 精灵信息卡（右块）：名称+属性+等级 / HP 条 / 状态+经验（同行） / 能力值两行。
     * 卡宽与行数均固定：悬停不同精灵时文本变化只在卡内布局，不牵动左块网格（防抖原则，同技能卡）。 */
    private VBox buildPartyInfoCard() {
        VBox card = battleCard(false);
        card.setStyle(BOTTOM_CARD_CSS); // 1/4 底栏：紧凑底样式（卡为 6 行信息，padding 6→3）
        card.setSpacing(2);
        card.setPrefWidth(PARTY_INFO_CARD_WIDTH);
        HBox line1 = new HBox(6);
        line1.setAlignment(Pos.CENTER_LEFT);
        partyInfoName.setStyle(YH + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a2a3a;");
        partyInfoType.setStyle(chip());
        partyInfoLv.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #4a5b70;");
        line1.getChildren().addAll(partyInfoName, partyInfoType, partyInfoLv);
        HBox line2 = new HBox(6);
        line2.setAlignment(Pos.CENTER_LEFT);
        partyInfoHpBar.setPrefWidth(118);
        partyInfoHpBar.getStyleClass().add("battle-hp"); // 棕底 + 渐变填充（/css/battle.css）
        partyInfoHpText.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #1a2a3a; -fx-font-weight: bold;");
        line2.getChildren().addAll(partyInfoHpBar, partyInfoHpText);
        partyInfoStatus.setStyle(YH + "-fx-font-size: 10px; -fx-text-fill: #5a6a7e;");
        partyInfoStat1.setStyle(YH + "-fx-font-size: 10px; -fx-text-fill: #22344c;");
        partyInfoStat2.setStyle(YH + "-fx-font-size: 10px; -fx-text-fill: #22344c;");
        partyInfoExp.setStyle(YH + "-fx-font-size: 10px; -fx-text-fill: #5a6a7e;");
        // 1/4 底栏压缩：状态与经验合并同行（左状态 / 右经验），卡由 6 行降至 5 行，行数仍恒定不抖动
        Region expGap = new Region();
        HBox.setHgrow(expGap, Priority.ALWAYS);
        HBox statusRow = new HBox(8, partyInfoStatus, expGap, partyInfoExp);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(line1, line2, statusRow, partyInfoStat1, partyInfoStat2);
        return card;
    }

    /** 联动刷新：把某只精灵完整信息刷到右块详情卡（null 清为占位）。 */
    private void updatePartyInfo(Pokemon p) {
        if (p == null) {
            partyInfoName.setText("—");
            partyInfoType.setText("");
            partyInfoLv.setText("");
            partyInfoHpBar.setProgress(0);
            partyInfoHpText.setText("HP - / -");
            partyInfoStatus.setText("状态：无");
            partyInfoStat1.setText("");
            partyInfoStat2.setText("");
            partyInfoExp.setText("");
            return;
        }
        partyInfoName.setText(p.getName());
        partyInfoType.setText(typeOf(p));
        partyInfoType.setStyle(chipFor(primaryTypeOf(p))); // 属性徽章按主属性分色
        partyInfoLv.setText("Lv." + p.getLevel());
        refreshHp(partyInfoHpBar, partyInfoHpText, p);
        String badge = statusBadgeText(p);
        partyInfoStatus.setText(badge.isEmpty() ? "状态：无" : "状态：" + badge);
        Stats stats = p.getStats();
        partyInfoStat1.setText("攻击 " + stats.getAttack() + "    防御 " + stats.getDefense()
                + "    特攻 " + stats.getSpAttack());
        partyInfoStat2.setText("特防 " + stats.getSpDefense() + "    速度 " + stats.getSpeed()
                + (p.effectiveSpeed() == stats.getSpeed() ? "" : "（实际 " + p.effectiveSpeed() + "）"));
        long need = p.expToNextLevel();
        partyInfoExp.setText(need <= 0 ? "已满级" : "经验 " + p.getExp() + " / 再 " + need + " 升级");
    }


    /**
     * 显示「学会新技能」抉择：精灵已掌握 4 个技能，玩家点选一个当前技能将其遗忘并学习
     * 新技能，或点击「放弃学习」跳过。
     *
     * @param prompt    说明文字（含精灵与新技能名）
     * @param slots     当前已掌握的技能（必为 4 个）
     * @param onForget  选中槽位下标（0~3）时回调
     * @param onDecline 选择放弃学习时回调
     */
    public void showLearnMoveMenu(String prompt, List<MoveSlot> slots,
                                  IntConsumer onForget, Runnable onDecline) {
        actionBox.getChildren().clear();
        Label title = new Label(prompt);
        title.setWrapText(true);
        title.setMaxWidth(190);
        title.setStyle(YH + "-fx-font-size: 12px; -fx-font-weight: bold;");
        VBox list = new VBox(5);
        list.setAlignment(Pos.CENTER);
        for (int i = 0; i < slots.size(); i++) {
            MoveSlot slot = slots.get(i);
            int index = i;
            String pp = slot.exhausted() ? "PP 不足" : "PP " + slot.getPp() + "/" + slot.getMove().getMaxPp();
            Button b = wideButton("遗忘：" + slot.getMove().getName() + "  " + pp, false);
            b.setOnAction(e -> onForget.accept(index));
            list.getChildren().add(b);
        }
        Button decline = wideButton("放弃学习", false);
        decline.setOnAction(e -> onDecline.run());
        VBox v = new VBox(5, title, list, decline);
        v.setAlignment(Pos.CENTER);
        actionBox.getChildren().add(v);
    }

    public void clearActions() {
        actionBox.getChildren().clear();
    }

    /** 战斗结束：清空按钮，只显示结果与返回按钮。 */
    public void showResult(String summary) {
        actionBox.getChildren().clear();
        Label result = new Label(summary);
        result.setWrapText(true);
        result.setMaxWidth(190);
        result.setStyle(YH + "-fx-font-size: 14px; -fx-font-weight: bold;");
        Button back = styledButton("返回主菜单");
        back.setOnAction(e -> actions.onExit());
        VBox v = new VBox(8, result, back);
        v.setAlignment(Pos.CENTER);
        actionBox.getChildren().add(v);
    }

    // ------------------------------------------------------------------
    // 按钮工厂 / 样式
    // ------------------------------------------------------------------

    /** 通用按钮（子菜单使用，185 宽单行）：白底深蓝描边（浅色次要按钮，与马卡龙按钮同构）。 */
    private Button wideButton(String text, boolean disabled) {
        Button b = new Button(text);
        String size = "-fx-font-size: 13px; -fx-pref-width: 185px; -fx-pref-height: 30px;";
        b.setStyle(disabled ? greyToneCss(size)
                : YH + size + "-fx-background-color: " + POKE_INK + ", #ffffff;"
                + "-fx-background-radius: 7, 5; -fx-background-insets: 0, 2;"
                + "-fx-text-fill: #1a2a3a;"
                + "-fx-effect: dropshadow(gaussian, rgba(42, 58, 92, 0.45), 0, 1, 0, 3);");
        b.setDisable(disabled);
        return b;
    }

    private Button styledButton(String text) {
        Button b = new Button(text);
        b.setStyle(toneCss(TONE_YELLOW, "-fx-font-size: 13px; -fx-padding: 5 16;"));
        return b;
    }

    // ------------------------------------------------------------------
    // 战斗动画（演出层）
    // ------------------------------------------------------------------

    /** 双方立绘底座（进场/放出/收回/受击/倒下/投球动画的操作对象），构建场景时赋值。 */
    private StackPane enemySpriteBox;
    private StackPane playerSpriteBox;

    // 单步演出时长（毫秒）：一次行动（我方出招 + 受击 + 敌方还手）合计约 1.4–1.8s，保持回合节奏不拖沓
    private static final int MS_ENTRANCE = 560;
    private static final int MS_SEND_OUT = 420;
    private static final int MS_RECALL = 320;
    private static final int MS_LUNGE = 130;
    private static final int MS_HIT = 300;
    private static final int MS_FAINT = 520;
    private static final int MS_FLY = 300;

    /** 进场时立绘自屏外滑入的水平距离（设计单位，界面宽 640）。 */
    private static final double ENTRANCE_OFFSET = 234;

    /**
     * 演出期间锁定/解锁行动区输入：屏蔽点击（避免动画未播完就触发下一次结算，控制器另有 playing 标志二次防护）。
     * JavaFX 对 {@code :disabled} 的默认半透明效果顺带充当「结算中」的视觉提示。
     */
    public void setInputLocked(boolean locked) {
        actionBox.setDisable(locked);
    }

    // ---- 播放入口 ----

    /**
     * 依次播放一组演出事件对应的动画，全部播完后回调 {@code onFinished}（在主线程）。
     *
     * <p>事件为空时立即回调；单步动画被跳过（无立绘）时该步耗时为零。播放前后各复位一次立绘变换与特效层，
     * 保证上一段演出的残留位移/缩放不会叠加到下一段。</p>
     */
    public void playEvents(List<BattleEvent> events, Runnable onFinished) {
        resetPerformance();
        playAt(events, 0, onFinished);
    }

    private void playAt(List<BattleEvent> events, int index, Runnable onFinished) {
        if (events == null || index >= events.size()) {
            resetPerformance();
            runNow(onFinished);
            return;
        }
        BattleEvent event = events.get(index);
        Runnable next = () -> playAt(events, index + 1, onFinished);
        switch (event.kind()) {
            case BATTLE_START -> playEntrance(next);
            case SEND_OUT -> playSendOut(event.side(), event.actor(), next);
            case RECALL -> playRecall(event.side(), event.actor(), next);
            case MOVE -> playMoveCast(event.side(), event.element(), event.category(), next);
            case HIT -> playHit(event.side(), event.element(), next);
            case FAINT -> playFaint(event.side(), event.actor(), next);
            case CAPTURE -> playCapture(event.success(), next);
            case ITEM -> playItem(next);
            case RUN -> playRun(event.success(), next);
        }
    }

    // ---- 单步动画 ----

    /** 战斗开场（进场动画）：敌立绘自右侧屏外（右上位置）、己方立绘自左侧屏外（左下位置）滑入并淡入。 */
    public void playEntrance(Runnable onFinished) {
        prepare(enemySpriteBox, playerSpriteBox);
        ParallelTransition entrance = new ParallelTransition();
        if (enemySpriteBox != null) {
            entrance.getChildren().add(slideIn(enemySpriteBox, ENTRANCE_OFFSET));
        }
        if (playerSpriteBox != null) {
            entrance.getChildren().add(slideIn(playerSpriteBox, -ENTRANCE_OFFSET));
        }
        if (entrance.getChildren().isEmpty()) {
            runNow(onFinished);
            return;
        }
        entrance.setOnFinished(e -> runNow(onFinished));
        entrance.play();
    }

    /** 自 {@code fromX} 滑到原位并淡入（进场用）。 */
    private static Animation slideIn(Node node, double fromX) {
        node.setOpacity(0);
        node.setTranslateX(fromX);
        TranslateTransition move = new TranslateTransition(Duration.millis(MS_ENTRANCE), node);
        move.setToX(0);
        move.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fade = new FadeTransition(Duration.millis(MS_ENTRANCE * 0.75), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        return new ParallelTransition(move, fade);
    }

    /** 派出精灵（放出动画）：立绘先切到该精灵，再自小球缩放展开 + 淡入，并闪一圈白色光环。 */
    public void playSendOut(BattleEvent.Side side, String name, Runnable onFinished) {
        StackPane box = spriteBoxOf(side);
        applySpriteBySide(side, name); // 事件只带名字：先切图再演，避免放出后仍是上一只
        if (box == null) {
            runNow(onFinished);
            return;
        }
        prepare(box);
        box.setOpacity(0);
        box.setScaleX(0.25);
        box.setScaleY(0.25);
        ScaleTransition grow = new ScaleTransition(Duration.millis(MS_SEND_OUT), box);
        grow.setToX(1);
        grow.setToY(1);
        grow.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fade = new FadeTransition(Duration.millis(MS_SEND_OUT * 0.6), box);
        fade.setFromValue(0);
        fade.setToValue(1);
        ParallelTransition appear = new ParallelTransition(grow, fade);
        appear.setOnFinished(e -> runNow(onFinished));
        appear.play();
        ringFlash(box, Color.WHITE, 0.9, 46, MS_SEND_OUT + 120);
    }

    /** 收回精灵（收回动画）：立绘缩小 + 上浮 + 淡出，并闪一圈淡蓝光环。 */
    public void playRecall(BattleEvent.Side side, String name, Runnable onFinished) {
        StackPane box = spriteBoxOf(side);
        applySpriteBySide(side, name);
        if (box == null) {
            runNow(onFinished);
            return;
        }
        prepare(box);
        ScaleTransition shrink = new ScaleTransition(Duration.millis(MS_RECALL), box);
        shrink.setToX(0.15);
        shrink.setToY(0.15);
        shrink.setInterpolator(Interpolator.EASE_IN);
        TranslateTransition rise = new TranslateTransition(Duration.millis(MS_RECALL), box);
        rise.setByY(-10);
        FadeTransition fade = new FadeTransition(Duration.millis(MS_RECALL), box);
        fade.setToValue(0);
        ParallelTransition leave = new ParallelTransition(shrink, rise, fade);
        leave.setOnFinished(e -> runNow(onFinished));
        leave.play();
        ringFlash(box, Color.web("#cfe0ff"), 0.8, 34, MS_RECALL + 100);
    }

    /**
     * 技能释放（施法动画）：攻击者朝目标小幅前冲再回位，同时弹道自攻击者飞向目标。
     * 弹道配色取招式属性（{@link ElementType#getColorCode()}），形态取招式分类
     * （物理=实心带白环、特殊=发光球、变化=空心脉冲环）。
     */
    public void playMoveCast(BattleEvent.Side side, ElementType element, MoveCategory category,
                             Runnable onFinished) {
        StackPane attacker = spriteBoxOf(side);
        StackPane target = spriteBoxOf(side == BattleEvent.Side.FOE
                ? BattleEvent.Side.PLAYER : BattleEvent.Side.FOE);
        if (attacker == null || target == null) {
            runNow(onFinished);
            return;
        }
        prepare(attacker, target);
        TranslateTransition forward = new TranslateTransition(Duration.millis(MS_LUNGE), attacker);
        forward.setByX(side == BattleEvent.Side.FOE ? -11 : 11); // 双方面向对方前冲（敌在右上向左、我在左下向右）
        forward.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition retreat = new TranslateTransition(Duration.millis(MS_LUNGE * 1.4), attacker);
        retreat.setByX(side == BattleEvent.Side.FOE ? 11 : -11);
        retreat.setInterpolator(Interpolator.EASE_IN);
        SequentialTransition lunge = new SequentialTransition(forward, retreat);
        ParallelTransition cast = new ParallelTransition(lunge, projectile(attacker, target, element, category));
        cast.setOnFinished(e -> runNow(onFinished));
        cast.play();
    }

    /** 从 {@code from} 中心飞向 {@code to} 中心并炸开的弹道特效。 */
    private Animation projectile(Node from, Node to, ElementType element, MoveCategory category) {
        Color color = element == null ? Color.web("#A8A878") : Color.web(element.getColorCode());
        Circle core = new Circle(6.5);
        if (category == MoveCategory.STATUS) {
            core.setFill(Color.TRANSPARENT);
            core.setStroke(color);
            core.setStrokeWidth(3);
        } else {
            core.setFill(color);
            core.setStroke(Color.web("#ffffff", 0.85));
            core.setStrokeWidth(category == MoveCategory.SPECIAL ? 0 : 2);
        }
        Group shot = new Group(core);
        if (category == MoveCategory.SPECIAL) {
            shot.getChildren().add(0, new Circle(12, color.deriveColor(0, 1, 1, 0.30)));
        }
        Point2D start = centerOf(from);
        Point2D end = centerOf(to);
        shot.setLayoutX(start.getX());
        shot.setLayoutY(start.getY());
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double arc = Math.min(96, 40 + Math.abs(dx) * 0.25); // 抛物线抬升量，纯视觉
        Timeline fly = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(shot.translateXProperty(), 0.0),
                        new KeyValue(shot.translateYProperty(), 0.0)),
                new KeyFrame(Duration.millis(MS_FLY * 0.6),
                        new KeyValue(shot.translateXProperty(), dx * 0.62),
                        new KeyValue(shot.translateYProperty(), dy * 0.45 - arc)),
                new KeyFrame(Duration.millis(MS_FLY),
                        new KeyValue(shot.translateXProperty(), dx),
                        new KeyValue(shot.translateYProperty(), dy)));
        ScaleTransition burst = new ScaleTransition(Duration.millis(190), shot);
        burst.setToX(2.3);
        burst.setToY(2.3);
        FadeTransition gone = new FadeTransition(Duration.millis(190), shot);
        gone.setToValue(0);
        SequentialTransition full = new SequentialTransition(fly, new ParallelTransition(burst, gone));
        effectLayer.getChildren().add(shot);
        full.setOnFinished(e -> effectLayer.getChildren().remove(shot));
        return full;
    }

    /** 受击（命中动画）：被打一方左右急抖，并叠一层招式属性色闪光。 */
    public void playHit(BattleEvent.Side side, ElementType element, Runnable onFinished) {
        StackPane box = spriteBoxOf(side);
        if (box == null) {
            runNow(onFinished);
            return;
        }
        prepare(box);
        Color color = element == null ? Color.WHITE : Color.web(element.getColorCode());
        Rectangle flash = overlayFor(box, color.deriveColor(0, 1, 1, 0.45));
        effectLayer.getChildren().add(flash);
        FadeTransition flashOut = new FadeTransition(Duration.millis(MS_HIT), flash);
        flashOut.setFromValue(0.95);
        flashOut.setToValue(0);
        flashOut.setOnFinished(e -> effectLayer.getChildren().remove(flash));
        flashOut.play();

        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(box.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(55), new KeyValue(box.translateXProperty(), -5)),
                new KeyFrame(Duration.millis(110), new KeyValue(box.translateXProperty(), 5)),
                new KeyFrame(Duration.millis(165), new KeyValue(box.translateXProperty(), -3)),
                new KeyFrame(Duration.millis(220), new KeyValue(box.translateXProperty(), 3)),
                new KeyFrame(Duration.millis(MS_HIT), new KeyValue(box.translateXProperty(), 0)));
        shake.setOnFinished(e -> runNow(onFinished));
        shake.play();
    }

    /**
     * 倒下（倒地动画）：立绘先切回倒下的那只，再下沉 + 淡出。
     * 复位交给序列结束时的 {@link #resetPerformance()}；若紧随 SEND_OUT，则由放出动画重新显示新精灵。
     */
    public void playFaint(BattleEvent.Side side, String name, Runnable onFinished) {
        StackPane box = spriteBoxOf(side);
        applySpriteBySide(side, name);
        if (box == null) {
            runNow(onFinished);
            return;
        }
        prepare(box);
        TranslateTransition down = new TranslateTransition(Duration.millis(MS_FAINT), box);
        down.setToY(14);
        down.setInterpolator(Interpolator.EASE_IN);
        FadeTransition fade = new FadeTransition(Duration.millis(MS_FAINT), box);
        fade.setToValue(0);
        ParallelTransition fall = new ParallelTransition(down, fade);
        fall.setOnFinished(e -> runNow(onFinished));
        fall.play();
    }

    /**
     * 投球（捕捉动画）：球自玩家立绘处抛物线飞向野生精灵；成功则原地摆动三次并把精灵收入球中，
     * 失败则球炸开、精灵抖一下。球体为纯几何绘制（不依赖图片资源）。
     */
    public void playCapture(boolean success, Runnable onFinished) {
        StackPane from = playerSpriteBox;
        StackPane to = enemySpriteBox;
        if (from == null || to == null) {
            runNow(onFinished);
            return;
        }
        prepare(from, to);
        Group ball = createBall();
        Point2D start = centerOf(from);
        Point2D end = centerOf(to);
        ball.setLayoutX(start.getX());
        ball.setLayoutY(start.getY());
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        Timeline fly = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(ball.translateXProperty(), 0.0),
                        new KeyValue(ball.translateYProperty(), 0.0),
                        new KeyValue(ball.rotateProperty(), 0.0)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(ball.translateXProperty(), dx * 0.55),
                        new KeyValue(ball.translateYProperty(), dy * 0.4 - 90)),
                new KeyFrame(Duration.millis(430),
                        new KeyValue(ball.translateXProperty(), dx),
                        new KeyValue(ball.translateYProperty(), dy)));

        SequentialTransition full;
        if (success) {
            Timeline wobble = new Timeline(
                    new KeyFrame(Duration.millis(120), new KeyValue(ball.rotateProperty(), -24)),
                    new KeyFrame(Duration.millis(300), new KeyValue(ball.rotateProperty(), 24)),
                    new KeyFrame(Duration.millis(480), new KeyValue(ball.rotateProperty(), -24)),
                    new KeyFrame(Duration.millis(660), new KeyValue(ball.rotateProperty(), 0)));
            ScaleTransition suck = new ScaleTransition(Duration.millis(360), to);
            suck.setToX(0.05);
            suck.setToY(0.05);
            FadeTransition vanish = new FadeTransition(Duration.millis(360), to);
            vanish.setToValue(0);
            full = new SequentialTransition(fly, wobble, new ParallelTransition(suck, vanish));
        } else {
            ScaleTransition burst = new ScaleTransition(Duration.millis(200), ball);
            burst.setToX(2.4);
            burst.setToY(2.4);
            FadeTransition gone = new FadeTransition(Duration.millis(200), ball);
            gone.setToValue(0);
            Timeline shake = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(to.translateXProperty(), 0)),
                    new KeyFrame(Duration.millis(70), new KeyValue(to.translateXProperty(), -5)),
                    new KeyFrame(Duration.millis(140), new KeyValue(to.translateXProperty(), 5)),
                    new KeyFrame(Duration.millis(210), new KeyValue(to.translateXProperty(), 0)));
            full = new SequentialTransition(fly, new ParallelTransition(burst, gone), shake);
        }
        effectLayer.getChildren().add(ball);
        full.setOnFinished(e -> {
            effectLayer.getChildren().remove(ball);
            runNow(onFinished);
        });
        full.play();
    }

    /** 精灵球图形：白底 + 红色上半球 + 中央按钮（上半球用 {@link ArcType#ROUND} 的 180° 圆弧拼出）。 */
    private static Group createBall() {
        Circle body = new Circle(8, Color.web("#f7f7f7"));
        body.setStroke(Color.web("#333333"));
        body.setStrokeWidth(1.4);
        Arc top = new Arc(0, 0, 8, 8, 0, 180);
        top.setType(ArcType.ROUND);
        top.setFill(Color.web("#e2504c"));
        top.setStroke(Color.web("#333333"));
        top.setStrokeWidth(1.4);
        Circle button = new Circle(2.6, Color.web("#fdfdfd"));
        button.setStroke(Color.web("#333333"));
        button.setStrokeWidth(1.2);
        return new Group(body, top, button);
    }

    /** 使用回复/解除类道具（回复动画）：玩家立绘上方依次浮起三颗绿色光点。 */
    public void playItem(Runnable onFinished) {
        StackPane box = playerSpriteBox;
        if (box == null) {
            runNow(onFinished);
            return;
        }
        prepare(box);
        Point2D center = centerOf(box);
        ParallelTransition sparks = new ParallelTransition();
        for (int i = 0; i < 3; i++) {
            Circle dot = new Circle(3.6, Color.web("#5fce62"));
            dot.setLayoutX(center.getX() + (i - 1) * 14);
            dot.setLayoutY(center.getY() + 6);
            effectLayer.getChildren().add(dot);
            Duration delay = Duration.millis(i * 80.0);
            TranslateTransition rise = new TranslateTransition(Duration.millis(520), dot);
            rise.setByY(-34);
            rise.setDelay(delay);
            FadeTransition fade = new FadeTransition(Duration.millis(520), dot);
            fade.setToValue(0);
            fade.setDelay(delay);
            ParallelTransition one = new ParallelTransition(rise, fade);
            one.setOnFinished(e -> effectLayer.getChildren().remove(dot));
            sparks.getChildren().add(one);
        }
        sparks.setOnFinished(e -> runNow(onFinished));
        sparks.play();
    }

    /** 逃跑动画：成功则玩家立绘向右滑出并淡出；失败则原地左右抖动。 */
    public void playRun(boolean success, Runnable onFinished) {
        StackPane box = playerSpriteBox;
        if (box == null) {
            runNow(onFinished);
            return;
        }
        prepare(box);
        if (success) {
            TranslateTransition out = new TranslateTransition(Duration.millis(460), box);
            out.setByX(230);
            out.setInterpolator(Interpolator.EASE_IN);
            FadeTransition fade = new FadeTransition(Duration.millis(460), box);
            fade.setToValue(0);
            ParallelTransition flee = new ParallelTransition(out, fade);
            flee.setOnFinished(e -> runNow(onFinished));
            flee.play();
            return;
        }
        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(box.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(70), new KeyValue(box.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(140), new KeyValue(box.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(210), new KeyValue(box.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(280), new KeyValue(box.translateXProperty(), 0)));
        shake.setOnFinished(e -> runNow(onFinished));
        shake.play();
    }

    // ---- 演出辅助 ----

    /** 复位双方立绘的位移/缩放/透明度并清空特效层（每段事件序列开始前与结束后各调用一次）。 */
    private void resetPerformance() {
        prepare(playerSpriteBox, enemySpriteBox);
        effectLayer.getChildren().clear();
    }

    /** 把立绘复位到「无变换」状态：清掉上一段演出残留的位移/缩放/旋转/透明度。 */
    private static void prepare(Node... nodes) {
        for (Node node : nodes) {
            if (node == null) {
                continue;
            }
            node.setTranslateX(0);
            node.setTranslateY(0);
            node.setScaleX(1);
            node.setScaleY(1);
            node.setRotate(0);
            node.setOpacity(1);
        }
    }

    /** 某阵营对应的立绘底座（事件阵营 → 界面方位）。 */
    private StackPane spriteBoxOf(BattleEvent.Side side) {
        return side == BattleEvent.Side.PLAYER ? playerSpriteBox : enemySpriteBox;
    }

    /** 按阵营把精灵名对应的立绘刷到该侧（放出/收回/倒下动画只拿到名字）。 */
    private void applySpriteBySide(BattleEvent.Side side, String name) {
        if (side == BattleEvent.Side.PLAYER) {
            applySpriteByName(playerSprite, playerSpriteFallback, name);
        } else if (side == BattleEvent.Side.FOE) {
            applySpriteByName(enemySprite, enemySpriteFallback, name);
        }
    }

    /** 节点中心在演出层坐标系中的位置（自动适配 {@link UiScale} 的全局缩放与各级内边距）。 */
    private Point2D centerOf(Node node) {
        Bounds b = node.getBoundsInLocal();
        return effectLayer.sceneToLocal(
                node.localToScene(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2));
    }

    /** 与节点同尺寸、贴合场景坐标的圆角覆盖矩形（受击闪光层）。 */
    private Rectangle overlayFor(Node node, Color color) {
        Bounds b = node.getBoundsInLocal();
        Point2D topLeft = effectLayer.sceneToLocal(node.localToScene(b.getMinX(), b.getMinY()));
        Point2D bottomRight = effectLayer.sceneToLocal(node.localToScene(b.getMaxX(), b.getMaxY()));
        Rectangle r = new Rectangle(topLeft.getX(), topLeft.getY(),
                bottomRight.getX() - topLeft.getX(), bottomRight.getY() - topLeft.getY());
        r.setArcWidth(20);
        r.setArcHeight(20);
        r.setFill(color);
        return r;
    }

    /**
     * 在节点中心闪一圈扩散光环（放出/收回演出用）。
     * 纯几何特效：不使用文本描边，也不改变立绘外布局，避免触到动态中文渲染性能红线。
     */
    private void ringFlash(Node node, Color color, double opacity, double radius, int ms) {
        Point2D center = centerOf(node);
        double base = Math.max(8, radius * 0.3);
        Circle ring = new Circle(base, color.deriveColor(0, 1, 1, opacity * 0.35));
        ring.setStroke(color.deriveColor(0, 1, 1, opacity));
        ring.setStrokeWidth(3);
        ring.setLayoutX(center.getX());
        ring.setLayoutY(center.getY());
        effectLayer.getChildren().add(ring);
        ScaleTransition expand = new ScaleTransition(Duration.millis(ms), ring);
        expand.setToX(radius / base);
        expand.setToY(radius / base);
        expand.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fade = new FadeTransition(Duration.millis(ms), ring);
        fade.setToValue(0);
        ParallelTransition flash = new ParallelTransition(expand, fade);
        flash.setOnFinished(e -> effectLayer.getChildren().remove(ring));
        flash.play();
    }

    private static void runNow(Runnable onFinished) {
        if (onFinished != null) {
            onFinished.run();
        }
    }

    private static String typeOf(Pokemon p) {
        List<String> types = p.getSpecies().getTypes().stream()
                .map(t -> t.getDisplayName()).toList();
        return String.join(" / ", types);
    }

    private static void refreshHp(ProgressBar bar, Label text, Pokemon p) {
        double ratio = p.getMaxHp() <= 0 ? 0 : (double) p.getCurrentHp() / p.getMaxHp();
        bar.setProgress(Math.max(0, ratio));
        text.setText("HP " + p.getCurrentHp() + " / " + p.getMaxHp());
        String color;
        // react HP 条：>50% 绿、>20% 黄、其余红，均为渐变填充（棕底 track 见 /css/battle.css）
        if (ratio > 0.5) {
            color = "linear-gradient(to right, #48c840, #78f060)";
        } else if (ratio > 0.2) {
            color = "linear-gradient(to right, #f8b800, #f8d840)";
        } else {
            color = "linear-gradient(to right, #f83800, #f86830)";
        }
        bar.setStyle("-fx-accent: " + color + ";");
    }

}
