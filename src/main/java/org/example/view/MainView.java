package org.example.view;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.example.model.ElementType;
import org.example.model.HeldItem;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.ItemStack;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Stats;
import org.example.model.StatusCondition;
import org.example.util.ImageBackgrounds;
import org.example.util.SpriteLoader;
import org.example.util.UiScale;

/**
 * 主菜单视图（当前阶段的地图驻留页占位，见 GameSession 类 javadoc）：铺当前段的 bg_map 随机背景。
 *
 * <p>整体分上中下三部分：</p>
 * <ul>
 *     <li><b>上</b>：左上角「返回主界面」按钮；中间训练家标题（无背景框，白晕保证地图背景上可读）；
 *     右侧为段位信息框（段号 / 金币 / 存档位分多行排列）。</li>
 *     <li><b>中</b>：左中右三栏，宽度约 3:3:2 —— 左栏为六格队伍位（首发格左侧标 ★、第二行
 *     右侧为灰色「已设首发」标识；非首发格第二行右侧附蓝色「设为首发」小按钮；悬停格子看中栏信息）；
 *     右栏为背包列表（精灵球恒置顶、按捕捉强度降序，其余保持原序）；中栏为简要信息框，随光标在
 *     左/右栏按钮上悬停切换内容（精灵：插画/名称/属性/等级与状态同行/图鉴描述/性格/HP/EXP/
 *     六项能力值/出战技能与技能库/装备与装备库，一次展示完整信息；技能库可「换上」，满 4 槽时
 *     在出战技能行「换下」完成互换；装备可「穿戴/换过来/脱下」，操作即时生效并局部刷新中栏；
 *     道具：插图/数量/功能表述）。中栏保持最后一次悬停内容，方便移开光标阅读。</li>
 *     <li><b>下</b>：进入层内事件 / 保存游戏 / 读取存档三个按钮居中排列（「返回主界面」已上移至顶部）。</li>
 * </ul>
 *
 * <p>背景由控制器按“段”决定后传入（同段多张图固定，换段才变），本类不做任何背景状态；
 * 每次进入主菜单都由控制器重新构建（队伍可能在对战中变化），因此本类不做整页状态刷新；
 * 中栏内的技能换装与装备穿脱直接作用于模型，操作后仅重建中栏内容即时反映（与原详情页同源逻辑）。</p>
 *
 * <p>ⓘ 道具插图目录（{@value #ITEM_IMAGE_DIR}）只有部分道具素材，缺图回退为「首字色块」占位，
 * 待美术补齐后自动生效。</p>
 */
public class MainView {

    /** 主菜单按钮点击回调。 */
    public interface Actions {
        /** 进入肉鸽层内事件页（楼层选项/点数/战斗入口）。 */
        void onStartRogueFloor();

        /** 将 index 对应的精灵设为下一场战斗先发（由队伍格内的「设为首发」按钮触发）。 */
        void onSetActive(int index);

        /** 保存游戏：写入所选档位（未作战时可用）。 */
        void onSaveGame();

        /** 读取存档：另选一个档位载入（未作战时可用）。 */
        void onLoadGame();

        /** 回到初始主界面（启动页）：不退出程序。 */
        void onExit();

        /** 返回游戏启动页（第一屏；重新「开始游戏」将重新创建训练家）。 */
        void onBackToStart();
    }

    private static final String YH = "-fx-font-family: 'Microsoft YaHei'; ";

    /** 队伍栏固定六格（含空位占位）。 */
    private static final int PARTY_SLOT_COUNT = 6;

    /** 单个队伍位的固定高度（设计像素；旧版 Vgrow 撑满导致纵向过高，改恒定高度更紧凑）。 */
    private static final double PARTY_SLOT_HEIGHT = 40;

    /** 中栏进度条宽度（设计像素；适配 3:3:2 的中栏宽度）。 */
    private static final double BAR_WIDTH = 170;

    /** 中栏顶部图标尺寸（设计像素；精灵插画与道具插图统一大小，约占中栏可视高度的 55%）。 */
    private static final double DETAIL_ICON_SIZE = 160;

    /** 道具插图目录（classpath；文件名与道具名一致，如「精灵球.png」）。 */
    private static final String ITEM_IMAGE_DIR = "/images/tool/";

    /** 道具插图缓存：道具名 → 图片；value 为 null 表示已确认无图。 */
    private static final Map<String, Image> ITEM_ICON_CACHE = new HashMap<>();

    private final Player player;
    private final Actions actions;
    private final String mapBackground; // 当前段地图背景（classpath，同一段内恒定）
    private final int segment; // 当前地图段号（仅用于展示）
    private final int gold; // 金币余额（负数表示本轮远征尚未开始，不展示）
    private final String slotName; // 当前存档位名（null = 尚未选档，仅用于展示）

    /** 中栏简要信息框内容容器（悬停联动时整体重建）。 */
    private VBox detailBox;

    /** 中栏当前展示的精灵（悬停切换到其他内容时清空待换技能状态；操作后刷新时保持不变）。 */
    private Pokemon shownPokemon;

    /** 待换上的技能（非 null 时处于「选择要换下的槽位」状态，出战技能行右侧临时显示「换下」）。 */
    private Move pendingSwap;

    public MainView(Player player, Actions actions, String mapBackground, int segment) {
        this(player, actions, mapBackground, segment, -1, null);
    }

    public MainView(Player player, Actions actions, String mapBackground, int segment,
                    String slotName) {
        this(player, actions, mapBackground, segment, -1, slotName);
    }

    public MainView(Player player, Actions actions, String mapBackground, int segment,
                    int gold, String slotName) {
        this.player = player;
        this.actions = actions;
        this.mapBackground = mapBackground;
        this.segment = segment;
        this.gold = gold;
        this.slotName = slotName;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        ImageBackgrounds.apply(root, mapBackground);
        root.setPadding(new Insets(10, 12, 10, 12));
        root.setTop(buildHeader());
        root.setCenter(buildContent());
        root.setBottom(buildActionBar());
        return UiScale.scene(root);
    }

    // ------------------------------------------------------------------
    // 上部分：返回 + 标题 + 信息框
    // ------------------------------------------------------------------

