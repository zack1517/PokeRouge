package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
 * <p>每行展示档位名、{@link org.example.save.SaveSummary#describe()} 摘要与可用状态，
 * 按钮文案随 {@link Purpose} 变化。选择结果经 {@code onChoose} 回调交回控制器 ——
 * 视图自身不做任何读写，覆盖确认与失败提示也都由控制器负责。</p>
 */
public final class SaveSlotView {

    /** 与 {@link StartView} 同款主画面背景。 */
    private static final String MAIN_BACKGROUND = "/images/background/bg_main.jpeg";

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
        BorderPane root = new BorderPane();
        ImageBackgrounds.apply(root, MAIN_BACKGROUND);
        root.setPadding(new Insets(12));

        Label title = new Label(purpose.title());
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        title.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label hint = new Label(purpose.hint());
        hint.setMaxWidth(Double.MAX_VALUE);
        hint.setAlignment(Pos.CENTER);
        hint.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px; -fx-text-fill: #555555;");

        VBox rows = new VBox(8);
        for (SaveStore.SlotStatus status : statuses) {
            rows.getChildren().add(buildSlotRow(status));
        }

        Button cancel = new Button("返回");
        cancel.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 14px; -fx-padding: 6 24;");
        cancel.setOnAction(e -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
        HBox cancelBar = new HBox(cancel);
        cancelBar.setAlignment(Pos.CENTER);

        VBox card = new VBox(12, title, hint, rows, cancelBar);
        card.setMaxWidth(460);
        // BorderPane 会拉满 center 子节点高度，用 USE_PREF_SIZE 让卡片按内容收拢并垂直居中
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPadding(new Insets(18));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.65);"
                + "-fx-border-color: #c9c9c9; -fx-border-radius: 10; -fx-background-radius: 10;");
        root.setCenter(card);
        return UiScale.scene(root);
    }

    /** 单个档位一行：左侧档位名 + 摘要，右侧操作按钮。 */
    private HBox buildSlotRow(SaveStore.SlotStatus status) {
        SaveSlot slot = status.slot();

        Label name = new Label(slot.displayName() + (slot == currentSlot ? "（当前）" : ""));
        name.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 15px; -fx-font-weight: bold;");

        Label detail = new Label(describe(status));
        detail.setWrapText(true);
        detail.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px; -fx-text-fill: "
                + detailColor(status) + ";");

        VBox texts = new VBox(2, name, detail);
        HBox.setHgrow(texts, Priority.ALWAYS);

        Button action = new Button(buttonText(status));
        action.setPrefWidth(150);
        action.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px; -fx-padding: 6 12;");
        action.setDisable(!selectable(status));
        action.setOnAction(e -> {
            if (onChoose != null) {
                onChoose.accept(slot);
            }
        });

        HBox row = new HBox(12, texts, action);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 10, 8, 10));
        row.setStyle("-fx-background-color: rgba(255, 255, 255, 0.55);"
                + "-fx-border-color: #dddddd; -fx-border-radius: 8; -fx-background-radius: 8;");
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
