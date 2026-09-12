package org.example.view;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.GameSession;
import org.example.data.GameData;
import org.example.data.ShopStock;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.ItemStack;
import org.example.model.RouteConfig;
import org.example.model.RunData;
import org.example.model.StatusCondition;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 商店界面（《需求文档》§4.2 商店 + §七 界面需求）：用金币购买道具、回复品。
 *
 * <p>只负责展示 {@link ShopStock} 与收集购买意图；金币校验与扣款、入包由控制器完成 ——
 * 购买成功或失败后控制器重建本页以刷新金币与可购状态。</p>
 *
 * <p>2026-09-12 按设计稿改版（仅样式与布局调整，回调机制不变），整体只分上下两部分：</p>
 * <ul>
 *   <li><b>上</b>：左「返回」胶囊（{@link FloatingMenu} 共享组件紧凑尺寸，与内层子页一致）／
 *       中「商店」标题（白描边 + 深蓝字，与内层「事件遭遇」横幅同款处理）／
 *       右段位信息卡（当前阶段与金币，start-menu.css 的 .rogue-info 同款）；</li>
 *   <li><b>下</b>：左商品信息框（上 1/2 商品图片、下 1/2 名称 →（换行）价格与可购状态 →
 *       （换行）背包拥有数量 →（换行）具体描述；悬停右侧商品行联动更新）、
 *       右商品列表（纵向排列，单行 = 商品名 / 可否购买状态 / 所需金币 / 购买按钮）。</li>
 * </ul>
 */
public class ShopView {

    /** 中文字体族（与主菜单 / 事件页一致）。 */
    private static final String FONT = "-fx-font-family: 'Microsoft YaHei'; ";

    /** 共享样式表（返回胶囊 .menu-pill / 信息卡 .rogue-info 由它承载）。 */
    private static final String STYLE_SHEET = "/css/start-menu.css";

    /** 标题白色描边：单 dropshadow 全向膨胀模拟描边（规避中文 stroke 光栅化性能红线）。 */
    private static final String TITLE_OUTLINE =
            " -fx-effect: dropshadow(gaussian, rgba(255, 255, 255, 1.0), 2.0, 1.0, 0, 0);";

    /** 道具插图目录与缓存（与主菜单同一套素材：有图用图，缺图回退首字色块）。 */
    private static final String ITEM_IMAGE_DIR = "/images/tool/";
    private static final Map<String, Image> ITEM_ICON_CACHE = new HashMap<>();

    /** 信息框留白与图片区内边距（图片自适应图片区大小，避免溢出半区）。 */
    private static final double INFO_PADDING = 10;
    private static final double IMAGE_INSET = 24;

    private final GameSession session;
    private final ShopStock stock;
    private final Consumer<ShopStock.Entry> onBuy;
    private final Runnable onLeave;

    /** 左栏信息框（悬停右侧商品行时重建内容）；每次构建场景时重建。 */
    private VBox detailBox;

    /** 左上角返回胶囊（复用 FloatingMenu 共享胶囊菜单组）；每次构建场景时重建。 */
    private FloatingMenu backMenu;

    public ShopView(GameSession session, ShopStock stock,
                    Consumer<ShopStock.Entry> onBuy, Runnable onLeave) {
        this.session = session;
        this.stock = stock;
        this.onBuy = onBuy;
        this.onLeave = onLeave;
    }

