package org.example.view;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.save.SaveSlot;
import org.example.save.SaveStore;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

import java.util.List;
import java.util.function.Consumer;

/**
 * 存档位选择页：一屏列出 4 个存档位，供「新游戏」「继续游戏」「保存游戏」三种用途复用。
 *
 * <p>布局与商店页 / 队伍配置页同族：顶栏「返回胶囊 + 白描边深蓝字标题 + 当前档位信息卡」，
 * 内容为 2×2 紧凑方形档位卡（白底蓝环卡；当前档位金环；队伍已全倒下的档位自动变灰并标注
 * 「队伍已全倒下」，继续游戏时不可选），卡内摘要按三行窄幅排版（时间单独一行），主操作按钮为小号黄→金胶囊，
 * 「删除」为白底红字小胶囊（空档禁用）；样式见 {@code /css/start-menu.css} 的 .slot-* 系列，
 * 「返回」由 {@link FloatingMenu} 紧凑胶囊承载（与启动页同款），Esc 亦返回。</p>
 *
 * <p>每张卡展示档位名（当前档加「当前」徽章）、{@link org.example.save.SaveSummary#describe()}
 * 摘要与可用状态，按钮文案随 {@link Purpose} 变化。选择与删除结果经回调交回控制器 ——
 * 视图自身不做任何读写，删除确认、覆盖确认与失败提示也都由控制器负责。</p>
 */
public final class SaveSlotView {

    /** 页面背景（与启动页同款主画面）。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_startpage.jpg";

    /** 共享样式表（胶囊按钮/图标/暗角/标题/档位卡，与启动页同一份）。 */
    private static final String STYLE_SHEET = "/css/start-menu.css";

    /** 档位卡统一高度（设计像素；四卡等高的紧凑排列基线，容纳三行摘要与全倒下警示行）。 */
    private static final double CARD_HEIGHT = 126;

    /** 2×2 网格总宽上限（设计像素）：每卡 256 宽，两侧留白使卡片群居中收拢。 */
    private static final double GRID_MAX_WIDTH = 520;

    /** 入场动效（与内层页卡片同一组参数基线：错峰上浮淡入）。 */
    private static final double ENTRANCE_RISE = 11;
    private static final double ENTRANCE_FADE_MS = 620;
    private static final double ENTRANCE_DELAY_BASE_MS = 120;
    private static final double ENTRANCE_DELAY_STEP_MS = 95;

    /** 存档位用途：决定标题文案与按钮可用性。 */
    public enum Purpose {
        /** 新游戏选档：空档与已占用档都可选（覆盖由控制器二次确认）。 */
        NEW_GAME("选择存档位", "新游戏会清空所选档位的旧进度"),
        /** 继续游戏：只有存在且可解析、且队伍未全倒下的档位可选。 */
        CONTINUE("继续游戏", "请选择要载入的存档"),
        /** 保存游戏：写入所选档位，选定后该档位成为当前档位。 */
        SAVE("保存游戏", "把当前进度写入所选档位，并切换到该档位继续游戏");

        private final String title;
        private final String hint;

        Purpose(String title, String hint) {
            this.title = title;
            this.hint = hint;
        }

        public String title() {
            return title;
        }

        public String hint() {
            return hint;
        }
    }

    private final Purpose purpose;
    private final List<SaveStore.SlotStatus> statuses;
    private final SaveSlot currentSlot;
    private final Consumer<SaveSlot> onChoose;
    private final Consumer<SaveSlot> onDelete;
    private final Runnable onCancel;

    /** 顶栏「返回」胶囊（入场动画引用）。 */
    private FloatingMenu backMenu;

    /**
     * @param purpose     用途，决定文案与按钮可用性
     * @param statuses    四个档位状态（{@link SaveStore#statuses()} 的结果，顺序即展示顺序）
     * @param currentSlot 当前已占用的档位，可为 {@code null}（新游戏尚未选档时）
     * @param onChoose    选中某个档位后的回调
     * @param onDelete    删除某个档位存档的请求回调，可为 {@code null}（为 null 时不显示删除按钮）
     * @param onCancel    返回上一屏回调，可为 {@code null}
     */
    public SaveSlotView(Purpose purpose,
                        List<SaveStore.SlotStatus> statuses,
                        SaveSlot currentSlot,
                        Consumer<SaveSlot> onChoose,
                        Consumer<SaveSlot> onDelete,
                        Runnable onCancel) {
        this.purpose = purpose;
        this.statuses = List.copyOf(statuses);
        this.currentSlot = currentSlot;
        this.onChoose = onChoose;
        this.onDelete = onDelete;
        this.onCancel = onCancel;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);

