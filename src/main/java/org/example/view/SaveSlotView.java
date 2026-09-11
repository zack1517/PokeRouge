package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.save.SaveSlot;
import org.example.save.SaveStore;
import org.example.util.ImageBackgrounds;
import org.example.util.UiScale;

import java.util.List;
import java.util.function.Consumer;

/**
 * 存档位选择页：一屏列出 4 个存档位，供「新游戏」「继续游戏」「保存游戏」三种用途复用。
 *
 * <p>无卡片悬浮式布局（与启动页、自定义战斗页、初始宝可梦选择页同一套视觉体系：
 * 背景插画 + 暗角遮罩，标题、档位行与「返回」胶囊入口直接悬浮其上）：档位行为白底
 * 深蓝描边环的圆角面板，行内操作按钮为小号胶囊（禁用灰化），样式见
 * {@code /css/start-menu.css} 的 .slot-* 系列；「返回」由 {@link FloatingMenu} 承载
 * （胶囊结构与入场动画与启动页一致），Esc 亦返回。</p>
 *
 * <p>每行展示档位名、{@link org.example.save.SaveSummary#describe()} 摘要与可用状态，
 * 按钮文案随 {@link Purpose} 变化。选择结果经 {@code onChoose} 回调交回控制器 ——
 * 视图自身不做任何读写，覆盖确认与失败提示也都由控制器负责。</p>
 */
public final class SaveSlotView {

    /** 页面背景（与启动页同款主画面）。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_startpage.jpg";

    /** 共享样式表（胶囊按钮/图标/暗角/标题/表单/档位行，与启动页同一份）。 */
    private static final String STYLE_SHEET = "/css/start-menu.css";

    /** 「返回」入口的 24 单位视口单色描边图标（lucide 风格手绘简化版）。 */
    private static final String ICON_BACK = "M19 12 L5 12 M11 18 L5 12 L11 6";

    /** 档位行统一宽度（设计画布 px，四行等宽对齐；宽度按常见摘要单行放下取舍）。 */
    private static final double ROW_WIDTH = 480;

    /** 存档位用途：决定标题文案与按钮可用性。 */
    public enum Purpose {
        /** 新游戏选档：空档与已占用档都可选（覆盖由控制器二次确认）。 */
        NEW_GAME("选择存档位", "新游戏会清空所选档位的旧进度"),
        /** 继续游戏：只有存在且可解析的档位可选。 */
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
    private final Runnable onCancel;

    /**
     * @param purpose     用途，决定文案与按钮可用性
     * @param statuses    四个档位状态（{@link SaveStore#statuses()} 的结果，顺序即展示顺序）
     * @param currentSlot 当前已占用的档位，可为 {@code null}（新游戏尚未选档时）
     * @param onChoose    选中某个档位后的回调
     * @param onCancel    返回上一屏回调，可为 {@code null}
     */
    public SaveSlotView(Purpose purpose,
                        List<SaveStore.SlotStatus> statuses,
                        SaveSlot currentSlot,
                        Consumer<SaveSlot> onChoose,
                        Runnable onCancel) {
        this.purpose = purpose;
        this.statuses = List.copyOf(statuses);
        this.currentSlot = currentSlot;
        this.onChoose = onChoose;
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

        Label title = new Label(purpose.title());
        title.getStyleClass().add("subpage-title");

        Label hint = new Label(purpose.hint());
        hint.getStyleClass().add("starter-note");

        VBox rows = new VBox(6);
        rows.setAlignment(Pos.CENTER);
        for (SaveStore.SlotStatus status : statuses) {
            rows.getChildren().add(buildSlotRow(status));
        }

        FloatingMenu menu = new FloatingMenu();
        menu.addPill("slate", "返回", "BACK", ICON_BACK, () -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });

        // 三组垂直堆叠：标题+提示 / 四个档位行 / 胶囊入口（整列按内容收拢并垂直居中）
        VBox header = new VBox(14, title, hint);
        header.setAlignment(Pos.CENTER);
        VBox column = new VBox(12, header, rows, menu.node());
        column.setAlignment(Pos.CENTER);
        // 关键：BorderPane 会把 center 子节点拉满可用高度，不设上限时整列会被垂直撑开；
        // maxHeight 用内容首选高封顶后，整列按内容收拢并垂直居中。
        column.setMaxHeight(Region.USE_PREF_SIZE);
        column.setMaxWidth(Region.USE_PREF_SIZE);

        BorderPane layout = new BorderPane();
        layout.setCenter(column);
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
        menu.playEntrance();
        return scene;
    }

    /** 单个档位一行：左侧档位名 + 摘要，右侧操作按钮（白底圆角面板，四行等宽）。 */
    private HBox buildSlotRow(SaveStore.SlotStatus status) {
        SaveSlot slot = status.slot();

        Label name = new Label(slot.displayName() + (slot == currentSlot ? "（当前）" : ""));
        name.getStyleClass().add("slot-name");

        Label detail = new Label(describe(status));
        detail.getStyleClass().add("slot-detail");
        detail.setWrapText(true);
        detail.setStyle("-fx-text-fill: " + detailColor(status) + ";"); // 按档位状态着色（空档灰 / 正常深灰 / 损坏红）

        VBox texts = new VBox(1, name, detail);
        HBox.setHgrow(texts, Priority.ALWAYS);

        Button action = new Button(buttonText(status));
        action.getStyleClass().add("slot-action");
        action.setPrefWidth(120);
        action.setDisable(!selectable(status));
        action.setOnAction(e -> {
            if (onChoose != null) {
                onChoose.accept(slot);
            }
        });

        HBox row = new HBox(12, texts, action);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 12, 6, 14));
        row.setPrefWidth(ROW_WIDTH);
        row.setMaxWidth(ROW_WIDTH);
        row.getStyleClass().add("slot-row");
        return row;
    }

    private String buttonText(SaveStore.SlotStatus status) {
        return switch (purpose) {
            case NEW_GAME -> status.empty() ? "在此开始" : "覆盖并开始";
            case CONTINUE -> "继续游戏";
            case SAVE -> status.slot() == currentSlot ? "保存到当前档" : "保存并切换到此";
        };
    }

    /** 继续游戏时空档与不可用档位不能选；新游戏与保存游戏允许写入任意档位。 */
    private boolean selectable(SaveStore.SlotStatus status) {
        return purpose != Purpose.CONTINUE || status.usable();
    }

    private static String describe(SaveStore.SlotStatus status) {
        if (status.empty()) {
            return "空存档位";
        }
        if (!status.readable()) {
            return "存档已损坏或版本不受支持";
        }
        return status.summary() != null ? status.summary().describe() : "存档信息缺失";
    }

    private static String detailColor(SaveStore.SlotStatus status) {
        if (status.empty()) {
            return "#888888";
        }
        return status.readable() ? "#333333" : "#b23c3c";
    }
}
