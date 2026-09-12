package org.example.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
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
import javafx.util.Duration;
import org.example.pokemon.domain.Pokemon;
import org.example.pokemon.domain.Species;
import org.example.pokemon.infrastructure.GameData;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;
import org.example.util.ImageBackgrounds;
import org.example.util.LogUtil;
import org.example.util.SpriteLoader;
import org.example.util.UiScale;

/**
 * 自定义战斗·队伍配置页（1 vs 1 / 2 vs 2 / 小队对战 / 自定义数量 四种模式共用）：
 * 从宝可梦库（CSV 数据源，{@link GameData#getAllSpecies()}）全部种族中挑选出战成员，
 * 成员一律满级（Lv.100）且 HP / PP 满状态入场。
 *
 * <p>出战数量由 {@code requiredCount} 决定：大于 0 时须恰好选满该数量才能开战
 * （1 vs 1 → 1、2 vs 2 → 2、自定义数量 → 玩家选定值）；为 0 时按小队对战规则
 * 「1~6 只任意」。队伍顺序即出战顺序，第一位为首发（可「设为首发」把选中成员移到首位）。</p>
 *
 * <p>「开始战斗」后由 {@code MainController} 转入真实战斗模块：与随机生成的同数量满级
 * 对手整队轮战，战斗中可通过「精灵」菜单换人。</p>
 *
 * <p>视觉：与商店页 / 内层子页 / 启动页统一——顶栏「返回胶囊 + 白描边深蓝字标题 +
 * 信息卡」、内容白底蓝环卡、黄→金胶囊按钮族，背景与模式选择页同款；
 * 样式集中在 {@code /css/custom-battle-setup.css}（大标题 / 胶囊 / 暗角复用 start-menu.css），
 * 本类只负责结构与接线。</p>
 */
public final class CustomBattleSetupView {

    /** 成员等级（统一满级）。 */
    private static final int MEMBER_LEVEL = 100;

    /** 队伍上限（与战斗契约一致：最多 6 只）。 */
    private static final int MAX_SQUAD = 6;

    /** 页面背景（与模式选择页 / 启动页同款主画面，进出本页保持连续感）。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_startpage.jpg";

    /** 共享样式表（胶囊按钮 / 标题 / 暗角 / .rogue-info 信息卡，与启动页同一份）。 */
    private static final String SHARED_STYLE_SHEET = "/css/start-menu.css";

    /** 本页样式表（面板 / 卡槽 / 按钮）。 */
    private static final String STYLE_SHEET = "/css/custom-battle-setup.css";

    /** 字体基串（行内样式统一前缀）。 */
    private static final String FONT = "-fx-font-family: 'Microsoft YaHei'; ";

    /** 候选列表小头像尺寸。 */
    private static final double CANDIDATE_ICON_SIZE = 24;

    /** 队伍卡槽精灵图尺寸。 */
    private static final double SLOT_ICON_SIZE = 40;

    /** 卡槽统一高度（槽内容按此收拢，避免被拉伸成大块空白）。 */
    private static final double SLOT_HEIGHT = 64;

    /** 页面标题（如「1 vs 1」「小队对战」「自定义数量（3）」）。 */
    private final String title;

    /** 出战数量：大于 0 须恰好选满该数量；为 0 表示 1~6 任意（小队对战）。 */
    private final int requiredCount;

    private final Runnable onBack;
    private final Consumer<List<Pokemon>> onStartBattle;
    private final PokemonService service = new PokemonServiceImpl();

    /** 已选队伍（顺序即出战顺序，第一位为首发）。 */
    private final List<Pokemon> team = new ArrayList<>();

    private ListView<Species> candidateList;
    private GridPane partyGrid;
    private Button addButton;
    private Button removeButton;
    private Button leadButton;
    private Button startButton;
    private Label teamCaption;
    /** 顶栏信息卡队伍进度（金色行，队伍变化时原地刷新）。 */
    private Label teamInfoLabel;
    /** 顶栏「返回」胶囊（入场动画用）。 */
    private FloatingMenu backMenu;