        // 暗角遮罩：叠在背景图之上、内容层之下（与启动页同款，弱化背景保证悬浮元素可读）
        Region vignette = new Region();
        vignette.getStyleClass().add("start-vignette");
        vignette.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        vignette.setMouseTransparent(true);

        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(10, 12, 10, 12));
        layout.setTop(buildHeader());

        GridPane grid = buildGrid();
        VBox center = new VBox(grid);
        center.setAlignment(Pos.CENTER);
        BorderPane.setMargin(center, new Insets(0, 0, 8, 0));
        layout.setCenter(center);

        root.getChildren().addAll(vignette, layout);

        Scene scene = UiScale.scene(root);
        var css = SaveSlotView.class.getResource(STYLE_SHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && onCancel != null) {
                onCancel.run(); // Esc 与「返回」同义
            }
        });
        backMenu.playEntrance();
        playCardsEntrance(grid);
        return scene;
    }

    /** 顶栏一行：左「返回」胶囊（启动页同款）/ 中标题（白描边深蓝字 + 副提示）/ 右当前档位信息卡。 */
    private StackPane buildHeader() {
        Label title = new Label(purpose.title());
        title.getStyleClass().add("page-title");
        Label hint = new Label(purpose.hint());
        hint.getStyleClass().add("starter-note");
        VBox center = new VBox(2, title, hint);
        center.setAlignment(Pos.CENTER);

        backMenu = new FloatingMenu();
        backMenu.setCompact(true);
        backMenu.setDeselectOnExit(true);
        backMenu.addPill("slate", "返回", "", "", () -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
        VBox back = backMenu.node();
        back.setMaxWidth(Region.USE_PREF_SIZE);
        back.setMaxHeight(Region.USE_PREF_SIZE);

        VBox info = buildInfoPanel();

        StackPane header = new StackPane(center);
        header.getChildren().addAll(back, info);
        StackPane.setAlignment(back, Pos.CENTER_LEFT);
        StackPane.setAlignment(info, Pos.CENTER_RIGHT);
        header.setPadding(new Insets(0, 0, 8, 0));
        return header;
    }

    /** 右侧信息卡（.rogue-info 同款）：当前档位名（金色小字；未选档显示「未选择」）。 */
    private VBox buildInfoPanel() {
        Label caption = new Label("当前档位");
        caption.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 10px; -fx-font-weight: bold;"
                + " -fx-text-fill: #123c63;");
        Label slot = new Label(currentSlot == null ? "未选择" : currentSlot.displayName());
        slot.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 8.5px; -fx-font-weight: bold;"
                + " -fx-text-fill: #E6A800;");
        VBox panel = new VBox(2, caption, slot);
        panel.setMinWidth(108);
        panel.setMaxWidth(Region.USE_PREF_SIZE);
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        panel.getStyleClass().add("rogue-info");
        return panel;
    }

    /** 2×2 方形卡网格（双列 percentWidth 50 严格平分）。 */
    private GridPane buildGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setMaxWidth(GRID_MAX_WIDTH); // 收窄卡群：每卡 256 设计宽，避免横向顶满窗口
        for (int c = 0; c < 2; c++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            grid.getColumnConstraints().add(column);
        }
        for (int i = 0; i < statuses.size(); i++) {
            grid.add(buildSlotCard(statuses.get(i)), i % 2, i / 2);
        }
        return grid;
    }

    /** 单个档位卡：档位名（+当前徽章）/ 摘要 / 状态行（队伍已全倒下）/ 底部操作按钮与删除按钮。 */
    private Node buildSlotCard(SaveStore.SlotStatus status) {
        SaveSlot slot = status.slot();
        boolean wiped = status.teamWiped();

        Label name = new Label(slot.displayName());
        name.getStyleClass().add("slot-name");
        HBox nameRow = new HBox(6, name);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        if (slot == currentSlot) {
            Label badge = new Label("当前");
            badge.getStyleClass().add("slot-badge");
            nameRow.getChildren().add(badge);
        }

        Label detail = new Label(describe(status));
        detail.getStyleClass().add("slot-detail");
        detail.setWrapText(true);
        detail.setStyle("-fx-text-fill: " + detailColor(status) + ";"); // 按档位状态着色（空档灰 / 正常深灰 / 损坏红 / 全倒下灰）

        VBox texts = new VBox(2, nameRow, detail);
        if (wiped) {
            Label warn = new Label("队伍已全倒下");
            warn.getStyleClass().add("slot-warn");
            texts.getChildren().add(warn);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS); // 吸收余高：标题区置顶、按钮行贴底

        Button action = new Button(buttonText(status));
        action.getStyleClass().add("slot-action");
        action.setMinWidth(Region.USE_PREF_SIZE);
        action.setDisable(!selectable(status));
        action.setOnAction(e -> {
            if (onChoose != null) {
                onChoose.accept(slot);
            }
        });

        HBox buttonRow = new HBox(6, action);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        if (onDelete != null) {
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            Button delete = new Button("删除");
            delete.getStyleClass().add("slot-delete");
            delete.setMinWidth(Region.USE_PREF_SIZE);
            delete.setDisable(status.empty());
            delete.setOnAction(e -> onDelete.accept(slot));
            buttonRow.getChildren().addAll(gap, delete);
        }

        VBox card = new VBox(2, texts, spacer, buttonRow);
        card.setMinHeight(CARD_HEIGHT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("slot-card");
        if (slot == currentSlot) {
            card.getStyleClass().add("slot-card-current");
        }
        if (wiped) {
            card.getStyleClass().add("slot-card-wiped");
        }
        return card;
    }

    private String buttonText(SaveStore.SlotStatus status) {
        return switch (purpose) {
            case NEW_GAME -> status.empty() ? "在此开始" : "覆盖并开始";
            case CONTINUE -> "继续游戏";
            case SAVE -> status.slot() == currentSlot ? "保存到当前档" : "保存并切换到此";
        };
    }

    /** 能否选择该档位：继续游戏要求有档、可解析且队伍未全倒下；新游戏与保存游戏允许写入任意档位。 */
    private boolean selectable(SaveStore.SlotStatus status) {
        return purpose != Purpose.CONTINUE || (!status.empty() && status.usable() && !status.teamWiped());
    }

    private static String describe(SaveStore.SlotStatus status) {
        if (status.empty()) {
            return "空存档位";
        }
        if (!status.readable()) {
            return "存档已损坏或版本不受支持";
        }
        return status.summary() != null
                ? String.join("\n", status.summary().describeLines())
                : "存档信息缺失";
    }

    private static String detailColor(SaveStore.SlotStatus status) {
        if (status.empty()) {
            return "#888888";
        }
        if (!status.readable()) {
            return "#b23c3c";
        }
        return status.teamWiped() ? "#7d8794" : "#333333";
    }

    /** 卡片错峰入场：上浮 + 淡入（延迟按展示顺序递增，与内层页参数一致）。 */
    private static void playCardsEntrance(GridPane grid) {
        int index = 0;
        for (Node card : grid.getChildren()) {
            card.setOpacity(0);
            card.setTranslateY(ENTRANCE_RISE);
            PauseTransition delay = new PauseTransition(
                    Duration.millis(ENTRANCE_DELAY_BASE_MS + index * ENTRANCE_DELAY_STEP_MS));
            delay.setOnFinished(e -> {
                FadeTransition fade = new FadeTransition(Duration.millis(ENTRANCE_FADE_MS), card);
                fade.setToValue(1);
                TranslateTransition rise = new TranslateTransition(Duration.millis(ENTRANCE_FADE_MS), card);
                rise.setToY(0);
                fade.play();
                rise.play();
            });
            delay.play();
            index++;
        }
    }
}
