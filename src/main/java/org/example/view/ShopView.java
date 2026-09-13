package org.example.view;

import javafx.application.Platform;
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

import java.util.List;
import java.util.function.Consumer;

/**
 * 商店界面（《需求文档》§4.2 商店 + §七 界面需求）：用金币购买消耗品与可携带装备。
 *
 * <p>货架按分区展示：<b>消耗品</b>列出本段已解锁的那些（逐段增加、解锁后一直有货），<b>装备</b>为
 * 未拥有装备的全量列表（可任选购买）。只负责展示 {@link ShopStock} 与收集购买意图；金币校验与
 * 扣款、入包 / 入库由控制器完成。</p>
 *
 * <p><b>购买后原地刷新</b>：控制器调 {@link #refresh(ShopStock)}，不要重建整个 Scene ——
 * 新建 Scene 会让 {@link ScrollPane} 的滚动位置归零，玩家在长货架中段买一件东西就被弹回最上面。</p>
 */
public class ShopView {

    private final GameSession session;
    private final Consumer<ShopStock.Entry> onBuy;
    private final Runnable onLeave;

    /** 当前货架：购买后由 {@link #refresh(ShopStock)} 换成控制器下架 / 更新后的那一份。 */
    private ShopStock stock;

    private VBox header;
    private VBox list;
    private ScrollPane scroll;

    public ShopView(GameSession session, ShopStock stock,
                    Consumer<ShopStock.Entry> onBuy, Runnable onLeave) {
        this.session = session;
        this.stock = stock;
        this.onBuy = onBuy;
        this.onLeave = onLeave;
    }

    /**
     * 建立商店场景（每次进商店只调一次）。
     *
     * <p>之后货架有变动一律走 {@link #refresh(ShopStock)}，不要重新调本方法。</p>
     */
    public Scene createScene() {
        VBox root = new VBox(16);
        ImageBackgrounds.apply(root, session.mapBackgroundPath());
        root.setPadding(new Insets(12));
        root.setAlignment(Pos.TOP_LEFT);

        header = new VBox(2);
        header.setStyle("-fx-background-color: rgba(255, 255, 255, 0.6); -fx-background-radius: 10;"
                + "-fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 10;"
                + "-fx-padding: 8 14 10 14;");

        list = new VBox(12);
        scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        root.getChildren().addAll(header, scroll, buildLeaveButton());

        render();
        return UiScale.scene(root);
    }

    /**
     * 购买后原地刷新：更新金币与各条目的可购状态，并保住滚动位置。
     *
     * @param updated 刷新后的货架（已售出的装备已在其中下架）
     */
    public void refresh(ShopStock updated) {
        this.stock = updated;
        double previousScroll = scroll.getVvalue();
        render();
        // 卡片刚重建、布局还没跑完，此刻写 vvalue 会被旧的内容高度夹断；等布局结束再恢复
        Platform.runLater(() -> scroll.setVvalue(previousScroll));
    }

    /** 重建头部文案与货架卡片；不碰 Scene 与 ScrollPane 本身，滚动位置因此得以保留。 */
    private void render() {
        int gold = session.getRogueRunData().getGold();
        List<ShopStock.Entry> consumables = stock.entries().stream()
                .filter(entry -> !entry.isEquipment())
                .toList();
        List<ShopStock.Entry> equipment = stock.entries().stream()
                .filter(ShopStock.Entry::isEquipment)
                .toList();

        header.getChildren().clear();
        header.getChildren().addAll(buildTitle(), buildSummary(gold));
        if (!equipment.isEmpty()) {
            header.getChildren().add(buildHeaderHint());
        }

        list.getChildren().clear();
        if (stock.isEmpty()) {
            list.getChildren().add(buildEmptyLabel());
            return;
        }
        if (!consumables.isEmpty()) {
            list.getChildren().add(buildSectionLabel("消耗品 · 已解锁 " + consumables.size()
                    + " 件（解锁后一直有货，可重复购买）"));
            for (ShopStock.Entry entry : consumables) {
                list.getChildren().add(buildEntry(entry, gold));
            }
        }
        if (!equipment.isEmpty()) {
            list.getChildren().add(buildSectionLabel("装备 · 全部 " + equipment.size()
                    + " 件（已拥有的不再列出，可任选购买）"));
            for (ShopStock.Entry entry : equipment) {
                list.getChildren().add(buildEntry(entry, gold));
            }
        }
    }

    private Label buildTitle() {
        Label title = new Label("商店 · 第 " + stock.segment() + " 段");
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #222;");
        return title;
    }

    private Label buildSummary(int gold) {
        Label summary = new Label("持有金币：" + gold + "  🪙　　（装备全量上架，想买哪件就买哪件；"
                + "消耗品随段逐步解锁、解锁后一直有货；两者都是越靠后的段越贵）");
        summary.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px; -fx-text-fill: #555;");
        return summary;
    }

    private Label buildHeaderHint() {
        Label hint = new Label("装备购买后入库，可在主菜单点击精灵名，在详情页中穿戴。");
        hint.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: #555;");
        return hint;
    }

    private Label buildEmptyLabel() {
        Label empty = new Label("本次货架是空的，下次再来看看。");
        empty.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-text-fill: #555;");
        return empty;
    }

    /** 分区标题：把随机上架的消耗品与全量上架的装备分开，便于在长列表里定位。 */
    private Label buildSectionLabel(String text) {
        Label section = new Label(text);
        section.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 15px; -fx-font-weight: bold;"
                + "-fx-text-fill: #333; -fx-background-color: rgba(255,255,255,0.6);"
                + "-fx-background-radius: 6; -fx-padding: 6 10;");
        return section;
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
