package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.example.GameSession;
import org.example.data.ShopStock;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

import java.util.function.Consumer;

/**
 * 商店界面（《需求文档》§4.2 商店 + §七 界面需求）：用金币购买消耗品与可携带装备。
 *
 * <p>只负责展示 {@link ShopStock} 与收集购买意图；金币校验与扣款、入包 / 入库由控制器完成 ——
 * 购买成功或失败后控制器重建本页以刷新金币与可购状态。</p>
 */
public class ShopView {

    private final GameSession session;
    private final ShopStock stock;
    private final Consumer<ShopStock.Entry> onBuy;
    private final Runnable onLeave;

    public ShopView(GameSession session, ShopStock stock,
                    Consumer<ShopStock.Entry> onBuy, Runnable onLeave) {
        this.session = session;
        this.stock = stock;
        this.onBuy = onBuy;
        this.onLeave = onLeave;
    }

    public Scene createScene() {
        VBox root = new VBox(16);
        ImageBackgrounds.apply(root, session.mapBackgroundPath());
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.TOP_LEFT);

        int gold = session.getRogueRunData().getGold();

        Label title = new Label("商店 · 第 " + stock.segment() + " 段");
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #222;");

        Label summary = new Label("持有金币：" + gold + "  🪙　　（段数越靠后，货架格数越多、售价越高）");
        summary.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px; -fx-text-fill: #555;");

        VBox header = new VBox(2, title, summary);
        if (stock.entries().stream().anyMatch(ShopStock.Entry::isEquipment)) {
            Label hint = new Label("装备购买后入库，可在主菜单点击精灵名，在详情页中穿戴。");
            hint.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #555;");
            header.getChildren().add(hint);
        }
        header.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6); -fx-background-radius: 10;"
                + "-fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 10;"
                + "-fx-padding: 8 14 10 14;");
        root.getChildren().add(header);

        if (stock.isEmpty()) {
            Label empty = new Label("本次货架是空的，下次再来看看。");
            empty.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-text-fill: #555;");
            root.getChildren().addAll(empty, buildLeaveButton());
            return UiScale.scene(root);
        }

        VBox list = new VBox(12);
        for (ShopStock.Entry entry : stock.entries()) {
            list.getChildren().add(buildEntry(entry, gold));
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        root.getChildren().addAll(scroll, buildLeaveButton());
        return UiScale.scene(root);
    }

    private VBox buildEntry(ShopStock.Entry entry, int gold) {
        boolean affordable = gold >= entry.price();

        Button buy = new Button(entry.isEquipment()
                ? "购买装备（" + entry.price() + " 金币）"
                : "购买（" + entry.price() + " 金币）");
        buy.setDisable(!affordable);
        buy.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px; -fx-padding: 8 14;");
        buy.setOnAction(e -> onBuy.accept(entry));

        Label name = new Label((entry.isEquipment() ? "【装备】" : "【道具】") + entry.itemName());
        name.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #222;");

        Label state = new Label(affordable ? "可购买" : "金币不足");
        state.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: "
                + (affordable ? "#2e7d32;" : "#b00020;"));

        VBox info = new VBox(2, name);
        if (!entry.description().isBlank()) {
            Label description = new Label(entry.description());
            description.setWrapText(true);
            description.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #444;");
            info.getChildren().add(description);
        }
        info.getChildren().add(state);

        VBox entryBox = new VBox(6, info, buy);
        entryBox.setStyle("-fx-background-color: rgba(255,255,255,0.78); -fx-padding: 10; -fx-background-radius: 8;");
        return entryBox;
    }

    private Button buildLeaveButton() {
        Button leave = new Button("离开商店");
        leave.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-padding: 8 14;");
        leave.setOnAction(e -> onLeave.run());
        return leave;
    }
}
