package org.example.view;

import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.model.ElementType;
import org.example.model.HeldItem;
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
 * 精灵详情页：由主菜单点击精灵名进入（替代原弹窗，改为场景切换）。
 *
 * <p>三栏布局：左侧为玩家队伍列表（点击切换查看）；中间为精灵立绘（名称、属性徽章与先发标记）；
 * 右侧为详细信息卡（图鉴描述 / 性格状态 / HP / EXP、六项能力值对照、技能栏与装备槽）。</p>
 *
 * <p>保留装备穿脱能力：穿戴 / 脱下直接作用于玩家数据（后续战斗生效），
 * 「返回」重建主菜单即可看到最新状态。</p>
 *
 * <p>ⓘ 页面仅保证基础功能可用，美术风格由后续美工统一调整。</p>
 */
public final class PokemonDetailView {

    private static final String YH = "-fx-font-family: 'Microsoft YaHei'; ";

    /** 卡片统一样式（半透明白底 + 浅灰描边，与全局风格一致）。 */
    private static final String CARD_STYLE = "-fx-background-color: rgba(255, 255, 255, 0.72);"
            + "-fx-background-radius: 10; -fx-border-color: #c9c9c9; -fx-border-radius: 10; -fx-padding: 8 10;";

    /** 小按钮统一样式。 */
    private static final String SMALL_BUTTON_STYLE = YH + "-fx-font-size: 11px;";

    /** 「换下」按钮质感样式：红底纵向渐变 + 深色描边 + 轻微投影（悬停变亮、阴影加深）。 */
    private static final String SWAP_BUTTON_STYLE = YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white;"
            + " -fx-background-color: linear-gradient(to bottom, #e8746a, #c0392b);"
            + " -fx-background-radius: 6; -fx-border-color: #8e2a1f; -fx-border-width: 1; -fx-border-radius: 6;"
            + " -fx-padding: 2 10; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.35), 4, 0.3, 0, 2);";
    private static final String SWAP_BUTTON_HOVER = YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white;"
            + " -fx-background-color: linear-gradient(to bottom, #f0837a, #d04a3c);"
            + " -fx-background-radius: 6; -fx-border-color: #8e2a1f; -fx-border-width: 1; -fx-border-radius: 6;"
            + " -fx-padding: 2 10; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.45), 6, 0.3, 0, 2);";

    private final Player player;
    private final int initialIndex;
    private final String mapBackground;
    private final Runnable onBack;

    private ListView<Pokemon> partyList;
    private ImageView sprite;
    private Label spriteFallback;
    private Label nameLabel;
    private HBox chipsRow;
    /** 右侧滚动内容容器（切换精灵时整体重建）。 */
    private VBox detailBox;
    /** 待换上的技能（非 null 时处于「选择要换下的槽位」状态，出战技能行临时显示「换下」按钮）。 */
    private org.example.model.Move pendingSwapMove;