    public Scene createScene() {
        RunData data = session.getRogueRunData();
        int gold = data.getGold();

        BorderPane root = new BorderPane();
        ImageBackgrounds.apply(root, session.mapBackgroundPath()); // 与主菜单 / 事件页同款地图背景
        root.setPadding(new Insets(10, 12, 10, 12));
        root.setTop(buildHeader(data));
        root.setCenter(buildContent(gold));

        Scene scene = UiScale.scene(root);
        var css = ShopView.class.getResource(STYLE_SHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        if (backMenu != null) {
            backMenu.playEntrance(); // 返回胶囊与内层各子页返回按钮同款的错峰上浮淡入
        }
        return scene;
    }

    // ------------------------------------------------------------------
    // 上部分：返回 + 标题 + 段位信息卡
    // ------------------------------------------------------------------

    /** 上部分一行：左返回胶囊 / 中「商店」标题 / 右段位信息卡（StackPane 保证标题整行居中）。 */
    private StackPane buildHeader(RunData data) {
        Label title = new Label("商店");
        title.setStyle(FONT + "-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #123c63;"
                + TITLE_OUTLINE);

        VBox back = buildBackMenu();
        VBox info = buildInfoPanel(data);

        StackPane header = new StackPane(title);
        header.getChildren().addAll(back, info); // 两侧置后：保证可点击层级高于标题
        StackPane.setAlignment(back, Pos.CENTER_LEFT);
        StackPane.setAlignment(info, Pos.CENTER_RIGHT);
        header.setPadding(new Insets(0, 0, 8, 0));
        return header;
    }

    /** 返回入口：复用 {@link FloatingMenu} 共享胶囊菜单组（紧凑尺寸，与内层各子页返回按钮一致；
     *  纯文字胶囊，鼠标移开后恢复黄色常态）。 */
    private VBox buildBackMenu() {
        FloatingMenu menu = new FloatingMenu();
        menu.setCompact(true);        // 与内层顶栏一致的小号尺寸（仅尺寸 / 字号变，配色与动效不变）
        menu.setDeselectOnExit(true); // 移出取消选中：悬停深蓝 → 移开恢复黄色
        menu.addPill("slate", "返回", "", "", onLeave); // 图标与副标签留空：纯文字胶囊
        backMenu = menu;
        VBox pane = menu.node();
        pane.setMaxWidth(Region.USE_PREF_SIZE);  // 不随头部拉伸：胶囊宽度只包内容（贴左）
        pane.setMaxHeight(Region.USE_PREF_SIZE); // 不被头部行高拉伸，与信息卡顶部对齐
        return pane;
    }

    /** 右侧信息卡（start-menu.css 的 .rogue-info 同款）：当前阶段 + 金币数。 */
    private VBox buildInfoPanel(RunData data) {
        Label segment = new Label("第 " + session.getSegment() + " / " + RouteConfig.TOTAL_SEGMENTS
                + " 段 · " + data.getPhase().getDisplayName());
        segment.setStyle(FONT + "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #123c63;");

        Label gold = new Label("金币: " + data.getGold());
        gold.setStyle(FONT + "-fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #E6A800;");

        VBox panel = new VBox(2, segment, gold);
        panel.setMinWidth(116);
        panel.setMaxWidth(Region.USE_PREF_SIZE);  // 不随 StackPane 拉伸：信息框只包内容（右侧小卡而非通栏）
        panel.setMaxHeight(Region.USE_PREF_SIZE); // 不被头部行高拉伸，只包内容
        panel.getStyleClass().add("rogue-info");
        return panel;
    }

    // ------------------------------------------------------------------
    // 下部分：左商品信息框 + 右商品列表
    // ------------------------------------------------------------------

    /** 下部分：左商品信息框（45%）+ 右商品列表（55%），两栏随窗口等高拉伸。 */
    private GridPane buildContent(int gold) {
        detailBox = new VBox();
        detailBox.getStyleClass().add("shop-detail");
        detailBox.setPadding(new Insets(INFO_PADDING));
        detailBox.setStyle(infoBoxStyle());
        detailBox.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); // 随栏位拉伸（VBox 默认只包内容高）
        showDetailHint();

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

        ScrollPane stockList = buildStockList(gold);
        GridPane.setVgrow(detailBox, Priority.ALWAYS);
        GridPane.setVgrow(stockList, Priority.ALWAYS);
        content.add(detailBox, 0, 0);
        content.add(stockList, 1, 0);
        return content;
    }

    /** 信息框默认提示：说明悬停交互（与主菜单中栏同模式）。 */
    private void showDetailHint() {
        Label hint = new Label("把光标移到右侧的商品上查看详情。");
        hint.setWrapText(true);
        hint.setMaxWidth(Double.MAX_VALUE);
        hint.setStyle(FONT + "-fx-font-size: 11px; -fx-text-fill: #666;");
        detailBox.setAlignment(Pos.TOP_LEFT);
        detailBox.getChildren().setAll(hint);
    }

