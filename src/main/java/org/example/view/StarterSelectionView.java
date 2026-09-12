package org.example.view;

import java.util.function.BiConsumer;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.example.config.AppConfig;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;
import org.example.pokemon.domain.Species;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;

/**
 * 初始宝可梦选择页（启动页「开始游戏」进入）。
 *
 * <p>与商店页 / 队伍配置页同一套视觉体系：顶栏「返回胶囊 + 白描边深蓝字标题 + 副提示 +
 * 新游戏信息卡」，中部为无卡底表单（训练家名称输入、初始宝可梦下拉与说明直接悬浮于背景），
 * 底部为黄→金大胶囊「带它开始冒险」；样式集中在 {@code /css/start-menu.css}
 * （.starter-* 表单系列 + .page-title 标题 + .primary-pill 主按钮 + 胶囊 / 暗角 / .rogue-info 共享规范）。</p>
 *
 * <p>接线不变：选好初始精灵后进入存档位选择（新游戏）；「返回」（或 Esc）不创建精灵直接回启动页。</p>
 */
public final class StarterSelectionView {

    /** 页面背景（与启动页同款主画面）。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_startpage.jpg";

    /** 共享样式表（胶囊按钮/图标/暗角/标题/表单，与启动页同一份）。 */
    private static final String STYLE_SHEET = "/css/start-menu.css";

    /** 初始宝可梦等级（信息卡展示与实际创建共用）。 */
    private static final int STARTER_LEVEL = 5;

    private final BiConsumer<String, org.example.pokemon.domain.Pokemon> onStart;
    private final Runnable onBack;
    private final PokemonService service = new PokemonServiceImpl();

    /** 顶栏「返回」胶囊（入场动画引用）。 */
    private FloatingMenu backMenu;

    public StarterSelectionView(BiConsumer<String, org.example.pokemon.domain.Pokemon> onStart, Runnable onBack) {
        this.onStart = onStart;
        this.onBack = onBack;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);

        // 暗角遮罩：叠在背景图之上、内容层之下（与启动页同款，弱化背景保证悬浮元素可读）
        Region vignette = new Region();
        vignette.getStyleClass().add("start-vignette");
        vignette.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        vignette.setMouseTransparent(true);

        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(10, 12, 10, 12));
        layout.setTop(buildHeader());

        // 中部：表单白卡（按内容收拢后居中）
        TextField name = new TextField(AppConfig.PLAYER_NAME);
        name.getStyleClass().add("starter-input");
        name.setMaxWidth(260);

        ComboBox<Species> choices = new ComboBox<>();
        choices.getStyleClass().add("starter-combo");
        choices.setMaxWidth(260);
        choices.getItems().addAll(service.getInitialPool());
        choices.getSelectionModel().selectFirst();
        choices.setConverter(new StringConverter<>() {
            @Override public String toString(Species value) { return value == null ? "" : value.getName() + "（" + value.getId() + "）"; }
            @Override public Species fromString(String value) { return null; }
        });
        // 下拉收起按钮与展开选项的文本均水平居中（cellFactory 回调参数是内部 ListView，converter 需从 choices 取）
        choices.setCellFactory(listView -> centeredSpeciesCell(choices));
        choices.setButtonCell(centeredSpeciesCell(choices));

        Label description = new Label();
        description.getStyleClass().add("starter-note");
        description.setWrapText(true);
        description.setTextAlignment(TextAlignment.CENTER); // wrap 折行后逐行居中需 textAlignment
        description.setMaxWidth(260); // 与输入行同宽，容纳整行不孤字折行
        description.setAlignment(Pos.CENTER);
        choices.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) description.setText(selected.getCategory() + "：" + selected.getDescription());
        });
        if (choices.getValue() != null) description.setText(choices.getValue().getCategory() + "：" + choices.getValue().getDescription());

        Label nameCaption = new Label("训练家名称");
        nameCaption.getStyleClass().add("starter-caption");
        Label speciesCaption = new Label("初始宝可梦");
        speciesCaption.getStyleClass().add("starter-caption");

        VBox form = new VBox(4, nameCaption, name, speciesCaption, choices, description);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(Region.USE_PREF_SIZE);
        form.getStyleClass().add("starter-card");
        VBox center = new VBox(form);
        center.setAlignment(Pos.CENTER);
        BorderPane.setMargin(center, new Insets(0, 0, 8, 0));
        layout.setCenter(center);

        // 底部：「带它开始冒险」（黄→金大胶囊，全页视觉重心；「返回」在顶栏左侧）
        Button start = new Button("带它开始冒险");
        start.getStyleClass().add("primary-pill");
        start.setOnAction(e -> {
            String trainerName = name.getText().isBlank() ? AppConfig.PLAYER_NAME : name.getText().trim();
            Species selected = choices.getValue();
            onStart.accept(trainerName, service.createPokemon(selected.getId(), STARTER_LEVEL));
        });
        HBox bar = new HBox(start);
        bar.setAlignment(Pos.CENTER);
        layout.setBottom(bar);

        root.getChildren().addAll(vignette, layout);

        Scene scene = UiScale.scene(root);
        var css = StarterSelectionView.class.getResource(STYLE_SHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        // 本页有输入控件，不安装全菜单键盘导航（避免与输入焦点争抢按键），仅 Esc 返回
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                onBack.run();
            }
        });
        backMenu.playEntrance();
        playEntrance(layout);
        return scene;
    }

    /** 顶栏一行：左「返回」胶囊（启动页同款）/ 中标题（白描边深蓝字 + 副提示）/ 右新游戏信息卡。 */
    private StackPane buildHeader() {
        Label title = new Label("选择初始宝可梦");
        title.getStyleClass().add("page-title");
        Label hint = new Label("初始宝可梦由宝可梦库创建，之后将直接进入对战流程。");
        hint.getStyleClass().add("starter-note");
        VBox center = new VBox(2, title, hint);
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

    /** 右侧信息卡（.rogue-info 同款）：流程名 + 初始等级（金色小字）。 */
    private static VBox buildInfoPanel() {
        Label mode = new Label("新游戏");
        mode.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 10px; -fx-font-weight: bold;"
                + " -fx-text-fill: #123c63;");
        Label level = new Label("Lv." + STARTER_LEVEL + " 出发");
        level.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 8.5px; -fx-font-weight: bold;"
                + " -fx-text-fill: #E6A800;");
        VBox panel = new VBox(2, mode, level);
        panel.setMinWidth(108);
        panel.setMaxWidth(Region.USE_PREF_SIZE);
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        panel.getStyleClass().add("rogue-info");
        return panel;
    }

    /** 页面入场：整体淡入（220ms，轻量不位移，与其它悬浮页一致）。 */
    private static void playEntrance(Region root) {
        FadeTransition fade = new FadeTransition(Duration.millis(220), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /** 精灵选择下拉：收起按钮与展开选项的文本均居中（与列内其他行一致）。 */
    private static ListCell<Species> centeredSpeciesCell(ComboBox<Species> combo) {
        return new ListCell<>() {
            @Override
            protected void updateItem(Species item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(combo.getConverter() == null
                        ? item.toString()
                        : combo.getConverter().toString(item));
                setAlignment(Pos.CENTER);
            }
        };
    }
}
