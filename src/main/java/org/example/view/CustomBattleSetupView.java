package org.example.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.pokemon.domain.Pokemon;
import org.example.pokemon.domain.Species;
import org.example.pokemon.infrastructure.GameData;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;
import org.example.util.ImageBackgrounds;
import org.example.util.LogUtil;
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
 * <p>ⓘ 页面仅保证基础功能可用，美术风格由后续美工统一调整。</p>
 */
public final class CustomBattleSetupView {

    /** 成员等级（统一满级）。 */
    private static final int MEMBER_LEVEL = 100;

    /** 队伍上限（与战斗契约一致：最多 6 只）。 */
    private static final int MAX_SQUAD = 6;

    /** 页面背景（与启动页同款主画面）。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_main.jpeg";

    /** 列标题统一样式。 */
    private static final String CAPTION_STYLE =
            "-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #333;";

    /** 按钮统一样式（基础可用即可）。 */
    private static final String BUTTON_STYLE =
            "-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-padding: 6 14;";

    /** 页面标题（如「1 vs 1」「小队对战」「自定义数量（3）」）。 */
    private final String title;

    /** 出战数量：大于 0 须恰好选满该数量；为 0 表示 1~6 任意（小队对战）。 */
    private final int requiredCount;

    private final Runnable onBack;
    private final Consumer<List<Pokemon>> onStartBattle;
    private final PokemonService service = new PokemonServiceImpl();

    /** 已选队伍（顺序即出战顺序，第一位为首发）。 */
    private final List<Pokemon> team = new ArrayList<>();

    private ListView<Pokemon> teamList;
    private Button addButton;
    private Button removeButton;
    private Button leadButton;
    private Button startButton;
    private Label teamCaption;

    public CustomBattleSetupView(String title, int requiredCount, Runnable onBack,
                                 Consumer<List<Pokemon>> onStartBattle) {
        this.title = title;
        this.requiredCount = requiredCount;
        this.onBack = onBack;
        this.onStartBattle = onStartBattle;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);
        root.setPadding(new Insets(10));

        Label titleLabel = new Label(title + " · 队伍配置");
        titleLabel.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 18px; -fx-font-weight: bold;");
        Label hint = new Label(hintText());
        hint.setWrapText(true);
        hint.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 11px; -fx-text-fill: #444;");
        VBox header = new VBox(2, titleLabel, hint);
        header.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6); -fx-background-radius: 10;"
                + " -fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 10;"
                + " -fx-padding: 8 12;");
        root.setTop(header);

        // 左列：全部宝可梦候选（按 CSV 数据源加载）
        List<Species> allSpecies = GameData.instance().getAllSpecies();
        Label candidateCaption = new Label("全部宝可梦（" + allSpecies.size() + "）");
        candidateCaption.setStyle(CAPTION_STYLE);
        ListView<Species> candidateList = new ListView<>(FXCollections.observableArrayList(allSpecies));
        candidateList.setPrefSize(268, 226);
        candidateList.setStyle("-fx-font-family: 'Microsoft YaHei';");
        candidateList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Species item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.getName() + "（" + item.getId() + "）  BST " + item.getBaseStats().getTotal());
            }
        });

        addButton = new Button("加入队伍 >>");
        addButton.setStyle(BUTTON_STYLE);
        addButton.setOnAction(e -> addSelected(candidateList.getSelectionModel().getSelectedItem()));

        VBox candidateBox = new VBox(6, candidateCaption, candidateList, addButton);
        candidateBox.setAlignment(Pos.CENTER);

        // 右列：我的队伍（第一位为首发）
        teamCaption = new Label();
        teamCaption.setStyle(CAPTION_STYLE);
        teamList = new ListView<>();
        teamList.setPrefSize(268, 226);
        teamList.setStyle("-fx-font-family: 'Microsoft YaHei';");
        teamList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Pokemon item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                int index = getIndex();
                setText((index == 0 ? "★首发  " : (index + 1) + ". ") + item.getName() + " Lv." + item.getLevel());
            }
        });

        removeButton = new Button("移除选中");
        removeButton.setStyle(BUTTON_STYLE);
        removeButton.setOnAction(e -> {
            int index = teamList.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                team.remove(index);
                refreshTeamUi();
            }
        });

        leadButton = new Button("设为首发");
        leadButton.setStyle(BUTTON_STYLE);
        leadButton.setOnAction(e -> {
            int index = teamList.getSelectionModel().getSelectedIndex();
            if (index > 0) {
                team.add(0, team.remove(index)); // 移到队伍首位 = 首发
                refreshTeamUi();
                teamList.getSelectionModel().select(0);
            }
        });

        HBox teamButtons = new HBox(8, removeButton, leadButton);
        teamButtons.setAlignment(Pos.CENTER);
        VBox teamBox = new VBox(6, teamCaption, teamList, teamButtons);
        teamBox.setAlignment(Pos.CENTER);

        HBox columns = new HBox(10, candidateBox, teamBox);
        columns.setAlignment(Pos.CENTER);
        root.setCenter(columns);

        // 底部：返回 + 开始战斗
        Button back = new Button("返回");
        back.setStyle(BUTTON_STYLE);
        back.setOnAction(e -> onBack.run());

        startButton = new Button("开始战斗");
        startButton.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px;"
                + " -fx-font-weight: bold; -fx-padding: 8 24;");
        startButton.setOnAction(e -> {
            if (canStart()) {
                onStartBattle.accept(new ArrayList<>(team));
            }
        });

        HBox bottomBar = new HBox(12, back, startButton);
        bottomBar.setAlignment(Pos.CENTER);
        bottomBar.setPadding(new Insets(8, 0, 0, 0));
        root.setBottom(bottomBar);

        refreshTeamUi();
        return UiScale.scene(root);
    }

    /** 队伍成员上限：限定数量模式即该数量；小队对战模式最多 6 只。 */
    private int maxCount() {
        return requiredCount > 0 ? Math.min(requiredCount, MAX_SQUAD) : MAX_SQUAD;
    }

    /** 是否满足开战条件：限定数量模式须恰好选满；小队对战模式至少 1 只。 */
    private boolean canStart() {
        return requiredCount > 0 ? team.size() == maxCount() : !team.isEmpty();
    }

    /** 页面提示文案（按出战数量语义区分）。 */
    private String hintText() {
        if (requiredCount == 1) {
            return "选择 1 只宝可梦出战（Lv.100 满状态），对手由系统随机生成；战斗结束后返回模式选择页。";
        }
        if (requiredCount > 1) {
            return "从全部宝可梦中挑选 " + maxCount() + " 只（Lv.100 满状态），对手随机生成同数量满级队伍；"
                    + "队伍第一位为首发，战斗中可通过「精灵」菜单换人。";
        }
        return "从全部宝可梦中挑选 1~" + MAX_SQUAD + " 只（Lv.100 满状态），对手随机生成同数量满级队伍；"
                + "队伍第一位为首发，战斗中可通过「精灵」菜单换人。";
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

    /** 刷新队伍列表、标题与按钮可用状态。 */
    private void refreshTeamUi() {
        teamList.getItems().setAll(team);
        teamList.refresh();
        teamCaption.setText("我的队伍（" + team.size() + "/" + maxCount() + "）");
        addButton.setDisable(team.size() >= maxCount());
        removeButton.setDisable(team.isEmpty());
        leadButton.setDisable(team.size() < 2);
        startButton.setDisable(!canStart());
    }
}
