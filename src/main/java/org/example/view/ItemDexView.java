package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.example.model.HeldItem;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

import java.util.List;

/**
 * 道具图鉴页（主菜单与启动页均有入口）：一页看全 93 件道具（16 件消耗品 + 77 件装备）。
 *
 * <p>每张卡片给出名称、【道具】/【装备】标记、效果说明、是否在商店出售与基础价，
 * 并用文字标注「已拥有 / 未拥有」；装备额外标注穿戴者 ——
 * 全量列出、不做收集解锁，方便查看「还有哪些没拿到、商店里能买什么」。</p>
 *
 * <p><b>可直接操作装备</b>：已拥有且未穿戴的装备，卡片刻出「穿给 [队伍成员] 」下拉与「穿戴」按钮；
 * 已被某只精灵穿戴的，给出「[名字] 持有」与「脱下」。穿脱后本页即时重建，
 * 与详情页（{@code PokemonDetailView}）共用 {@link Player#equip} / {@link Player#unequip}，
 * 因此全队唯一穿戴的约束由模型层保证。消耗品为纯展示（战斗内才可使用）。</p>
 *
 * <p>数据来自 {@link ItemDexData}（商店商品目录 + 玩家持有情况），本类只负责展示与交互。
 * 启动页打开时没有存档（{@code player == null}），此时全部条目按「未拥有」只读展示。</p>
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

    private static final String CARD_STYLE = "-fx-background-color: rgba(255,255,255,0.78);"
            + " -fx-padding: 10; -fx-background-radius: 8;";

    private final Player player;
    private final String background;
    private final Runnable onBack;
    private final String backLabel;

    private Filter filter = Filter.ALL;
    private Label summary;
    private VBox listBox;

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
        VBox root = new VBox(12);
        if (background != null && !background.isBlank()) {
            ImageBackgrounds.apply(root, background);
        }
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.TOP_LEFT);
        root.getChildren().addAll(buildHeader(), buildFilterBar(), buildList(), buildBackBar());

        refresh();
        return UiScale.scene(root);
    }

    private VBox buildHeader() {
        Label title = new Label("道具图鉴");
        title.setStyle(YH + "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #222;");

        summary = new Label();
        summary.setWrapText(true);
        summary.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #555;");

        VBox header = new VBox(2, title, summary);
        header.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6); -fx-background-radius: 10;"
                + "-fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 10;"
                + "-fx-padding: 8 14 10 14;");
        return header;
    }

    private HBox buildFilterBar() {
        ToggleGroup group = new ToggleGroup();
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        for (Filter option : Filter.values()) {
            ToggleButton button = new ToggleButton(option.label());
            button.setToggleGroup(group);
            button.setSelected(option == filter);
            button.setStyle(YH + "-fx-font-size: 12px; -fx-padding: 6 14;");
            button.setOnAction(e -> {
                // ToggleGroup 允许点击已选项取消选中，这里强制保持单选
                button.setSelected(true);
                filter = option;
                refresh();
            });
            bar.getChildren().add(button);
        }
        return bar;
    }

    private ScrollPane buildList() {
        listBox = new VBox(10);
        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private HBox buildBackBar() {
        Button back = new Button(backLabel);
        back.setStyle(YH + "-fx-font-size: 13px; -fx-padding: 8 14;");
        back.setOnAction(e -> onBack.run());
        HBox bar = new HBox(back);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /** 重建摘要与列表（穿脱装备后调用）。 */
    private void refresh() {
        List<ItemDexData.Entry> entries = ItemDexData.build(player);
        long owned = entries.stream().filter(ItemDexData.Entry::owned).count();
        long equipped = entries.stream().filter(ItemDexData.Entry::equipped).count();
        summary.setText("共 " + entries.size() + " 件（" + (entries.size() - countEquipment(entries))
                + " 件道具 + " + countEquipment(entries) + " 件装备）"
                + (player == null ? "" : "　已拥有 " + owned + " 件"
                        + (equipped == 0 ? "" : "，其中 " + equipped + " 件已穿戴"))
                + "　未拥有的道具与装备可随时在商店买到（第 1 段起即可能上架，货架格位随段数变多）");

        listBox.getChildren().clear();
        List<ItemDexData.Entry> shown = entries.stream().filter(this::matches).toList();
        if (shown.isEmpty()) {
            Label empty = new Label("该分类下暂无条目。");
            empty.setStyle(YH + "-fx-font-size: 13px; -fx-text-fill: #555;");
            listBox.getChildren().add(empty);
            return;
        }
        for (ItemDexData.Entry entry : shown) {
            listBox.getChildren().add(buildCard(entry));
        }
    }

    private static long countEquipment(List<ItemDexData.Entry> entries) {
        return entries.stream().filter(ItemDexData.Entry::equipment).count();
    }

    private boolean matches(ItemDexData.Entry entry) {
        return switch (filter) {
            case ALL -> true;
            case CONSUMABLE -> !entry.equipment();
            case EQUIPMENT -> entry.equipment();
            case OWNED -> entry.owned();
        };
    }

    private VBox buildCard(ItemDexData.Entry entry) {
        Label name = new Label((entry.equipment() ? "【装备】" : "【道具】") + entry.name());
        name.setStyle(YH + "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #222;");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Label state = new Label(ownedText(entry));
        state.setWrapText(true);
        state.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: "
                + (entry.owned() ? "#2e7d32;" : "#888;"));
        state.setMinWidth(Label.USE_PREF_SIZE);

        VBox card = new VBox(4, new HBox(8, name, state));
        card.setStyle(CARD_STYLE);

        if (!entry.description().isBlank()) {
            Label description = new Label(entry.description());
            description.setWrapText(true);
            description.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #444;");
            card.getChildren().add(description);
        }

        Label source = new Label(entry.sold()
                ? "商店出售 · 基础价 " + entry.basePrice() + " 金币（段数越靠后售价越高）"
                : "不售卖 · 剧情专属道具（击败火箭队首领必得）");
        source.setWrapText(true);
        source.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #777;");
        card.getChildren().add(source);

        HBox actions = buildActions(entry);
        if (actions != null) {
            card.getChildren().add(actions);
        }
        return card;
    }

    private static String ownedText(ItemDexData.Entry entry) {
        if (entry.equipment()) {
            if (entry.equipped()) {
                return "已拥有 · " + entry.holderName() + " 持有";
            }
            return entry.owned() ? "已拥有 · 未穿戴" : "未拥有";
        }
        return entry.owned() ? "已拥有 ×" + entry.ownedCount() : "未拥有";
    }

    /** 装备的穿脱操作行；消耗品与未拥有的装备返回提示行（或 {@code null}）。 */
    private HBox buildActions(ItemDexData.Entry entry) {
        if (!entry.equipment()) {
            return null;
        }
        if (player == null) {
            return hintRow("进入游戏后可在此直接穿戴 / 脱下装备。");
        }
        if (!entry.owned()) {
            return hintRow("可在商店购买（第 1 段起即可能上架），或在肉鸽「装备补给」事件中获得。");
        }

        Pokemon holder = holderOf(entry.id());
        if (holder != null) {
            Label worn = new Label(holder.getName() + " 持有");
            worn.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #2a6e2a;");
            Button unequip = new Button("脱下");
            unequip.setStyle(YH + "-fx-font-size: 11px;");
            unequip.setOnAction(e -> {
                player.unequip(holder);
                refresh();
            });
            HBox row = new HBox(8, worn, unequip);
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }
        if (player.getParty().isEmpty()) {
            return hintRow("队伍为空，暂无可穿戴的精灵。");
        }

        HeldItem item = equipmentOf(entry.id());
        ComboBox<Pokemon> target = new ComboBox<>();
        target.getItems().addAll(player.getParty());
        target.getSelectionModel().select(player.getActive());
        target.setConverter(new StringConverter<>() {
            @Override
            public String toString(Pokemon pokemon) {
                return pokemon == null ? "" : pokemon.getName() + " Lv." + pokemon.getLevel();
            }

            @Override
            public Pokemon fromString(String text) {
                return null;
            }
        });

        Button equip = new Button("穿戴");
        equip.setStyle(YH + "-fx-font-size: 11px;");
        equip.setDisable(item == null);
        equip.setOnAction(e -> {
            Pokemon selected = target.getSelectionModel().getSelectedItem();
            if (selected != null && item != null && player.equip(selected, item)) {
                refresh();
            }
        });

        Label label = new Label("穿给");
        label.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #555;");
        HBox row = new HBox(8, label, target, equip);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static HBox hintRow(String text) {
        Label hint = new Label(text);
        hint.setWrapText(true);
        hint.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #888;");
        return new HBox(hint);
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
