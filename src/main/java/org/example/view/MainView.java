package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.config.AppConfig;
import org.example.model.ItemStack;
import org.example.model.Player;
import org.example.model.Pokemon;

import java.util.List;

/**
 * 主菜单视图：展示训练家队伍与背包，并给予「开始遭遇」「治疗」「退出」等入口。
 * <p>每次进入主菜单都由控制器重新构建（队伍可能在对战中变化），因此本类不做状态刷新。</p>
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

    private final Player player;
    private final Actions actions;

    public MainView(Player player, Actions actions) {
        this.player = player;
        this.actions = actions;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setTop(buildHeader());
        root.setCenter(buildContent());
        root.setBottom(buildActionBar());
        return new Scene(root, AppConfig.WINDOW_WIDTH, AppConfig.WINDOW_HEIGHT);
    }

    private Parent buildHeader() {
        Label title = new Label("宝可梦对战 · 训练家 " + player.getName());
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 20px; -fx-font-weight: bold;");
        VBox box = new VBox(title);
        box.setPadding(new Insets(0, 0, 10, 0));
        return box;
    }

    private Parent buildContent() {
        VBox left = new VBox(6);
        Label teamTitle = new Label("队伍");
        teamTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        left.getChildren().add(teamTitle);

        List<Pokemon> party = player.getParty();
        if (party.isEmpty()) {
            left.getChildren().add(new Label("你的队伍空空如也……"));
        } else {
            Pokemon active = player.getActive();
            for (int i = 0; i < party.size(); i++) {
                left.getChildren().add(buildPartyRow(party.get(i), active, i));
            }
        }
        left.setStyle("-fx-border-color: #bbb; -fx-border-radius: 6; -fx-padding: 8; -fx-background-radius: 6;");

        VBox right = new VBox(4);
        Label bagTitle = new Label("背包");
        bagTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        right.getChildren().add(bagTitle);
        List<ItemStack> stacks = player.getBag().availableStacks();
        if (stacks.isEmpty()) {
            right.getChildren().add(new Label("背包空空如也……"));
        }
        for (ItemStack stack : stacks) {
            right.getChildren().add(new Label("· " + stack.getItem().getName() + " ×" + stack.getCount()));
        }
        right.setStyle("-fx-border-color: #bbb; -fx-border-radius: 6; -fx-padding: 8; -fx-background-radius: 6;");

        HBox content = new HBox(16, left, right);
        content.setAlignment(Pos.TOP_CENTER);

        // 放入滚动区，队伍长时也能完整查看
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
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
        info.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px;"
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
