package org.example.view;

import java.util.function.BiConsumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;
import org.example.config.AppConfig;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;
import org.example.pokemon.domain.Species;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;

/** 游戏开始页（初始主页面）：整页铺 bg_main 主画面背景，仅中央一张半透明卡片承载标题与表单；
 * 选择初始精灵后进入战斗主流程，「返回」不创建精灵直接回启动页。 */
public final class StarterSelectionView {

    /** 初始主页面背景（classpath；素材源在 临时/images/background/bg_main.jpeg，与资源目录同步）。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_main.jpeg";

    private final BiConsumer<String, org.example.pokemon.domain.Pokemon> onStart;
    private final Runnable onBack;
    private final PokemonService service = new PokemonServiceImpl();

    public StarterSelectionView(BiConsumer<String, org.example.pokemon.domain.Pokemon> onStart, Runnable onBack) {
        this.onStart = onStart;
        this.onBack = onBack;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);
        root.setPadding(new Insets(12));
        Label title = new Label("选择你的初始宝可梦");
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 20px; -fx-font-weight: bold;");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        Label detail = new Label("初始宝可梦由新 pokemon 数据库创建，之后将直接进入对战流程。");
        detail.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #555;");
        detail.setMaxWidth(Double.MAX_VALUE);
        detail.setAlignment(Pos.CENTER);
        detail.setTextAlignment(TextAlignment.CENTER); // wrap 折行后逐行居中需 textAlignment
        detail.setWrapText(true);

        // 输入框/下拉框收窄为适宽居中（不再占满卡内全宽，消除两侧大片空白）；fillWidth 拉伸受 maxWidth 封顶
        TextField name = new TextField(AppConfig.PLAYER_NAME);
        name.setMaxWidth(220);
        name.setStyle("-fx-alignment: center;");
        ComboBox<Species> choices = new ComboBox<>();
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
        description.setWrapText(true);
        description.setMaxWidth(Double.MAX_VALUE);
        description.setAlignment(Pos.CENTER);
        description.setTextAlignment(TextAlignment.CENTER); // wrap 折行后逐行居中需 textAlignment
        description.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #306090;");
        choices.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) description.setText(selected.getCategory() + "：" + selected.getDescription());
        });
        if (choices.getValue() != null) description.setText(choices.getValue().getCategory() + "：" + choices.getValue().getDescription());

        Button start = new Button("带它开始冒险");
        start.setMaxWidth(150);
        start.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-padding: 6 18;");
        start.setOnAction(e -> {
            String trainerName = name.getText().isBlank() ? AppConfig.PLAYER_NAME : name.getText().trim();
            Species selected = choices.getValue();
            onStart.accept(trainerName, service.createPokemon(selected.getId(), 5));
        });

        // 「返回」：不创建宝可梦，直接切回启动页（开始页 → 启动页）
        Button back = new Button("返回");
        back.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px; -fx-padding: 6 14;");
        back.setOnAction(e -> onBack.run());
        HBox buttonRow = new HBox(8, start, back);
        buttonRow.setAlignment(Pos.CENTER);

        // 不做外层大衬底框，背景尽量露出；标题/说明并入卡内，由半透明白卡统一承载以保持可读
        // （0.65 与主菜单半透明面板同族；卡宽只容纳内容，所有行文本水平居中）
        Label nameCaption = new Label("训练家名称");
        nameCaption.setMaxWidth(Double.MAX_VALUE);
        nameCaption.setAlignment(Pos.CENTER);
        Label speciesCaption = new Label("初始宝可梦");
        speciesCaption.setMaxWidth(Double.MAX_VALUE);
        speciesCaption.setAlignment(Pos.CENTER);
        // 行距 8→6、内边距 14→10：上下整体收紧，卡片更矮更紧凑
        VBox card = new VBox(6, title, detail, nameCaption, name,
                speciesCaption, choices, description, buttonRow);
        card.setMaxWidth(330);
        // 关键：BorderPane 会把 center 子节点拉满可用高度，不设上限时卡片背景将
        // 撑满整窗高（上下大片空白）。maxHeight 封顶后卡片按内容高收拢并垂直居中。
        card.setMaxHeight(290);
        card.setPadding(new Insets(10));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.65);"
                + "-fx-border-color: #c9c9c9; -fx-border-radius: 10; -fx-background-radius: 10;");
        root.setCenter(card);
        return UiScale.scene(root);
    }

    /** 精灵选择下拉：收起按钮与展开选项的文本均居中（与卡内其他行一致）。 */
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