    private Parent buildHeader() {
        Button back = new Button("返回主界面");
        back.getStyleClass().add("menu-back");
        back.setStyle(backPillStyle(false));
        back.setOnMouseEntered(e -> back.setStyle(backPillStyle(true)));
        back.setOnMouseExited(e -> back.setStyle(backPillStyle(false)));
        back.setOnAction(e -> actions.onExit());

        Label title = new Label("宝可梦对战 · 训练家 " + player.getName());
        title.getStyleClass().add("menu-title");
        // 样式迁移：原「深色字 + 白色外发光」改为事件页「事件遭遇」横幅同款胶囊条（黄→金渐变芯 + 深蓝字）
        title.setStyle(titleBannerStyle());

        // 右侧信息框：原上部分小字信息（段号 / 金币 / 存档位）分多行排列
        VBox info = new VBox(1);
        info.getStyleClass().add("menu-info");
        info.setAlignment(Pos.CENTER_RIGHT);
        info.setStyle("-fx-background-color: #123c63, rgba(255, 255, 255, 0.92);"
                + " -fx-background-insets: 0, 1.5; -fx-background-radius: 12, 10.5;"
                + " -fx-padding: 4 10 4 9;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.35), 8, 0.06, 0, 3);");
        info.getChildren().add(infoLine("第 " + segment + " 段 · 地图"));
        if (gold >= 0) {
            info.getChildren().add(goldLine(gold));
        }
        if (slotName != null) {
            info.getChildren().add(infoLine("存档位：" + slotName));
        }
        // 不随 StackPane 拉伸：信息框宽度只包住内容（右侧信息框，而非通栏）
        info.setMaxWidth(Region.USE_PREF_SIZE);

        // StackPane 保证标题在整个窗口宽度上居中（不受两侧内容宽度影响）
        StackPane header = new StackPane(title);
        header.getChildren().addAll(back, info);
        StackPane.setAlignment(back, Pos.CENTER_LEFT);
        StackPane.setAlignment(info, Pos.CENTER_RIGHT);
        header.setPadding(new Insets(0, 0, 8, 0));
        return header;
    }

    private static Label infoLine(String text) {
        Label label = new Label(text);
        label.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #123c63;");
        return label;
    }

    /** 金币行：「金币」+ 硬币插图 + 数量（🪙 emoji 在部分运行环境渲染为方块，改用图片资源）。 */
    private static HBox goldLine(int gold) {
        Label caption = new Label("金币");
        caption.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #123c63;");
        Label amount = new Label(String.valueOf(gold));
        amount.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #E6A800;");
        HBox line = new HBox(4, caption, itemIcon("金币", 12), amount);
        line.setAlignment(Pos.CENTER_RIGHT);
        return line;
    }

    // ------------------------------------------------------------------
    // 中间部分：队伍（3） / 简要信息（3） / 背包（2）
    // ------------------------------------------------------------------

    private Parent buildContent() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        ColumnConstraints leftCol = new ColumnConstraints();
        leftCol.setPercentWidth(37.5); // 左 = 3
        ColumnConstraints midCol = new ColumnConstraints();
        midCol.setPercentWidth(37.5); // 中 = 3
        ColumnConstraints rightCol = new ColumnConstraints();
        rightCol.setPercentWidth(25); // 右 = 2
        grid.getColumnConstraints().addAll(leftCol, midCol, rightCol);

