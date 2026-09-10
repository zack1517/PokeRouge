package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.model.ElementType;
import org.example.model.HeldItem;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.util.SpriteLoader;

import java.util.List;

/**
 * 精灵图鉴弹窗：点击队伍面板中的精灵名打开。
 *
 * <p>展示立绘、图鉴信息（分类/描述/属性/性格/状态）、六项能力值（实际值与种族值对照）、
 * 技能栏（4 招），并提供装备槽：查看当前装备、从玩家装备库穿戴/脱下。
 * 穿脱后通过 {@code onChanged} 回调通知控制器重建主菜单。</p>
 */
public final class PokemonDetailDialog {

    private static final String YH = "-fx-font-family: 'Microsoft YaHei'; ";

    private final Pokemon pokemon;
    private final Player player;
    private final Runnable onChanged;
    /** 装备区容器（穿脱后重建内容）。 */
    private final VBox equipmentBox = new VBox(6);

    public PokemonDetailDialog(Pokemon pokemon, Player player, Runnable onChanged) {
        this.pokemon = pokemon;
        this.player = player;
        this.onChanged = onChanged;
    }

    public void show() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(pokemon.getName() + " · 图鉴");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(430);
        scroll.setPrefViewportWidth(420);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setStyle("-fx-background-color: rgba(250, 250, 250, 0.97);");
        dialog.showAndWait();
    }

    private VBox buildContent() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().add(buildHeader());
        root.getChildren().add(buildInfoCard());
        root.getChildren().add(buildStatsCard());
        root.getChildren().add(buildMovesCard());
        rebuildEquipmentBox();
        root.getChildren().add(equipmentBox);
        return root;
    }

    /** 顶部：立绘 + 名称/等级/分类/图鉴描述。 */
    private HBox buildHeader() {
        ImageView sprite = new ImageView();
        sprite.setPreserveRatio(true);
        sprite.setSmooth(true);
        sprite.setFitWidth(96);
        sprite.setFitHeight(96);
        Image image = SpriteLoader.load(pokemon.getName());
        if (image != null) {
            sprite.setImage(image);
        } else {
            sprite.setImage(null);
            sprite.setVisible(false);
            sprite.setManaged(false);
        }

        Species species = pokemon.getSpecies();
        Label name = new Label(pokemon.getName() + "  Lv." + pokemon.getLevel());
        name.setStyle(YH + "-fx-font-size: 18px; -fx-font-weight: bold;");
        // 分类/图鉴描述来自宝可梦库（battle 模块种族模型不含这两个字段）；种族 id 与库一致，无数据时留空
        org.example.pokemon.domain.Species librarySpecies =
                org.example.pokemon.infrastructure.GameData.instance().getSpecies(species.getId()).orElse(null);
        String categoryText = librarySpecies != null && librarySpecies.getCategory() != null
                ? librarySpecies.getCategory() : "";
        String descriptionText = librarySpecies != null && librarySpecies.getDescription() != null
                ? librarySpecies.getDescription() : "";
        Label category = new Label(categoryText);
        category.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #555;");
        category.setVisible(!categoryText.isBlank());
        category.setManaged(!categoryText.isBlank());
        Label description = new Label(descriptionText);
        description.setWrapText(true);
        description.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #306090;");
        description.setVisible(!descriptionText.isBlank());
        description.setManaged(!descriptionText.isBlank());
        Label noSpriteFallback = new Label(pokemon.getName() + "\n（暂无立绘）");
        noSpriteFallback.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #999; -fx-alignment: center;");
        noSpriteFallback.setVisible(image == null);
        noSpriteFallback.setManaged(image == null);
        javafx.scene.layout.StackPane spriteBox = new javafx.scene.layout.StackPane(noSpriteFallback, sprite);
        spriteBox.setMinSize(96, 96);

        VBox text = new VBox(3, name, category, description);
        HBox header = new HBox(12, spriteBox, text);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /** 信息卡：属性/性格/异常状态/HP/EXP。 */
    private VBox buildInfoCard() {
        Species species = pokemon.getSpecies();
        String types = String.join("/", species.getTypes().stream()
                .map(ElementType::getDisplayName).toList());
        String status = pokemon.getStatus() == org.example.model.StatusCondition.NONE
                ? (pokemon.isConfused() ? "混乱" : "无")
                : pokemon.getStatus().getDisplayName() + (pokemon.isConfused() ? "+混乱" : "");
        Label info = new Label("属性：" + types + "　性格：" + pokemon.getNature().getName()
                + "　状态：" + status);
        info.setWrapText(true);
        info.setStyle(YH + "-fx-font-size: 12px;");
        Label hp = new Label("HP：" + pokemon.getCurrentHp() + " / " + pokemon.getMaxHp());
        hp.setStyle(YH + "-fx-font-size: 12px;");
        String expText = pokemon.expToNextLevel() <= 0
                ? "EXP：MAX（满级）"
                : "EXP：" + pokemon.getExp() + " / " + pokemon.expToNextLevel();
        Label exp = new Label(expText);
        exp.setStyle(YH + "-fx-font-size: 12px;");
        return card("信息", info, hp, exp);
    }

    /** 数值卡：六项能力值（实际值 vs 种族值）。 */
    private VBox buildStatsCard() {
        Stats actual = pokemon.getStats();
        Stats base = pokemon.getSpecies().getBaseStats();
        Label title = new Label("能力值（左：当前值　右：种族值）");
        title.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #666;");
        Label row = new Label(String.format(
                "HP　 %3d / %d\n物攻 %3d / %d\n物防 %3d / %d\n特攻 %3d / %d\n特防 %3d / %d\n速度 %3d / %d",
                actual.getHp(), base.getHp(),
                actual.getAttack(), base.getAttack(),
                actual.getDefense(), base.getDefense(),
                actual.getSpAttack(), base.getSpAttack(),
                actual.getSpDefense(), base.getSpDefense(),
                actual.getSpeed(), base.getSpeed()));
        row.setStyle(YH + "-fx-font-size: 12px;");
        return card("能力值", title, row);
    }

    /** 技能卡：4 招（名称/属性/分类/威力/命中/PP）。 */
    private VBox buildMovesCard() {
        VBox box = cardEmpty();
        Label title = new Label("技能");
        title.setStyle(YH + "-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #000;");
        box.getChildren().add(title);
        for (MoveSlot slot : pokemon.getMoveSlots()) {
            Label line = new Label(String.format("· %s（%s %s）威力 %d 命中 %s PP %d/%d",
                    slot.getMove().getName(),
                    slot.getMove().getType().getDisplayName(),
                    slot.getMove().getCategory() == org.example.model.MoveCategory.PHYSICAL ? "物理"
                            : slot.getMove().getCategory() == org.example.model.MoveCategory.SPECIAL ? "特殊" : "变化",
                    slot.getMove().getPower(),
                    slot.getMove().getAccuracy() < 0 ? "--" : String.valueOf(slot.getMove().getAccuracy()),
                    slot.getCurrentPp(), slot.getMove().getMaxPp()));
            line.setWrapText(true);
            line.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #000;");
            box.getChildren().add(line);
        }
        return box;
    }

    /** 装备区：当前装备 + 脱下；玩家装备库列表 + 穿戴。 */
    private void rebuildEquipmentBox() {
        equipmentBox.getChildren().clear();
        Label title = new Label("装备槽");
        title.setStyle(YH + "-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #000;");
        equipmentBox.getChildren().add(title);

        HeldItem current = pokemon.getHeldItem();
        if (current == null) {
            Label empty = new Label("当前未穿戴装备。");
            empty.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #888;");
            equipmentBox.getChildren().add(empty);
        } else {
            Label worn = new Label("已穿戴：" + current.getName() + " — " + current.getDescription());
            worn.setWrapText(true);
            worn.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #2a6e2a;");
            Button unequip = new Button("脱下");
            unequip.setStyle(YH + "-fx-font-size: 11px;");
            unequip.setOnAction(e -> {
                player.unequip(pokemon);
                rebuildEquipmentBox();
                onChanged.run();
            });
            equipmentBox.getChildren().add(new HBox(8, worn, unequip));
        }

        List<HeldItem> owned = player.getEquipment();
        if (owned.isEmpty()) {
            Label none = new Label("装备库为空：可在肉鸽楼层选择「装备补给」事件获得装备。");
            none.setWrapText(true);
            none.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #888;");
            equipmentBox.getChildren().add(none);
        } else {
            for (HeldItem item : owned) {
                equipmentBox.getChildren().add(equipmentRow(item));
            }
        }
        equipmentBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6);"
                + "-fx-border-color: #bbb; -fx-border-radius: 6; -fx-padding: 8; -fx-background-radius: 6;");
    }

    /** 装备库单行：名称 + 效果说明 + 穿戴状态按钮。 */
    private HBox equipmentRow(HeldItem item) {
        Label info = new Label(item.getName() + "：" + item.getDescription());
        info.setWrapText(true);
        info.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #333;");

        Pokemon holder = null;
        for (Pokemon p : player.getParty()) {
            if (p.getHeldItem() == item) {
                holder = p;
                break;
            }
        }
        Button action = new Button();
        action.setStyle(YH + "-fx-font-size: 11px;");
        if (holder == pokemon) {
            action.setText("已穿戴");
            action.setDisable(true);
        } else if (holder != null) {
            action.setText("换过来");
            action.setOnAction(e -> {
                player.equip(pokemon, item);
                rebuildEquipmentBox();
                onChanged.run();
            });
        } else {
            action.setText("穿戴");
            action.setOnAction(e -> {
                player.equip(pokemon, item);
                rebuildEquipmentBox();
                onChanged.run();
            });
        }
        HBox row = new HBox(8, info, action);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** 信息/数值卡通用底。 */
    private VBox card(String titleText, Label... lines) {
        VBox box = cardEmpty();
        Label title = new Label(titleText);
        title.setStyle(YH + "-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #000;");
        box.getChildren().add(title);
        for (Label line : lines) {
            line.setWrapText(true);
            box.getChildren().add(line);
        }
        return box;
    }

    private VBox cardEmpty() {
        VBox box = new VBox(4);
        box.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6);"
                + "-fx-border-color: #bbb; -fx-border-radius: 6; -fx-padding: 8; -fx-background-radius: 6;");
        return box;
    }
}
