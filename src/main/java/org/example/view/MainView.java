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
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
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
 *     <li><b>中</b>：左中右三栏，宽度约 3:3:2 —— 左栏为六格队伍位（首发格左侧标 ⭐，非首发格
 *     第二行右侧附「设为首发」小按钮；点格子其余区域进详情）；
 *     右栏为背包列表（精灵球恒置顶、按捕捉强度降序，其余保持原序）；中栏为简要信息框，随光标在
 *     左/右栏按钮上悬停切换内容（精灵：插画/名称/属性/等级/状态/HP/EXP/四个技能/装备；
 *     道具：插图/数量/功能表述）。中栏保持最后一次悬停内容，方便移开光标阅读。</li>
 *     <li><b>下</b>：进入层内事件 / 保存游戏 / 读取存档三个按钮居中排列（「返回主界面」已上移至顶部）。</li>
 * </ul>
 *
 * <p>背景由控制器按“段”决定后传入（同段多张图固定，换段才变），本类不做任何背景状态；
 * 每次进入主菜单都由控制器重新构建（队伍可能在对战中变化），因此本类不做状态刷新。</p>
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

        /** 打开 index 对应精灵的详情页（立绘/属性/技能/装备；装备穿脱后返回主菜单自动同步）。 */
        void onShowPokemonDetail(int index);

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

    /** 中栏插画高度（设计像素；约合中栏可视高度 290 的 2/5，即页面顶部的「图标部分」）。 */
    private static final double PORTRAIT_HEIGHT = 116;

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
        back.setStyle(YH + "-fx-font-size: 13px; -fx-padding: 5 12; -fx-cursor: hand;");
        back.setOnAction(e -> actions.onExit());

        Label title = new Label("宝可梦对战 · 训练家 " + player.getName());
        title.getStyleClass().add("menu-title");
        // 已删除原大背景框：改用白色外发光，保证文字压在地图背景上仍清晰可读
        title.setStyle(YH + "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #222;"
                + " -fx-effect: dropshadow(gaussian, rgba(255, 255, 255, 0.95), 10, 0.75, 0, 0);");

        // 右侧信息框：原上部分小字信息（段号 / 金币 / 存档位）分多行排列
        VBox info = new VBox(1);
        info.getStyleClass().add("menu-info");
        info.setAlignment(Pos.CENTER_RIGHT);
        info.setStyle("-fx-background-color: rgba(255, 255, 255, 0.66); -fx-background-radius: 8;"
                + " -fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 8;"
                + " -fx-padding: 4 10;");
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
        label.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #444;");
        return label;
    }

    /** 金币行：「金币」+ 硬币插图 + 数量（🪙 emoji 在部分运行环境渲染为方块，改用图片资源）。 */
    private static HBox goldLine(int gold) {
        Label caption = new Label("金币");
        caption.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #444;");
        Label amount = new Label(String.valueOf(gold));
        amount.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #444;");
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

    /** 左栏：六个队伍位纵排（空位画虚线占位），点击任意精灵进入详情页。 */
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
     * 单个精灵位：上行「[⭐]名称 · Lv.X」，下行「HP cur/max · EXP a/b +『设为首发』按钮」；
     * 悬停联动中栏、点击（按钮以外区域）进详情。
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
        slot.setOnAction(e -> actions.onShowPokemonDetail(index));

        if (!isActive) {
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            line2.getChildren().addAll(gap, buildSetActiveButton(pokemon, index, slot));
        }
        return slot;
    }

    /** 「设为首发」小按钮（第二行右侧）；倒下的精灵禁用，光标直接落在按钮上也触发格子悬停联动。 */
    private Button buildSetActiveButton(Pokemon pokemon, int index, Button slot) {
        Button fire = new Button("设为首发");
        fire.getStyleClass().add("party-fire");
        fire.setStyle(fireStyle(false));
        fire.setDisable(pokemon.isFainted());
        fire.setOnAction(e -> {
            // ActionEvent 会沿父链冒泡到外层格子（触发其「进详情」），这里必须消费掉
            actions.onSetActive(index);
            e.consume();
        });
        fire.setOnMouseEntered(e -> {
            fire.setStyle(fireStyle(true));
            slot.setStyle(slotStyle(false, true));
            showPokemonDetail(pokemon);
        });
        fire.setOnMouseExited(e -> fire.setStyle(fireStyle(false)));
        return fire;
    }

    private static String fireStyle(boolean hover) {
        return YH + "-fx-font-size: 10px; -fx-text-fill: white; -fx-padding: 1 6; -fx-cursor: hand;"
                + " -fx-background-radius: 6; -fx-background-color: "
                + (hover ? "#1e7ae0" : "#1565C0") + ";";
    }

    private static String slotStyle(boolean active, boolean hover) {
        String border = active ? "#DAA520" : (hover ? "#1565C0" : "#c9c9c9");
        String background = hover ? "rgba(255, 255, 255, 0.9)" : "rgba(255, 255, 255, 0.66)";
        return YH + "-fx-font-size: 11px; -fx-text-fill: #222; -fx-background-color: " + background + ";"
                + " -fx-background-radius: 8; -fx-border-color: " + border + "; -fx-border-width: 1;"
                + " -fx-border-radius: 8; -fx-padding: 3 8; -fx-alignment: center-left; -fx-cursor: hand;";
    }

    /** 空队伍位：灰底虚线框占位，不响应悬停与点击。 */
    private static StackPane buildEmptySlot() {
        Label empty = new Label("空位");
        empty.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #9a9a9a;");
        StackPane slot = new StackPane(empty);
        slot.setStyle("-fx-background-color: rgba(255, 255, 255, 0.25); -fx-background-radius: 8;"
                + " -fx-border-color: #b5b5b5; -fx-border-style: dashed; -fx-border-width: 1;"
                + " -fx-border-radius: 8;");
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
        showDetailHint();

        ScrollPane scroll = new ScrollPane(detailBox);
        scroll.getStyleClass().add("detail-pane");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6); -fx-background-radius: 10;"
                + " -fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 10;"
                + " -fx-background: transparent;");
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

    /** 中栏内容：精灵简要信息（插画 → 名称 → 属性 → 等级 → 状态 → HP → EXP → 技能 → 装备）。 */
    private void showPokemonDetail(Pokemon pokemon) {
        detailBox.getChildren().clear();
        detailBox.setAlignment(Pos.TOP_LEFT);

        // 插画（无素材时回退占位文本）
        Image image = SpriteLoader.load(pokemon.getName());
        Node portrait;
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(PORTRAIT_HEIGHT);
            view.setFitHeight(PORTRAIT_HEIGHT);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            portrait = view;
        } else {
            Label fallback = new Label("（暂无立绘）");
            fallback.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #999;");
            portrait = fallback;
        }
        StackPane portraitBox = new StackPane(portrait);
        portraitBox.setMinHeight(PORTRAIT_HEIGHT);
        portraitBox.getStyleClass().add("detail-portrait");

        Label name = new Label(pokemon.getName());
        name.setStyle(YH + "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #222;");

        HBox types = new HBox(4);
        for (ElementType type : pokemon.getSpecies().getTypes()) {
            types.getChildren().add(chip(type.getDisplayName(), type.getColorCode()));
        }
        types.setAlignment(Pos.CENTER_LEFT);

        Label level = new Label("等级　Lv." + pokemon.getLevel());
        level.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #222;");
        Label status = new Label("状态　" + statusText(pokemon));
        status.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: " + statusColor(pokemon) + ";");

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

        VBox moves = new VBox(3);
        if (pokemon.getMoveSlots().isEmpty()) {
            Label none = new Label("尚未携带技能。");
            none.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #888;");
            moves.getChildren().add(none);
        } else {
            for (MoveSlot slot : pokemon.getMoveSlots()) {
                moves.getChildren().add(moveRow(slot));
            }
        }

        HeldItem held = pokemon.getHeldItem();
        Label equipment = new Label(held == null ? "未穿戴" : held.getName() + " — " + held.getDescription());
        equipment.setWrapText(true);
        equipment.setMaxWidth(Double.MAX_VALUE);
        equipment.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: "
                + (held == null ? "#888" : "#2a6e2a") + ";");

        detailBox.getChildren().addAll(portraitBox, name, types, level, status, hp, hpBar, exp, expBar,
                divider(), sectionLabel("技能"), moves, divider(), sectionLabel("装备"), equipment);
    }

    /** 技能单行：属性徽章 + 技能名（右端威力；变化类技能威力显示 --）。 */
    private static HBox moveRow(MoveSlot slot) {
        Label name = new Label(slot.getMove().getName());
        name.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #333;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label power = new Label("威力 " + (slot.getMove().getPower() <= 0 ? "--" : slot.getMove().getPower()));
        power.setStyle(YH + "-fx-font-size: 10px; -fx-text-fill: #666;");
        HBox row = new HBox(5, chip(slot.getMove().getType().getDisplayName(),
                slot.getMove().getType().getColorCode()), name, spacer, power);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** 中栏内容：道具简要信息（插图 → 名称×数量 → 功能表述）。 */
    private void showItemDetail(ItemStack stack) {
        detailBox.getChildren().clear();
        detailBox.setAlignment(Pos.TOP_CENTER);
        Item item = stack.getItem();

        StackPane iconBox = new StackPane(itemIcon(item.getName(), 56));
        iconBox.setMinHeight(56);

        Label title = new Label(item.getName() + " ×" + stack.getCount());
        title.setStyle(YH + "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #222;");

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

    private static String bagRowStyle(boolean hover) {
        return "-fx-background-color: "
                + (hover ? "rgba(255, 255, 255, 0.92)" : "rgba(255, 255, 255, 0.66)") + ";"
                + " -fx-background-radius: 6; -fx-border-color: " + (hover ? "#1565C0" : "#c9c9c9") + ";"
                + " -fx-border-width: 1; -fx-border-radius: 6; -fx-padding: 2 6;";
    }

    // ------------------------------------------------------------------
    // 下部分：三个入口按钮
    // ------------------------------------------------------------------

    private HBox buildActionBar() {
        // 主入口：肉鸽层内事件
        Button rogue = new Button("进入层内事件");
        rogue.setStyle(YH + "-fx-font-size: 15px; -fx-padding: 10 18; -fx-cursor: hand;");
        rogue.setOnAction(e -> actions.onStartRogueFloor());

        Button save = new Button("保存游戏");
        save.setStyle(YH + "-fx-font-size: 13px; -fx-padding: 8 14; -fx-cursor: hand;");
        save.setOnAction(e -> actions.onSaveGame());

        Button load = new Button("读取存档");
        load.setStyle(YH + "-fx-font-size: 13px; -fx-padding: 8 14; -fx-cursor: hand;");
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
        label.setStyle(YH + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #333;");
        return label;
    }

    private static Region divider() {
        Region line = new Region();
        line.setPrefHeight(1);
        line.setMinHeight(1);
        line.setMaxHeight(1);
        line.setStyle("-fx-background-color: #cfcfcf;");
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
