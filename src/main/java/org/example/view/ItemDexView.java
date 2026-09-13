package org.example.view;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.example.model.HeldItem;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 道具图鉴页（主菜单与启动页均有入口）：一页看全 94 件道具（17 件消耗品 + 77 件装备）。
 *
 * <p>样式与「外层界面」（主菜单 / 事件遭遇页）统一：地图背景铺底，顶栏一行 ——
 * 左「返回」金色胶囊（白圈 → 深蓝描边环 → 黄→金渐变芯，悬停亮一档）、中「道具图鉴 ITEM DEX」
 * 横幅（白色描边 + 深蓝字 + 副提示胶囊，无卡底）、右上统计信息卡（.rogue-info 白底蓝环小卡，
 * 随刷新同步「全量 / 分类 / 已拥有·已穿戴」计数）；其下为四档筛选胶囊
 * （.dex-filter：未选中金色胶囊 / 选中蓝芯 + 黄环 + 黄晕）。</p>
 *
 * <p>主体为双栏（同商店页 45% / 55% 分栏）：<b>左栏信息框</b>（白底蓝环卡，上 46% 图片区
 * 「有图用图、缺图回退首字色块」，下 54% 依次为类型徽章 + 名称、拥有量状态、来源行、
 * 效果说明与装备穿脱操作行）；<b>右栏条目列表</b>（.dex-row 白底蓝环小行卡：
 * 类型徽章 + 名称 + 拥有量状态，悬停环加深并联动左栏）。</p>
 *
 * <p>左栏骨架只构建一次（悬停联动与穿脱刷新都只改内容、不重建节点，杜绝重建闪烁）；
 * 装备的穿脱走 {@link Player#equip} / {@link Player#unequip}，全队唯一穿戴的约束由模型层保证，
 * 操作后就地刷新统计卡 / 各列表行状态 / 左栏（不重建列表，保住悬停与滚动位置）。
 * 消耗品为纯展示（回复 / 解除 / 神奇糖果的局外使用入口在主菜单中栏的道具详情框，
 * 精灵球在战斗内投出）。</p>
 *
 * <p>数据来自 {@link ItemDexData}（商店商品目录 + 玩家持有情况），本类只负责展示与交互。
 * 启动页打开时没有存档（{@code player == null}），此时全部条目按「未拥有」只读展示；
 * 入场动效沿用启动页参数基线（错峰上浮淡入，左栏信息框与前 {@value #ENTRANCE_CARD_CAP} 行参与，
 * 避免长列表末张延迟过久）。</p>
 */
public final class ItemDexView {

    /** 筛选口径。 */
    public enum Filter {
        ALL("全部"),
        CONSUMABLE("道具"),
        EQUIPMENT("装备"),
        OWNED("已拥有");

        private final String label;

        Filter(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final String YH = "-fx-font-family: 'Microsoft YaHei'; ";

    /** 启动页共用样式表（胶囊按钮 / 信息卡 / 内联操作按钮 / 下拉的类均取自这里）。 */
    private static final String STYLE_SHEET = "/css/start-menu.css";

    /** 顶栏标题白色描边（同事件遭遇页：单 dropshadow 全向膨胀模拟，规避中文 stroke 光栅化性能红线）。 */
    private static final String TITLE_OUTLINE =
            " -fx-effect: dropshadow(gaussian, rgba(255, 255, 255, 1.0), 2.2, 1.0, 0, 0);";
    private static final String TITLE_OUTLINE_THIN =
            " -fx-effect: dropshadow(gaussian, rgba(255, 255, 255, 1.0), 1.2, 1.0, 0, 0);";

    /** 条目插图目录（classpath；文件名与名字一致）：道具用主菜单 / 商店同套素材（/images/tool/），
     *  装备再查携带道具目录（/images/portable Items/，目前少量有图，其余回退首字色块）。 */
    private static final String ITEM_IMAGE_DIR = "/images/tool/";
    private static final String EQUIPMENT_IMAGE_DIR = "/images/portable Items/";

    /** 插图缓存：名字 → 图片；value 为 null 表示已确认无图。 */
    private static final Map<String, Image> ICON_CACHE = new HashMap<>();

    /** 信息框留白与图片区内边距（图片自适应图片区大小，避免溢出半区）。 */
    private static final double INFO_PADDING = 10;
    private static final double IMAGE_INSET = 24;

    /** 信息框上下分区比例：图片区 46% / 文字区 54%（文字区多一行穿脱操作行，比商店略高）。 */
    private static final double IMAGE_AREA_PERCENT = 46;

    /** 入场动效（启动页参数基线：错峰上浮淡入）。 */
    private static final double ENTRANCE_RISE = 11;
    private static final double ENTRANCE_FADE_MS = 620;
    private static final double ENTRANCE_DELAY_BASE_MS = 120;
    private static final double ENTRANCE_DELAY_STEP_MS = 95;

    /** 首批入场节点上限：94 行全排按 95ms 步进会拖出数秒延迟，左栏 + 前几行参与即可。 */
    private static final int ENTRANCE_CARD_CAP = 9;

    private final Player player;
    private final String background;
    private final Runnable onBack;
    private final String backLabel;

    private Filter filter = Filter.ALL;

    /** 右上统计信息卡三行（每次刷新同步计数）。 */
    private Label countLine;
    private Label splitLine;
    private Label ownedLine;

    // ---- 左栏信息框（骨架只构建一次，悬停联动与穿脱刷新都只改内容）----
    private VBox detailBox;
    private ImageView detailImage;
    private Label detailFallback;
    private Label detailTag;
    private Label detailName;
    private Label detailState;
    private Label detailSource;
    private Label detailDesc;
    private Label detailHint;
    private Label detailHolder;
    private Label detailTargetLabel;
    private ComboBox<Pokemon> detailTarget;
    private Button detailEquip;
    private Button detailUnequip;

    /** 信息框当前展示的条目（null = 当前筛选下列表为空）。 */
    private ItemDexData.Entry currentEntry;

    // ---- 右栏条目列表 ----
    private VBox listBox;

    /** 列表滚动容器（筛选切换后把列表拉回顶部；穿脱就地更新不重建列表）。 */
    private ScrollPane listScroll;

    /** 列表行的状态元素（穿脱后就地刷新各行的拥有量 / 持有者，不重建行）。 */
    private final List<RowRef> rowRefs = new ArrayList<>();

    /** 行状态引用：条目 id + 状态标签。 */
    private record RowRef(String id, Label state) {
    }

    /** 入场动画节点（每次重建场景时清空重收集）：顶栏与筛选为一串、左栏 + 首批行为一串，各自错峰。 */
    private final List<Node> chromeEntrance = new ArrayList<>();
    private final List<Node> cardEntrance = new ArrayList<>();

    /** 首批填充标记：只有建场景时的第一批行参与入场动画（筛选 / 穿脱刷新时列表直接呈现）。 */
    private boolean firstFill;

    /**
     * @param player     玩家（提供装备库 / 背包 / 队伍，穿脱装备即改这份数据）
     * @param background 页面背景（classpath 资源，可为 {@code null}）
     * @param onBack     「返回」回调（回主菜单）
     */
    public ItemDexView(Player player, String background, Runnable onBack) {
        this(player, background, onBack, "返回主菜单");
    }

    /**
     * @param player     玩家（{@code null} = 无存档场景，如启动页；此时全部条目按「未拥有」只读展示）
     * @param background 页面背景（classpath 资源，可为 {@code null}）
     * @param onBack     「返回」回调
     * @param backLabel  返回按钮文案（主菜单用「返回主菜单」，启动页用「返回主界面」）
     */
    public ItemDexView(Player player, String background, Runnable onBack, String backLabel) {
        this.player = player;
        this.background = background;
        this.onBack = onBack;
        this.backLabel = backLabel;
    }

    public Scene createScene() {
        chromeEntrance.clear();
        cardEntrance.clear();
        firstFill = true;

        VBox root = new VBox(10);
        if (background != null && !background.isBlank()) {
            ImageBackgrounds.apply(root, background);
        }
        root.setPadding(new Insets(10, 12, 10, 12));
        root.setAlignment(Pos.TOP_LEFT);
        root.getChildren().addAll(buildHeader(), buildFilterBar(), buildContent());

        refresh();
        Scene scene = UiScale.scene(root);
        var css = ItemDexView.class.getResource(STYLE_SHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        playEntrance(chromeEntrance);
        playEntrance(cardEntrance);
        return scene;
    }

    // ------------------------------------------------------------------
    // 顶栏：返回胶囊 / 标题横幅 / 统计信息卡
    // ------------------------------------------------------------------

    /** 顶栏一行：左返回胶囊、右统计信息卡贴两侧；标题横幅水平居中于画布（同事件遭遇页头部结构）。 */
    private StackPane buildHeader() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox sides = new HBox(8, buildBackPill(), spacer, buildInfoPanel());
        sides.setAlignment(Pos.TOP_LEFT);
        StackPane header = new StackPane(buildBanner(), sides); // sides 在上层：保证两侧控件可点击
        header.setAlignment(Pos.TOP_CENTER);
        return header;
    }

    /** 「返回」金色胶囊（主菜单返回按钮同款；文案随入口传入）。 */
    private Button buildBackPill() {
        Button back = new Button(backLabel);
        back.setStyle(backPillStyle(false));
        back.setOnMouseEntered(e -> back.setStyle(backPillStyle(true)));
        back.setOnMouseExited(e -> back.setStyle(backPillStyle(false)));
        back.setOnAction(e -> onBack.run());
        back.setMaxHeight(Region.USE_PREF_SIZE); // 不被头部行高拉伸（与统计卡顶部对齐）
        chromeEntrance.add(back);
        return back;
    }

    /** 金色胶囊样式（白圈 → 深蓝描边环 → 黄→金渐变芯 + 深蓝字，悬停亮一档）。 */
    private static String backPillStyle(boolean hover) {
        return YH + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #123c63; -fx-padding: 5 12;"
                + " -fx-cursor: hand; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;"
                + " -fx-background-color: rgba(255, 255, 255, " + (hover ? "1.0" : "0.96") + "), "
                + (hover ? "#2a75bb" : "#123c63") + ", " + goldGradient(hover) + ";"
                + " -fx-background-insets: 0, 2, 4; -fx-background-radius: 999, 997, 995;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, " + (hover ? "0.55" : "0.45") + "), 10, 0.08, 0, 3);";
    }

    /** 黄→金渐变芯（悬停亮一档）：胶囊族共用色值。 */
    private static String goldGradient(boolean hover) {
        return hover
                ? "linear-gradient(to bottom, rgba(255, 255, 255, 0.95) 0%, #ffd83d 18%, #f0b000 78%, #ffd83d 100%)"
                : "linear-gradient(to bottom, rgba(255, 255, 255, 0.92) 0%, #ffcb05 18%, #eea800 78%, #ffcb05 100%)";
    }

    /** 标题横幅：白色描边 + 深蓝字 + 英文副标 + 副提示胶囊（无卡底，同事件遭遇页横幅理念）。 */
    private VBox buildBanner() {
        Label title = new Label("道具图鉴");
        title.setStyle(YH + "-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #123c63;"
                + TITLE_OUTLINE);
        Label english = new Label("ITEM DEX");
        english.setStyle("-fx-font-size: 7.5px; -fx-font-weight: bold;"
                + " -fx-text-fill: #123c63;" + TITLE_OUTLINE_THIN);
        VBox titleCol = new VBox(0, title, english);
        titleCol.setAlignment(Pos.CENTER);

        Label tip = new Label("全量列出 · 悬停右侧条目看详情 · 未拥有的道具与装备可在商店买到（装备每次进店随机上架）");
        tip.setStyle(YH + "-fx-font-size: 8.5px; -fx-font-weight: bold;"
                + " -fx-text-fill: rgba(10,30,80,0.7); -fx-background-color: rgba(255,255,255,0.6);"
                + " -fx-background-radius: 20; -fx-padding: 2 12;");

        VBox box = new VBox(3, titleCol, tip);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** 右上统计信息卡（复用 .rogue-info 白底蓝环小卡）：全量 / 分类 / 已拥有·已穿戴，随刷新同步。 */
    private VBox buildInfoPanel() {
        countLine = new Label();
        countLine.setStyle(YH + "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #123c63;");
        splitLine = new Label();
        splitLine.setStyle(YH + "-fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #123c63;");
        ownedLine = new Label();
        ownedLine.setStyle(YH + "-fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #E6A800;");

        VBox panel = new VBox(2, countLine, splitLine, ownedLine);
        panel.setMinWidth(116);
        panel.setMaxHeight(Region.USE_PREF_SIZE); // 同事件遭遇页信息卡：不被头部行高拉伸
        panel.getStyleClass().add("rogue-info");
        chromeEntrance.add(panel);
        return panel;
    }

    // ------------------------------------------------------------------
    // 筛选栏
    // ------------------------------------------------------------------

    /** 四档筛选胶囊（.dex-filter：未选中金色胶囊、选中蓝芯 + 黄晕，同胶囊族选中语义）。 */
    private HBox buildFilterBar() {
        ToggleGroup group = new ToggleGroup();
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER);
        bar.setMaxWidth(Double.MAX_VALUE);
        for (Filter option : Filter.values()) {
            ToggleButton button = new ToggleButton(option.label());
            button.setToggleGroup(group);
            button.setSelected(option == filter);
            button.getStyleClass().add("dex-filter");
            button.setOnAction(e -> {
                // ToggleGroup 允许点击已选项取消选中，这里强制保持单选
                button.setSelected(true);
                filter = option;
                refresh();
                if (listScroll != null) {
                    listScroll.setVvalue(0); // 换了筛选口径，回到列表顶部
                }
            });
            bar.getChildren().add(button);
            chromeEntrance.add(button);
        }
        return bar;
    }

    // ------------------------------------------------------------------
    // 主体双栏：左信息框 + 右条目列表
    // ------------------------------------------------------------------

    /** 主体：左信息框（45%）+ 右条目列表（55%），两栏随窗口等高拉伸（同商店页分栏比例）。 */
    private GridPane buildContent() {
        Node detail = buildDetailBox();
        ScrollPane list = buildList();

        GridPane content = new GridPane();
        content.setHgap(12);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(45);
        left.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(55);
        right.setHgrow(Priority.ALWAYS);
        content.getColumnConstraints().addAll(left, right);
        RowConstraints row = new RowConstraints();
        row.setVgrow(Priority.ALWAYS);
        content.getRowConstraints().add(row);

        GridPane.setVgrow(detail, Priority.ALWAYS);
        GridPane.setVgrow(list, Priority.ALWAYS);
        content.add(detail, 0, 0);
        content.add(list, 1, 0);
        VBox.setVgrow(content, Priority.ALWAYS); // 撑满筛选栏以下剩余高度（列表条目少时也不塌缩）
        return content;
    }

    /**
     * 左栏信息框骨架：白底蓝环卡（与商店信息框 / 主菜单中栏详情框同族），内嵌 GridPane 两行 ——
     * 上 {@value #IMAGE_AREA_PERCENT}% 图片区（图片与首字色块共用区域，按有无素材切换可见性）、
     * 下为文字区（徽章 + 名称 / 拥有量状态 / 来源 / 效果说明 / 穿脱操作行）。
     * 节点只构建一次（{@link #populateDetail} 只改内容），杜绝悬停联动时重建引起的闪烁。
     */
    private Node buildDetailBox() {
        // 上 46%：图片区（图片与首字色块共用区域，按有无素材切换可见性）
        detailImage = new ImageView();
        detailImage.setPreserveRatio(true);
        detailImage.setSmooth(true);

        detailFallback = new Label();
        detailFallback.setAlignment(Pos.CENTER);
        detailFallback.setStyle(YH + "-fx-font-size: 48px; -fx-font-weight: bold;"
                + " -fx-text-fill: white; -fx-background-color: #8a97a5; -fx-background-radius: 10;");

        StackPane imagePane = new StackPane(detailImage, detailFallback);
        imagePane.getStyleClass().add("dex-image");
        imagePane.setMinHeight(60);
        imagePane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); // 填满上半格
        detailImage.fitWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, imagePane.getWidth() - IMAGE_INSET), imagePane.widthProperty()));
        detailImage.fitHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, imagePane.getHeight() - IMAGE_INSET), imagePane.heightProperty()));
        DoubleBinding fallbackSide = Bindings.createDoubleBinding( // 首字色块同图片尺寸逻辑（区域短边正方形）
                () -> Math.max(40, Math.min(imagePane.getWidth(), imagePane.getHeight()) - IMAGE_INSET),
                imagePane.widthProperty(), imagePane.heightProperty());
        detailFallback.minWidthProperty().bind(fallbackSide);
        detailFallback.prefWidthProperty().bind(fallbackSide);
        detailFallback.maxWidthProperty().bind(fallbackSide);
        detailFallback.minHeightProperty().bind(fallbackSide);
        detailFallback.prefHeightProperty().bind(fallbackSide);
        detailFallback.maxHeightProperty().bind(fallbackSide);

        // 下 54%：徽章 + 名称 →（换行）拥有量状态 →（换行）来源 →（换行）效果说明 →（换行）穿脱操作行
        detailTag = new Label();
        detailName = new Label();
        detailName.setStyle(YH + "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #123c63;");
        HBox titleRow = new HBox(6, detailTag, detailName);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        detailState = new Label();
        detailState.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold;");

        detailSource = new Label();
        detailSource.setWrapText(true);
        detailSource.setMaxWidth(Double.MAX_VALUE);
        detailSource.setStyle(YH + "-fx-font-size: 10.5px; -fx-text-fill: rgba(18,60,99,0.62);");

        detailDesc = new Label();
        detailDesc.setWrapText(true);
        detailDesc.setMaxWidth(Double.MAX_VALUE);
        detailDesc.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #333;");

        Node actions = buildActionsRow();
        VBox text = new VBox(5, titleRow, detailState, detailSource, detailDesc, actions);
        text.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); // 填满下半格
        text.setPadding(new Insets(8, 2, 0, 2));

        GridPane box = new GridPane();
        box.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(100); // 单列撑满信息框宽（图片区 / 文字区都随列宽铺满）
        column.setHgrow(Priority.ALWAYS);
        box.getColumnConstraints().add(column);
        RowConstraints top = new RowConstraints();
        top.setPercentHeight(IMAGE_AREA_PERCENT);
        top.setVgrow(Priority.ALWAYS);
        RowConstraints bottom = new RowConstraints();
        bottom.setPercentHeight(100 - IMAGE_AREA_PERCENT);
        bottom.setVgrow(Priority.ALWAYS);
        box.getRowConstraints().addAll(top, bottom);
        box.add(imagePane, 0, 0);
        box.add(text, 0, 1);
        VBox.setVgrow(box, Priority.ALWAYS);

        detailBox = new VBox(box);
        detailBox.getStyleClass().add("dex-detail"); // 定位锚点（探针 / 测试用；样式为内联 infoBoxStyle）
        detailBox.setPadding(new Insets(INFO_PADDING));
        detailBox.setStyle(infoBoxStyle());
        detailBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); // 随栏位拉伸（VBox 默认只包内容高）
        cardEntrance.add(detailBox); // 左栏与首批行卡一同错峰上浮入场
        return detailBox;
    }

    /** 信息框：蓝环照片卡（与商店信息框同款；背景为素材照片 /images/ui/bg_info_dex.png，按本栏卡面比例裁切）。 */
    private static String infoBoxStyle() {
        return "-fx-background-color: #123c63;"
                + " -fx-background-image: url(\"/images/ui/bg_info_dex.png\");"
                + " -fx-background-size: 100% 100%;"
                + " -fx-background-position: center center;"
                + " -fx-background-repeat: no-repeat;"
                + " -fx-background-insets: 0;"
                + " -fx-background-radius: 14;"
                + " -fx-border-color: #123c63; -fx-border-width: 1.5; -fx-border-radius: 14;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.45), 10, 0.08, 0, 3);";
    }

    /**
     * 穿脱操作行骨架：提示 / 持有者 + 脱下 / 穿给下拉 + 穿戴 三种形态共用一行，
     * 由 {@link #populateActions} 按当前条目状态切换可见性（不重建控件）。
     */
    private HBox buildActionsRow() {
        detailHint = new Label();
        detailHint.setWrapText(true);
        detailHint.setStyle(YH + "-fx-font-size: 10.5px; -fx-text-fill: rgba(18,60,99,0.55);");
        detailHint.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(detailHint, Priority.ALWAYS);

        detailHolder = new Label();
        detailHolder.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;");

        detailUnequip = new Button("脱下");
        detailUnequip.getStyleClass().add("slot-delete"); // 白底红字小胶囊（危险操作的低调入口）
        detailUnequip.setMinWidth(Region.USE_PREF_SIZE);
        detailUnequip.setOnAction(e -> {
            if (player == null || currentEntry == null) {
                return;
            }
            Pokemon holder = holderOf(currentEntry.id());
            if (holder != null) {
                player.unequip(holder);
                afterEquipChange();
            }
        });

        detailTargetLabel = new Label("穿给");
        detailTargetLabel.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: rgba(18,60,99,0.75);");

        detailTarget = new ComboBox<>();
        detailTarget.getStyleClass().add("starter-combo"); // 白底深蓝描边环下拉（与胶囊族同款）
        detailTarget.setPrefWidth(112);
        detailTarget.setMinWidth(Region.USE_PREF_SIZE);
        detailTarget.setConverter(new StringConverter<>() {
            @Override
            public String toString(Pokemon pokemon) {
                return pokemon == null ? "" : pokemon.getName() + " Lv." + pokemon.getLevel();
            }

            @Override
            public Pokemon fromString(String text) {
                return null;
            }
        });

        detailEquip = new Button("穿戴");
        detailEquip.getStyleClass().add("slot-action"); // 金色小胶囊（禁用自动灰化）
        detailEquip.setMinWidth(Region.USE_PREF_SIZE);
        detailEquip.setOnAction(e -> {
            if (player == null || currentEntry == null) {
                return;
            }
            HeldItem item = equipmentOf(currentEntry.id());
            Pokemon selected = detailTarget.getSelectionModel().getSelectedItem();
            if (item != null && selected != null && player.equip(selected, item)) {
                afterEquipChange();
            }
        });

        HBox row = new HBox(8, detailHint, detailHolder, detailUnequip,
                detailTargetLabel, detailTarget, detailEquip);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    /** 可滚动条目列表：背景与视口全透明（透出地图背景，同主菜单两栏的 ScrollPane 处理）。 */
    private ScrollPane buildList() {
        listBox = new VBox(8);
        listBox.setPadding(new Insets(2, 2, 10, 2));
        ScrollPane scroll = new ScrollPane(listBox);
        listScroll = scroll;
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scroll.skinProperty().addListener((o, oldSkin, skin) -> {
            if (skin != null) {
                makeViewportTransparent(scroll);
            }
        });
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    /** 滚动区 viewport 默认不透明，会遮住地图背景；按 styleClass 递归定位后置为全透明。 */
    private static void makeViewportTransparent(Parent node) {
        for (Node child : node.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains("viewport")) {
                child.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            }
            if (child instanceof Parent p) {
                makeViewportTransparent(p);
            }
        }
    }

    // ------------------------------------------------------------------
    // 列表刷新与左栏联动
    // ------------------------------------------------------------------

    /** 全量重建（进入页 / 筛选切换）：统计卡、列表行与左栏都换成新口径；首批行收集进入场动画序列。 */
    private void refresh() {
        List<ItemDexData.Entry> entries = ItemDexData.build(player);
        updateStats(entries);

        List<ItemDexData.Entry> shown = entries.stream().filter(this::matches).toList();
        rebuildRows(shown);
        populateDetail(shown.isEmpty() ? null : shown.get(0)); // 进入 / 切换筛选后左栏默认展示第一行
        firstFill = false;
    }

    /** 统计卡三行：全量 / 分类 / 已拥有·已穿戴（穿脱后就地刷新）。 */
    private void updateStats(List<ItemDexData.Entry> entries) {
        long equipment = entries.stream().filter(ItemDexData.Entry::equipment).count();
        long owned = entries.stream().filter(ItemDexData.Entry::owned).count();
        long equipped = entries.stream().filter(ItemDexData.Entry::equipped).count();
        countLine.setText("道具图鉴 · 全量 " + entries.size() + " 件");
        splitLine.setText("道具 " + (entries.size() - equipment) + " 件 · 装备 " + equipment + " 件");
        ownedLine.setText(player == null
                ? "未载入存档 · 只读浏览"
                : "已拥有 " + owned + " 件 · 已穿戴 " + equipped + " 件");
        ownedLine.setStyle(YH + "-fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: "
                + (player == null ? "rgba(10,30,80,0.6);" : "#E6A800;"));
    }

    /** 重建条目列表（筛选切换时调用）：行卡悬停联动左栏，首批行参与入场动画。 */
    private void rebuildRows(List<ItemDexData.Entry> shown) {
        rowRefs.clear();
        listBox.getChildren().clear();
        if (shown.isEmpty()) {
            Label empty = new Label("该分类下暂无条目。");
            empty.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: rgba(10,30,80,0.7);"
                    + " -fx-background-color: rgba(255,255,255,0.6); -fx-background-radius: 20; -fx-padding: 3 14;");
            HBox holder = new HBox(empty);
            holder.setAlignment(Pos.CENTER);
            holder.setPadding(new Insets(8, 0, 0, 0));
            listBox.getChildren().add(holder);
            return;
        }
        for (ItemDexData.Entry entry : shown) {
            HBox row = buildRow(entry);
            listBox.getChildren().add(row);
            if (firstFill && cardEntrance.size() < ENTRANCE_CARD_CAP) {
                cardEntrance.add(row);
            }
        }
    }

    /** 单行条目卡：类型徽章 + 名称 + 拥有量状态；悬停环加深（CSS）并联动左栏信息框。 */
    private HBox buildRow(ItemDexData.Entry entry) {
        Label tag = new Label(entry.equipment() ? "装备" : "道具");
        styleTag(tag, entry.equipment());

        Label name = new Label(entry.name());
        name.setStyle(YH + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #123c63;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label state = new Label(ownedText(entry));
        state.setStyle(stateStyle(entry));
        state.setMinWidth(Label.USE_PREF_SIZE);

        HBox row = new HBox(8, tag, name, spacer, state);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("dex-row");
        row.setOnMouseEntered(e -> {
            if (currentEntry == null || !currentEntry.id().equals(entry.id())) {
                populateDetail(entry); // 内容级联动：无节点重建，杜绝悬停闪烁
            }
        });
        rowRefs.add(new RowRef(entry.id(), state));
        return row;
    }

    /** 类型徽章着色（道具 = 金徽章 / 装备 = 蓝徽章，与旧卡片同款）。 */
    private static void styleTag(Label tag, boolean equipment) {
        tag.setStyle(YH + (equipment
                ? "-fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;"
                        + " -fx-background-color: linear-gradient(to bottom, #1976D2, #0D47A1);"
                        + " -fx-background-radius: 20; -fx-border-color: #1A1A1A; -fx-border-width: 1;"
                        + " -fx-border-radius: 20; -fx-padding: 0 7;"
                : "-fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #5c4600;"
                        + " -fx-background-color: linear-gradient(to bottom right, #FFD800, #FFA000);"
                        + " -fx-background-radius: 20; -fx-border-color: #E6A800; -fx-border-width: 1;"
                        + " -fx-border-radius: 20; -fx-padding: 0 7;"));
    }

    /** 行状态文字样式（已拥有 / 持有 = 绿，未拥有 = 灰蓝）。 */
    private static String stateStyle(ItemDexData.Entry entry) {
        return YH + "-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: "
                + (entry.owned() ? "#2e7d32;" : "rgba(18,60,99,0.45);");
    }

    private boolean matches(ItemDexData.Entry entry) {
        return switch (filter) {
            case ALL -> true;
            case CONSUMABLE -> !entry.equipment();
            case EQUIPMENT -> entry.equipment();
            case OWNED -> entry.owned();
        };
    }

    /**
     * 穿脱后的就地刷新：重算数据，统计卡、各列表行状态与左栏信息都换成最新持有情况。
     * 穿脱不改变任何条目的「已拥有」归属，列表行集合与滚动位置原样保留（不重建行）。
     */
    private void afterEquipChange() {
        List<ItemDexData.Entry> entries = ItemDexData.build(player);
        updateStats(entries);

        Map<String, ItemDexData.Entry> byId = new HashMap<>();
        for (ItemDexData.Entry entry : entries) {
            byId.put(entry.id(), entry);
        }
        for (RowRef ref : rowRefs) {
            ItemDexData.Entry fresh = byId.get(ref.id());
            if (fresh != null) {
                ref.state().setText(ownedText(fresh));
                ref.state().setStyle(stateStyle(fresh));
            }
        }
        if (currentEntry != null) {
            ItemDexData.Entry fresh = byId.get(currentEntry.id());
            if (fresh != null) {
                populateDetail(fresh);
            }
        }
    }

    // ------------------------------------------------------------------
    // 左栏内容填充（只改内容、不重建节点）
    // ------------------------------------------------------------------

    /** 更新左栏信息框内容：图片 / 徽章 / 名称 / 拥有量状态 / 来源 / 效果说明 / 操作行（{@code null} = 空态）。 */
    private void populateDetail(ItemDexData.Entry entry) {
        currentEntry = entry;
        if (entry == null) {
            populateEmptyDetail();
            return;
        }

        Image image = loadItemIcon(entry.name());
        boolean hasImage = image != null;
        detailImage.setImage(image);
        detailImage.setVisible(hasImage);
        detailImage.setManaged(hasImage); // 不参与布局：缺图时由首字色块顶替
        detailFallback.setText(entry.name().isEmpty() ? "?" : entry.name().substring(0, 1));
        detailFallback.setVisible(!hasImage);
        detailFallback.setManaged(!hasImage);

        show(detailTag, true);
        detailTag.setText(entry.equipment() ? "装备" : "道具");
        styleTag(detailTag, entry.equipment());
        detailName.setText(entry.name());

        show(detailState, true);
        detailState.setText(ownedText(entry));
        detailState.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                + (entry.owned() ? "#2e7d32;" : "rgba(18,60,99,0.45);"));

        show(detailSource, true);
        detailSource.setText(sourceText(entry));

        boolean hasDesc = !entry.description().isBlank();
        detailDesc.setText(entry.description());
        show(detailDesc, hasDesc);

        populateActions(entry);
    }

    /** 空态（当前筛选下没有条目）：占位图 + 引导文案。 */
    private void populateEmptyDetail() {
        detailImage.setImage(null);
        detailImage.setVisible(false);
        detailImage.setManaged(false);
        detailFallback.setText("?");
        detailFallback.setVisible(true);
        detailFallback.setManaged(true);

        show(detailTag, false);
        detailName.setText("该分类下暂无条目。");
        show(detailState, false);
        show(detailSource, false);
        detailDesc.setText("换个筛选口径看看：全部 / 道具 / 装备 / 已拥有。");
        show(detailDesc, true);
        show(detailHint, false);
        show(detailHolder, false);
        show(detailUnequip, false);
        show(detailTargetLabel, false);
        show(detailTarget, false);
        show(detailEquip, false);
    }

    /** 操作行三种形态：提示文案 / 持有者 + 脱下 / 穿给下拉 + 穿戴（按条目状态切换可见性）。 */
    private void populateActions(ItemDexData.Entry entry) {
        show(detailHint, false);
        show(detailHolder, false);
        show(detailUnequip, false);
        show(detailTargetLabel, false);
        show(detailTarget, false);
        show(detailEquip, false);

        if (!entry.equipment()) {
            return; // 消耗品为纯展示：局外使用入口在主菜单中栏
        }
        if (player == null) {
            hint("进入游戏后可在此直接穿戴 / 脱下装备。");
            return;
        }
        if (!entry.owned()) {
            hint("可以在商店碰运气买到（每次进店随机上架），或在肉鸽「装备补给」事件中获得。");
            return;
        }
        Pokemon holder = holderOf(entry.id());
        if (holder != null) {
            detailHolder.setText(holder.getName() + " 持有");
            show(detailHolder, true);
            show(detailUnequip, true);
            return;
        }
        if (player.getParty().isEmpty()) {
            hint("队伍为空，暂无可穿戴的精灵。");
            return;
        }
        detailTarget.getItems().setAll(player.getParty());
        detailTarget.getSelectionModel().select(player.getActive());
        detailEquip.setDisable(equipmentOf(entry.id()) == null);
        show(detailTargetLabel, true);
        show(detailTarget, true);
        show(detailEquip, true);
    }

    private void hint(String text) {
        detailHint.setText(text);
        show(detailHint, true);
    }

    /** 显隐一并切换 managed（不占位），避免隐藏控件在 HBox 里留下空隙。 */
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /**
     * 来源行：在售的标注解锁段与基础价，不售卖（剧情专属）的标注获得途径。
     *
     * <p>装备与第 1 段就解锁的消耗品不必赘述解锁段，从第 2 段起才放开的消耗品才写明「第几段起解锁」。</p>
     */
    private static String sourceText(ItemDexData.Entry entry) {
        if (!entry.sold()) {
            return "不售卖 · 剧情专属道具（击败火箭队首领必得）";
        }
        if (entry.unlockSegment() <= 1) {
            return "商店出售 · 基础价 " + entry.basePrice() + " 金币（段数越靠后售价越高）";
        }
        return "商店出售 · 第 " + entry.unlockSegment() + " 段起解锁后一直有货 · 基础价 "
                + entry.basePrice() + " 金币（段数越靠后售价越高）";
    }

    /** 拥有量状态行：消耗品为背包件数，装备区分「未拥有 / 已拥有未穿戴 / 某精灵持有」。 */
    private static String ownedText(ItemDexData.Entry entry) {
        if (entry.equipment()) {
            if (entry.equipped()) {
                return "已拥有 · " + entry.holderName() + " 持有";
            }
            return entry.owned() ? "已拥有 · 未穿戴" : "未拥有";
        }
        return entry.owned() ? "已拥有 ×" + entry.ownedCount() : "未拥有";
    }

    // ------------------------------------------------------------------
    // 插图加载
    // ------------------------------------------------------------------

    /** 按名字加载插图：先查道具素材目录，再查装备（携带道具）素材目录；缺图返回 {@code null}（回退首字色块）。 */
    private static Image loadItemIcon(String name) {
        if (ICON_CACHE.containsKey(name)) {
            return ICON_CACHE.get(name);
        }
        Image image = tryLoadImage(ITEM_IMAGE_DIR + name + ".png");
        if (image == null) {
            image = tryLoadImage(EQUIPMENT_IMAGE_DIR + name + ".png");
        }
        ICON_CACHE.put(name, image);
        return image;
    }

    private static Image tryLoadImage(String path) {
        try (InputStream in = ItemDexView.class.getResourceAsStream(path)) {
            return in == null ? null : new Image(in);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 入场动效 / 通用
    // ------------------------------------------------------------------

    /** 入场动效：节点错峰上浮淡入（启动页参数基线）；两串序列（顶栏 / 左栏 + 行卡）各自从基准延迟开始。 */
    private static void playEntrance(List<Node> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        Interpolator spline = Interpolator.SPLINE(0.16, 1, 0.3, 1);
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            node.setOpacity(0);
            node.setTranslateY(ENTRANCE_RISE);

            FadeTransition fade = new FadeTransition(Duration.millis(ENTRANCE_FADE_MS), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setInterpolator(spline);

            TranslateTransition rise = new TranslateTransition(Duration.millis(ENTRANCE_FADE_MS), node);
            rise.setFromY(ENTRANCE_RISE);
            rise.setToY(0);
            rise.setInterpolator(spline);

            new SequentialTransition(
                    new PauseTransition(Duration.millis(ENTRANCE_DELAY_BASE_MS + i * ENTRANCE_DELAY_STEP_MS)),
                    new ParallelTransition(fade, rise)).play();
        }
    }

    /** 装备库中该 id 的实例（{@link Player#equip} 按同一性判定归属，必须取库里那一份）。 */
    private HeldItem equipmentOf(String id) {
        for (HeldItem item : player.getEquipment()) {
            if (item.getId().equals(id)) {
                return item;
            }
        }
        return null;
    }

    /** 当前穿戴该装备的队员；无人穿戴返回 {@code null}。 */
    private Pokemon holderOf(String id) {
        for (Pokemon member : player.getParty()) {
            HeldItem worn = member.getHeldItem();
            if (worn != null && worn.getId().equals(id)) {
                return member;
            }
        }
        return null;
    }
}
