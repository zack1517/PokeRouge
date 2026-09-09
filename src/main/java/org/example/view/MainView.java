package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;
import org.example.model.ItemStack;
import org.example.model.Player;
import org.example.model.Pokemon;

import java.util.List;

/**
 * 主菜单视图（当前阶段的地图驻留页占位，见 GameSession 类 javadoc）：铺当前段的 bg_map 随机背景，
 * 展示训练家队伍与背包，并给予「开始遭遇」「治疗」「退出」等入口。
 * <p>背景由控制器按“段”决定后传入（同段多张图固定，换段才变），本类不做任何背景状态；
 * 每次进入主菜单都由控制器重新构建（队伍可能在对战中变化），因此本类不做状态刷新。</p>
 */
public class MainView {

    /** 主菜单按钮点击回调。 */
    public interface Actions {
        void onStartBattle();

        /** 将 index 对应的精灵设为下一场战斗先发。 */
        void onSetActive(int index);

        void onHealAll();

        void onExit();
    }

    private static final String YH = "-fx-font-family: 'Microsoft YaHei'; ";

    private final Player player;
    private final Actions actions;
    private final String mapBackground; // 当前段地图背景（classpath，同一段内恒定）
    private final int segment; // 当前地图段号（仅用于展示）

    public MainView(Player player, Actions actions, String mapBackground, int segment) {
        this.player = player;
        this.actions = actions;
        this.mapBackground = mapBackground;
        this.segment = segment;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        ImageBackgrounds.apply(root, mapBackground);
        root.setPadding(new Insets(12));
        root.setTop(buildHeader());
        root.setCenter(buildContent());
        root.setBottom(buildActionBar());
        return UiScale.scene(root);
    }

    private Parent buildHeader() {
        Label title = new Label("宝可梦对战 · 训练家 " + player.getName());
        title.setStyle(YH + "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #222;");
        Label stage = new Label("第 " + segment + " 段 · 地图");
        stage.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #555;");
        VBox box = new VBox(2, title, stage);
        // 半透明 header：标题叠在地图背景上可读，同时背景明显透出（本段地图需展示）
        box.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6); -fx-background-radius: 10;"
                + " -fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 10;"
                + " -fx-padding: 8 14 10 14;");
        return box;
    }

    private Parent buildContent() {
        VBox left = new VBox(6);
        Label teamTitle = new Label("队伍");
        teamTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #000;");
        left.getChildren().add(teamTitle);

        List<Pokemon> party = player.getParty();
        if (party.isEmpty()) {
            Label empty = new Label("你的队伍空空如也……");
            empty.setStyle(YH + "-fx-text-fill: #000;");
            left.getChildren().add(empty);
        } else {
            Pokemon active = player.getActive();
            for (int i = 0; i < party.size(); i++) {
                left.getChildren().add(buildPartyRow(party.get(i), active, i));
            }
        }
        // 内容两栏为半透明白底：文字可读且地图背景明显透出（栏间留窄缝同样可见背景）
        left.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6);"
                + "-fx-border-color: #bbb; -fx-border-radius: 6; -fx-padding: 8; -fx-background-radius: 6;");

        VBox right = new VBox(4);
        Label bagTitle = new Label("背包");
        bagTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #000;");
        right.getChildren().add(bagTitle);
        List<ItemStack> stacks = player.getBag().availableStacks();
        if (stacks.isEmpty()) {
            Label empty = new Label("背包空空如也……");
            empty.setStyle(YH + "-fx-text-fill: #000;");
            right.getChildren().add(empty);
        }
        for (ItemStack stack : stacks) {
            Label itemLine = new Label("· " + stack.getItem().getName() + " ×" + stack.getCount());
            itemLine.setStyle(YH + "-fx-text-fill: #000;");
            right.getChildren().add(itemLine);
        }
        right.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6);"
                + "-fx-border-color: #bbb; -fx-border-radius: 6; -fx-padding: 8; -fx-background-radius: 6;");

        HBox content = new HBox(16, left, right);
        content.setAlignment(Pos.TOP_CENTER);

        // 放入滚动区，队伍长时也能完整查看
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        // 滚动区自身与内部 viewport 默认不透明，会遮住两栏之间的地图背景：均置为全透明
        // （viewport 无公开属性，skin 创建后按 styleClass 递归定位）
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scroll.skinProperty().addListener((o, oldSkin, skin) -> {
            if (skin != null) makeViewportTransparent(scroll);
        });
        return scroll;
    }

    private static void makeViewportTransparent(Parent node) {
        for (Node child : node.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains("viewport")) {
                child.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            }
            if (child instanceof Parent p) makeViewportTransparent(p);
        }
    }

    private HBox buildPartyRow(Pokemon p, Pokemon active, int index) {
        boolean isActive = active == p;
        String leadMark = isActive ? "★" : "  ";
        String hpText = p.isFainted() ? "已倒下" : "HP " + p.getCurrentHp() + "/" + p.getMaxHp();
        String expText = p.expToNextLevel() <= 0
                ? "EXP MAX"
                : "EXP " + p.getExp() + "/" + p.expToNextLevel();
        Label info = new Label(String.format("%s%s  Lv.%d  类型:%s   %s   %s",
                leadMark, p.getName(), p.getLevel(), typeText(p), hpText, expText));
        info.setWrapText(true);
        info.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px; -fx-text-fill: #000;"
                + (p.isFainted() ? " -fx-text-fill: #aa2222;" : ""));

        Button lead = new Button("设为先发");
        lead.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px;");
        lead.setDisable(isActive || p.isFainted());
        int idx = index;
        lead.setOnAction(e -> actions.onSetActive(idx));

        HBox row = new HBox(8, info, lead);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox buildActionBar() {
        Button battle = new Button("遭遇野生精灵");
        battle.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 15px; -fx-padding: 10 18;");
        battle.setOnAction(e -> actions.onStartBattle());

        Button heal = new Button("治疗队伍");
        heal.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px; -fx-padding: 8 14;");
        heal.setOnAction(e -> actions.onHealAll());

        Button exit = new Button("退出游戏");
        exit.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px; -fx-padding: 8 14;");
        exit.setOnAction(e -> actions.onExit());

        HBox bar = new HBox(12, battle, heal, exit);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10, 0, 0, 0));
        return bar;
    }

    private static String typeText(Pokemon p) {
        return String.join("/", p.getSpecies().getTypes().stream()
                .map(t -> t.getDisplayName()).toList());
    }
}