    public PokemonDetailView(Player player, int initialIndex, String mapBackground, Runnable onBack) {
        this.player = player;
        this.initialIndex = initialIndex;
        this.mapBackground = mapBackground;
        this.onBack = onBack;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        ImageBackgrounds.apply(root, mapBackground);
        root.setPadding(new Insets(10));
        root.setTop(buildHeader());
        root.setCenter(buildContent());
        root.setBottom(buildBottomBar());

        // 默认选中进入时点击的精灵；之后点击左侧列表即切换展示
        partyList.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> {
            pendingSwapMove = null; // 切换精灵时退出换技能状态
            if (selected != null) {
                renderDetail(selected);
            }
        });
        if (player.getPartySize() > 0) {
            int index = Math.max(0, Math.min(initialIndex, player.getPartySize() - 1));
            partyList.getSelectionModel().select(index);
        }
        return UiScale.scene(root);
    }

    private Parent buildHeader() {
        Label title = new Label("宝可梦详情 · 训练家 " + player.getName());
        title.setStyle(YH + "-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #222;");
        Label hint = new Label("点击左侧列表切换精灵；装备穿脱立即生效，「返回」后主菜单同步刷新。");
        hint.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #555;");
        VBox box = new VBox(2, title, hint);
        box.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6); -fx-background-radius: 10;"
                + " -fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 10;"
                + " -fx-padding: 8 14 10 14;");
        return box;
    }

    private Parent buildContent() {
        ScrollPane detailScroll = buildDetailColumn();
        HBox.setHgrow(detailScroll, Priority.ALWAYS);
        HBox content = new HBox(12, buildPartyColumn(), buildSpriteColumn(), detailScroll);
        content.setAlignment(Pos.CENTER);
        return content;
    }

    /** 左栏：玩家队伍列表（点击切换查看的精灵）。 */
    private VBox buildPartyColumn() {
        Label caption = new Label("我的队伍（" + player.getPartySize() + "）");
        caption.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #333;");
        partyList = new ListView<>();
        partyList.setPrefSize(152, 282);
        partyList.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-background-color: rgba(255, 255, 255, 0.85);"
                + " -fx-background-radius: 8; -fx-border-color: #c9c9c9; -fx-border-radius: 8;");
        partyList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Pokemon item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                boolean active = item == player.getActive();
                Label title = new Label((active ? "★ " : "") + item.getName());
                title.setStyle(YH + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
                Label sub = new Label("Lv." + item.getLevel() + "  " + (item.isFainted()
                        ? "已倒下" : "HP " + item.getCurrentHp() + "/" + item.getMaxHp()));
                sub.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: "
                        + (item.isFainted() ? "#aa2222" : "#666") + ";");
                setText(null);
                setGraphic(new VBox(1, title, sub));
            }
        });
        partyList.getItems().setAll(player.getParty());
        VBox box = new VBox(6, caption, partyList);
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    /** 中栏：精灵立绘 + 名称 + 属性/状态徽章。 */
    private VBox buildSpriteColumn() {
        sprite = new ImageView();
        sprite.setPreserveRatio(true);
        sprite.setSmooth(true);
        sprite.setFitWidth(148);
        sprite.setFitHeight(148);
        sprite.setEffect(new DropShadow(12, 0, 4, Color.rgb(0, 0, 0, 0.3)));

        spriteFallback = new Label("（暂无立绘）");
        spriteFallback.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #999;");
        spriteFallback.setVisible(false);
        spriteFallback.setManaged(false);

        StackPane spriteBox = new StackPane(spriteFallback, sprite);
        spriteBox.setMinSize(160, 160);
        spriteBox.setPrefSize(160, 160);
        spriteBox.setMaxSize(160, 160);
        spriteBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.55);"
                + " -fx-background-radius: 16; -fx-border-color: #c9c9c9; -fx-border-radius: 16;");

        nameLabel = new Label();
        nameLabel.setStyle(YH + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222;");
        chipsRow = new HBox(5);
        chipsRow.setAlignment(Pos.CENTER);

        VBox box = new VBox(8, spriteBox, nameLabel, chipsRow);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** 右栏：可滚动详情区（信息 / 能力值 / 技能 / 装备，切换精灵时重建）。 */
    private ScrollPane buildDetailColumn() {
        detailBox = new VBox(8);
        ScrollPane scroll = new ScrollPane(detailBox);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(252, 282);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scroll.skinProperty().addListener((o, oldSkin, skin) -> {
            if (skin != null) makeViewportTransparent(scroll);
        });
        return scroll;
    }

    private HBox buildBottomBar() {
        Button back = new Button("返回主菜单");
        back.setStyle(YH + "-fx-font-size: 13px; -fx-padding: 7 16;");
        back.setOnAction(e -> onBack.run());
        HBox bar = new HBox(back);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(8, 0, 0, 0));
        return bar;
    }

    // ------------------------------------------------------------------
    // 详情渲染：切换精灵 / 装备穿脱后重建三个区域
    // ------------------------------------------------------------------

    /** 展示指定精灵：中栏立绘徽章 + 右栏详情卡。 */
    private void renderDetail(Pokemon pokemon) {
        Image image = SpriteLoader.load(pokemon.getName());
        boolean hasSprite = image != null;
        sprite.setImage(image);
        sprite.setVisible(hasSprite);
        sprite.setManaged(hasSprite);
        spriteFallback.setVisible(!hasSprite);
        spriteFallback.setManaged(!hasSprite);

        nameLabel.setText(pokemon.getName() + "  Lv." + pokemon.getLevel());

        chipsRow.getChildren().clear();
        if (pokemon.isFainted()) {
            chipsRow.getChildren().add(chip("已倒下", "#C0392B"));
        }
        if (pokemon == player.getActive()) {
            chipsRow.getChildren().add(chip("★ 先发", "#DAA520"));
        }
        for (ElementType type : pokemon.getSpecies().getTypes()) {
            chipsRow.getChildren().add(chip(type.getDisplayName(), type.getColorCode()));
        }
        if (pokemon.getStatus() != StatusCondition.NONE) {
            chipsRow.getChildren().add(chip(pokemon.getStatus().getDisplayName()
                    + (pokemon.isConfused() ? "+混乱" : ""), "#8E44AD"));
        } else if (pokemon.isConfused()) {
            chipsRow.getChildren().add(chip("混乱", "#8E44AD"));
        }

        detailBox.getChildren().setAll(buildInfoCard(pokemon), buildStatsCard(pokemon),
                buildMovesCard(pokemon), buildEquipmentCard(pokemon));
    }

    /** 信息卡：图鉴描述、性格/状态、HP、EXP。 */
    private VBox buildInfoCard(Pokemon pokemon) {
        VBox card = new VBox(4);
        card.setStyle(CARD_STYLE);
        card.getChildren().add(cardTitle("信息"));

        // 图鉴描述来自宝可梦库（battle 模块种族模型不含该字段）；种族 id 与库一致，无数据时省略
        org.example.pokemon.domain.Species library =
                org.example.pokemon.infrastructure.GameData.instance()
                        .getSpecies(pokemon.getSpecies().getId()).orElse(null);
        if (library != null && library.getDescription() != null && !library.getDescription().isBlank()) {
            Label description = new Label(library.getDescription());
            description.setWrapText(true);
            description.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #306090;");
            card.getChildren().add(description);
        }

        Label info = new Label("性格：" + pokemon.getNature().getName() + "　状态：" + statusText(pokemon));
        info.setWrapText(true);
        info.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #222;");
        card.getChildren().add(info);

        double hpRatio = pokemon.getMaxHp() <= 0 ? 0 : (double) pokemon.getCurrentHp() / pokemon.getMaxHp();
        String hpColor = hpRatio > 0.5 ? "#4CAF50" : (hpRatio > 0.2 ? "#FF9800" : "#E53935");
        Label hp = new Label("HP　" + pokemon.getCurrentHp() + " / " + pokemon.getMaxHp());
        hp.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #222;");
        card.getChildren().add(hp);
        card.getChildren().add(bar(hpRatio, hpColor, 120, 8));

        double expRatio;
        String expText;
        if (pokemon.expToNextLevel() <= 0) {
            expText = "EXP　MAX（满级）";
            expRatio = 1;
        } else {
            expText = "EXP　" + pokemon.getExp() + " / " + pokemon.expToNextLevel();
            expRatio = Math.min(1, (double) pokemon.getExp() / pokemon.expToNextLevel());
        }
        Label exp = new Label(expText);
        exp.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #222;");
        card.getChildren().add(exp);
        card.getChildren().add(bar(expRatio, "#4F8FD9", 120, 8));
        return card;
    }

    /** 能力值卡：六项能力值（当前值 + 比例条）与种族值对照。 */
    private VBox buildStatsCard(Pokemon pokemon) {
        VBox card = new VBox(4);
        card.setStyle(CARD_STYLE);
        card.getChildren().add(cardTitle("能力值（左：当前　右：种族）"));

        Stats actual = pokemon.getStats();
        Stats base = pokemon.getSpecies().getBaseStats();
        int max = Math.max(1, Math.max(actual.getHp(), Math.max(actual.getAttack(),
                Math.max(Math.max(actual.getDefense(), actual.getSpAttack()),
                        Math.max(actual.getSpDefense(), actual.getSpeed())))));
        card.getChildren().add(statRow("HP", actual.getHp(), base.getHp(), max));
        card.getChildren().add(statRow("物攻", actual.getAttack(), base.getAttack(), max));
        card.getChildren().add(statRow("物防", actual.getDefense(), base.getDefense(), max));
        card.getChildren().add(statRow("特攻", actual.getSpAttack(), base.getSpAttack(), max));
        card.getChildren().add(statRow("特防", actual.getSpDefense(), base.getSpDefense(), max));
        card.getChildren().add(statRow("速度", actual.getSpeed(), base.getSpeed(), max));
        return card;
    }

    /** 技能卡：出战 4 槽 + 技能库（库中未出战的技能可一键换上 / 替换出战槽）。 */
    private VBox buildMovesCard(Pokemon pokemon) {
        VBox card = new VBox(6);
        card.setStyle(CARD_STYLE);
        card.getChildren().add(cardTitle("出战技能（" + pokemon.getMoveSlots().size() + "/4）"));
        if (pendingSwapMove != null) {
            Label hint = new Label("即将换上【" + pendingSwapMove.getName()
                    + "】，点击某个出战技能右侧的「换下」完成互换（或在技能库点「取消」）。");
            hint.setWrapText(true);
            hint.setMaxWidth(Double.MAX_VALUE);
            hint.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #b35900;"
                    + " -fx-background-color: rgba(255, 196, 108, 0.35); -fx-background-radius: 6; -fx-padding: 3 6;");
            card.getChildren().add(hint);
        }
        if (pokemon.getMoveSlots().isEmpty()) {
            Label none = new Label("尚未携带任何出战技能。");
            none.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #888;");
            card.getChildren().add(none);
        } else {
            for (int i = 0; i < pokemon.getMoveSlots().size(); i++) {
                MoveSlot slot = pokemon.getMoveSlots().get(i);
                final int slotIndex = i;
                Runnable onSwap = pendingSwapMove == null ? null : () -> swapWithPool(pokemon, slotIndex);
                card.getChildren().add(moveRow(slot, onSwap));
            }
        }
        card.getChildren().add(sectionDivider());
        Label poolTitle = cardTitle("技能库（" + pokemon.getKnownMoves().size() + "）");
        // 浅灰底横幅：与上方分隔线共同强化「技能库」分区边界
        poolTitle.setStyle(YH + "-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #333;"
                + " -fx-background-color: rgba(0, 0, 0, 0.07); -fx-background-radius: 6; -fx-padding: 2 8;");
        card.getChildren().add(poolTitle);
        for (org.example.model.Move known : pokemon.getKnownMoves()) {
            card.getChildren().add(poolRow(pokemon, known));
        }
        return card;
    }

    /** 技能库单行：已出战标注「出战中」；未出战提供「换上」按钮。 */
    private HBox poolRow(Pokemon pokemon, org.example.model.Move move) {
        Label info = new Label(move.getName()
                + " · " + move.getType().getDisplayName()
                + " · 威力 " + (move.getPower() <= 0 ? "--" : move.getPower()));
        info.setWrapText(true);
        info.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(info, Priority.ALWAYS);
        info.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #333;");

        Button action = new Button();
        action.setStyle(SMALL_BUTTON_STYLE);
        if (pokemon.hasMove(move)) {
            action.setText("出战中");
            action.setDisable(true);
        } else if (move == pendingSwapMove) {
            action.setText("取消");
            action.setOnAction(e -> {
                pendingSwapMove = null;
                renderDetail(pokemon);
            });
        } else {
            action.setText("换上");
            action.setOnAction(e -> equipFromPool(pokemon, move));
        }
        HBox row = new HBox(8, info, action);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * 把技能库中的技能装上出战槽：有空槽直接携带；满 4 招时进入「选择要换下的槽位」状态，
     * 出战技能行临时出现「换下」按钮，点击即与该技能互换（不弹窗）。
     */
    private void equipFromPool(Pokemon pokemon, org.example.model.Move move) {
        if (pokemon.hasMove(move)) {
            return;
        }
        if (!pokemon.moveSlotsFull()) {
            pokemon.learnMove(move);
            pendingSwapMove = null;
            renderDetail(pokemon);
            return;
        }
        pendingSwapMove = move;
        renderDetail(pokemon);
    }

    /** 用待换技能替换指定出战槽：被换下的技能仍保留在技能库中，之后可再换回。 */
    private void swapWithPool(Pokemon pokemon, int slotIndex) {
        if (pendingSwapMove == null) {
            return;
        }
        pokemon.swapBattleMove(slotIndex, pendingSwapMove);
        pendingSwapMove = null;
        renderDetail(pokemon);
    }

    /** 装备卡：当前装备 + 玩家装备库（穿戴 / 换过来 / 脱下，立即生效）。 */
    private VBox buildEquipmentCard(Pokemon pokemon) {
        VBox card = new VBox(5);
        card.setStyle(CARD_STYLE);
        card.getChildren().add(cardTitle("装备"));

        HeldItem current = pokemon.getHeldItem();
        if (current == null) {
            Label empty = new Label("当前未穿戴装备。");
            empty.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #888;");
            card.getChildren().add(empty);
        } else {
            Label worn = new Label("已穿戴：" + current.getName() + " — " + current.getDescription());
            worn.setWrapText(true);
            worn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(worn, Priority.ALWAYS);
            worn.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #2a6e2a;");
            Button unequip = new Button("脱下");
            unequip.setStyle(SMALL_BUTTON_STYLE);
            unequip.setOnAction(e -> {
                player.unequip(pokemon);
                renderDetail(pokemon);
            });
            HBox wornRow = new HBox(8, worn, unequip);
            wornRow.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(wornRow);
        }

        List<HeldItem> owned = player.getEquipment();
        if (owned.isEmpty()) {
            Label none = new Label("装备库为空：可在肉鸽楼层选择「装备补给」事件获得装备。");
            none.setWrapText(true);
            none.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #888;");
            card.getChildren().add(none);
        } else {
            for (HeldItem item : owned) {
                card.getChildren().add(equipmentRow(pokemon, item));
            }
        }
        return card;
    }

    // ------------------------------------------------------------------
    // 组件构建
    // ------------------------------------------------------------------

    /** 装备库单行：名称 + 效果说明 + 穿戴状态按钮。 */
    private HBox equipmentRow(Pokemon pokemon, HeldItem item) {
        Label info = new Label(item.getName() + "：" + item.getDescription());
        info.setWrapText(true);
        info.setMaxWidth(Double.MAX_VALUE);
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
        action.setStyle(SMALL_BUTTON_STYLE);
        if (holder == pokemon) {
            action.setText("已穿戴");
            action.setDisable(true);
        } else if (holder != null) {
            action.setText("换过来");
            action.setOnAction(e -> {
                player.equip(pokemon, item);
                renderDetail(pokemon);
            });
        } else {
            action.setText("穿戴");
            action.setOnAction(e -> {
                player.equip(pokemon, item);
                renderDetail(pokemon);
            });
        }
        HBox row = new HBox(8, info, action);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** 单项能力值行：名称 + 当前值 + 比例条（相对六项最大值）+ 种族值。 */
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
        HBox row = new HBox(6, nameLabel, valueLabel, bar((double) value / max, "#5E9ED6", 82, 6), baseLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * 技能单行：名称 + PP（第一行），属性徽章 + 分类/威力/命中（第二行）。
     * {@code onSwap} 非 null 时，第二行（技能描述行）右侧出现「换下」按钮（仅换技能状态临时显示）。
     */
    private static VBox moveRow(MoveSlot slot, Runnable onSwap) {
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
        HBox line2 = new HBox(6, chip(slot.getMove().getType().getDisplayName(),
                slot.getMove().getType().getColorCode()), detail);
        if (onSwap != null) {
            Region push = new Region();
            HBox.setHgrow(push, Priority.ALWAYS); // 弹性占位：把「换下」按钮推到行最右端
            line2.getChildren().add(push);
            Button swap = new Button("换下");
            swap.setStyle(SWAP_BUTTON_STYLE);
            swap.setOnMouseEntered(e -> swap.setStyle(SWAP_BUTTON_HOVER));
            swap.setOnMouseExited(e -> swap.setStyle(SWAP_BUTTON_STYLE));
            swap.setOnAction(e -> onSwap.run());
            line2.getChildren().add(swap);
        }
        line2.setAlignment(Pos.CENTER_LEFT);
        return new VBox(2, line1, line2);
    }

    /** 卡片标题（统一粗体小标题）。 */
    private static Label cardTitle(String text) {
        Label title = new Label(text);
        title.setStyle(YH + "-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #000;");
        return title;
    }

    /**
     * 区域分隔线：出战技能与技能库之间的可视界限。
     * 两端渐隐的加粗横线（2px），颜色明显深于卡片描边，视觉上把两个区域切开。
     */
    private static HBox sectionDivider() {
        Region line = new Region();
        line.setPrefHeight(2);
        line.setMaxHeight(2);
        line.setMinHeight(2);
        line.setStyle("-fx-background-color: linear-gradient(to right, transparent, #8a8a8a 12%, #8a8a8a 88%, transparent);"
                + " -fx-background-radius: 1;");
        HBox box = new HBox(line);
        box.setPadding(new Insets(2, 0, 2, 0));
        return box;
    }

    /** 彩色徽章（属性 / 状态 / 先发标记）。 */
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
