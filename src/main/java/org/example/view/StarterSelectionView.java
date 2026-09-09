package org.example.view;

import java.util.function.BiConsumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.example.config.AppConfig;
import org.example.pokemon.domain.Species;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;

/** 游戏开始页：从新宝可梦系统选择初始精灵后进入战斗主流程。 */
public final class StarterSelectionView {

    private final BiConsumer<String, org.example.pokemon.domain.Pokemon> onStart;
    private final PokemonService service = new PokemonServiceImpl();

    public StarterSelectionView(BiConsumer<String, org.example.pokemon.domain.Pokemon> onStart) {
        this.onStart = onStart;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(28));
        Label title = new Label("选择你的初始宝可梦");
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 24px; -fx-font-weight: bold;");
        Label detail = new Label("初始宝可梦由新 pokemon 数据库创建，之后将直接进入对战流程。");
        detail.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #555;");

        TextField name = new TextField(AppConfig.PLAYER_NAME);
        ComboBox<Species> choices = new ComboBox<>();
        choices.getItems().addAll(service.getInitialPool());
        choices.getSelectionModel().selectFirst();
        choices.setConverter(new StringConverter<>() {
            @Override public String toString(Species value) { return value == null ? "" : value.getName() + "（" + value.getId() + "）"; }
            @Override public Species fromString(String value) { return null; }
        });
        Label description = new Label();
        description.setWrapText(true);
        description.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #306090;");
        choices.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) description.setText(selected.getCategory() + "：" + selected.getDescription());
        });
        if (choices.getValue() != null) description.setText(choices.getValue().getCategory() + "：" + choices.getValue().getDescription());

        Button start = new Button("带它开始冒险");
        start.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 16px; -fx-padding: 10 24;");
        start.setOnAction(e -> {
            String trainerName = name.getText().isBlank() ? AppConfig.PLAYER_NAME : name.getText().trim();
            Species selected = choices.getValue();
            onStart.accept(trainerName, service.createPokemon(selected.getId(), 5));
        });

        VBox card = new VBox(12, new Label("训练家名称"), name, new Label("初始宝可梦"), choices, description, start);
        card.setMaxWidth(430);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: #f7f7f7; -fx-border-color: #b8c6d9; -fx-border-radius: 8; -fx-background-radius: 8;");
        VBox body = new VBox(22, title, detail, card);
        body.setAlignment(Pos.CENTER);
        root.setCenter(body);
        return new Scene(root, AppConfig.WINDOW_WIDTH, AppConfig.WINDOW_HEIGHT);
    }
}