    /** 当前选中的队伍卡槽下标（-1 = 未选中）；「移除选中 / 设为首发」对其操作。 */
    private int selectedTeamIndex = -1;

    public CustomBattleSetupView(String title, int requiredCount, Runnable onBack,
                                 Consumer<List<Pokemon>> onStartBattle) {
        this.title = title;
        this.requiredCount = requiredCount;
        this.onBack = onBack;
        this.onStartBattle = onStartBattle;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);

        // 暗角遮罩：与启动页 / 模式选择页同款（弱化背景，保证悬浮元素可读）
        Region vignette = new Region();
        vignette.getStyleClass().add("start-vignette");
        vignette.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        vignette.setMouseTransparent(true);

        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(10, 12, 10, 12));

        // 上部分：返回胶囊 + 白描边标题 + 信息卡（与商店页 / 内层子页同款顶栏）
        layout.setTop(buildHeader());

        // 中部：左栏候选（固定宽）+ 右栏队伍（自适应占满剩余宽度）
        Region candidatePanel = buildCandidatePanel();
        candidatePanel.setPrefWidth(250);
        candidatePanel.setMinWidth(250);
        candidatePanel.setMaxWidth(250);
        Region teamPanel = buildTeamPanel();
        HBox columns = new HBox(10, candidatePanel, teamPanel);
        HBox.setHgrow(teamPanel, Priority.ALWAYS);
        BorderPane.setMargin(columns, new Insets(0, 0, 8, 0));
        layout.setCenter(columns);

        // 底部：开始战斗（主行动；「返回」已移至顶栏左侧胶囊）
        layout.setBottom(buildBottomBar());

        root.getChildren().addAll(vignette, layout);

        refreshTeamUi();

        Scene scene = UiScale.scene(root);
        for (String sheet : new String[] {SHARED_STYLE_SHEET, STYLE_SHEET}) {
            var css = CustomBattleSetupView.class.getResource(sheet);
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
        }
        backMenu.playEntrance();
        playEntrance(layout);
        return scene;
    }

    /** 队伍成员上限：限定数量模式即该数量；小队对战模式最多 6 只。 */
    private int maxCount() {
        return requiredCount > 0 ? Math.min(requiredCount, MAX_SQUAD) : MAX_SQUAD;
    }

    /** 是否满足开战条件：限定数量模式须恰好选满；小队对战模式至少 1 只。 */
    private boolean canStart() {
        return requiredCount > 0 ? team.size() == maxCount() : !team.isEmpty();
    }

    /** 标题下副提示（按出战数量语义区分；单行短句，与标题形成层级）。 */
    private String hintText() {
        if (requiredCount == 1) {
            return "挑选 1 只 Lv.100 满状态宝可梦，对手随机生成同级队伍。";
        }
        if (requiredCount > 1) {
            return "挑选 " + maxCount() + " 只 Lv.100 满状态宝可梦，首位首发，对手随机生成同级队伍。";
        }
        return "挑选 1~" + MAX_SQUAD + " 只 Lv.100 满状态宝可梦，首位首发，对手随机生成同级队伍。";
    }

    /** 把候选列表当前选中项加入队伍：新建 Lv.100 满状态个体；未选中或已达上限时忽略。 */
    private void addSelected(Species selected) {
        if (selected == null) {
            LogUtil.info("[CustomBattleSetupView] 请先在左侧选择要加入的宝可梦");
            return;
        }
        if (team.size() >= maxCount()) {
            LogUtil.info("[CustomBattleSetupView] 队伍已满（最多 " + maxCount() + " 只）");
            return;
        }
        Pokemon member = service.createPokemon(selected.getId(), MEMBER_LEVEL);
        member.fullRestore(); // HP / PP 补满（满状态入场）
        team.add(member);
        refreshTeamUi();
    }

    /** 刷新队伍网格、标题与按钮可用状态。 */
    private void refreshTeamUi() {
        if (selectedTeamIndex >= team.size()) {
            selectedTeamIndex = -1;
        }
        rebuildPartyGrid();
        teamCaption.setText("我的队伍（" + team.size() + "/" + maxCount() + "）");
        teamInfoLabel.setText("队伍: " + team.size() + "/" + maxCount());
        addButton.setDisable(team.size() >= maxCount());
        removeButton.setDisable(team.isEmpty() || selectedTeamIndex < 0);
        leadButton.setDisable(team.size() < 2 || selectedTeamIndex <= 0);
        startButton.setDisable(!canStart());
    }

    // ------------------------------------------------------------------
    // UI 构建（宝可梦蓝黄 + 白卡悬浮，样式见 /css/custom-battle-setup.css）
    // ------------------------------------------------------------------

    /** 顶栏一行：左「返回」胶囊（启动页同款）/ 中标题（白描边深蓝字 + 副提示）/ 右信息卡。 */
    private StackPane buildHeader() {
        Label titleLabel = new Label("队伍配置");
        titleLabel.getStyleClass().add("cbs-title");
        Label hint = new Label(hintText());
        hint.getStyleClass().add("cbs-hint");
        VBox center = new VBox(2, titleLabel, hint);
        center.setAlignment(Pos.CENTER);

        backMenu = new FloatingMenu();
        backMenu.setCompact(true);
        backMenu.setDeselectOnExit(true);
        backMenu.addPill("slate", "返回", "", "", onBack);
        VBox back = backMenu.node();
        back.setMaxWidth(Region.USE_PREF_SIZE);
        back.setMaxHeight(Region.USE_PREF_SIZE);

        VBox info = buildInfoPanel();

        StackPane header = new StackPane(center);
        header.getChildren().addAll(back, info);
        StackPane.setAlignment(back, Pos.CENTER_LEFT);
        StackPane.setAlignment(info, Pos.CENTER_RIGHT);
        header.setPadding(new Insets(0, 0, 8, 0));
        return header;
    }

    /** 右侧信息卡（.rogue-info 同款）：当前模式 + 队伍进度（金色小字，选人后原地刷新）。 */
    private VBox buildInfoPanel() {
        Label mode = new Label(title);
        mode.setStyle(FONT + "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #123c63;");
        teamInfoLabel = new Label("队伍: 0/" + maxCount());
        teamInfoLabel.setStyle(FONT + "-fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #E6A800;");
        VBox panel = new VBox(2, mode, teamInfoLabel);
        panel.setMinWidth(108);
        panel.setMaxWidth(Region.USE_PREF_SIZE);
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        panel.getStyleClass().add("rogue-info");
        return panel;
    }

    /** 左栏：全部宝可梦候选（小头像 + 中文名 + 英文名 + BST；双击条目可直接入队）。 */
    private Region buildCandidatePanel() {
        List<Species> allSpecies = GameData.instance().getAllSpecies();
        HBox caption = panelCaption(new Label("全部宝可梦（" + allSpecies.size() + "）"));

        candidateList = new ListView<>(FXCollections.observableArrayList(allSpecies));
        candidateList.getStyleClass().add("cbs-list");
        candidateList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Species item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label name = new Label(item.getName());
                name.getStyleClass().add("cbs-cell-name");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label bst = new Label("BST " + item.getBaseStats().getTotal());
                bst.getStyleClass().add("cbs-cell-bst");
                HBox topRow = new HBox(4, name, spacer, bst);
                topRow.setAlignment(Pos.CENTER_LEFT);
                Label english = new Label(PokedexData.englishNameOf(item.getId()));
                english.getStyleClass().add("cbs-cell-sub");
                VBox text = new VBox(0, topRow, english);
                text.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(text, Priority.ALWAYS);
                HBox row = new HBox(7, buildSpriteIcon(item.getName(), CANDIDATE_ICON_SIZE,
                        "cbs-cell-icon", "cbs-cell-noicon"), text);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        candidateList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) { // 双击快捷入队（与「加入队伍」按钮同一逻辑）
                addSelected(candidateList.getSelectionModel().getSelectedItem());
            }
        });
        VBox.setVgrow(candidateList, Priority.ALWAYS);

        addButton = actionButton("加入队伍", null);
        addButton.setMaxWidth(Double.MAX_VALUE);
        addButton.setOnAction(e -> addSelected(candidateList.getSelectionModel().getSelectedItem()));

        VBox panel = new VBox(6, caption, candidateList, addButton);
        panel.getStyleClass().add("cbs-panel");
        return panel;
    }

    /** 右栏：我的队伍 —— 卡槽网格（槽数 = 本模式上限；点击卡槽选中，行内按钮对其操作）。 */
    private Region buildTeamPanel() {
        teamCaption = new Label();
        HBox caption = panelCaption(teamCaption);

        partyGrid = new GridPane();
        partyGrid.getStyleClass().add("cbs-party-grid");
        partyGrid.setHgap(6);
        partyGrid.setVgap(6);
        partyGrid.setAlignment(Pos.CENTER);

        removeButton = actionButton("移除选中", null);
        removeButton.setOnAction(e -> {
            int index = selectedTeamIndex;
            if (index >= 0 && index < team.size()) {
                team.remove(index); // 移除后无选中（与旧版 setAll 清空选中的行为一致）
                selectedTeamIndex = -1;
                refreshTeamUi();
            }
        });
        leadButton = actionButton("设为首发", null);
        leadButton.setOnAction(e -> {
            int index = selectedTeamIndex;
            if (index > 0) {
                team.add(0, team.remove(index)); // 移到队伍首位 = 首发
                selectedTeamIndex = 0;           // 选中项跟随（与旧版 select(0) 一致）
                refreshTeamUi();
            }
        });
        HBox buttonRow = new HBox(8, removeButton, leadButton);
        removeButton.setMaxWidth(Double.MAX_VALUE);
        leadButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(removeButton, Priority.ALWAYS);
        HBox.setHgrow(leadButton, Priority.ALWAYS);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS); // 吸收剩余高度：卡槽区置顶、操作按钮贴底

        VBox panel = new VBox(6, caption, partyGrid, spacer, buttonRow);
        panel.getStyleClass().add("cbs-panel");
        return panel;
    }

    /** 底部操作栏：开始战斗（黄色大胶囊，全页视觉重心；「返回」在顶栏左侧）。 */
    private Region buildBottomBar() {
        startButton = actionButton("开始战斗", "cbs-btn-start");
        startButton.setOnAction(e -> {
            if (canStart()) {
                onStartBattle.accept(new ArrayList<>(team));
            }
        });
        installHoverLift(startButton, 1.04);

        HBox bar = new HBox(startButton);
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    /** 栏标题行：黄色装饰色条 + 深蓝粗体标题（左右两栏统一）。 */
    private static HBox panelCaption(Label caption) {
        caption.getStyleClass().add("cbs-panel-title");
        Region accent = new Region();
        accent.getStyleClass().add("cbs-accent");
        accent.setMinSize(3.5, 14);
        accent.setPrefSize(3.5, 14);
        accent.setMaxSize(3.5, 14);
        HBox box = new HBox(6, accent, caption);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** 统一样式的按钮（基础类 cbs-btn；variant 为 null 时不加变体类）。 */
    private static Button actionButton(String text, String variantStyleClass) {
        Button button = new Button(text);
        button.getStyleClass().add("cbs-btn");
        if (variantStyleClass != null) {
            button.getStyleClass().add(variantStyleClass);
        }
        return button;
    }

    /** 精灵小图：有立绘用图（等比缩放居中），无图回退「?」（与图鉴同款策略）。 */
    private static StackPane buildSpriteIcon(String pokemonName, double size,
                                             String boxStyleClass, String fallbackStyleClass) {
        StackPane box = new StackPane();
        box.getStyleClass().add(boxStyleClass);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        Image image = SpriteLoader.load(pokemonName);
        if (image == null) {
            Label fallback = new Label("?");
            fallback.getStyleClass().add(fallbackStyleClass);
            box.getChildren().add(fallback);
        } else {
            ImageView view = new ImageView(image);
            view.setFitWidth(size - 4);
            view.setFitHeight(size - 4);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            box.getChildren().add(view);
        }
        return box;
    }

    /** 队伍成员卡：精灵图 + 名称 + 英文名 + 等级；首位挂「★ 首发」徽章；点击选中。 */
    private Node buildMemberSlot(int index) {
        Pokemon member = team.get(index);
        Species species = member.getSpecies();

        Label name = new Label(member.getName());
        name.getStyleClass().add("cbs-slot-name");
        HBox nameRow = new HBox(4, name);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        if (index == 0) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label badge = new Label("★ 首发");
            badge.getStyleClass().add("cbs-slot-badge");
            nameRow.getChildren().addAll(spacer, badge);
        }
        Label english = new Label(PokedexData.englishNameOf(species.getId()));
        english.getStyleClass().add("cbs-slot-sub");
        Label level = new Label("Lv." + member.getLevel());
        level.getStyleClass().add("cbs-slot-sub");
        VBox info = new VBox(0, nameRow, english, level);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox card = new HBox(7,
                buildSpriteIcon(species.getName(), SLOT_ICON_SIZE, "cbs-slot-sprite", "cbs-slot-noicon"),
                info);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMinHeight(SLOT_HEIGHT);
        card.setPrefHeight(SLOT_HEIGHT);
        card.setMaxHeight(SLOT_HEIGHT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("cbs-slot");
        if (index == selectedTeamIndex) {
            card.getStyleClass().add("cbs-slot-selected");
        }
        card.setOnMouseClicked(e -> {
            selectedTeamIndex = index;
            refreshTeamUi();
        });
        installHoverLift(card, 1.02);
        return card;
    }

    /** 空卡槽：虚线占位；下一个空位给出引导文案，其余仅「＋」。 */
    private Node buildEmptySlot(int index) {
        Label placeholder = new Label(index == team.size() ? "＋ 选择宝可梦加入队伍" : "＋");
        placeholder.getStyleClass().add(index == team.size() ? "cbs-slot-empty-hint" : "cbs-slot-plus");
        StackPane slot = new StackPane(placeholder);
        slot.getStyleClass().add("cbs-slot-empty");
        slot.setMinHeight(SLOT_HEIGHT);
        slot.setPrefHeight(SLOT_HEIGHT);
        slot.setMaxHeight(SLOT_HEIGHT);
        slot.setMaxWidth(Double.MAX_VALUE);
        return slot;
    }

    /** 重建队伍网格：槽数 = 本模式上限（1 vs 1 → 1；2 vs 2 → 2；小队 / 自定义 → 6）。 */
    private void rebuildPartyGrid() {
        partyGrid.getChildren().clear();
        partyGrid.getColumnConstraints().clear();
        int slots = maxCount();
        int cols = slots == 1 ? 1 : 2;
        for (int c = 0; c < cols; c++) {
            ColumnConstraints column = new ColumnConstraints();
            if (cols == 1) {
                column.setPrefWidth(200); // 单槽模式：居中的固定宽度展示卡
                column.setHgrow(Priority.NEVER);
            } else {
                column.setPercentWidth(50); // 双列严格平分（仅 Hgrow 时列宽会被内容首选宽拉偏）
            }
            partyGrid.getColumnConstraints().add(column);
        }
        for (int i = 0; i < slots; i++) {
            partyGrid.add(i < team.size() ? buildMemberSlot(i) : buildEmptySlot(i), i % cols, i / cols);
        }
    }

    /** 轻量悬停反馈：进入轻微放大、移出还原（110ms，纯视觉效果，不改布局）。 */
    private static void installHoverLift(Node node, double scale) {
        ScaleTransition up = new ScaleTransition(Duration.millis(110), node);
        up.setToX(scale);
        up.setToY(scale);
        ScaleTransition down = new ScaleTransition(Duration.millis(110), node);
        down.setToX(1);
        down.setToY(1);
        node.setOnMouseEntered(e -> {
            down.stop();
            up.playFromStart();
        });
        node.setOnMouseExited(e -> {
            up.stop();
            down.playFromStart();
        });
    }

    /** 页面入场：整体淡入（220ms，轻量不位移）。 */
    private static void playEntrance(Region root) {
        FadeTransition fade = new FadeTransition(Duration.millis(220), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }
}
