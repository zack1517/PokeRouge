package org.example.view;

import java.util.function.BiConsumer;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;
import org.example.config.AppConfig;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;
import org.example.pokemon.domain.Species;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;

/**
 * 初始宝可梦选择页（启动页「开始游戏」进入）：无卡片悬浮式布局。
 *
 * <p>与启动页、自定义战斗页同一套视觉体系（背景插画 + 暗角遮罩，标题、表单与胶囊入口
 * 直接悬浮其上，不再使用半透明中央卡片）：「带它开始冒险」「返回」由 {@link FloatingMenu}
 * 承载（胶囊结构、悬停/选中动效、入场动画与启动页完全一致），表单控件为白底胶囊样式
 * （见 {@code /css/start-menu.css} 的 .starter-* 系列）。</p>
 *
 * <p>接线不变：选好初始精灵后进入存档位选择（新游戏）；「返回」（或 Esc）不创建精灵直接回启动页。</p>
 */
public final class StarterSelectionView {

    /** 页面背景（与启动页同款主画面）。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_startpage.jpg";

    /** 共享样式表（胶囊按钮/图标/暗角/标题/表单，与启动页同一份）。 */
    private static final String STYLE_SHEET = "/css/start-menu.css";

    /** 两个入口的 24 单位视口单色描边图标（lucide 风格手绘简化版）。 */
    private static final String ICON_PLAY = "M7 4 L20 12 L7 20 Z";
    private static final String ICON_BACK = "M19 12 L5 12 M11 18 L5 12 L11 6";

    private final BiConsumer<String, org.example.pokemon.domain.Pokemon> onStart;
    private final Runnable onBack;
    private final PokemonService service = new PokemonServiceImpl();

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

        Label title = new Label("选择你的初始宝可梦");
        title.getStyleClass().add("subpage-title");

        Label detail = new Label("初始宝可梦由新 pokemon 数据库创建，之后将直接进入对战流程。");
        detail.getStyleClass().add("starter-note");
        detail.setWrapText(true);
        detail.setTextAlignment(TextAlignment.CENTER); // wrap 折行后逐行居中需 textAlignment
        detail.setMaxWidth(340); // 宽度容下整行，避免末字孤行折行
        detail.setAlignment(Pos.CENTER);

        TextField name = new TextField(AppConfig.PLAYER_NAME);
        name.getStyleClass().add("starter-input");
        name.setMaxWidth(220);

        ComboBox<Species> choices = new ComboBox<>();
        choices.getStyleClass().add("starter-combo");
        choices.setMaxWidth(220);
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
        description.setMaxWidth(340); // 与说明行同宽，容纳整行不孤字折行
        description.setAlignment(Pos.CENTER);
        choices.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) description.setText(selected.getCategory() + "：" + selected.getDescription());
        });
        if (choices.getValue() != null) description.setText(choices.getValue().getCategory() + "：" + choices.getValue().getDescription());

        Label nameCaption = new Label("训练家名称");
        nameCaption.getStyleClass().add("starter-caption");
        Label speciesCaption = new Label("初始宝可梦");
        speciesCaption.getStyleClass().add("starter-caption");

        FloatingMenu menu = new FloatingMenu();
        menu.addPill("yellow", "带它开始冒险", "START", ICON_PLAY, () -> {
            String trainerName = name.getText().isBlank() ? AppConfig.PLAYER_NAME : name.getText().trim();
            Species selected = choices.getValue();
            onStart.accept(trainerName, service.createPokemon(selected.getId(), 5));
        });
        menu.addPill("slate", "返回", "BACK", ICON_BACK, onBack);

        // 三组垂直堆叠：标题+说明 / 表单 / 胶囊入口（整列按内容收拢并垂直居中；
        // 标题与说明间距 14：避开标题投影带，说明文字保持清晰）
        VBox header = new VBox(14, title, detail);
        header.setAlignment(Pos.CENTER);
        VBox form = new VBox(4, nameCaption, name, speciesCaption, choices, description);
        form.setAlignment(Pos.CENTER);
        VBox column = new VBox(14, header, form, menu.node());
        column.setAlignment(Pos.CENTER);
        // 关键：BorderPane 会把 center 子节点拉满可用高度，不设上限时整列会被垂直撑开；
        // maxHeight 用内容首选高封顶后，整列按内容收拢并垂直居中。
        column.setMaxHeight(Region.USE_PREF_SIZE);
        column.setMaxWidth(Region.USE_PREF_SIZE);

        BorderPane layout = new BorderPane();
        layout.setCenter(column);
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
        menu.selectFirst();
        menu.playEntrance();
        return scene;
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
