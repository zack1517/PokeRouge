package org.example.view;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.example.growth.GrowthProgress;
import org.example.pokemon.domain.BaseStats;
import org.example.pokemon.domain.ElementType;
import org.example.pokemon.domain.Move;
import org.example.pokemon.domain.MoveCategory;
import org.example.pokemon.domain.Species;
import org.example.util.SpriteLoader;
import org.example.util.UiScale;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 宝可梦图鉴页（Pokédex）：由启动页「宝可梦图鉴」进入，整体是一本摊在木桌上的手绘冒险图鉴。
 *
 * <p>左页为图鉴索引（编号 / 小图 / 名称，可滚动、悬停高亮、选中呈书签标签）；右页为当前
 * 宝可梦详情（编号与中英文名、大立绘、属性徽章、图鉴说明、六项基础数据条、身高体重），
 * 右页底部「基本信息 / 技能 / 进化链 / 图鉴记录」四个标签切换下方内容。</p>
 *
 * <p><b>解锁展示</b>：当前数据层不启用「遇到 / 捕获解锁」（{@link PokedexData} 开关为关），
 * 全部宝可梦直接完整展示；剪影与「？？？」渲染分支保留（由 {@link PokedexData.Entry#unlocked()}
 * 驱动），若恢复解锁机制本类无需改动。数据来自
 * {@link PokedexData}（宝可梦库 + 局外成长进度），本类只负责展示与交互。</p>
 *
 * <p>样式表：{@code /css/pokedex.css}；缺失时页面仍可打开，仅视觉退化为内联基础样式。</p>
 */
public final class PokedexView {

    /** 左页宽度（图鉴索引页，设计分辨率 px）。 */
    private static final double LEFT_PAGE_WIDTH = 206;

    /** 右页大立绘显示边长。 */
    private static final double SPRITE_SIZE = 108;

    /** 六项基础数据条的满值参考（宝可梦种族值单项约在 10~160 之间）。 */
    private static final int STAT_MAX = 160;

    /** 数据条宽度 / 高度。 */
    private static final double STAT_BAR_WIDTH = 226;
    private static final double STAT_BAR_HEIGHT = 8;

    /** 大立绘阴影（解锁态：正常投影；未解锁态：剪影后加投影）。 */
    private static final DropShadow SPRITE_SHADOW = new DropShadow(12, 0, 4, Color.rgb(70, 45, 20, 0.35));

    /** 底部标签名（顺序与渲染分支一一对应）。 */
    private static final String[] TAB_NAMES = {"基本信息", "技能", "进化链", "图鉴记录"};

    private final List<PokedexData.Entry> entries;
    private final Runnable onBack;

    private ListView<PokedexData.Entry> dexList;
    private Label numberLabel;
    private Label nameLabel;
    private Label englishLabel;
    private ImageView sprite;
    private Label spriteFallback;
    private HBox chipsRow;
    private VBox contentBox;
    private ScrollPane contentScroll;
    private VBox rightPage;
    private final List<Button> tabButtons = new ArrayList<>();

    /** 当前选中的底部标签（0 基本信息 / 1 技能 / 2 进化链 / 3 图鉴记录）。 */
    private int activeTab;
    /** 当前展示的图鉴条目；图鉴数据缺失时为 null。 */
    private PokedexData.Entry current;

    /**
     * @param growthProgress   局外成长进度（捕捉 / 对战 / 个体值加成；图鉴解锁数据源）
     * @param ownedSpeciesIds  当前队伍持有的种族 id 集合（辅助解锁判定，可为 null）
     * @param onBack           「返回」回调（回启动页）
     */
    public PokedexView(GrowthProgress growthProgress, Set<String> ownedSpeciesIds, Runnable onBack) {
        this.entries = PokedexData.build(growthProgress, ownedSpeciesIds);
        this.onBack = onBack;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("pokedex-root");
        root.setPadding(new Insets(10));
        root.setTop(buildTopBar());
        root.setCenter(buildBook());

        dexList.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> {
            if (selected != null) {
                renderDetail(selected);
            }
        });
        if (entries.isEmpty()) {
            renderEmpty();
        } else {
            int first = firstUnlockedIndex();
            dexList.getSelectionModel().select(first >= 0 ? first : 0);
        }

        Scene scene = UiScale.scene(root);
        java.net.URL css = PokedexView.class.getResource("/css/pokedex.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    // ------------------------------------------------------------------
    // 顶部：返回按钮 + 木牌标题
    // ------------------------------------------------------------------

    private Region buildTopBar() {
        Button back = new Button("← 返回");
        back.getStyleClass().add("dex-back-button");
        back.setTooltip(new Tooltip("返回启动页"));
        back.setOnAction(e -> {
            if (onBack != null) {
                onBack.run();
            }
        });

        Label title = new Label("宝可梦图鉴");
        title.getStyleClass().add("dex-sign-title");
        Label sub = new Label("POKÉDEX");
        sub.getStyleClass().add("dex-sign-sub");
        VBox signText = new VBox(-1, title, sub);
        signText.setAlignment(Pos.CENTER);
        StackPane sign = new StackPane(signText);
        sign.getStyleClass().add("dex-sign");
        sign.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Region balance = new Region(); // 与返回按钮等宽的透明占位，让木牌在整条上居中
        balance.setMinWidth(74);
        balance.setPrefWidth(74);

        BorderPane bar = new BorderPane();
        bar.setLeft(back);
        bar.setCenter(sign);
        bar.setRight(balance);
        bar.setPadding(new Insets(0, 2, 8, 2));
        return bar;
    }

    // ------------------------------------------------------------------
    // 书本：左页（索引） + 书脊 + 右页（详情）
    // ------------------------------------------------------------------

    private HBox buildBook() {
        VBox leftPage = buildLeftPage();
        Region spine = new Region();
        spine.getStyleClass().add("dex-spine");
        spine.setMinWidth(10);
        spine.setPrefWidth(10);
        spine.setMaxWidth(10);
        rightPage = buildRightPage();
        HBox.setHgrow(rightPage, Priority.ALWAYS);
        return new HBox(leftPage, spine, rightPage);
    }

    /** 左页：图鉴索引（收集进度 + 可滚动列表）。 */
    private VBox buildLeftPage() {
        Label caption = new Label("图鉴索引");
        caption.getStyleClass().add("dex-page-caption");
        long found = entries.stream().filter(PokedexData.Entry::unlocked).count();
        Label progress = new Label("已发现 " + found + " / " + entries.size());
        progress.getStyleClass().add("dex-page-progress");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(6, caption, spacer, progress);
        header.setAlignment(Pos.CENTER_LEFT);

        dexList = new ListView<>();
        dexList.getStyleClass().add("dex-list");
        dexList.getItems().setAll(entries);
        dexList.setCellFactory(list -> new DexCell());
        VBox.setVgrow(dexList, Priority.ALWAYS);

        VBox page = new VBox(6, header, dexList);
        page.getStyleClass().add("dex-page-left");
        page.setPadding(new Insets(10, 8, 10, 10));
        page.setMinWidth(LEFT_PAGE_WIDTH);
        page.setPrefWidth(LEFT_PAGE_WIDTH);
        page.setMaxWidth(LEFT_PAGE_WIDTH);
        return page;
    }

    /** 右页：编号与名称 / 属性徽章 / 大立绘 / 随标签变化的内容区 / 底部标签栏。 */
    private VBox buildRightPage() {
        numberLabel = new Label("#---");
        numberLabel.getStyleClass().add("dex-number");
        nameLabel = new Label("——");
        nameLabel.getStyleClass().add("dex-name");
        englishLabel = new Label(" ");
        englishLabel.getStyleClass().add("dex-english");
        VBox titleBox = new VBox(0, numberLabel, nameLabel, englishLabel);
        titleBox.setAlignment(Pos.CENTER);

        chipsRow = new HBox(5);
        chipsRow.setAlignment(Pos.CENTER);
        chipsRow.setMinHeight(18);

        sprite = new ImageView();
        sprite.setPreserveRatio(true);
        sprite.setSmooth(true);
        sprite.setFitWidth(SPRITE_SIZE);
        sprite.setFitHeight(SPRITE_SIZE);
        sprite.setEffect(SPRITE_SHADOW);

        spriteFallback = new Label("（暂无图片）");
        spriteFallback.getStyleClass().add("dex-sprite-fallback");
        spriteFallback.setVisible(false);
        spriteFallback.setManaged(false);

        Region pad = new Region(); // 立绘脚下的手绘光垫（“宝可梦从图鉴中浮现”）
        pad.getStyleClass().add("dex-sprite-pad");
        pad.setMinSize(88, 14);
        pad.setPrefSize(88, 14);
        pad.setMaxSize(88, 14);
        StackPane spriteHolder = new StackPane(pad, spriteFallback, sprite);
        StackPane.setAlignment(pad, Pos.BOTTOM_CENTER);
        StackPane.setMargin(pad, new Insets(0, 0, 6, 0));
        spriteHolder.setMinHeight(SPRITE_SIZE + 6);
        spriteHolder.setPrefHeight(SPRITE_SIZE + 6);

        contentBox = new VBox(6);
        contentScroll = new ScrollPane(contentBox);
        contentScroll.getStyleClass().add("dex-content-scroll");
        contentScroll.setFitToWidth(true);
        contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScroll.skinProperty().addListener((o, oldSkin, skin) -> {
            if (skin != null) {
                makeViewportTransparent(contentScroll);
            }
        });
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        VBox page = new VBox(4, titleBox, chipsRow, spriteHolder, contentScroll, buildTabBar());
        page.getStyleClass().add("dex-page-right");
        page.setPadding(new Insets(10, 12, 8, 12));
        return page;
    }

    /** 底部标签栏：四个书签式按钮，点击切换右页内容区。 */
    private HBox buildTabBar() {
        HBox bar = new HBox(4);
        bar.setAlignment(Pos.CENTER);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            Button tab = new Button(TAB_NAMES[i]);
            tab.getStyleClass().add("dex-tab");
            tab.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(tab, Priority.ALWAYS);
            final int index = i;
            tab.setOnAction(e -> switchTab(index));
            tabButtons.add(tab);
            bar.getChildren().add(tab);
        }
        refreshTabStyles();
        return bar;
    }

    private void switchTab(int index) {
        if (activeTab == index) {
            return;
        }
        activeTab = index;
        refreshTabStyles();
        renderTabContent();
        playFade(contentScroll);
    }

    private void refreshTabStyles() {
        for (int i = 0; i < tabButtons.size(); i++) {
            Button tab = tabButtons.get(i);
            tab.getStyleClass().remove("dex-tab-active");
            if (i == activeTab) {
                tab.getStyleClass().add("dex-tab-active");
            }
        }
    }

    // ------------------------------------------------------------------
    // 右页渲染：切换宝可梦 / 切换标签
    // ------------------------------------------------------------------

    /** 展示指定条目：名称、立绘、属性徽章与当前标签的内容。 */
    private void renderDetail(PokedexData.Entry entry) {
        current = entry;
        boolean unlocked = entry.unlocked();

        numberLabel.setText(entry.numberText());
        nameLabel.setText(unlocked ? entry.species().getName() : "？？？");
        englishLabel.setText(unlocked ? entry.englishName() : "？？？");

        Image image = SpriteLoader.load(entry.species().getName());
        boolean hasSprite = image != null;
        if (hasSprite) {
            sprite.setImage(image);
            if (unlocked) {
                sprite.setEffect(SPRITE_SHADOW);
                sprite.setOpacity(1);
            } else {
                // 未解锁：先整体压黑成剪影，再叠加投影（effect 链）
                DropShadow silhouetteShadow = new DropShadow(10, 0, 3, Color.rgb(70, 45, 20, 0.25));
                silhouetteShadow.setInput(new ColorAdjust(0, 0, -1, 0));
                sprite.setEffect(silhouetteShadow);
                sprite.setOpacity(0.85);
            }
        }
        sprite.setVisible(hasSprite);
        sprite.setManaged(hasSprite);
        spriteFallback.setText(unlocked ? "（暂无图片）" : "？？？");
        spriteFallback.setVisible(!hasSprite);
        spriteFallback.setManaged(!hasSprite);

        chipsRow.getChildren().clear();
        if (unlocked) {
            for (ElementType type : entry.species().getTypes()) {
                chipsRow.getChildren().add(typeChip(type));
            }
        }

        renderTabContent();
        playFade(rightPage);
    }

    /** 按当前标签重建内容区；未解锁时统一显示迷雾提示，不泄露信息。 */
    private void renderTabContent() {
        if (current == null) {
            contentBox.getChildren().clear();
            return;
        }
        if (!current.unlocked()) {
            contentBox.getChildren().setAll(buildLockedHint());
            return;
        }
        Node tabContent = switch (activeTab) {
            case 1 -> buildMovesTab();
            case 2 -> buildEvolutionTab();
            case 3 -> buildRecordTab();
            default -> buildBasicTab();
        };
        contentBox.getChildren().setAll(tabContent);
    }

    /** 基本信息：图鉴说明 -> 六项基础数据条 -> 身高体重。 */
    private VBox buildBasicTab() {
        VBox box = new VBox(6);
        Species species = current.species();

        VBox descCard = new VBox(2);
        descCard.getStyleClass().add("dex-desc-card");
        String category = species.getCategory();
        if (category != null && !category.isBlank()) {
            Label categoryLabel = new Label("分类：" + category);
            categoryLabel.getStyleClass().add("dex-desc-category");
            descCard.getChildren().add(categoryLabel);
        }
        Label desc = new Label(species.getDescription());
        desc.setWrapText(true);
        desc.getStyleClass().add("dex-desc");
        descCard.getChildren().add(desc);
        box.getChildren().add(descCard);

        BaseStats stats = species.getBaseStats();
        VBox statRows = new VBox(3);
        statRows.getChildren().addAll(
                statRow("HP", stats.getHp()),
                statRow("攻击", stats.getAttack()),
                statRow("防御", stats.getDefense()),
                statRow("特攻", stats.getSpAttack()),
                statRow("特防", stats.getSpDefense()),
                statRow("速度", stats.getSpeed()));
        box.getChildren().add(statRows);

        Label size = new Label(current.sizeText());
        size.getStyleClass().add("dex-size-text");
        box.getChildren().add(size);
        return box;
    }

    /** 技能：按习得等级升序展示学招表（等级 + 属性徽章 + 技能名 + 分类威力）。 */
    private VBox buildMovesTab() {
        VBox box = new VBox(4);
        List<PokedexData.LearnRow> rows = PokedexData.learnRows(current.species().getId());
        if (rows.isEmpty()) {
            box.getChildren().add(hint("暂无技能数据。"));
            return box;
        }
        for (PokedexData.LearnRow row : rows) {
            Label level = new Label("Lv." + row.level());
            level.getStyleClass().add("dex-move-level");
            level.setMinWidth(34);
            Label moveName = new Label(row.move().getName());
            moveName.getStyleClass().add("dex-move-name");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label meta = new Label(moveMeta(row.move()));
            meta.getStyleClass().add("dex-move-meta");
            HBox line = new HBox(6, level, typeChip(row.move().getType()), moveName, spacer, meta);
            line.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(line);
        }
        return box;
    }

    /** 进化链：完整进化线（各阶段小图 + 名称 + 进化等级箭头）；链中未解锁的形态显示剪影。 */
    private VBox buildEvolutionTab() {
        VBox box = new VBox(8);
        List<Species> chain = PokedexData.evolutionChain(current.species().getId());
        if (chain.size() <= 1) {
            box.getChildren().add(hint("这只宝可梦不会进化。"));
            return box;
        }
        HBox line = new HBox(4);
        line.setAlignment(Pos.CENTER);
        for (int i = 0; i < chain.size(); i++) {
            Species stage = chain.get(i);
            if (i > 0) {
                Species previous = chain.get(i - 1);
                VBox arrowBox = new VBox(-2);
                arrowBox.setAlignment(Pos.CENTER);
                Label level = new Label("Lv." + previous.getEvolutionLevel());
                level.getStyleClass().add("dex-evo-level");
                Label arrow = new Label("→");
                arrow.getStyleClass().add("dex-evo-arrow");
                arrowBox.getChildren().addAll(level, arrow);
                line.getChildren().add(arrowBox);
            }
            line.getChildren().add(evolutionStage(stage));
        }
        box.getChildren().add(line);
        return box;
    }

    /** 进化链单个阶段小卡：小图（未解锁为剪影）+ 名称（未解锁为？？？），当前阶段高亮。 */
    private VBox evolutionStage(Species stage) {
        PokedexData.Entry stageEntry = entryOf(stage.getId());
        boolean stageUnlocked = stageEntry != null && stageEntry.unlocked();
        boolean isCurrent = stage.getId().equals(current.species().getId());

        Label icon;
        Image image = SpriteLoader.load(stage.getName());
        if (image == null) {
            icon = new Label("?");
            icon.getStyleClass().add("dex-cell-noicon");
        } else {
            ImageView view = new ImageView(image);
            view.setFitWidth(44);
            view.setFitHeight(44);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            if (!stageUnlocked) {
                view.setEffect(new ColorAdjust(0, 0, -1, 0));
                view.setOpacity(0.85);
            }
            icon = new Label();
            icon.setGraphic(view);
        }
        Label name = new Label(stageUnlocked ? stage.getName() : "？？？");
        name.getStyleClass().add("dex-evo-name");
        VBox card = new VBox(2, icon, name);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("dex-evo-card");
        if (isCurrent) {
            card.getStyleClass().add("dex-evo-current");
        }
        return card;
    }

    /** 图鉴记录：该族的捕捉次数 / 对战次数 / 个体值加成。 */
    private VBox buildRecordTab() {
        VBox box = new VBox(5);
        box.getChildren().add(recordRow("已捕获", current.captureCount() + " 次"));
        box.getChildren().add(recordRow("已对战", current.battleCount() + " 次"));
        box.getChildren().add(recordRow("个体值加成", "+" + current.ivBonus()));
        if (current.captureCount() == 0 && current.battleCount() == 0 && current.ivBonus() == 0) {
            box.getChildren().add(hint("还没有图鉴记录：带着它或遇见它，留下冒险的足迹吧。"));
        }
        return box;
    }

    /** 未解锁提示：问号 + 迷雾文案（不泄露任何信息）。 */
    private VBox buildLockedHint() {
        Label mark = new Label("？");
        mark.getStyleClass().add("dex-locked-mark");
        Label text = new Label("尚未发现这只宝可梦。\n继续冒险，在路线上遇见或捕获它，\n图鉴就会留下它的记录。");
        text.getStyleClass().add("dex-locked-text");
        text.setWrapText(true);
        VBox box = new VBox(6, mark, text);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(8, 6, 6, 6));
        return box;
    }

    /** 图鉴数据缺失（种族库为空）时的兜底展示。 */
    private void renderEmpty() {
        numberLabel.setText("#---");
        nameLabel.setText("——");
        englishLabel.setText(" ");
        sprite.setVisible(false);
        sprite.setManaged(false);
        spriteFallback.setText("（图鉴数据缺失）");
        spriteFallback.setVisible(true);
        spriteFallback.setManaged(true);
        contentBox.getChildren().setAll(hint("没有可展示的图鉴数据：请检查 data/species.csv 资源。"));
    }

    // ------------------------------------------------------------------
    // 组件与工具
    // ------------------------------------------------------------------

    /** 图鉴索引单元格：编号 + 小图（未解锁为剪影）+ 名称（未解锁为？？？）+ 已捕获圆点。 */
    private static final class DexCell extends ListCell<PokedexData.Entry> {

        @Override
        protected void updateItem(PokedexData.Entry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label number = new Label(item.numberText());
            number.getStyleClass().add("dex-cell-number");

            StackPane iconBox = new StackPane(buildIcon(item));
            iconBox.getStyleClass().add("dex-cell-icon");
            iconBox.setMinSize(30, 30);
            iconBox.setPrefSize(30, 30);
            iconBox.setMaxSize(30, 30);

            Label name = new Label(item.unlocked() ? item.species().getName() : "？？？");
            name.getStyleClass().add("dex-cell-name");
            if (!item.unlocked()) {
                name.getStyleClass().add("dex-cell-name-locked");
            }

            HBox row = new HBox(7, number, iconBox, name);
            row.setAlignment(Pos.CENTER_LEFT);
            if (item.captureCount() > 0) {
                Region caught = new Region();
                caught.getStyleClass().add("dex-caught-dot");
                Tooltip.install(caught, new Tooltip("已捕获 " + item.captureCount() + " 次"));
                row.getChildren().add(caught);
            }
            setText(null);
            setGraphic(row);
        }

        /** 小图标：有图用图（未解锁压黑成剪影），无图回退为「?」占位。 */
        private static Node buildIcon(PokedexData.Entry item) {
            Image image = SpriteLoader.load(item.species().getName());
            if (image == null) {
                Label fallback = new Label("?");
                fallback.getStyleClass().add("dex-cell-noicon");
                return fallback;
            }
            ImageView view = new ImageView(image);
            view.setFitWidth(30);
            view.setFitHeight(30);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            if (!item.unlocked()) {
                view.setEffect(new ColorAdjust(0, 0, -1, 0));
                view.setOpacity(0.85);
            }
            return view;
        }
    }

    /** 单项基础数据行：名称 + 比例条 + 数值。 */
    private static HBox statRow(String name, int value) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("dex-stat-name");
        nameLabel.setMinWidth(34);
        Label valueLabel = new Label(String.valueOf(value));
        valueLabel.getStyleClass().add("dex-stat-value");
        valueLabel.setMinWidth(30);
        valueLabel.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(6, nameLabel, statBar(value), valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** 手绘风比例条：纸色轨 + 绿色填充（按 STAT_MAX 比例）。 */
    private static StackPane statBar(int value) {
        double ratio = Math.min(1.0, Math.max(0.0, value / (double) STAT_MAX));
        Region track = new Region();
        track.getStyleClass().add("dex-stat-track");
        track.setPrefSize(STAT_BAR_WIDTH, STAT_BAR_HEIGHT);
        double fillWidth = ratio <= 0 ? 0 : Math.max(4, STAT_BAR_WIDTH * ratio);
        Region fill = new Region();
        fill.getStyleClass().add("dex-stat-fill");
        fill.setMinWidth(fillWidth);
        fill.setPrefWidth(fillWidth);
        fill.setMaxWidth(fillWidth);
        fill.setMinHeight(STAT_BAR_HEIGHT);
        fill.setPrefHeight(STAT_BAR_HEIGHT);
        fill.setMaxHeight(STAT_BAR_HEIGHT);
        StackPane bar = new StackPane(track, fill);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinSize(STAT_BAR_WIDTH, STAT_BAR_HEIGHT);
        bar.setPrefSize(STAT_BAR_WIDTH, STAT_BAR_HEIGHT);
        bar.setMaxSize(STAT_BAR_WIDTH, STAT_BAR_HEIGHT);
        return bar;
    }

    /** 属性圆角小标签（手绘柔和配色，颜色来自 {@link PokedexData#typeStyle}）。 */
    private static Label typeChip(ElementType type) {
        PokedexData.TypeStyle style = PokedexData.typeStyle(type);
        Label chip = new Label(type.getDisplayName());
        chip.setStyle("-fx-background-color: " + style.background() + ";"
                + " -fx-border-color: " + style.border() + "; -fx-border-width: 1;"
                + " -fx-border-radius: 9; -fx-background-radius: 9;"
                + " -fx-text-fill: " + style.text() + ";"
                + " -fx-font-size: 10px; -fx-padding: 1 9 2 9;");
        return chip;
    }

    /** 图鉴记录单行（label 左、值右）。 */
    private static HBox recordRow(String label, String value) {
        Label name = new Label(label);
        name.getStyleClass().add("dex-record-label");
        name.setMinWidth(72);
        Label data = new Label(value);
        data.getStyleClass().add("dex-record-value");
        HBox row = new HBox(6, name, data);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("dex-record-row");
        return row;
    }

    /** 技能行尾注：分类 + 威力。 */
    private static String moveMeta(Move move) {
        String category = switch (move.getCategory()) {
            case PHYSICAL -> "物理";
            case SPECIAL -> "特殊";
            case STATUS -> "变化";
        };
        String power = move.getPower() > 0 ? String.valueOf(move.getPower()) : "--";
        return category + " · 威力 " + power;
    }

    private static Label hint(String text) {
        Label hint = new Label(text);
        hint.setWrapText(true);
        hint.getStyleClass().add("dex-hint");
        return hint;
    }

    private int firstUnlockedIndex() {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).unlocked()) {
                return i;
            }
        }
        return -1;
    }

    /** 按种族 id 查图鉴条目（进化链中判断各形态解锁状态用）；不存在返回 null。 */
    private PokedexData.Entry entryOf(String speciesId) {
        for (PokedexData.Entry entry : entries) {
            if (entry.species().getId().equals(speciesId)) {
                return entry;
            }
        }
        return null;
    }

    /** 简单淡入动画（切换宝可梦 / 标签时使用）。 */
    private static void playFade(Node node) {
        if (node == null) {
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(170), node);
        fade.setFromValue(0.4);
        fade.setToValue(1);
        fade.play();
    }

    /** 滚动区 viewport 默认不透明，会遮住纸张底色；按 styleClass 递归定位后置为全透明。 */
    private static void makeViewportTransparent(Parent node) {
        for (Node child : node.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains("viewport")) {
                child.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            }
            if (child instanceof Parent p) {
                makeViewportTransparent(p);
            }
        }
    }
}
