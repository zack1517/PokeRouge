package org.example.view;

import java.util.List;
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
import org.example.pokemon.domain.GrowthEvent;
import org.example.pokemon.domain.Move;
import org.example.pokemon.domain.Pokemon;
import org.example.pokemon.domain.Species;
import org.example.pokemon.domain.Stats;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;

/** JavaFX 页面：直接展示并操作新宝可梦系统，不经过旧战斗模型。 */
public final class PokemonLabView {

    private final Runnable onBack;
    private final PokemonService service = new PokemonServiceImpl();
    private final ComboBox<Species> starters = new ComboBox<>();
    private final Label summary = new Label("请选择初始宝可梦，然后开始测试。");
    private final Label stats = new Label();
    private final Label moves = new Label();
    private final Label events = new Label("成长事件将在这里显示。");
    private Pokemon pokemon;

    public PokemonLabView(Runnable onBack) {
        this.onBack = onBack;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(18));
        root.setTop(header());
        root.setCenter(content());
        root.setBottom(actions());
        return new Scene(root, AppConfig.WINDOW_WIDTH, AppConfig.WINDOW_HEIGHT);
    }

    private VBox header() {
        Label title = new Label("新宝可梦系统 · 功能验证");
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 21px; -fx-font-weight: bold;");
        Label subtitle = new Label("此页面直接使用 org.example.pokemon 的数据、服务和领域模型。");
        subtitle.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #555;");
        return new VBox(5, title, subtitle);
    }

    private VBox content() {
        TextField trainer = new TextField("测试训练师");
        trainer.setPrefWidth(150);
        starters.getItems().addAll(service.getInitialPool());
        starters.getSelectionModel().selectFirst();
        starters.setConverter(new StringConverter<>() {
            @Override public String toString(Species value) {
                return value == null ? "" : value.getName() + "（" + value.getId() + "）";
            }
            @Override public Species fromString(String value) { return null; }
        });
        Button create = new Button("创建初始宝可梦");
        create.setOnAction(e -> createPokemon(trainer.getText()));

        HBox setup = new HBox(10, new Label("训练家："), trainer, new Label("初始宝可梦："), starters, create);
        setup.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, summary, stats, moves, events);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #f7f7f7; -fx-border-color: #b8c6d9; -fx-border-radius: 6; -fx-background-radius: 6;");
        for (Label label : List.of(summary, stats, moves, events)) {
            label.setWrapText(true);
            label.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px;");
        }
        events.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-text-fill: #306090;");
        return new VBox(20, setup, card);
    }

    private HBox actions() {
        Button exp = new Button("获得 1000 经验");
        exp.setOnAction(e -> gainExperience());
        Button damage = new Button("受到 20 点伤害");
        damage.setOnAction(e -> { if (pokemon != null) { pokemon.takeDamage(20); refresh(); } });
        Button heal = new Button("完全恢复");
        heal.setOnAction(e -> { if (pokemon != null) { service.healPokemon(pokemon); refresh(); } });
        Button back = new Button("返回旧主菜单");
        back.setOnAction(e -> onBack.run());
        HBox bar = new HBox(10, exp, damage, heal, back);
        bar.setPadding(new Insets(14, 0, 0, 0));
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private void createPokemon(String trainerName) {
        Species starter = starters.getValue();
        service.resetPlayer();
        service.createPlayer(trainerName == null || trainerName.isBlank() ? "测试训练师" : trainerName.trim());
        pokemon = service.createPokemon(starter.getId(), 5);
        service.addPokemonToParty(pokemon);
        events.setText("已创建新系统中的初始宝可梦。");
        refresh();
    }

    private void gainExperience() {
        if (pokemon == null) {
            events.setText("请先创建初始宝可梦。");
            return;
        }
        List<GrowthEvent> growth = service.gainExp(pokemon, 1000);
        events.setText(growth.isEmpty() ? "获得了 1000 经验。" : "成长事件：" + eventText(growth));
        refresh();
    }

    private void refresh() {
        if (pokemon == null) return;
        Stats s = pokemon.getStats();
        summary.setText(String.format("%s  Lv.%d  HP %d/%d  EXP %d  性格：%s", pokemon.getName(), pokemon.getLevel(),
                pokemon.getCurrentHp(), pokemon.getMaxHp(), pokemon.getExp(), pokemon.getNature().getName()));
        stats.setText(String.format("能力：HP %d / 攻击 %d / 防御 %d / 特攻 %d / 特防 %d / 速度 %d", s.getHp(),
                s.getAttack(), s.getDefense(), s.getSpAttack(), s.getSpDefense(), s.getSpeed()));
        moves.setText("已学技能：" + pokemon.getMoves().stream().map(Move::getName).toList());
    }

    private static String eventText(List<GrowthEvent> growth) {
        return growth.stream().map(event -> switch (event.getType()) {
            case LEVEL_UP -> "升级 " + event.getOldLevel() + "→" + event.getNewLevel();
            case LEARN_MOVE -> "学会 " + event.getLearnedMove().getName();
            case EVOLUTION -> "进化为 " + event.getEvolvedTo().getName();
        }).toList().toString();
    }
}