    /** 信息框内容：上 1/2 商品图片、下 1/2 名称 /（换行）价格与可购状态 /（换行）背包拥有数量 /（换行）描述。 */
    private void showProductDetail(ShopStock.Entry entry, int gold) {
        Node box = buildProductDetail(entry, gold);
        VBox.setVgrow(box, Priority.ALWAYS); // 撑满信息框（行分区才按 50%/50% 生效）
        detailBox.setAlignment(Pos.TOP_LEFT);
        detailBox.getChildren().setAll(box);
    }

    /** 单个商品详情：内嵌 GridPane 两行严格平分（上 = 图片区、下 = 文字区）。 */
    private Node buildProductDetail(ShopStock.Entry entry, int gold) {
        Item item = GameData.instance().item(entry.itemId());
        boolean affordable = gold >= entry.price();

        // 上 1/2：商品图片（自适应图片区大小；缺图回退「首字色块」）
        StackPane imagePane = new StackPane();
        imagePane.getStyleClass().add("shop-image");
        imagePane.setMinHeight(60);
        imagePane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); // 填满上半格
        imagePane.getChildren().add(productImage(entry.itemName(), imagePane));

        // 下 1/2：名称 →（换行）价格 + 可否购买状态 →（换行）背包拥有数量 →（换行）具体描述
        Label name = new Label(entry.itemName());
        name.setStyle(FONT + "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #123c63;");

        Label price = new Label(entry.price() + " 金币");
        price.setStyle(FONT + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #E6A800;");
        Label state = new Label(affordable ? "可购买" : "金币不足");
        state.setStyle(FONT + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                + (affordable ? "#2e7d32" : "#C62828") + ";");
        HBox priceRow = new HBox(8, price, state);
        priceRow.setAlignment(Pos.CENTER_LEFT);

        Label owned = new Label("背包已有 ×" + bagCount(entry.itemName()));
        owned.setStyle(FONT + "-fx-font-size: 11px; -fx-text-fill: #555;");

        Label description = new Label(item == null ? "" : describeItem(item));
        description.setWrapText(true);
        description.setMaxWidth(Double.MAX_VALUE);
        description.setStyle(FONT + "-fx-font-size: 11px; -fx-text-fill: #333;");

        VBox text = new VBox(6, name, priceRow, owned, description);
        text.getStyleClass().add("shop-text");
        text.setAlignment(Pos.TOP_LEFT);
        text.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); // 填满下半格
        text.setPadding(new Insets(8, 2, 0, 2));