        VBox party = buildPartyColumn();
        ScrollPane detail = buildDetailPane();
        ScrollPane bag = buildBagColumn();
        GridPane.setVgrow(party, Priority.ALWAYS);
        GridPane.setVgrow(detail, Priority.ALWAYS);
        GridPane.setVgrow(bag, Priority.ALWAYS);
        grid.add(party, 0, 0);
        grid.add(detail, 1, 0);
        grid.add(bag, 2, 0);
        return grid;
    }

    /** 左栏：六个队伍位纵排（空位画虚线占位），悬停任意精灵在中栏查看信息。 */
    private VBox buildPartyColumn() {
        VBox column = new VBox(4);
        List<Pokemon> party = player.getParty();
        Pokemon active = player.getActive();
        for (int i = 0; i < PARTY_SLOT_COUNT; i++) {
            if (i < party.size()) {
                column.getChildren().add(buildPartySlot(party.get(i), active, i));
            } else {
                column.getChildren().add(buildEmptySlot());
            }
        }
        return column;
    }

    /**
     * 单个精灵位：上行「[★]名称 · Lv.X」，下行「HP cur/max · EXP a/b +『设为首发』/『已设首发』按钮」；
     * 悬停联动中栏展示完整信息；点击格子不跳转页面。
     */
    private Button buildPartySlot(Pokemon pokemon, Pokemon active, int index) {
        boolean isActive = pokemon == active;

        Label name = new Label(pokemon.getName());
        name.setStyle(YH + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: "
                + (pokemon.isFainted() ? "#aa2222" : "#222") + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label level = new Label("Lv." + pokemon.getLevel());
        level.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #555;");
        HBox line1 = new HBox(4);
        if (isActive) {
            // ★ 为实心符号字形，配合 text-fill 呈「实心金星」（⭐ 在本环境回退为单色空心字形，无法彩色）
            Label star = new Label("★");
            star.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #F5A623;");
            line1.getChildren().add(star);
        }
        line1.getChildren().addAll(name, spacer, level);
        line1.setAlignment(Pos.CENTER_LEFT);

        Label hp = new Label(pokemon.isFainted()
                ? "已倒下"
                : "HP " + pokemon.getCurrentHp() + "/" + pokemon.getMaxHp());
        hp.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: "
                + (pokemon.isFainted() ? "#aa2222" : "#333") + ";");
        Label exp = new Label(pokemon.expToNextLevel() <= 0
                ? "EXP MAX"
                : "EXP " + pokemon.getExp() + "/" + pokemon.expToNextLevel());
        exp.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #6a6a6a;");
        HBox line2 = new HBox(10, hp, exp);
        line2.setAlignment(Pos.CENTER_LEFT);

        Button slot = new Button();
        slot.setGraphic(new VBox(1, line1, line2));
        slot.getStyleClass().add("party-slot");
        slot.setStyle(slotStyle(isActive, false));
        slot.setMaxWidth(Double.MAX_VALUE);
        slot.setPrefHeight(PARTY_SLOT_HEIGHT);
        slot.setMinHeight(Region.USE_PREF_SIZE);
        slot.setMaxHeight(Region.USE_PREF_SIZE);
        slot.setOnMouseEntered(e -> {
            slot.setStyle(slotStyle(isActive, true));
            showPokemonDetail(pokemon);
        });
        slot.setOnMouseExited(e -> slot.setStyle(slotStyle(isActive, false)));

        // 第二行右侧：非首发格为蓝色「设为首发」按钮；首发格为灰色「已设首发」标识
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        line2.getChildren().addAll(gap, isActive
                ? buildActiveBadge()
                : buildSetActiveButton(pokemon, index, slot));
        return slot;
    }

    /** 「设为首发」小按钮（第二行右侧）；倒下的精灵禁用，光标直接落在按钮上也触发格子悬停联动。 */
    private Button buildSetActiveButton(Pokemon pokemon, int index, Button slot) {
        Button fire = new Button("设为首发");
        fire.getStyleClass().add("party-fire");
        fire.setStyle(pillStyle(false));
        fire.setDisable(pokemon.isFainted());
        fire.setOnAction(e -> {
            // 防御性消费：避免 ActionEvent 冒泡到外层格子按钮引发未知动作
            actions.onSetActive(index);
            e.consume();
        });
        fire.setOnMouseEntered(e -> {
            fire.setStyle(pillStyle(true));
            slot.setStyle(slotStyle(false, true));
            showPokemonDetail(pokemon);
        });
        fire.setOnMouseExited(e -> fire.setStyle(pillStyle(false)));
        return fire;
    }

    /** 首发格右侧的灰色「已设首发」标识（禁用态按钮；对应非首发格的蓝色「设为首发」）。 */
    private static Button buildActiveBadge() {
        Button badge = new Button("已设首发");
        badge.getStyleClass().add("party-active-badge");
        badge.setStyle(pillDisabledStyle());
        badge.setDisable(true);
        return badge;
    }

    /** 「已设首发」等禁用标识：灰化胶囊（启动页 .menu-pill:disabled 同款灰渐变），保留胶囊外形。 */
    private static String pillDisabledStyle() {
        return YH + "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #7d8794; -fx-padding: 2 8;"
                + " -fx-cursor: default; -fx-opacity: 1;"
                + " -fx-background-color: rgba(255, 255, 255, 0.96), #7d8794,"
                + " linear-gradient(to bottom, #e9e9e9 0%, #c7c7c7 78%, #dfdfdf 100%);"
                + " -fx-background-insets: 0, 1.5, 2.5; -fx-background-radius: 999, 997.5, 996.5;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.20), 5, 0.04, 0, 1);";
    }

    /** 操作按钮统一胶囊（启动页 .slot-action 同族）：白圈 → 深蓝描边环 → 黄→金渐变芯 + 深蓝字；悬停亮一档。 */
    private static String pillStyle(boolean hover) {
        return YH + "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #123c63; -fx-padding: 2 8;"
                + " -fx-cursor: hand; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;"
                + " -fx-background-color: rgba(255, 255, 255, " + (hover ? "1.0" : "0.96") + "), "
                + (hover ? "#2a75bb" : "#123c63") + ", " + goldGradient(hover) + ";"
                + " -fx-background-insets: 0, 1.5, 2.5; -fx-background-radius: 999, 997.5, 996.5;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.35), 7, 0.05, 0, 2);";
    }

    /** 黄→金渐变芯（悬停亮一档）：启动页 / 事件页胶囊族共用色值。 */
    private static String goldGradient(boolean hover) {
        return hover
                ? "linear-gradient(to bottom, rgba(255, 255, 255, 0.95) 0%, #ffd83d 18%, #f0b000 78%, #ffd83d 100%)"
                : "linear-gradient(to bottom, rgba(255, 255, 255, 0.92) 0%, #ffcb05 18%, #eea800 78%, #ffcb05 100%)";
    }

    /** 「返回主界面」按钮：启动页紧凑胶囊同款（白圈 → 深蓝环 → 黄→金渐变芯 + 深蓝字），悬停亮一档。 */
    private static String backPillStyle(boolean hover) {
        return YH + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #123c63; -fx-padding: 5 12;"
                + " -fx-cursor: hand; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;"
                + " -fx-background-color: rgba(255, 255, 255, " + (hover ? "1.0" : "0.96") + "), "
                + (hover ? "#2a75bb" : "#123c63") + ", " + goldGradient(hover) + ";"
                + " -fx-background-insets: 0, 2, 4; -fx-background-radius: 999, 997, 995;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, " + (hover ? "0.55" : "0.45") + "), 10, 0.08, 0, 3);";
    }

    /** 训练家标题：事件页「事件遭遇」横幅同款胶囊条（白圈 → 深蓝环 → 黄→金渐变芯 + 深蓝字，无黑框）。 */
    private static String titleBannerStyle() {
        return YH + "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #123c63;"
                + " -fx-padding: 2 18;"
                + " -fx-background-color: rgba(255, 255, 255, 0.96), #123c63,"
                + " linear-gradient(to bottom, rgba(255, 255, 255, 0.92) 0%, #ffcb05 18%, #eea800 78%, #ffcb05 100%);"
                + " -fx-background-insets: 0, 2.5, 5; -fx-background-radius: 999, 996.5, 994;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.50), 12, 0.08, 0, 4);";
    }

    /** 底部操作按钮：启动页 .menu-pill 同款大胶囊；字号/内边距按原按钮传入以保持尺寸不变。 */
    private static String barPillStyle(String fontSize, String padding, boolean hover) {
        return YH + "-fx-font-size: " + fontSize + "; -fx-font-weight: bold; -fx-padding: " + padding + ";"
                + " -fx-text-fill: #123c63; -fx-cursor: hand;"
                + " -fx-focus-color: transparent; -fx-faint-focus-color: transparent;"
                + " -fx-background-color: rgba(255, 255, 255, " + (hover ? "1.0" : "0.96") + "), "
                + (hover ? "#2a75bb" : "#123c63") + ", " + goldGradient(hover) + ";"
                + " -fx-background-insets: 0, 2.5, 5; -fx-background-radius: 999, 996.5, 994;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, " + (hover ? "0.55" : "0.45") + "), 12, 0.08, 0, 4);";
    }

    /** 队伍位：内层事件页事件卡同族（#1565C0 深蓝描边环 + 白底圆角卡）；悬停环加深一档；首发格金色环。 */
    private static String slotStyle(boolean active, boolean hover) {
        String ring = active ? "#DAA520" : (hover ? "#0D47A1" : "#1565C0");
        String fill = hover ? "rgba(255, 255, 255, 0.98)" : "rgba(255, 255, 255, 0.92)";
        return YH + "-fx-font-size: 11px; -fx-text-fill: #222; -fx-cursor: hand;"
                + " -fx-background-color: " + ring + ", " + fill + ";"
                + " -fx-background-insets: 0, 1.5; -fx-background-radius: 12, 10.5;"
                + " -fx-padding: 3 8; -fx-alignment: center-left;"
                + " -fx-effect: dropshadow(gaussian, rgba(21, 101, 192, 0.18), 12, 0, 0, 4);";
    }

    /** 空队伍位：灰底虚线框占位，不响应悬停与点击。 */
    private static StackPane buildEmptySlot() {
        Label empty = new Label("空位");
        empty.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #90aac8;");
        StackPane slot = new StackPane(empty);
        slot.setStyle("-fx-background-color: rgba(255, 255, 255, 0.35); -fx-background-radius: 12;"
                + " -fx-border-color: #90aac8; -fx-border-style: dashed; -fx-border-width: 1;"
                + " -fx-border-radius: 12;");
        slot.setMaxWidth(Double.MAX_VALUE);
        slot.setPrefHeight(PARTY_SLOT_HEIGHT);
        slot.setMinHeight(Region.USE_PREF_SIZE);
        slot.setMaxHeight(Region.USE_PREF_SIZE);
        return slot;
    }

    /** 中栏：简要信息框（初始为提示文案；悬停左/右栏内容后保持最后一次结果，便于移开光标阅读）。 */
    private ScrollPane buildDetailPane() {
        detailBox = new VBox(5);
        detailBox.setPadding(new Insets(8, 10, 8, 10));
        detailBox.getStyleClass().add("detail-box");
        showDetailHint();

        ScrollPane scroll = new ScrollPane(detailBox);
        scroll.getStyleClass().add("detail-pane");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: #123c63, rgba(255, 255, 255, 0.92);"
                + " -fx-background-insets: 0, 1.5; -fx-background-radius: 14, 12.5;"
                + " -fx-background: transparent;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.45), 10, 0.08, 0, 3);");
        scroll.skinProperty().addListener((o, oldSkin, skin) -> {
            if (skin != null) makeViewportTransparent(scroll);
        });
        return scroll;
    }

    /** 默认提示：说明左右栏的悬停交互。 */
    private void showDetailHint() {
        detailBox.setAlignment(Pos.TOP_LEFT);
        detailBox.getChildren().clear();
        Label hint = new Label("把光标移到左侧的精灵上查看简要信息；移到右侧背包道具上查看道具说明。");
        hint.setWrapText(true);
        hint.setMaxWidth(Double.MAX_VALUE);
        hint.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #666;");
        detailBox.getChildren().add(hint);
    }

    /**
     * 中栏内容：精灵完整信息（插画 → 名称/属性 → 等级与状态同行 → 图鉴/性格/HP·EXP → 能力值 →
     * 技能库 → 装备）；技能库「换上」与装备「穿戴/换过来/脱下」即时生效，操作后由本方法重建中栏。
     */
    private void showPokemonDetail(Pokemon pokemon) {
        // 切换到其他精灵时退出「选择要换下的槽位」状态（同精灵重复渲染——如操作后刷新——则保持）
        if (pokemon != shownPokemon) {
            pendingSwap = null;
            shownPokemon = pokemon;
        }
        detailBox.getChildren().clear();
        detailBox.setAlignment(Pos.TOP_LEFT);

        // 插画（无素材时回退占位文本）
        Image image = SpriteLoader.load(pokemon.getName());
        Node portrait;
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(DETAIL_ICON_SIZE);
            view.setFitHeight(DETAIL_ICON_SIZE);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            portrait = view;
        } else {
            Label fallback = new Label("（暂无立绘）");
            fallback.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #999;");
            portrait = fallback;
        }
        StackPane portraitBox = new StackPane(portrait);
        portraitBox.setMinHeight(DETAIL_ICON_SIZE);
        portraitBox.getStyleClass().add("detail-portrait");

        Label name = new Label(pokemon.getName());
        name.setStyle(YH + "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #123c63;");

        HBox types = new HBox(4);
        for (ElementType type : pokemon.getSpecies().getTypes()) {
            types.getChildren().add(chip(type.getDisplayName(), type.getColorCode()));
        }
        types.setAlignment(Pos.CENTER_LEFT);

        // 等级与状态合并为一行（原各占一行，纵向更紧凑）
        Label level = new Label("等级　Lv." + pokemon.getLevel());
        level.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #222;");
        Label status = new Label("状态　" + statusText(pokemon));
        status.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: " + statusColor(pokemon) + ";");
        HBox levelStatus = new HBox(14, level, status);
        levelStatus.setAlignment(Pos.CENTER_LEFT);

        // 图鉴描述（宝可梦库无数据时省略）
        org.example.pokemon.domain.Species library =
                org.example.pokemon.infrastructure.GameData.instance()
                        .getSpecies(pokemon.getSpecies().getId()).orElse(null);
        Label description = null;
        if (library != null && library.getDescription() != null && !library.getDescription().isBlank()) {
            description = new Label(library.getDescription());
            description.setWrapText(true);
            description.setMaxWidth(Double.MAX_VALUE);
            description.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #306090;");
        }

        // 性格行（状态已与等级合并到上方行，此处不重复）
        Label nature = new Label("性格：" + pokemon.getNature().getName());
        nature.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #222;");

        double hpRatio = pokemon.getMaxHp() <= 0 ? 0 : (double) pokemon.getCurrentHp() / pokemon.getMaxHp();
        Label hp = new Label("HP　" + pokemon.getCurrentHp() + " / " + pokemon.getMaxHp());
        hp.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #222;");
        StackPane hpBar = bar(hpRatio, hpBarColor(hpRatio), BAR_WIDTH, 8);

        String expText;
        double expRatio;
        if (pokemon.expToNextLevel() <= 0) {
            expText = "EXP　MAX（满级）";
            expRatio = 1;
        } else {
            expText = "EXP　" + pokemon.getExp() + " / " + pokemon.expToNextLevel();
            expRatio = Math.min(1, (double) pokemon.getExp() / pokemon.expToNextLevel());
        }
        Label exp = new Label(expText);
        exp.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #222;");
        StackPane expBar = bar(expRatio, "#4F8FD9", BAR_WIDTH, 8);

        // 出战技能（两行式：名称+PP ／ 属性徽章+分类·威力·命中；待换技能时行右侧临时出现「换下」）
        VBox moves = new VBox(4);
        if (pendingSwap != null) {
            Label swapping = new Label("即将换上【" + pendingSwap.getName()
                    + "】，点击某个出战技能右侧的「换下」完成互换（或在技能库点「取消」）。");
            swapping.setWrapText(true);
            swapping.setMaxWidth(Double.MAX_VALUE);
            swapping.getStyleClass().add("swap-hint");
            swapping.setStyle(YH + "-fx-font-size: 10px; -fx-text-fill: #123c63;"
                    + " -fx-background-color: rgba(255, 203, 5, 0.35); -fx-background-radius: 6; -fx-padding: 3 6;");
            moves.getChildren().add(swapping);
        }
        if (pokemon.getMoveSlots().isEmpty()) {
            Label none = new Label("尚未携带技能。");
            none.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #888;");
            moves.getChildren().add(none);
        } else {
            for (int i = 0; i < pokemon.getMoveSlots().size(); i++) {
                moves.getChildren().add(battleMoveRow(pokemon, pokemon.getMoveSlots().get(i), i));
            }
        }

        // 技能库（灰底小标题 + 全部已知技能；未出战可「换上」：空槽直接携带，满槽则进入待换状态）
        Label poolTitle = new Label("技能库（" + pokemon.getKnownMoves().size() + "）");
        poolTitle.setStyle(YH + "-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #123c63;"
                + " -fx-background-color: rgba(42, 117, 187, 0.16); -fx-background-radius: 6; -fx-padding: 2 8;");
        VBox movePool = new VBox(2);
        if (pokemon.getKnownMoves().isEmpty()) {
            Label none = new Label("技能库为空。");
            none.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #888;");
            movePool.getChildren().add(none);
        } else {
            for (Move known : pokemon.getKnownMoves()) {
                movePool.getChildren().add(poolRow(pokemon, known));
            }
        }

        // 装备（已穿戴行附「脱下」按钮；装备库逐件操作：「穿戴」/「换过来」/「已穿戴」禁用）
        HeldItem held = pokemon.getHeldItem();
        VBox equipment = new VBox(3);
        Label worn = new Label(held == null ? "当前未穿戴装备。"
                : "已穿戴：" + held.getName() + " — " + held.getDescription());
        worn.setWrapText(true);
        worn.setMaxWidth(Double.MAX_VALUE);
        worn.setMinWidth(0); // 右侧有「脱下」按钮：信息文本优先让位
        worn.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: " + (held == null ? "#888" : "#2a6e2a") + ";");
        if (held == null) {
            equipment.getChildren().add(worn);
        } else {
            HBox wornRow = new HBox(6, worn, buildUnequipButton(pokemon));
            wornRow.setAlignment(Pos.CENTER_LEFT);
            equipment.getChildren().add(wornRow);
        }
        List<HeldItem> owned = player.getEquipment();
        if (owned.isEmpty()) {
            Label none = new Label("装备库为空：可在肉鸽楼层选择「装备补给」事件获得装备。");
            none.setWrapText(true);
            none.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #888;");
            equipment.getChildren().add(none);
        } else {
            for (HeldItem item : owned) {
                equipment.getChildren().add(equipmentRow(pokemon, item));
            }
        }

        detailBox.getChildren().addAll(portraitBox, name, types, levelStatus);
        if (description != null) {
            detailBox.getChildren().add(description);
        }
        detailBox.getChildren().addAll(nature, hp, hpBar, exp, expBar,
                divider(), sectionLabel("能力值（左：当前　右：种族）"), statsRows(pokemon),
                divider(), sectionLabel("出战技能（" + pokemon.getMoveSlots().size() + "/4）"), moves,
                poolTitle, movePool,
                divider(), sectionLabel("装备"), equipment);
    }

    /** 能力值 6 行：名称 + 当前值 + 比例条 + 种族值。 */
    private static VBox statsRows(Pokemon pokemon) {
        Stats actual = pokemon.getStats();
        Stats base = pokemon.getSpecies().getBaseStats();
        int max = Math.max(1, Math.max(actual.getHp(), Math.max(actual.getAttack(),
                Math.max(Math.max(actual.getDefense(), actual.getSpAttack()),
                        Math.max(actual.getSpDefense(), actual.getSpeed())))));
        VBox box = new VBox(2);
        box.getChildren().addAll(
                statRow("HP", actual.getHp(), base.getHp(), max),
                statRow("物攻", actual.getAttack(), base.getAttack(), max),
                statRow("物防", actual.getDefense(), base.getDefense(), max),
                statRow("特攻", actual.getSpAttack(), base.getSpAttack(), max),
                statRow("特防", actual.getSpDefense(), base.getSpDefense(), max),
                statRow("速度", actual.getSpeed(), base.getSpeed(), max));
        return box;
    }

    /** 单项能力值行：名称 + 当前值 + 比例条（缩窄适配中栏）+ 种族值。 */
    private static HBox statRow(String name, int value, int baseValue, int max) {
        Label nameLabel = new Label(name);
        nameLabel.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #444;");
        nameLabel.setPrefWidth(30);
        Label valueLabel = new Label(String.valueOf(value));
        valueLabel.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #222;");
        valueLabel.setPrefWidth(30);
        valueLabel.setAlignment(Pos.CENTER_RIGHT);
        Label baseLabel = new Label("(" + baseValue + ")");
        baseLabel.setStyle(YH + "-fx-font-size: 10px; -fx-text-fill: #999;");
        baseLabel.setPrefWidth(30);
        HBox row = new HBox(5, nameLabel, valueLabel, bar((double) value / max, "#5E9ED6", 76, 6), baseLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** 出战技能单行：名称 + PP ／ 属性徽章 + 分类·威力·命中；待换技能时行右侧附红色「换下」。 */
    private VBox battleMoveRow(Pokemon pokemon, MoveSlot slot, int slotIndex) {
        Label name = new Label(slot.getMove().getName());
        name.setStyle(YH + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #222;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label pp = new Label("PP " + slot.getCurrentPp() + "/" + slot.getMaxPp());
        pp.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: "
                + (slot.getCurrentPp() == 0 ? "#aa2222" : "#555") + ";");
        HBox line1 = new HBox(6, name, spacer, pp);
        line1.setAlignment(Pos.CENTER_LEFT);

        MoveCategory category = slot.getMove().getCategory();
        String categoryText = category == MoveCategory.PHYSICAL ? "物理"
                : category == MoveCategory.SPECIAL ? "特殊" : "变化";
        Label detail = new Label(categoryText
                + " · 威力 " + (slot.getMove().getPower() <= 0 ? "--" : slot.getMove().getPower())
                + " · 命中 " + (slot.getMove().getAccuracy() < 0 ? "--" : slot.getMove().getAccuracy()));
        detail.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #666;");
        HBox line2 = new HBox(5, chip(slot.getMove().getType().getDisplayName(),
                slot.getMove().getType().getColorCode()), detail);
        if (pendingSwap != null) {
            detail.setMinWidth(0); // 右侧有「换下」按钮：详情文本让位收缩，按钮保宽
            Region push = new Region();
            HBox.setHgrow(push, Priority.ALWAYS);
            Button swap = new Button("换下");
            swap.setMinWidth(Region.USE_PREF_SIZE); // 钉宽：不随行收缩
            swap.getStyleClass().add("move-swap");
            swap.setStyle(dangerPillStyle(false));
            swap.setOnMouseEntered(e -> swap.setStyle(dangerPillStyle(true)));
            swap.setOnMouseExited(e -> swap.setStyle(dangerPillStyle(false)));
            swap.setOnAction(e -> swapWithPool(pokemon, slotIndex));
            line2.getChildren().addAll(push, swap);
        }
        line2.setAlignment(Pos.CENTER_LEFT);
        return new VBox(1, line1, line2);
    }

    /** 技能库操作行：名称 · 属性 · 威力 + 右侧按钮（出战中禁用 ／ 换上 ／ 待换时取消）。 */
    private HBox poolRow(Pokemon pokemon, Move move) {
        Label info = new Label(move.getName() + " · " + move.getType().getDisplayName()
                + " · 威力 " + (move.getPower() <= 0 ? "--" : move.getPower()));
        info.setWrapText(true);
        info.setMaxWidth(Double.MAX_VALUE);
        info.setMinWidth(0); // 允许 HBox 收缩换行，避免挤掉右侧按钮
        HBox.setHgrow(info, Priority.ALWAYS);
        info.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #333;");

        Button action = new Button();
        action.setMinWidth(Region.USE_PREF_SIZE); // 钉宽：HBox 空间不足时收缩全部由左侧文本承担
        if (pokemon.hasMove(move)) {
            action.setText("出战中");
            action.getStyleClass().add("pool-in-battle");
            action.setStyle(pillDisabledStyle());
            action.setDisable(true);
        } else if (move == pendingSwap) {
            action.setText("取消");
            action.getStyleClass().add("pool-cancel");
            action.setStyle(pillStyle(false));
            action.setOnMouseEntered(e -> action.setStyle(pillStyle(true)));
            action.setOnMouseExited(e -> action.setStyle(pillStyle(false)));
            action.setOnAction(e -> {
                pendingSwap = null;
                showPokemonDetail(pokemon);
            });
        } else {
            action.setText("换上");
            action.getStyleClass().add("pool-swap-in");
            action.setStyle(pillStyle(false));
            action.setOnMouseEntered(e -> action.setStyle(pillStyle(true)));
            action.setOnMouseExited(e -> action.setStyle(pillStyle(false)));
            action.setOnAction(e -> equipFromPool(pokemon, move));
        }
        HBox row = new HBox(6, info, action);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** 装备库操作行：名称：说明 + 右侧按钮（已穿戴禁用 ／ 换过来 ／ 穿戴）。 */
    private HBox equipmentRow(Pokemon pokemon, HeldItem item) {
        Label info = new Label(item.getName() + "：" + item.getDescription());
        info.setWrapText(true);
        info.setMaxWidth(Double.MAX_VALUE);
        info.setMinWidth(0); // 允许 HBox 收缩换行，避免挤掉右侧按钮
        HBox.setHgrow(info, Priority.ALWAYS);
        info.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #333;");

        Pokemon holder = null;
        for (Pokemon mate : player.getParty()) {
            if (mate.getHeldItem() == item) {
                holder = mate;
                break;
            }
        }
        Button action = new Button();
        action.setMinWidth(Region.USE_PREF_SIZE); // 钉宽：HBox 空间不足时收缩全部由左侧文本承担
        if (holder == pokemon) {
            action.setText("已穿戴");
            action.getStyleClass().add("equip-worn");
            action.setStyle(pillDisabledStyle());
            action.setDisable(true);
        } else if (holder != null) {
            action.setText("换过来");
            action.getStyleClass().add("equip-move-over");
            action.setStyle(pillStyle(false));
            action.setOnMouseEntered(e -> action.setStyle(pillStyle(true)));
            action.setOnMouseExited(e -> action.setStyle(pillStyle(false)));
            action.setOnAction(e -> {
                player.equip(pokemon, item); // 自动从原持有者处脱下再穿给当前精灵
                showPokemonDetail(pokemon);
            });
        } else {
            action.setText("穿戴");
            action.getStyleClass().add("equip-wear");
            action.setStyle(pillStyle(false));
            action.setOnMouseEntered(e -> action.setStyle(pillStyle(true)));
            action.setOnMouseExited(e -> action.setStyle(pillStyle(false)));
            action.setOnAction(e -> {
                player.equip(pokemon, item);
                showPokemonDetail(pokemon);
            });
        }
        HBox row = new HBox(6, info, action);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** 已穿戴行的「脱下」按钮（红底，与原详情页同语义）。 */
    private Button buildUnequipButton(Pokemon pokemon) {
        Button unequip = new Button("脱下");
        unequip.setMinWidth(Region.USE_PREF_SIZE); // 钉宽：不随行收缩
        unequip.getStyleClass().add("equip-take-off");
        unequip.setStyle(dangerPillStyle(false));
        unequip.setOnMouseEntered(e -> unequip.setStyle(dangerPillStyle(true)));
        unequip.setOnMouseExited(e -> unequip.setStyle(dangerPillStyle(false)));
        unequip.setOnAction(e -> {
            player.unequip(pokemon);
            showPokemonDetail(pokemon);
        });
        return unequip;
    }

    /**
     * 把技能库中的技能装上出战槽：有空槽直接携带；满 4 招时进入「选择要换下的槽位」状态，
     * 出战技能行临时出现「换下」按钮，点击即与该技能互换（不弹窗；逻辑与原详情页一致）。
     */
    private void equipFromPool(Pokemon pokemon, Move move) {
        if (pokemon.hasMove(move)) {
            return;
        }
        if (!pokemon.moveSlotsFull()) {
            pokemon.learnMove(move);
            pendingSwap = null;
            showPokemonDetail(pokemon);
            return;
        }
        pendingSwap = move;
        showPokemonDetail(pokemon);
    }

    /** 用待换技能替换指定出战槽：被换下的技能仍保留在技能库中，之后可再换回。 */
    private void swapWithPool(Pokemon pokemon, int slotIndex) {
        if (pendingSwap == null) {
            return;
        }
        pokemon.swapBattleMove(slotIndex, pendingSwap);
        pendingSwap = null;
        showPokemonDetail(pokemon);
    }

    /** 「换下」「脱下」等破坏性操作按钮样式：红胶囊、悬停变亮（沿用原详情页红色语义）。 */
    private static String dangerPillStyle(boolean hover) {
        return YH + "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: white; -fx-padding: 2 8;"
                + " -fx-cursor: hand; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;"
                + " -fx-background-color: rgba(255, 255, 255, 0.96), " + (hover ? "#b03a2e" : "#7a1c12") + ", "
                + (hover ? "linear-gradient(to bottom, rgba(255, 255, 255, 0.92) 0%, #f0837a 18%, #d64535 78%, #f0837a 100%)"
                        : "linear-gradient(to bottom, rgba(255, 255, 255, 0.92) 0%, #e74c3c 18%, #c0392b 78%, #e74c3c 100%)") + ";"
                + " -fx-background-insets: 0, 1.5, 2.5; -fx-background-radius: 999, 997.5, 996.5;"
                + " -fx-effect: dropshadow(gaussian, rgba(6, 22, 42, 0.35), 7, 0.05, 0, 2);";
    }

    /** 中栏内容：道具简要信息（插图 → 名称×数量 → 功能表述）。 */
    private void showItemDetail(ItemStack stack) {
        // 切到道具内容：退出精灵上下文，清空待换技能状态
        shownPokemon = null;
        pendingSwap = null;
        detailBox.getChildren().clear();
        detailBox.setAlignment(Pos.TOP_CENTER);
        Item item = stack.getItem();

        StackPane iconBox = new StackPane(itemIcon(item.getName(), DETAIL_ICON_SIZE));
        iconBox.setMinHeight(DETAIL_ICON_SIZE);

        Label title = new Label(item.getName() + " ×" + stack.getCount());
        title.setStyle(YH + "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #123c63;");

        Label description = new Label(describeItem(item));
        description.setWrapText(true);
        description.setMaxWidth(Double.MAX_VALUE);
        description.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #333;");

        detailBox.getChildren().addAll(iconBox, title, description);
    }

    /** 右栏：背包列表（精灵球恒置顶并按捕捉强度降序，其余保持原顺序）。 */
    private ScrollPane buildBagColumn() {
        VBox list = new VBox(4);
        list.setPadding(new Insets(2));
        List<ItemStack> stacks = sortedBagStacks();
        if (stacks.isEmpty()) {
            Label empty = new Label("背包空空如也……");
            empty.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #333;");
            list.getChildren().add(empty);
        } else {
            for (ItemStack stack : stacks) {
                list.getChildren().add(buildBagRow(stack));
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

    /** 精灵球恒置顶、按捕捉强度（效果值）降序；其余道具保持背包原顺序。 */
    private List<ItemStack> sortedBagStacks() {
        List<ItemStack> stacks = player.getBag().availableStacks();
        List<ItemStack> balls = new ArrayList<>();
        List<ItemStack> others = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.getItem().getCategory() == ItemCategory.POKE_BALL) {
                balls.add(stack);
            } else {
                others.add(stack);
            }
        }
        balls.sort(Comparator.comparingDouble((ItemStack s) -> s.getItem().getEffect()).reversed());
        List<ItemStack> sorted = new ArrayList<>(balls);
        sorted.addAll(others);
        return sorted;
    }

    /** 背包单行：插图（缺图回退首字色块）+ 名称 + ×数量；悬停联动中栏。 */
    private HBox buildBagRow(ItemStack stack) {
        Item item = stack.getItem();
        Node icon = itemIcon(item.getName(), 18);
        Label name = new Label(item.getName());
        name.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #222;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label count = new Label("×" + stack.getCount());
        count.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #555;");

        HBox row = new HBox(6, icon, name, spacer, count);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("bag-row");
        row.setStyle(bagRowStyle(false));
        row.setMaxWidth(Double.MAX_VALUE);
        row.setOnMouseEntered(e -> {
            row.setStyle(bagRowStyle(true));
            showItemDetail(stack);
        });
        row.setOnMouseExited(e -> row.setStyle(bagRowStyle(false)));
        return row;
    }

    /** 背包行：事件卡同族小号白底蓝环卡；悬停环加深一档。 */
    private static String bagRowStyle(boolean hover) {
        String ring = hover ? "#0D47A1" : "#1565C0";
        String fill = hover ? "rgba(255, 255, 255, 0.98)" : "rgba(255, 255, 255, 0.92)";
        return "-fx-background-color: " + ring + ", " + fill + ";"
                + " -fx-background-insets: 0, 1.5; -fx-background-radius: 8, 6.5;"
                + " -fx-padding: 2 6;"
                + " -fx-effect: dropshadow(gaussian, rgba(21, 101, 192, 0.16), 8, 0, 0, 2);";
    }

    // ------------------------------------------------------------------
    // 下部分：三个入口按钮
    // ------------------------------------------------------------------

    private HBox buildActionBar() {
        // 主入口：肉鸽层内事件
        Button rogue = new Button("进入层内事件");
        rogue.setStyle(barPillStyle("15px", "10 18", false));
        rogue.setOnMouseEntered(e -> rogue.setStyle(barPillStyle("15px", "10 18", true)));
        rogue.setOnMouseExited(e -> rogue.setStyle(barPillStyle("15px", "10 18", false)));
        rogue.setOnAction(e -> actions.onStartRogueFloor());

        Button save = new Button("保存游戏");
        save.setStyle(barPillStyle("13px", "8 14", false));
        save.setOnMouseEntered(e -> save.setStyle(barPillStyle("13px", "8 14", true)));
        save.setOnMouseExited(e -> save.setStyle(barPillStyle("13px", "8 14", false)));
        save.setOnAction(e -> actions.onSaveGame());

        Button load = new Button("读取存档");
        load.setStyle(barPillStyle("13px", "8 14", false));
        load.setOnMouseEntered(e -> load.setStyle(barPillStyle("13px", "8 14", true)));
        load.setOnMouseExited(e -> load.setStyle(barPillStyle("13px", "8 14", false)));
        load.setOnAction(e -> actions.onLoadGame());

        HBox bar = new HBox(12, rogue, save, load);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10, 0, 0, 0));
        return bar;
    }

    // ------------------------------------------------------------------
    // 公共组件与文案
    // ------------------------------------------------------------------

    /** 道具插图：有素材用图片，缺图回退为「首字色块」占位（保证所有道具可辨识）。 */
    private static Node itemIcon(String itemName, double size) {
        Image image = loadItemIcon(itemName);
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            return view;
        }
        Label fallback = new Label(itemName.isEmpty() ? "?" : itemName.substring(0, 1));
        fallback.setAlignment(Pos.CENTER);
        fallback.setMinSize(size, size);
        fallback.setPrefSize(size, size);
        fallback.setMaxSize(size, size);
        fallback.setStyle(YH + "-fx-font-size: " + Math.round(size * 0.55) + "px; -fx-font-weight: bold;"
                + " -fx-text-fill: white; -fx-background-color: #8a97a5; -fx-background-radius: 5;");
        return fallback;
    }

    private static Image loadItemIcon(String itemName) {
        if (ITEM_ICON_CACHE.containsKey(itemName)) {
            return ITEM_ICON_CACHE.get(itemName);
        }
        String path = ITEM_IMAGE_DIR + itemName + ".png";
        try (InputStream in = MainView.class.getResourceAsStream(path)) {
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

    /** 道具功能表述（与战斗背包口径一致）：回复量 / 解除范围 / 捕捉率。 */
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

    /** 彩色徽章（属性 / 状态）。 */
    private static Label chip(String text, String colorCode) {
        Label chip = new Label(text);
        chip.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: white; -fx-background-color: "
                + colorCode + "; -fx-background-radius: 8; -fx-padding: 1 8;");
        return chip;
    }

    /** 细进度条：浅灰底 + 按比例着色前景（ratio 为 0 时不显示前景）。 */
    private static StackPane bar(double ratio, String color, double width, double height) {
        Region track = new Region();
        track.setPrefSize(width, height);
        track.setStyle("-fx-background-color: #e3e3e3; -fx-background-radius: " + (height / 2) + ";");

        double clamped = Math.min(1, Math.max(0, ratio));
        double fillWidth = clamped <= 0 ? 0 : Math.max(3, width * clamped);
        Region fill = new Region();
        fill.setPrefSize(fillWidth, height);
        fill.setMaxWidth(fillWidth);
        fill.setStyle("-fx-background-color: " + color + "; -fx-background-radius: " + (height / 2) + ";");

        StackPane bar = new StackPane(track, fill);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinSize(width, height);
        bar.setPrefSize(width, height);
        bar.setMaxSize(width, height);
        return bar;
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle(YH + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #123c63;");
        return label;
    }

    private static Region divider() {
        Region line = new Region();
        line.setPrefHeight(1);
        line.setMinHeight(1);
        line.setMaxHeight(1);
        line.setStyle("-fx-background-color: #c3d4e6;");
        return line;
    }

    /** 状态文案：倒下优先，其次异常状态 / 混乱 / 正常。 */
    private static String statusText(Pokemon pokemon) {
        if (pokemon.isFainted()) {
            return "已倒下";
        }
        if (pokemon.getStatus() == StatusCondition.NONE) {
            return pokemon.isConfused() ? "混乱" : "正常";
        }
        return pokemon.getStatus().getDisplayName() + (pokemon.isConfused() ? "+混乱" : "");
    }

    private static String statusColor(Pokemon pokemon) {
        if (pokemon.isFainted()) {
            return "#aa2222";
        }
        if (pokemon.getStatus() != StatusCondition.NONE || pokemon.isConfused()) {
            return "#8E44AD";
        }
        return "#444";
    }

    /** 血条颜色：健康绿 / 半血橙 / 濒危红。 */
    private static String hpBarColor(double ratio) {
        return ratio > 0.5 ? "#4CAF50" : (ratio > 0.2 ? "#FF9800" : "#E53935");
    }

    /** 滚动区 viewport 默认不透明，会遮住地图背景；按 styleClass 递归定位后置为全透明。 */
    private static void makeViewportTransparent(Parent node) {
        for (Node child : node.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains("viewport")) {
                child.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            }
            if (child instanceof Parent p) makeViewportTransparent(p);
        }
    }
}
