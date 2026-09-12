package org.example.view;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.GameSession;
import org.example.config.AppConfig;
import org.example.model.Option;
import org.example.model.OptionType;
import org.example.model.RouteConfig;
import org.example.model.RoutePhase;
import org.example.model.RunData;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 路线节点页：显示当前段、行动点与金币，并按《需求文档》§4.2 列出本段节点供玩家消耗行动点进入。
 *
 * <p>呈现方式（2026-09-12 按设计稿 EncounterPage / EventCard 改版：仅样式与布局调整，行为不变）：</p>
 * <ul>
 *   <li>头部一行 —— 左「◀ 返回主菜单」胶囊、右段位信息卡（段号 / 阶段 / 行动点 / 金币，均沿用
 *       「初始主界面」（启动页）胶囊族样式的小号版本，见 start-menu.css 的 .rogue-back / .rogue-info）
 *       分贴行内两侧；「事件遭遇 ENCOUNTER EVENT」横幅（启动页胶囊族同款：黄→金渐变芯 + 深蓝描边环，
 *       无黑框、深蓝字，+ 副提示胶囊）贴页面最上方、水平居中于画布；</li>
 *   <li>节点卡 —— 最多 3 列 × 2 行网格：上排固定三位常驻节点（野生宝可梦 → 路人训练师 → 医院，
 *       从左到右位置恒定），随机事件保持生成顺序排在下排；只渲染已刷新出来的事件、不做占位补格
 *       （真实节点至多 {@link RouteConfig#MAX_ROUTE_NODES} 个）：顶部图片预留区（渐变 + 类型 emoji + 精灵球装饰），
 *       底部「类型 · 名称」与金色行动点徽章；悬停上浮并在卡片上方弹出深蓝金框描述弹窗，
 *       点击进入节点（点击行为与旧版按钮一致）；</li>
 *   <li>已走过的一次性节点 —— 灰底虚线只读卡（由 {@link Option#isConsumed()} 标记，取代旧版
 *       「名字等于『隐藏事件』」的哨兵写法）；</li>
 *   <li>已走过的常驻节点（路人 / 野外精灵 / 医院）—— 仍是可点击卡片，卡面标注「已走过 N 次」，
 *       再次进入的行动点消耗沿用 {@link Option#apCostForNextEntry()}，仅受行动点限制；</li>
 *   <li>行动点耗尽 / 无节点可走 —— 提示必然节点（道馆战）即将展开，并给出「挑战道馆」按钮。</li>
 * </ul>
 */
public class RogueFloorView {

    /** 设计稿字体（正文中文统一微软雅黑）。 */
    private static final String FONT = "-fx-font-family: 'Microsoft YaHei';";

    /** 启动页共用样式表（顶栏返回胶囊与信息卡沿用「初始主界面」胶囊族视觉）。 */
    private static final String STYLE_SHEET = "/css/start-menu.css";

    /** 事件卡尺寸（设计画布 640×426.67 内按 3 列 × 2 行铺排）。 */
    private static final double CARD_W = 198;
    private static final double CARD_H = 120;
    /** 卡面上部「图片预留区」高度（设计稿 66%）。 */
    private static final double CARD_TOP_H = 79;
    /** 卡面下部「标题 + 行动点徽章」高度（设计稿 34%）。 */
    private static final double CARD_BOTTOM_H = 41;
    /** 网格：最多 3 列 × 2 行（只渲染已刷新事件，不补占位格）。 */
    private static final int GRID_COLS = 3;
    private static final double GRID_GAP = 10;
    /** 探索态网格垂直居中校正：修正底部预告条与头部行高的差额，使网格中心对准画布垂直中心。 */
    private static final double GRID_BOTTOM_RESERVE = 37;

    /** 卡片入场动效（与启动页胶囊按钮入场动画同参数：错峰上浮淡入；错峰顺序 = 从左到右、从上到下）。 */
    private static final double ENTRANCE_RISE = 11;
    private static final double ENTRANCE_FADE_MS = 620;
    private static final double ENTRANCE_DELAY_BASE_MS = 120;
    private static final double ENTRANCE_DELAY_STEP_MS = 95;

    /** 描述弹窗尺寸（设计稿 260px 等比适配到 640 画布）。 */
    private static final double POPUP_W = 208;
    private static final double POPUP_ARROW_W = 10;
    private static final double POPUP_ARROW_H = 6;

    /** 卡片投影：常态与悬停上浮（设计稿 0 4px 16px → 0 8px 24px）。 */
    private static final String CARD_SHADOW =
            "-fx-effect: dropshadow(gaussian, rgba(21,101,192,0.18), 12, 0.0, 0, 4);";
    private static final String CARD_SHADOW_HOVER =
            "-fx-effect: dropshadow(gaussian, rgba(21,101,192,0.30), 16, 0.0, 0, 7);";
    /** 已走过卡（灰底 + 虚线框，设计稿 isEmpty 样式；整卡与顶区分层用 Region 绘制）。 */
    private static final String IDLE_CARD_STYLE =
            "-fx-background-color: rgba(200,210,230,0.55); -fx-background-radius: 12;"
                    + " -fx-border-color: #90aac8; -fx-border-width: 2; -fx-border-radius: 12;"
                    + " -fx-border-style: dashed;";
    private static final String IDLE_TOP_STYLE =
            "-fx-background-color: rgba(180,200,225,0.3); -fx-background-radius: 10 10 0 0;"
                    + " -fx-border-color: #90aac8; -fx-border-width: 0 0 1.5 0;"
                    + " -fx-border-style: none none dashed none;";

    private final GameSession session;
    private final Consumer<Option> onOptionSelected;
    private final Runnable onBack;

    /** 本次场景构建出的网格卡片，按展示顺序（从左到右、从上到下）收集，供入场动画错峰播放。 */
    private final List<Node> entranceCards = new ArrayList<>();

    /** 入场动画进行中（期间忽略悬停上浮位移，避免与入场位移争抢 translateY）。 */
    private boolean entrancePlaying = true;

    public RogueFloorView(GameSession session, Consumer<Option> onOptionSelected, Runnable onBack) {
        this.session = session;
        this.onOptionSelected = onOptionSelected;
        this.onBack = onBack;
    }

    public Scene createScene() {
        entranceCards.clear();

        StackPane root = new StackPane();
        root.setAlignment(Pos.TOP_LEFT);
        ImageBackgrounds.apply(root, session.mapBackgroundPath()); // 与主菜单同款地图背景

        RunData data = session.getRogueRunData();

        // 弹窗层：与内容同属根 StackPane（在滚动容器 / 卡片裁剪之外），仅展示、不吃鼠标事件
        Pane overlay = new Pane();
        overlay.setMouseTransparent(true);

        VBox content = new VBox(6);
        content.setPadding(new Insets(10, 12, 8, 12));
        content.setAlignment(Pos.TOP_CENTER);

        Region body = buildBody(data, overlay);
        VBox.setVgrow(body, Priority.ALWAYS);
        content.getChildren().addAll(buildHeader(data), body);

        root.getChildren().addAll(buildWatermark(), content, overlay);

        Scene scene = UiScale.scene(root);
        var css = RogueFloorView.class.getResource(STYLE_SHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        playEntrance(); // 卡片按从左到右、从上到下的顺序错峰上浮淡入（与启动页入场动效同参数）
        return scene;
    }

    // ------------------------------------------------------------------
    // 页面骨架：头部一行 / 正文分支
    // ------------------------------------------------------------------

    /**
     * 头部一行：左返回胶囊、右段位信息面板贴行内两侧；「事件遭遇」横幅贴页面最上方、水平居中于画布。
     * 横幅与两侧控件同放一个 StackPane —— 横幅居中位置取画布中点，不受左右两侧宽度差影响。
     */
    private StackPane buildHeader(RunData data) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox sides = new HBox(8, buildBackButton(), spacer, buildInfoPanel(data));
        sides.setAlignment(Pos.TOP_LEFT);
        StackPane header = new StackPane(buildBanner(), sides); // sides 在上层：保证两侧控件可点击
        header.setAlignment(Pos.TOP_CENTER);
        return header;
    }

    /** 段位信息面板：段号 · 阶段 + 行动点 / 金币（缩小版，样式与启动页胶囊族统一）。 */
    private VBox buildInfoPanel(RunData data) {
        Label segment = new Label("第 " + session.getSegment() + " / " + RouteConfig.TOTAL_SEGMENTS
                + " 段 · " + data.getPhase().getDisplayName());
        segment.setStyle(FONT + " -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #123c63;");

        Label apIcon = new Label("⚡");
        apIcon.setStyle("-fx-font-size: 8px;");
        Label ap = new Label("行动点: " + data.getAp() + "/" + data.getApMax());
        ap.setStyle(FONT + " -fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #123c63;");
        Label goldIcon = new Label("🪙");
        goldIcon.setStyle("-fx-font-size: 8px;");
        HBox.setMargin(goldIcon, new Insets(0, 0, 0, 7));
        Label gold = new Label("金币: " + data.getGold());
        gold.setStyle(FONT + " -fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #123c63;");

        HBox stats = new HBox(3, apIcon, ap, goldIcon, gold);
        stats.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(2, segment, stats);
        panel.setMinWidth(116);
        // 头部行高由「事件遭遇」横幅决定，信息卡保持自身内容高度：
        // 不封顶时会被 HBox 拉到整行高，底部多出一块空白
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        panel.getStyleClass().add("rogue-info");
        return panel;
    }

    /** 标题横幅：启动页胶囊族同款（黄金渐变芯 + 深蓝描边环，深蓝字）+ 副提示胶囊。 */
    private VBox buildBanner() {
        Label left = new Label("⚔");
        left.setStyle("-fx-font-size: 14px; -fx-text-fill: #123c63;");
        Label right = new Label("⚔");
        right.setStyle("-fx-font-size: 14px; -fx-text-fill: #123c63;");

        Label title = new Label("事件遭遇");
        title.setStyle(FONT + " -fx-font-size: 15px; -fx-font-weight: 900; -fx-text-fill: #123c63;");
        Label english = new Label("ENCOUNTER EVENT");
        english.setStyle("-fx-font-size: 6.5px; -fx-font-weight: bold;"
                + " -fx-text-fill: rgba(42,117,187,0.85);");
        VBox titleCol = new VBox(0, title, english);
        titleCol.setAlignment(Pos.CENTER);

        HBox band = new HBox(8, left, titleCol, right);
        band.setAlignment(Pos.CENTER);
        band.setMaxWidth(Region.USE_PREF_SIZE); // 紧凑胶囊条（设计稿宽度随内容）
        band.getStyleClass().add("rogue-banner");

        Label tip = new Label("选择你的下一步行动吧，训练家！");
        tip.setStyle(FONT + " -fx-font-size: 8.5px; -fx-font-weight: bold;"
                + " -fx-text-fill: rgba(10,30,80,0.7); -fx-background-color: rgba(255,255,255,0.6);"
                + " -fx-background-radius: 20; -fx-padding: 2 12;");

        VBox box = new VBox(3, band, tip);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** 正文分支：结束 / 必然节点 / 无节点可走 / 正常节点网格（分支条件与旧版一致）。 */
    private Region buildBody(RunData data, Pane overlay) {
        if (session.isRogueRunFinished()) {
            return centered(buildResultBox());
        }
        if (data.getPhase().isMandatoryBattle()) {
            return centered(buildMandatoryBox(data));
        }
        if (!data.hasSelectableOption()) {
            return centered(buildNoOptionBox(data));
        }
        return buildExplorationBody(data, overlay);
    }

    /** 正常路线探索：3×2 节点卡网格（整体居画布正中）+ 底部必然节点预告条。 */
    private VBox buildExplorationBody(RunData data, Pane overlay) {
        VBox gridHolder = new VBox(buildGrid(data, overlay));
        gridHolder.setAlignment(Pos.CENTER);
        gridHolder.setPadding(new Insets(0, 0, GRID_BOTTOM_RESERVE, 0)); // 居中校正：抵消头部长、底部预告条短的差额
        VBox.setVgrow(gridHolder, Priority.ALWAYS);

        VBox body = new VBox(6, gridHolder, buildMandatoryPreview(data));
        body.setAlignment(Pos.TOP_CENTER);
        return body;
    }

    /** 居中承载卡片（必然节点 / 无节点可走 / 结束态）。 */
    private VBox centered(Node card) {
        VBox holder = new VBox(card);
        holder.setAlignment(Pos.CENTER);
        return holder;
    }

    // ------------------------------------------------------------------
    // 节点卡片网格
    // ------------------------------------------------------------------

    /** 最多 3 列 × 2 行网格：固定位（野生宝可梦 → 路人训练师 → 医院）从左到右占上排，
     *  随机事件保持生成顺序排下排；只渲染已刷新出来的事件，不做占位补格。 */
    private GridPane buildGrid(RunData data, Pane overlay) {
        GridPane grid = new GridPane();
        grid.setHgap(GRID_GAP);
        grid.setVgap(GRID_GAP);
        grid.setAlignment(Pos.CENTER);

        int index = 0;
        for (Option option : orderedOptions(data)) {
            Node card = buildOptionCard(option, data, overlay);
            grid.add(card, index % GRID_COLS, index / GRID_COLS);
            entranceCards.add(card); // 序号即展示顺序：从左到右、从上到下
            index++;
        }
        return grid;
    }

    /** 网格卡片入场：与启动页胶囊按钮同一组动效参数（错峰上浮淡入）；
     *  不可进入卡的降透明度作为各自淡入终点，不被动画覆盖成 1。 */
    private void playEntrance() {
        if (entranceCards.isEmpty()) {
            return;
        }
        Interpolator spline = Interpolator.SPLINE(0.16, 1, 0.3, 1);
        entrancePlaying = true;
        for (int i = 0; i < entranceCards.size(); i++) {
            Node card = entranceCards.get(i);
            double target = card.getOpacity(); // 入场前原态透明度（行动点不足卡为 0.62）
            card.setOpacity(0);
            card.setTranslateY(ENTRANCE_RISE);

            FadeTransition fade = new FadeTransition(Duration.millis(ENTRANCE_FADE_MS), card);
            fade.setFromValue(0);
            fade.setToValue(target);
            fade.setInterpolator(spline);

            TranslateTransition rise = new TranslateTransition(Duration.millis(ENTRANCE_FADE_MS), card);
            rise.setFromY(ENTRANCE_RISE);
            rise.setToY(0);
            rise.setInterpolator(spline);

            SequentialTransition sequence = new SequentialTransition(
                    new PauseTransition(Duration.millis(ENTRANCE_DELAY_BASE_MS + i * ENTRANCE_DELAY_STEP_MS)),
                    new ParallelTransition(fade, rise));
            if (i == entranceCards.size() - 1) {
                sequence.setOnFinished(e -> entrancePlaying = false); // 最后一张入座后放行悬停位移
            }
            sequence.play();
        }
    }

    /** 展示顺序：三个常驻节点固定位（野生宝可梦 → 路人训练师 → 医院，从左到右），
     *  其余随机事件保持生成顺序排在其后。{@link List#sort} 是稳定排序，仅调整展示，
     *  不修改 {@link RunData} 里的原始列表。 */
    private static List<Option> orderedOptions(RunData data) {
        List<Option> options = new ArrayList<>();
        for (Option option : data.getAvailableOptions()) {
            if (option != null) {
                options.add(option);
            }
        }
        options.sort(Comparator.comparingInt(RogueFloorView::fixedSlot));
        return options;
    }

    /** 固定位序号：野生宝可梦 → 路人训练师 → 医院（0 / 1 / 2）；其余随机事件统一排后（3）。 */
    private static int fixedSlot(Option option) {
        OptionType type = option.getType();
        if (type == OptionType.WILD) {
            return 0;
        }
        if (type == OptionType.TRAINER) {
            return 1;
        }
        if (type == OptionType.HOSPITAL) {
            return 2;
        }
        return 3;
    }

    /**
     * 单个节点卡：可进入时是可点击卡片（悬停上浮 + 描述弹窗），
     * 已走过的一次性节点是灰底虚线只读卡；已走过的常驻节点仍可点击（可重复进入，仅受行动点限制）。
     */
    private Node buildOptionCard(Option option, RunData data, Pane overlay) {
        if (option.isConsumed() && !option.isRepeatable()) {
            return buildUsedCard(option);
        }

        int cost = option.apCostForNextEntry();
        boolean affordable = cost <= data.getAp();

        // ── 卡面内容：上部图片预留区（类型 emoji + 精灵球装饰；渐变底由卡片背景层绘制）──
        StackPane top = new StackPane();
        fixedSize(top, CARD_W, CARD_TOP_H);

        Label icon = new Label(emojiFor(option));
        icon.setStyle("-fx-font-size: 24px;");
        Label areaTag = new Label("事件图片预留区");
        areaTag.setStyle(FONT + " -fx-font-size: 7.5px; -fx-font-weight: bold; -fx-text-fill: #1565C0;"
                + " -fx-background-color: rgba(255,255,255,0.7); -fx-background-radius: 20;"
                + " -fx-border-color: #90CAF9; -fx-border-width: 1; -fx-border-radius: 20;"
                + " -fx-padding: 1 7;");
        VBox centerCol = new VBox(3, icon, areaTag);
        centerCol.setAlignment(Pos.CENTER);
        top.getChildren().add(centerCol);

        Region ball = buildPokeballDecoration(13, 0.6);
        StackPane.setAlignment(ball, Pos.TOP_RIGHT);
        StackPane.setMargin(ball, new Insets(4, 5, 0, 0));
        top.getChildren().add(ball);

        if (option.isConsumed()) { // 常驻节点走过后仍可再次进入：卡面标出次数（详情见弹窗）
            Label visits = new Label("已走过 " + option.getVisitCount() + " 次");
            visits.setStyle(FONT + " -fx-font-size: 7px; -fx-font-weight: bold; -fx-text-fill: #8a6000;"
                    + " -fx-background-color: rgba(255,255,255,0.88); -fx-background-radius: 20;"
                    + " -fx-border-color: #E6A800; -fx-border-width: 1; -fx-border-radius: 20;"
                    + " -fx-padding: 1 6;");
            StackPane.setAlignment(visits, Pos.TOP_LEFT);
            StackPane.setMargin(visits, new Insets(4, 0, 0, 5));
            top.getChildren().add(visits);
        }

        // ── 卡面内容：下部「类型 · 名称」+ 金色行动点徽章 + 进入提示（白底由卡片背景层绘制）──
        VBox bottom = new VBox(2);
        fixedSize(bottom, CARD_W, CARD_BOTTOM_H);
        bottom.setPadding(new Insets(3, 8, 3, 8));
        bottom.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(option.getTypeDisplayName() + " · " + option.getName());
        name.setStyle(FONT + " -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #1A1A1A;");
        name.setMaxWidth(CARD_W - 18);

        Label costBadge = new Label(cost == 0 ? "⚡ 不消耗行动点" : "⚡ 消耗 " + cost + " 行动点");
        costBadge.setStyle(FONT + " -fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #1A1A1A;"
                + " -fx-background-color: linear-gradient(to bottom right, #FFD800, #FFA000);"
                + " -fx-background-radius: 20; -fx-border-color: #E6A800; -fx-border-width: 1;"
                + " -fx-border-radius: 20; -fx-padding: 1 7;");
        Region lineSpacer = new Region();
        HBox.setHgrow(lineSpacer, Priority.ALWAYS);
        Label hint = new Label(affordable ? "点击进入 ▸" : "行动点不足");
        hint.setStyle(FONT + " -fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: "
                + (affordable ? "#1565C0;" : "#C62828;"));
        HBox meta = new HBox(4, costBadge, lineSpacer, hint);
        meta.setAlignment(Pos.CENTER_LEFT);
        bottom.getChildren().addAll(name, meta);

        VBox content = new VBox(top, bottom);
        fixedSize(content, CARD_W, CARD_H);
        content.setMouseTransparent(true); // 内容只是装饰与文字，点击统一交给下面的按钮

        // 卡片底色：单节点多层背景（白底 / 顶区分隔线 / 顶区渐变，无外边框）+ 投影。
        // 底色元素全部绘制在同一个无子节点的 Region 上——单节点绘制保证各层圆角与
        // 分隔线位置精确可控，不受子节点布局影响。
        // 背景层列表第一个在最底层：白底 → 分隔线（top 高 79 的下沿 2px）→ 顶区渐变。
        Region bg = new Region();
        fixedSize(bg, CARD_W, CARD_H);
        final String layers = "-fx-background-color: rgba(255,255,255,0.90), #1565C0,"
                + " linear-gradient(to bottom right, #dbeafe 0%, #bfdbfe 55%, #93c5fd 100%);"
                + " -fx-background-insets: 0, " + (int) (CARD_TOP_H - 2) + " 0 " + (int) CARD_BOTTOM_H + " 0,"
                + " 0 0 " + (int) (CARD_BOTTOM_H + 2) + " 0;"
                + " -fx-background-radius: 12, 0, 12 12 0 0;";
        final String baseStyle = layers + CARD_SHADOW;
        final String hoverStyle = layers.replace("rgba(255,255,255,0.90)", "rgba(255,255,255,0.96)")
                + CARD_SHADOW_HOVER;
        bg.setStyle(baseStyle);

        StackPane face = new StackPane(bg, content);
        fixedSize(face, CARD_W, CARD_H);

        Button card = new Button();
        card.setGraphic(face);
        card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.setDisable(!affordable);
        card.setStyle("-fx-background-color: transparent; -fx-background-insets: 0;"
                + " -fx-background-radius: 0; -fx-padding: 0; -fx-opacity: 1;"
                + " -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        card.setOnAction(e -> onOptionSelected.accept(option));

        StackPane wrapper = new StackPane(card);
        wrapper.getStyleClass().add("event-card"); // 供截图 / 冒烟测试定位（无样式表依赖）
        fixedSize(wrapper, CARD_W, CARD_H);
        wrapper.setCursor(affordable ? Cursor.HAND : Cursor.DEFAULT);
        // 行动点不足：沿用「不可进入」行为（setDisable），整卡降透明度，但保留悬停查看描述
        if (!affordable) {
            wrapper.setOpacity(0.62);
        }
        wrapper.setOnMouseEntered(e -> {
            if (!entrancePlaying) {
                wrapper.setTranslateY(-3); // 入场位移进行中不叠加悬停上浮（与启动页胶囊一致）
            }
            bg.setStyle(hoverStyle);
            showDescription(option, wrapper, overlay);
        });
        wrapper.setOnMouseExited(e -> {
            wrapper.setTranslateY(0);
            bg.setStyle(baseStyle);
            hideDescription(overlay);
        });
        return wrapper;
    }

    /** 已走过的一次性节点：灰底虚线只读卡（无悬停、无点击，语义与旧版灰色条目一致）。 */
    private StackPane buildUsedCard(Option option) {
        Label icon = new Label(emojiFor(option));
        icon.setStyle("-fx-font-size: 22px; -fx-opacity: 0.35;");
        Label tag = new Label("已走过");
        tag.setStyle(FONT + " -fx-font-size: 7.5px; -fx-font-weight: bold; -fx-text-fill: #7090b0;");
        VBox centerCol = new VBox(2, icon, tag);
        centerCol.setAlignment(Pos.CENTER);

        Label name = new Label(option.getName());
        name.setStyle(FONT + " -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #7090b0;");
        name.setMaxWidth(CARD_W - 18);
        name.setAlignment(Pos.CENTER);
        return buildIdleCard(centerCol, name);
    }

    /** 灰底虚线卡：整卡灰底 + 虚线外框 + 顶区分隔虚线 + 内容（已走过的一次性节点）。 */
    private StackPane buildIdleCard(Node topCenter, Label bottomLabel) {
        // 整卡底色与虚线外框：单节点绘制，子节点不会盖住边框
        Region bg = new Region();
        fixedSize(bg, CARD_W, CARD_H);
        bg.setStyle(IDLE_CARD_STYLE);

        // 顶区：半透明灰底 + 底部虚线分隔（单节点，位置不受子节点影响）
        Region topArea = new Region();
        fixedSize(topArea, CARD_W, CARD_TOP_H);
        topArea.setStyle(IDLE_TOP_STYLE);

        // 内容层：透明，不参与拾取
        StackPane top = new StackPane(topCenter);
        fixedSize(top, CARD_W, CARD_TOP_H);
        VBox bottom = new VBox(bottomLabel);
        fixedSize(bottom, CARD_W, CARD_BOTTOM_H);
        bottom.setAlignment(Pos.CENTER);
        VBox content = new VBox(top, bottom);
        fixedSize(content, CARD_W, CARD_H);
        content.setMouseTransparent(true);

        StackPane face = new StackPane(bg, topArea, content);
        fixedSize(face, CARD_W, CARD_H);
        return face;
    }

    // ------------------------------------------------------------------
    // 描述弹窗（悬停触发；点击卡片保持「进入节点」行为）
    // ------------------------------------------------------------------

    /** 在卡片上方（空间不足则翻转到下方）弹出描述弹窗，箭头指向卡片。 */
    private void showDescription(Option option, Node anchor, Pane overlay) {
        Label title = new Label(option.getTypeDisplayName() + " · " + option.getName());
        title.setStyle(FONT + " -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-text-fill: #FFD800;");

        VBox popup = new VBox(3, title);
        if (option.isConsumed() && option.isRepeatable()) {
            Label visits = new Label("已走过 " + option.getVisitCount() + " 次 · 可再次进入");
            visits.setStyle(FONT + " -fx-font-size: 8px; -fx-text-fill: #A9CBFF;");
            popup.getChildren().add(visits);
        }
        Label desc = new Label(option.getDescription());
        desc.setWrapText(true);
        desc.setMaxWidth(POPUP_W - 28);
        desc.setStyle(FONT + " -fx-font-size: 9.5px; -fx-text-fill: #E0EEFF;");
        popup.getChildren().add(desc);
        popup.setMinWidth(POPUP_W);
        popup.setPrefWidth(POPUP_W);
        popup.setMaxWidth(POPUP_W);
        popup.setStyle("-fx-background-color: rgba(21,60,120,0.97); -fx-background-radius: 10;"
                + " -fx-border-color: #FFD800; -fx-border-width: 2; -fx-border-radius: 10;"
                + " -fx-padding: 8 12 9 12;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 12, 0.0, 0, 4);");

        Region arrow = buildPopupArrow();
        overlay.getChildren().setAll(popup, arrow);
        overlay.applyCss();
        overlay.layout(); // 先让弹窗完成一次真实测量（Pane 会把子节点调整到首选尺寸）
        placePopup(popup, arrow, anchor, overlay);
        // 布局引擎完成最终测量后再校正一次：prefHeight(width) 对换行正文会高估一行，导致弹窗偏上
        Platform.runLater(() -> {
            if (popup.getParent() == overlay) {
                placePopup(popup, arrow, anchor, overlay);
            }
        });
    }

    /** 按卡片的布局边界（不含投影效果）摆放弹窗：上方优先，空间不足翻转下方，水平钳制在画布内。 */
    private void placePopup(VBox popup, Region arrow, Node anchor, Pane overlay) {
        double popupH = popup.getHeight() > 1
                ? popup.getHeight()
                : Math.max(popup.prefHeight(POPUP_W), 34);
        popup.resize(POPUP_W, popupH);
        // ⚠ 必须用 getLayoutBounds()：getBoundsInLocal() 会带上卡面子节点的投影效果，
        // 使锚点虚大一圈、弹窗被放高一截。
        Bounds card = overlay.sceneToLocal(anchor.localToScene(anchor.getLayoutBounds()));
        double x = clamp(card.getMinX() + (card.getWidth() - POPUP_W) / 2,
                4, AppConfig.WINDOW_WIDTH - POPUP_W - 4);
        double aboveY = card.getMinY() - POPUP_ARROW_H - 5 - popupH;
        boolean above = aboveY >= 2;
        double y = above ? aboveY : card.getMaxY() + POPUP_ARROW_H + 5;
        y = clamp(y, 2, AppConfig.WINDOW_HEIGHT - popupH - 2);
        popup.relocate(x, y);

        arrow.setStyle("-fx-background-color: #FFD800; -fx-shape: \""
                + (above ? "M0 0 L10 0 L5 6 Z" : "M5 0 L10 6 L0 6 Z") + "\";");
        double arrowX = clamp(card.getMinX() + card.getWidth() / 2 - POPUP_ARROW_W / 2,
                x + 6, x + POPUP_W - POPUP_ARROW_W - 6);
        arrow.relocate(arrowX, above ? y + popupH - 1 : y - POPUP_ARROW_H + 1);
    }

    /** 弹窗三角箭头（-fx-shape 绘制，金色；方向由 placePopup 按上/下翻转设置）。 */
    private static Region buildPopupArrow() {
        Region arrow = new Region();
        fixedSize(arrow, POPUP_ARROW_W, POPUP_ARROW_H);
        arrow.setMouseTransparent(true);
        return arrow;
    }

    private static void hideDescription(Pane overlay) {
        overlay.getChildren().clear();
    }

    // ------------------------------------------------------------------
    // 其余状态卡（结束 / 必然节点 / 无节点可走）
    // ------------------------------------------------------------------

    /** 结束态：远征通关 / 结束提示卡（返回入口在顶栏金色胶囊）。 */
    private VBox buildResultBox() {
        boolean cleared = session.isRogueRunCleared();
        Label icon = new Label(cleared ? "🏆" : "💀");
        icon.setStyle("-fx-font-size: 34px;");
        Label done = new Label(cleared ? "本轮远征已通关！" : "本轮远征已结束……");
        done.setStyle(FONT + " -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: "
                + (cleared ? "#0D47A1;" : "#8b3a00;"));

        VBox box = new VBox(8, icon, done);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(340);
        box.setStyle("-fx-background-color: rgba(255,255,255,0.92); -fx-background-radius: 14;"
                + " -fx-border-color: #1565C0; -fx-border-width: 2.5; -fx-border-radius: 14;"
                + " -fx-padding: 14 18;"
                + " -fx-effect: dropshadow(gaussian, rgba(21,101,192,0.22), 14, 0.0, 0, 5);");
        return box;
    }

    /** 必然节点阶段：显示节点信息与挑战按钮（文案与旧版一致，行为不变）。 */
    private VBox buildMandatoryBox(RunData data) {
        Option mandatory = data.getMandatoryOption();
        String name = mandatory == null ? data.getPhase().getDisplayName() : mandatory.getName();
        String description = mandatory == null
                ? data.getPhase().getDisplayName()
                : mandatory.getDescription();

        Label icon = new Label(mandatory == null ? "⚔" : emojiFor(mandatory));
        icon.setStyle("-fx-font-size: 30px;");
        Label tag = new Label("必然节点");
        tag.setStyle(FONT + " -fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;"
                + " -fx-background-color: linear-gradient(to bottom, #1976D2, #0D47A1);"
                + " -fx-background-radius: 20; -fx-border-color: #1A1A1A; -fx-border-width: 1.5;"
                + " -fx-border-radius: 20; -fx-padding: 2 10;");
        VBox head = new VBox(4, icon, tag);
        head.setAlignment(Pos.CENTER);

        Label hint = new Label("必然节点：" + name);
        hint.setStyle(FONT + " -fx-font-size: 12.5px; -fx-font-weight: bold; -fx-text-fill: #0D47A1;");

        Label desc = new Label(description);
        desc.setWrapText(true);
        desc.setMaxWidth(300);
        desc.setStyle(FONT + " -fx-font-size: 9.5px; -fx-text-fill: #333333;");

        Label retry = new Label(retryText(data));
        retry.setWrapText(true);
        retry.setMaxWidth(300);
        retry.setStyle(FONT + " -fx-font-size: 9px; -fx-text-fill: #555555;");

        HBox row = new HBox(buildPrimaryButton("开始挑战",
                () -> onOptionSelected.accept(data.getMandatoryOption())));
        row.setAlignment(Pos.CENTER);

        VBox box = new VBox(6, head, hint, desc, retry, row);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(340);
        box.setStyle("-fx-background-color: rgba(255,255,255,0.92); -fx-background-radius: 14;"
                + " -fx-border-color: #1565C0; -fx-border-width: 2.5; -fx-border-radius: 14;"
                + " -fx-padding: 12 16 14 16;"
                + " -fx-effect: dropshadow(gaussian, rgba(21,101,192,0.22), 14, 0.0, 0, 5);");
        return box;
    }

    /** 行动点耗尽 / 无节点可走：提示道馆战即将展开并提供挑战按钮（文案与旧版一致）。 */
    private VBox buildNoOptionBox(RunData data) {
        Label icon = new Label("⛔");
        // 大字号 emoji 在 JavaFX 下回退为单色字形，用 text-fill 染红（小字号才是彩色字形）
        icon.setStyle("-fx-font-size: 30px; -fx-text-fill: #C62828;");

        Label bossHint = new Label("行动点已用尽，无法再进入本段节点 —— 道馆战即将展开！");
        bossHint.setWrapText(true);
        bossHint.setAlignment(Pos.CENTER);
        bossHint.setMaxWidth(230); // 收窄使换行落在「——」之后，两行长度均衡
        bossHint.setStyle(FONT + " -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #8b3a00;");

        HBox row = new HBox(buildPrimaryButton("挑战道馆",
                () -> onOptionSelected.accept(data.getMandatoryOption())));
        row.setAlignment(Pos.CENTER);

        VBox box = new VBox(8, icon, bossHint, row);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(340);
        box.setStyle("-fx-background-color: rgba(255,255,255,0.92); -fx-background-radius: 14;"
                + " -fx-border-color: #E6A800; -fx-border-width: 2.5; -fx-border-radius: 14;"
                + " -fx-padding: 14 18;"
                + " -fx-effect: dropshadow(gaussian, rgba(138,96,0,0.28), 14, 0.0, 0, 5);");
        return box;
    }

    // ------------------------------------------------------------------
    // 文案 / 装饰 / 通用控件
    // ------------------------------------------------------------------

    /** 必然节点预告条：让玩家在规划路线时知道行动点耗尽的后果（文案与旧版一致）。
     *  设计稿底部警示条为浅蓝半透底，但本页背景为实景地图图，需提高底不透明度保证可读；
     *  警示字色为红色，前置 ⛔（U+26D4，渲染尺寸 ≤15px 时为彩色 emoji）。 */
    private Label buildMandatoryPreview(RunData data) {
        Label preview = new Label("⛔ 行动点耗尽或本段无可走节点时，必然触发：" + mandatoryName(data));
        preview.setWrapText(true);
        preview.setAlignment(Pos.CENTER);
        preview.setMaxWidth(Double.MAX_VALUE);
        preview.setStyle(FONT + " -fx-font-size: 8.5px; -fx-font-weight: bold;"
                + " -fx-text-fill: #C62828;"
                + " -fx-background-color: rgba(235,242,252,0.86); -fx-background-radius: 12;"
                + " -fx-border-color: rgba(21,101,192,0.35); -fx-border-width: 1.5;"
                + " -fx-border-radius: 12; -fx-padding: 4 16;");
        return preview;
    }

    private String retryText(RunData data) {
        if (data.getPhase().allowsOneRetry()) {
            return data.canRetry() ? "本节点可失败一次：战败只扣金币并可再挑战一次。"
                    : "已用过本段的失败机会：再战败本轮远征即结束。";
        }
        return "本节点不可失败：战败本轮远征立即结束。";
    }

    private String mandatoryName(RunData data) {
        Option mandatory = data.getMandatoryOption();
        return mandatory == null ? RoutePhase.GYM.getDisplayName() : mandatory.getName();
    }

    /** 节点类型 → 图片预留区 emoji（设计稿卡片顶部大图标）。 */
    private static String emojiFor(Option option) {
        OptionType type = option.getType();
        if (type == null) {
            return "❓";
        }
        return switch (type) {
            case TRAINER -> "🎒";
            case WILD -> "🐾";
            case HOSPITAL -> "🏥";
            case SHOP -> "🛒";
            case SPECIAL -> "✨";
            case REWARD -> "🎁";
            case ROCKET -> "😈";
            case ROCKET_CAPTURE -> "🕸";
            case LEGENDARY -> "🌟";
            case GYM -> "🥊";
            case ELITE_FOUR -> "🤺";
            case CHAMPION -> "👑";
            case ROCKET_INVASION -> "👹";
        };
    }

    /** 返回胶囊：启动页胶囊族的小号版本（样式见 start-menu.css 的 .rogue-back，回调与旧版一致）。 */
    private Button buildBackButton() {
        Label arrow = new Label("◀");
        arrow.setStyle("-fx-font-size: 7.5px; -fx-font-weight: bold; -fx-text-fill: #123c63;");

        StackPane iconWrap = new StackPane(arrow);
        iconWrap.getStyleClass().add("rogue-back-icon");
        fixedSize(iconWrap, 18, 18);

        Button back = new Button("返回主菜单", iconWrap);
        back.getStyleClass().add("rogue-back");
        back.setOnAction(e -> onBack.run());
        return back;
    }

    /** 深蓝主按钮（挑战类动作）。 */
    private static Button buildPrimaryButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle(FONT + " -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #FFD800;"
                + " -fx-background-color: linear-gradient(to bottom, #1976D2, #0D47A1);"
                + " -fx-background-radius: 20; -fx-border-color: #1A1A1A; -fx-border-width: 2.5;"
                + " -fx-border-radius: 20; -fx-padding: 6 20;"
                + " -fx-effect: dropshadow(one-pass-box, rgba(5,36,74,0.9), 1, 0.0, 0, 3);"
                + " -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        btn.setCursor(Cursor.HAND);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    /** 右下角精灵球水印（设计稿 Pokeball watermark，贴角半出画）。 */
    private Region buildWatermark() {
        Region ball = new Region();
        fixedSize(ball, 150, 150);
        ball.setStyle("-fx-background-color: linear-gradient(to bottom,"
                + " rgba(239,68,68,0.12) 50%, rgba(255,255,255,0.10) 50%);"
                + " -fx-background-radius: 75; -fx-border-color: rgba(26,26,26,0.08);"
                + " -fx-border-width: 5; -fx-border-radius: 75;");
        ball.setMouseTransparent(true);
        StackPane.setAlignment(ball, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(ball, new Insets(0, -55, -55, 0));
        return ball;
    }

    /** 卡片右上角精灵球装饰（设计稿 18px 等比缩小）。 */
    private static Region buildPokeballDecoration(double size, double opacity) {
        Region ball = new Region();
        fixedSize(ball, size, size);
        ball.setStyle("-fx-background-color: linear-gradient(to bottom, #ef4444 50%, white 50%);"
                + " -fx-background-radius: " + (size / 2) + ";"
                + " -fx-border-color: #1A1A1A; -fx-border-width: 1.5;"
                + " -fx-border-radius: " + (size / 2) + "; -fx-opacity: " + opacity + ";");
        ball.setMouseTransparent(true);
        return ball;
    }

    private static void fixedSize(Region node, double w, double h) {
        node.setMinSize(w, h);
        node.setPrefSize(w, h);
        node.setMaxSize(w, h);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