        GridPane box = new GridPane();
        box.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(100); // 单列撑满信息框宽（图片区 / 文字区都随列宽铺满）
        column.setHgrow(Priority.ALWAYS);
        box.getColumnConstraints().add(column);
        RowConstraints top = new RowConstraints();
        top.setPercentHeight(50);
        top.setVgrow(Priority.ALWAYS);
        RowConstraints bottom = new RowConstraints();
        bottom.setPercentHeight(50);
        bottom.setVgrow(Priority.ALWAYS);
        box.getRowConstraints().addAll(top, bottom);
        box.add(imagePane, 0, 0);
        box.add(text, 0, 1);
        return box;
    }

    /** 背包中该商品当前的拥有数量（按道具名匹配堆叠；不在背包则为 0）。 */
    private int bagCount(String itemName) {
        if (session.getPlayer() == null) {
            return 0;
        }
        for (ItemStack stack : session.getPlayer().getBag().availableStacks()) {
            if (itemName.equals(stack.getItem().getName())) {
                return stack.getCount();
            }
        }
        return 0;
    }

    /** 右栏：商品列表（纵向排列，单行 = 名称 / 状态 / 金币 / 购买按钮；悬停联动左栏信息框）。 */
    private ScrollPane buildStockList(int gold) {
        VBox list = new VBox(6);
        list.setPadding(new Insets(2));
        if (stock.isEmpty()) {
            Label empty = new Label("本次货架是空的，下次再来看看。");
            empty.setStyle(FONT + "-fx-font-size: 12px; -fx-text-fill: #333;");
            list.getChildren().add(empty);
        } else {
            for (ShopStock.Entry entry : stock.entries()) {
                list.getChildren().add(buildStockRow(entry, gold));
            }
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scroll.skinProperty().addListener((o, oldSkin, skin) -> {
            if (skin != null) makeViewportTransparent(scroll);
        });
        return scroll;
    }

    /** 商品行：商品名 / 可否购买状态 / 所需金币 / 购买按钮（一行）；悬停联动左栏信息框。 */
    private HBox buildStockRow(ShopStock.Entry entry, int gold) {
        boolean affordable = gold >= entry.price();

        Label name = new Label(entry.itemName());
        name.setStyle(FONT + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #222;");

        Label state = new Label(affordable ? "可购买" : "金币不足");
        state.setStyle(FONT + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                + (affordable ? "#2e7d32" : "#C62828") + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label price = new Label(entry.price() + " 金币");
        price.setStyle(FONT + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #E6A800;");

        Button buy = new Button("购买");
        buy.setMinWidth(Region.USE_PREF_SIZE); // 钉宽：不随行收缩
        buy.getStyleClass().add("shop-buy");
        if (affordable) {
            buy.setStyle(pillStyle(false));
            buy.setOnMouseEntered(e -> buy.setStyle(pillStyle(true)));
            buy.setOnMouseExited(e -> buy.setStyle(pillStyle(false)));
            buy.setOnAction(e -> onBuy.accept(entry));
        } else {
            buy.setStyle(pillDisabledStyle());
            buy.setDisable(true); // 金币不足：灰化不可点（状态文案已说明原因）
        }

        HBox row = new HBox(8, name, state, spacer, price, buy);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("shop-row");
        row.setStyle(rowCardStyle(false));
        row.setMaxWidth(Double.MAX_VALUE);
        row.setOnMouseEntered(e -> {
            row.setStyle(rowCardStyle(true));
            showProductDetail(entry, gold);
        });
        row.setOnMouseExited(e -> row.setStyle(rowCardStyle(false)));
        return row;
    }

    // ------------------------------------------------------------------
    // 样式与图片
    // ------------------------------------------------------------------

    /** 信息框：白底蓝环卡（与主菜单中栏详情框同族）。 */
    private static String infoBoxStyle() {
        return "-fx-background-color: #123c63, rgba(255, 255, 255, 0.92);"
                + " -fx-background-insets: 0, 1.5; -fx-background-radius: 14, 12.5;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.45), 10, 0.08, 0, 3);";
    }

    /** 商品行：事件卡同族小号白底蓝环卡；悬停环加深一档（与主菜单背包行同款）。 */
    private static String rowCardStyle(boolean hover) {
        String ring = hover ? "#0D47A1" : "#1565C0";
        String fill = hover ? "rgba(255, 255, 255, 0.98)" : "rgba(255, 255, 255, 0.92)";
        return "-fx-background-color: " + ring + ", " + fill + ";"
                + " -fx-background-insets: 0, 1.5; -fx-background-radius: 8, 6.5;"
                + " -fx-padding: 3 8;"
                + " -fx-effect: dropshadow(gaussian, rgba(21, 101, 192, 0.16), 8, 0, 0, 2);";
    }

    /** 购买按钮胶囊（启动页 .slot-action 同族）：白圈 → 深蓝描边环 → 黄→金渐变芯 + 深蓝字；悬停亮一档。 */
    private static String pillStyle(boolean hover) {
        return FONT + "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #123c63; -fx-padding: 2 8;"
                + " -fx-cursor: hand; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;"
                + " -fx-background-color: rgba(255, 255, 255, " + (hover ? "1.0" : "0.96") + "), "
                + (hover ? "#2a75bb" : "#123c63") + ", " + goldGradient(hover) + ";"
                + " -fx-background-insets: 0, 1.5, 2.5; -fx-background-radius: 999, 997.5, 996.5;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.35), 7, 0.05, 0, 2);";
    }

    /** 灰化胶囊（金币不足的购买按钮）：仅灰芯与灰字，尺寸不变。 */
    private static String pillDisabledStyle() {
        return FONT + "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #7d8794; -fx-padding: 2 8;"
                + " -fx-cursor: default; -fx-opacity: 1;"
                + " -fx-background-color: rgba(255, 255, 255, 0.96), #7d8794,"
                + " linear-gradient(to bottom, #e9e9e9 0%, #c7c7c7 78%, #dfdfdf 100%);"
                + " -fx-background-insets: 0, 1.5, 2.5; -fx-background-radius: 999, 997.5, 996.5;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.20), 5, 0.04, 0, 1);";
    }

    /** 黄→金渐变芯（悬停亮一档）：启动页 / 事件页胶囊族共用色值。 */
    private static String goldGradient(boolean hover) {
        return hover
                ? "linear-gradient(to bottom, rgba(255, 255, 255, 0.95) 0%, #ffd83d 18%, #f0b000 78%, #ffd83d 100%)"
                : "linear-gradient(to bottom, rgba(255, 255, 255, 0.92) 0%, #ffcb05 18%, #eea800 78%, #ffcb05 100%)";
    }

    /** 商品图片：有素材用图片（随图片区大小自适应缩放），缺图回退「首字色块」占位。 */
    private static Node productImage(String itemName, StackPane pane) {
        Image image = loadItemIcon(itemName);
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.fitWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(0, pane.getWidth() - IMAGE_INSET), pane.widthProperty()));
            view.fitHeightProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(0, pane.getHeight() - IMAGE_INSET), pane.heightProperty()));
            return view;
        }
        Label fallback = new Label(itemName.isEmpty() ? "?" : itemName.substring(0, 1));
        fallback.setAlignment(Pos.CENTER);
        fallback.setMinSize(96, 96);
        fallback.setPrefSize(96, 96);
        fallback.setMaxSize(96, 96);
        fallback.setStyle(FONT + "-fx-font-size: 52px; -fx-font-weight: bold;"
                + " -fx-text-fill: white; -fx-background-color: #8a97a5; -fx-background-radius: 10;");
        return fallback;
    }

    private static Image loadItemIcon(String itemName) {
        if (ITEM_ICON_CACHE.containsKey(itemName)) {
            return ITEM_ICON_CACHE.get(itemName);
        }
        String path = ITEM_IMAGE_DIR + itemName + ".png";
        try (InputStream in = ShopView.class.getResourceAsStream(path)) {
            if (in == null) {
                ITEM_ICON_CACHE.put(itemName, null);
                return null;
            }
            Image image = new Image(in);
            ITEM_ICON_CACHE.put(itemName, image);
            return image;
        } catch (Exception e) {
            ITEM_ICON_CACHE.put(itemName, null);
            return null;
        }
    }

    /** 道具具体描述（与主菜单中栏口径一致）：回复量 / 解除范围 / 捕捉率。 */
    private static String describeItem(Item item) {
        if (item.getCategory() == ItemCategory.HEAL) {
            return "回复 " + (int) item.getEffect() + " HP";
        }
        if (item.getCategory() == ItemCategory.CURE) {
            return "解除" + curesText(item);
        }
        if (item.getCategory() == ItemCategory.POKE_BALL) {
            return item.isAlwaysCatch() ? "必定捕捉" : "捕捉率 ×" + effectText(item.getEffect());
        }
        return "";
    }

    /** 数值文案：整数省略小数位（精灵球 ×3 而非 ×3.0）。 */
    private static String effectText(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    /** 解除道具的适用范围文案：万灵药显示「全部异常状态」，其余逐一列出具体状态名。 */
    private static String curesText(Item item) {
        if (item.curesAll()) {
            return "全部异常状态";
        }
        List<StatusCondition> conditions = item.curedStatuses();
        if (conditions.isEmpty()) {
            return "异常状态";
        }
        return conditions.stream()
                .map(StatusCondition::getDisplayName)
                .collect(Collectors.joining("/"))
                + "状态";
    }

    /** 滚动容器 viewport 透明化（与主菜单同款局部递归，避免整页样式表被 ScrollPane 覆盖）。 */
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
}
