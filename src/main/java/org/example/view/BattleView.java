package org.example.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.MoveSlot;
import org.example.model.Pokemon;
import org.example.model.Stats;
import org.example.model.StatusCondition;
import org.example.model.Terrain;
import org.example.model.Weather;
import org.example.util.ImageBackgrounds;
import org.example.util.SpriteLoader;
import org.example.util.UiScale;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * 战斗场景布局（纯界面，无业务逻辑）。
 *
 * <p>2026-09-09 视觉改版：上中下三段横向区域 ——
 * 上：敌信息卡(左上) + 敌立绘占位(卡右侧) + 阶段/金币块(右上角)；
 * 中：中央透出背景图，右下角 [己方立绘占位 + 己方信息卡]（绘左、卡右，与敌区镜像）；
 * 下：大横面板（约界面高度 1/3），左=战斗日志，右=行动区（主菜单 2×2 网格，子菜单同区切换）；
 * 「战斗」→ 技能面板时：左块整组换成 [状态行+2×2 技能格]，右块换成技能信息卡（名称/属性/分类/威力/命中/PP，
 * 悬停技能格实时联动右块）。
 * 双方信息卡同款同尺寸（名称/属性/等级 + HP 条，EXP 行随「卡样式一致」要求移除，待确认后另寻展示位）；
 * 立绘显示精灵图片（classpath /images/pokemon/，文件名与精灵中文名一致；内建精灵无图时回退「精灵名+立绘」占位文本）；
 * 战斗背景从 bg_battle1-3 随机取一（临时素材，dev 流程桩无分段概念；道馆/Boss 专属图待流程接入后按节点切换）。</p>
 *
 * <p>全局等比缩放由 {@link org.example.util.UiScale} 统一施加（本类布局按 640×426.67 设计，3:2）。</p>
 */
public class BattleView {

    /** 面板按钮点击时的回调（由控制器绑定）。 */
    public interface Actions {
        /** 选择技能。 */
        void onMoveSelected(MoveSlot slot);

        /** 选择道具（索引为背包可用堆叠的下标）。 */
        void onItemSelected(int stackIndex);

        /** 切换到队伍中第 partyIndex 只精灵（消耗本回合行动）。 */
        void onSwitchSelected(int partyIndex);

        /** 逃跑。 */
        void onRun();

        /** 返回主菜单。 */
        void onExit();
    }

    /** 战斗背景候选（classpath；每次进入战斗随机取一，见类 javadoc）。 */
    private static final String[] BATTLE_BACKGROUNDS = {
            "/images/background/bg_battle1.jpeg",
            "/images/background/bg_battle2.jpeg",
            "/images/background/bg_battle3.jpeg"};

    // ---- 双方立绘（精灵图片；无图时回退占位文本）----
    private final ImageView playerSprite = new ImageView();
    private final ImageView enemySprite = new ImageView();
    private final Label playerSpriteFallback = new Label();
    private final Label enemySpriteFallback = new Label();

    // ---- 敌方信息（左上卡片） ----
    private final Label wildName = new Label("--");
    private final Label wildType = new Label();
    private final Label wildLv = new Label();
    private final ProgressBar wildHpBar = new ProgressBar();
    private final Label wildHpText = new Label();
    private final Label wildStatus = new Label(); // 异常状态徽章（无异常时隐藏）

    // ---- 己方信息（右下卡片，样式与敌方卡一致） ----
    private final Label playerName = new Label("--");
    private final Label playerType = new Label();
    private final Label playerLv = new Label();
    private final ProgressBar playerHpBar = new ProgressBar();
    private final Label playerHpText = new Label();
    private final Label playerStatus = new Label(); // 异常状态徽章（无异常时隐藏）

    // ---- 右上角信息块：地图阶段 / 金币（数据来自流程系统，TODO(dev) 接入前为桩文本） ----
    private final Label stageLabel = new Label("第 1 段 · 野外遭遇");
    private final Label moneyLabel = new Label("金币 500");

    // ---- 底栏：场况行 + 日志 + 行动区 ----
    /**
     * 日志可视行数（底栏内容区高约 130 逻辑；日志 15px 字号行高约 20，5 行 + 场况行 20 = 120，超出自动丢弃最旧）。
     * <p>性能红线：勿对动态中文文本启用 JavaFX 描边（Text stroke）——实测每字符矢量光栅化约 50ms 且无缓存，
     * 日志每回合重建即卡顿数秒至十余秒（2026-09-09 量化验证），故战斗文本用普通深色 Label。</p>
     */
    private static final int MAX_LOG_ROWS = 5;
    /**
     * 日志态左块外框（与右侧行动区视觉分隔）：浅白底 + 细灰边 + 圆角；
     * 纵向空间紧凑（内容区高约 130，场况 21 + 日志 5×20 + 间距 2 已占 123），故上下 padding 仅 2。
     * 技能面板态由 showMoveMenu 清除本样式（左块那时放技能格，不套日志框）。
     */
    private static final String LEFT_LOG_FRAME_CSS =
            "-fx-background-color: rgba(255, 255, 255, 0.55); -fx-background-radius: 8;"
                    + " -fx-border-color: #c9c9c9; -fx-border-width: 1; -fx-border-radius: 8;"
                    + " -fx-padding: 2 10;";
    /** 场况行（天气/场地）文本：深色粗体小字（浅底无描边，同日志区样式基调）。 */
    private final Label fieldStatus = new Label();
    private final VBox logLines = new VBox(0); // 战斗日志行（showLog 重建，保留最近 MAX_LOG_ROWS 行）
    // 左块容器：日志模式 [场况行+日志]；技能面板模式时整组替换为 [状态行+技能格]（showLog 每次渲染恢复）
    private final VBox leftPanel = new VBox(2);
    private final VBox actionBox = new VBox(5);

    // ---- 技能面板：右块技能信息卡（随面板显示，默认/悬停联动刷新文本）----
    private final Label moveInfoName = new Label("—");
    private final Label moveInfoMeta = new Label();
    private final Label moveInfoStats = new Label(); // 威力 / 命中
    private final Label moveInfoPp = new Label(); // PP（不足时红字提示）
    private final Label moveInfoEffect = new Label(); // 效果说明（无效果时隐藏该行）

    // ---- 道具信息卡（背包面板右块） ----
    private final Label itemInfoName = new Label("—");
    private final Label itemInfoDesc = new Label(); // 效果说明（无说明时显示占位提示）

    // ---- 精灵面板：右块精灵信息卡（随面板显示，悬停/默认联动刷新文本）----
    private final Label partyInfoName = new Label("—");
    private final Label partyInfoType = new Label();
    private final Label partyInfoLv = new Label();
    private final ProgressBar partyInfoHpBar = new ProgressBar();
    private final Label partyInfoHpText = new Label();
    private final Label partyInfoStatus = new Label(); // 异常状态（无则显示“无”保持行高稳定）
    private final Label partyInfoStat1 = new Label(); // 能力值行 1：攻击/防御/特攻
    private final Label partyInfoStat2 = new Label(); // 能力值行 2：特防/速度
    private final Label partyInfoExp = new Label(); // 经验进度

    private final Actions actions;

    public BattleView(Actions actions) {
        this.actions = actions;
    }

    /** 构建战斗场景（等比缩放由 {@link UiScale} 统一施加）。 */
    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        ImageBackgrounds.apply(root, BATTLE_BACKGROUNDS[(int) (Math.random() * BATTLE_BACKGROUNDS.length)]); // 背景随机铺满；卡片/色块/面板其上叠加
        root.setTop(buildTop());
        root.setCenter(buildCenter());
        root.setBottom(buildBottom());
        return UiScale.scene(root);
    }

    // ------------------------------------------------------------------
    // 构建
    // ------------------------------------------------------------------

    /** 上部区域：敌信息卡(左) + 敌立绘占位 + 右上角阶段/金币信息块。 */
    private Parent buildTop() {
        HBox top = new HBox(10);
        top.setPadding(new Insets(0, 0, 6, 0));
        top.setAlignment(Pos.TOP_LEFT); // 子块贴顶排列，右上角信息随之上移贴近角部
        top.getChildren().addAll(
                buildEnemyCard(),
                spritePane(enemySprite, enemySpriteFallback, "rgba(196, 66, 66, 0.30)"),
                spacer(),
                buildHud());
        return top;
    }

    /** 敌信息卡（左上，与己方卡同款，见 {@link #buildStatCard}）。 */
    private VBox buildEnemyCard() {
        return buildStatCard(wildName, wildType, wildLv, wildHpBar, wildHpText, wildStatus);
    }

    /** 中部区域：中央留空展示背景；右下角 [己方立绘 + 己方信息卡]（立绘在信息卡左侧，与敌区镜像）。 */
    private Parent buildCenter() {
        VBox card = buildStatCard(playerName, playerType, playerLv, playerHpBar, playerHpText, playerStatus);
        StackPane sprite = spritePane(playerSprite, playerSpriteFallback, "rgba(66, 110, 196, 0.30)");
        HBox group = new HBox(8, sprite, card);
        group.setAlignment(Pos.CENTER_LEFT);
        // 关键：center 是 StackPane，默认会把整组拉高到与中部区域同高、拉宽到同宽，导致信息卡过高且立绘落到页面最左侧；
        // 宽高都限定为内容自然尺寸（高=立绘 80；宽=立绘+卡+间距），再以 BOTTOM_RIGHT 归位到右下，fillHeight 使信息卡与敌卡同高。
        group.setMaxHeight(Region.USE_PREF_SIZE);
        group.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane center = new StackPane(group);
        StackPane.setAlignment(group, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(group, new Insets(0, 6, 8, 0));
        return center;
    }

    /** 底部区域：大横面板（半透明白，约界面高 1/4 ≈ 120）—— 左：场况行 + 日志；右：行动区。 */
    private Parent buildBottom() {
        // 左块：场况（天气/场地，无数据时留空）+ 战斗日志（深色文字；白字黑描边方案因 JavaFX 描边渲染性能
        // 问题废弃——动态中文描边每字符 ~50ms 光栅且无缓存，见 MAX_LOG_ROWS 注释）
        fieldStatus.setStyle(YH + "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #333;");
        leftPanel.setStyle(LEFT_LOG_FRAME_CSS); // 初始即日志态：带外框（showMoveMenu 切换技能面板时清除）
        leftPanel.getChildren().setAll(fieldStatus, logLines);
        VBox.setVgrow(logLines, Priority.ALWAYS);
        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        leftPanel.setAlignment(Pos.CENTER); // 内容（场况行+日志 / 技能面板组）在左块内垂直居中，不贴顶
        leftPanel.setFillWidth(true);

        // 右块：行动区（主菜单 2×2 网格 / 子菜单 / 学招 / 结算全量重建；技能面板时换为技能信息卡）
        actionBox.setAlignment(Pos.CENTER);

        HBox bottom = new HBox(10, leftPanel, actionBox);
        bottom.setPadding(new Insets(6, 12, 6, 12));
        bottom.setAlignment(Pos.CENTER);
        // 预置底栏高度略高于内容 pref（约 1/3 屏），让左/右块内容在栏内有富余空间垂直居中
        bottom.setPrefHeight(142);
        bottom.setStyle("-fx-background-color: rgba(255, 255, 255, 0.66);"
                + "-fx-background-radius: 10; -fx-border-radius: 10;");
        return bottom;
    }

    /**
     * 双方同款信息卡：上行 名称/属性徽章/等级，下行 HP 条与数值，再下行异常状态徽章
     * （无异常时整行隐藏，卡高自动回落）。
     */
    private VBox buildStatCard(Label name, Label type, Label lv,
                               ProgressBar hpBar, Label hpText, Label status) {
        VBox card = battleCard();
        HBox line1 = new HBox(6);
        line1.setAlignment(Pos.CENTER_LEFT);
        name.setStyle(YH + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1c1c1c;");
        type.setStyle(chip());
        lv.setStyle(YH + "-fx-font-size: 13px; -fx-text-fill: #6a6a6a;");
        line1.getChildren().addAll(name, type, lv);

        HBox line2 = new HBox(6);
        line2.setAlignment(Pos.CENTER_LEFT);
        hpBar.setPrefWidth(130);
        hpText.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #333; -fx-font-weight: bold;");
        line2.getChildren().addAll(hpBar, hpText);

        status.setStyle(statusChip());
        status.setManaged(false);
        status.setVisible(false);

        card.getChildren().addAll(line1, line2, status);
        return card;
    }

    /** 异常状态徽章样式（橙底深字，与属性徽章区分）。 */
    private static String statusChip() {
        return YH + "-fx-background-color: #ffe6c7; -fx-background-radius: 4;"
                + "-fx-padding: 1 8; -fx-font-size: 12px; -fx-text-fill: #a35200;"
                + "-fx-font-weight: bold;";
    }

    /**
     * 异常状态徽章文案：倒下 &gt; 主要异常 &gt; 混乱（两者可同时显示，用空格连接）；无异常返回空串。
     */
    private static String statusBadgeText(Pokemon p) {
        if (p == null) {
            return "";
        }
        if (p.isFainted()) {
            return "已倒下";
        }
        StringBuilder sb = new StringBuilder();
        if (p.getStatus() != StatusCondition.NONE) {
            sb.append(p.getStatus().getDisplayName());
        }
        if (p.isConfused()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(StatusCondition.CONFUSION.getDisplayName());
        }
        return sb.toString();
    }

    /** 把异常状态徽章刷到标签：无异常时隐藏并让出布局空间。 */
    private static void applyStatusBadge(Label target, Pokemon p) {
        String text = statusBadgeText(p);
        target.setText(text);
        boolean visible = !text.isEmpty();
        target.setManaged(visible);
        target.setVisible(visible);
    }

    /**
     * 立绘区：半透明底色底座 + 白字占位（无图时可见）+ 精灵图片（有图时覆盖占位）。
     * 图片等比缩放适配 92×80 底座，保留底色区分敌我方位（敌方红底 / 己方蓝底）。
     */
    private StackPane spritePane(ImageView imageView, Label fallback, String bgColor) {
        fallback.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        fallback.setStyle(YH + "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: rgba(255,255,255,0.95);");
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(80);
        imageView.setFitHeight(70);
        StackPane box = new StackPane(fallback, imageView);
        box.setPrefSize(92, 80);
        box.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10;"
                + "-fx-border-width: 2; -fx-border-radius: 10; -fx-border-color: rgba(255,255,255,0.9);");
        return box;
    }

    /**
     * 按精灵名加载立绘图片（classpath {@value #POKEMON_IMAGE_DIR}）；
     * 加载失败返回 {@code null}（调用方回退占位文本）。结果带缓存。
     */
    private static Image loadSprite(String pokemonName) {
        return SpriteLoader.load(pokemonName);
    }

    /** 把某只精灵的立绘刷到指定图片区：有图则显示图片；无图则隐藏图片、回退显示精灵名占位。 */
    private static void applySprite(ImageView imageView, Label fallback, Pokemon p) {
        String name = p == null ? null : p.getName();
        Image image = loadSprite(name);
        if (image != null) {
            imageView.setImage(image);
            imageView.setManaged(true);
            imageView.setVisible(true);
            fallback.setVisible(false);
            fallback.setManaged(false);
        } else {
            imageView.setImage(null);
            imageView.setVisible(false);
            imageView.setManaged(false);
            fallback.setText(name == null || name.isBlank() ? "立 绘" : name + "\n立 绘");
            fallback.setVisible(true);
            fallback.setManaged(true);
        }
    }

    /** 右上角纯文本块：当前地图阶段（上）+ 玩家金币（下，金色）。无背景框、小字号，直接叠于背景图上。 */
    private VBox buildHud() {
        stageLabel.setStyle(YH + "-fx-font-size: 10px; -fx-text-fill: #333;"
                + "-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.85), 2, 0.6, 0, 0);"); // 白晕保底可读
        moneyLabel.setStyle(YH + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #b8860b;"
                + "-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.85), 2, 0.6, 0, 0);");
        VBox hud = new VBox(1, stageLabel, moneyLabel);
        hud.setAlignment(Pos.CENTER_RIGHT);
        // 关键：HBox 默认 fillHeight 会把 hud 拉高至同行最高子块，文字随之居中悬空；
        // 限定 max 高度保持内容自然高即可贴顶排列，配合 translateY 让字形距上边框约 5px（实测：-9→顶1px、-6→顶≈5px）。
        hud.setMaxHeight(Region.USE_PREF_SIZE);
        hud.setMaxWidth(Region.USE_PREF_SIZE);
        hud.setTranslateY(-6);
        return hud;
    }

    /** 信息卡通用底：近不透明实底圆角卡。 */
    private VBox battleCard() {
        VBox card = new VBox(3);
        card.setStyle("-fx-background-color: rgba(250, 250, 250, 0.94); -fx-background-radius: 10;"
                + "-fx-padding: 6 12;");
        return card;
    }

    /** 占位弹性空白（把右上角信息块顶到最右）。 */
    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static final String YH = "-fx-font-family: 'Microsoft YaHei'; ";

    /** 属性徽章样式。 */
    private static String chip() {
        return YH + "-fx-background-color: #e3edf9; -fx-background-radius: 4;"
                + "-fx-padding: 1 8; -fx-font-size: 12px; -fx-text-fill: #2f5d9e;";
    }

    // ------------------------------------------------------------------
    // 更新
    // ------------------------------------------------------------------

    /** 刷新双方状态卡片（含异常状态徽章）与双方立绘。 */
    public void refreshPokemon(Pokemon player, Pokemon wild) {
        playerName.setText(player.getName());
        playerType.setText(typeOf(player));
        playerLv.setText("Lv." + player.getLevel());
        refreshHp(playerHpBar, playerHpText, player);
        applyStatusBadge(playerStatus, player);
        applySprite(playerSprite, playerSpriteFallback, player);

        wildName.setText(wild.getName());
        wildType.setText(typeOf(wild));
        wildLv.setText("Lv." + wild.getLevel());
        refreshHp(wildHpBar, wildHpText, wild);
        applyStatusBadge(wildStatus, wild);
        applySprite(enemySprite, enemySpriteFallback, wild);
    }

    /** 刷新天气/场地状态行（无天气/场地时清空；位于底栏日志上方）。 */
    public void refreshFieldStatus(Weather weather, Terrain terrain) {
        StringBuilder sb = new StringBuilder();
        if (weather.isActive()) {
            sb.append("天气：").append(weather.getDisplayName());
        }
        if (terrain.isActive()) {
            if (sb.length() > 0) {
                sb.append("    ");
            }
            sb.append("场地：").append(terrain.getDisplayName());
        }
        fieldStatus.setText(sb.toString());
    }

    /** 设置右上角信息块内容（阶段/金币；流程数据接入后由控制器调用，TODO(dev)）。 */
    public void setHud(String stageText, int money) {
        if (stageText != null && !stageText.isBlank()) {
            stageLabel.setText(stageText);
        }
        moneyLabel.setText("金币 " + money);
    }

    /** 显示战斗日志：先恢复左块为日志态，再按行重建 Label（仅保留最近 {@link #MAX_LOG_ROWS} 行，最新一行在底部）。 */
    public void showLog(List<String> lines) {
        restoreLeftPanel();
        logLines.getChildren().clear();
        int from = Math.max(0, lines.size() - MAX_LOG_ROWS);
        for (int i = from; i < lines.size(); i++) {
            logLines.getChildren().add(logLine(lines.get(i)));
        }
    }

    /** 战斗日志行：深色文字（与场况行同字体基调）；不换行，超长截断防溢出。 */
    private Label logLine(String line) {
        Label l = new Label(line.length() <= 40 ? line : line.substring(0, 39) + "…");
        l.setStyle(YH + "-fx-font-size: 15px; -fx-text-fill: #333;");
        l.setWrapText(false);
        return l;
    }

    // ---- 底部右侧行动区 ----

    /** 主菜单：2×2 等大网格（战斗 / 背包 / 精灵 / 逃跑，行优先）。精灵入口恒可点开（可换性判定在队伍菜单内）。 */
    public void showMainMenu(Runnable onSkills, Runnable onBag, Runnable onOpenParty) {
        actionBox.getChildren().clear();
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setAlignment(Pos.CENTER);
        grid.add(gridButton("战斗", false, e -> onSkills.run()), 0, 0);
        grid.add(gridButton("背包", false, e -> onBag.run()), 1, 0);
        // 无论是否有可换精灵都能点开队伍菜单（倒下/当前出战精灵在菜单内禁用）
        grid.add(gridButton("精灵", false, e -> onOpenParty.run()), 0, 1);
        grid.add(gridButton("逃跑", false, e -> actions.onRun()), 1, 1);
        actionBox.getChildren().add(grid);
    }

    /** 主菜单 2×2 网格按钮：等大（92×36），禁用态灰字灰底不透明。 */
    private Button gridButton(String text, boolean disabled, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button b = new Button(text);
        b.setMaxWidth(200);
        b.setStyle(YH + "-fx-font-size: 14px; -fx-pref-width: 92px; -fx-pref-height: 36px;"
                + "-fx-background-color: #ffffff; -fx-background-radius: 6;"
                + "-fx-border-color: #c9c9c9; -fx-border-radius: 6; -fx-text-fill: #222;");
        b.setDisable(disabled);
        if (disabled) {
            b.setStyle(YH + "-fx-font-size: 14px; -fx-pref-width: 92px; -fx-pref-height: 36px;"
                    + "-fx-background-color: #d9d9d9; -fx-background-radius: 6;"
                    + "-fx-border-color: #c2c2c2; -fx-border-radius: 6; -fx-text-fill: #7f7f7f;");
        }
        b.setOnAction(handler);
        return b;
    }

    /** 显示技能面板（点主菜单「战斗」后）：左块换成 [状态行+2×2 技能格]，右块换成技能信息卡；
     * 光标移到技能格上右块联动显示该技能的名称/类型·分类/威力/命中/PP；点击可用技能即出招（PP 不足灰格可看不可点）。 */
    public void showMoveMenu(List<MoveSlot> slots, Runnable onBack) {
        // ---- 左块：状态行（天气/场地 + 右侧返回按钮）----
        Button back = compactButton("返回");
        back.setOnAction(e -> onBack.run());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox statusRow = new HBox(8, fieldStatus, gap, back);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // ---- 左块：2×2 技能格（名 + PP·类型·威力 两行）----
        GridPane grid = new GridPane();
        // 格距宽松化（2026-09-09 验收微调）：列距 16、行距 12，四格舒展不拥挤
        grid.setHgap(16);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < 4; i++) {
            MoveSlot slot = i < slots.size() ? slots.get(i) : null;
            if (slot == null) {
                grid.add(skillCell("—", "未习得", true, null), i % 2, i / 2);
                continue;
            }
            boolean exhausted = slot.exhausted();
            String pp = exhausted ? "PP 不足" : "PP " + slot.getPp() + "/" + slot.getMove().getMaxPp();
            // 按钮第二行只保留 PP（2026-09-09：威力/属性不再进按钮，集中展示在右侧技能信息卡）
            Button b = skillCell(slot.getMove().getName(), pp, exhausted, slot);
            // 悬停联动：右侧信息卡切换为当前技能（PP 不足也可查看，仅不能点击出招）
            b.setOnMouseEntered(e -> updateMoveInfo(slot));
            if (!exhausted) {
                b.setOnAction(e -> actions.onMoveSelected(slot));
            }
            grid.add(b, i % 2, i / 2);
        }

        leftPanel.setStyle(""); // 技能面板态：左块是状态行+技能格，不套日志框（restoreLeftPanel 恢复）
        leftPanel.getChildren().setAll(statusRow, grid);
        actionBox.getChildren().clear();
        actionBox.getChildren().add(buildMoveInfoCard());
        updateMoveInfo(slots.isEmpty() ? null : slots.get(0)); // 打开面板默认展示第一个技能
    }

    /** 恢复底栏左块为日志态（[场况行, 日志 Label 流]）：除技能面板外的状态都经 showLog 回到该布局。 */
    private void restoreLeftPanel() {
        leftPanel.setStyle(LEFT_LOG_FRAME_CSS);
        leftPanel.getChildren().setAll(fieldStatus, logLines);
    }

    /** 技能格（两行：技能名 / PP）；grey=PP 不足格（可悬停查看详情，点击无动作）；null slot 真禁用占位。 */
    private Button skillCell(String name, String sub, boolean grey, MoveSlot slot) {
        Button b = new Button(name + "\n" + sub);
        b.setFocusTraversable(false);
        b.setStyle(YH + "-fx-font-size: 12px; -fx-pref-width: 168px; -fx-pref-height: 38px;"
                + "-fx-background-color: " + (grey ? "#e9e9e9" : "#ffffff") + "; -fx-background-radius: 6;"
                + "-fx-border-color: " + (grey ? "#c8c8c8" : "#c9c9c9") + "; -fx-border-radius: 6;"
                + "-fx-text-fill: " + (grey ? "#8a8a8a" : "#222222") + ";"
                + "-fx-line-spacing: 1; -fx-padding: 2 4;");
        b.setDisable(slot == null); // 未习得占位格真禁用（无详情可看）；PP 不足格保留悬停联动
        return b;
    }

    /** 技能信息卡（右块）：技能名 / 类型·分类 / 威力·命中 / PP / 效果（无效果时隐藏效果行）。
     * 卡宽固定为常量（效果行 wrap 上限 170 + 卡 padding 24 = 194）：否则悬停不同技能时文本长短变化会
     * 改变 actionBox pref 宽，牵动左块 2×2 技能格水平位移（2026-09-09 实测悬停短文本技能时网格右移 7px）。 */
    private VBox buildMoveInfoCard() {
        VBox card = battleCard();
        card.setPrefWidth(194);
        moveInfoName.setStyle(YH + "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1c1c1c;");
        moveInfoMeta.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #2f5d9e;");
        moveInfoStats.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #333;");
        moveInfoPp.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #333;");
        moveInfoEffect.setWrapText(true);
        moveInfoEffect.setMaxWidth(170);
        moveInfoEffect.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #777;");
        card.getChildren().addAll(moveInfoName, moveInfoMeta, moveInfoStats, moveInfoPp, moveInfoEffect);
        return card;
    }

    /** 联动刷新：把某技能数据刷到右块信息卡（null 清为占位）。 */
    private void updateMoveInfo(MoveSlot slot) {
        if (slot == null) {
            moveInfoName.setText("—");
            moveInfoMeta.setText("未习得");
            moveInfoStats.setText("");
            moveInfoPp.setText("");
            moveInfoEffect.setText("");
            moveInfoEffect.setManaged(false);
            moveInfoEffect.setVisible(false);
            return;
        }
        Move move = slot.getMove();
        boolean exhausted = slot.exhausted();
        moveInfoName.setText(move.getName());
        moveInfoMeta.setText(move.getType().getDisplayName() + " · " + categoryText(move.getCategory()));
        String power = move.isStatus() ? "—" : String.valueOf(move.getPower());
        String acc = move.getAccuracy() <= 0 ? "—" : String.valueOf(move.getAccuracy());
        moveInfoStats.setText("威力 " + power + "    命中 " + acc);
        String ppText = "PP " + slot.getPp() + " / " + slot.getMaxPp();
        moveInfoPp.setText(exhausted ? ppText + "（PP 不足）" : ppText);
        moveInfoPp.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: "
                + (exhausted ? "#c03030;" : "#333;"));
        String effect = moveEffectText(move);
        moveInfoEffect.setText(effect);
        boolean hasEffect = !effect.isEmpty();
        moveInfoEffect.setManaged(hasEffect);
        moveInfoEffect.setVisible(hasEffect);
    }

    /** 技能类别中文文案。 */
    private static String categoryText(MoveCategory category) {
        return switch (category) {
            case PHYSICAL -> "物理";
            case SPECIAL -> "特殊";
            case STATUS -> "变化";
        };
    }

    /**
     * 效果行文案：天气/场地类技能显示开启目标；附带异常状态的技能显示「可能使目标陷入X」
     * （必定触发时显示「使目标陷入X」）；无效果返回空串（调用方隐藏该行）。
     */
    private static String moveEffectText(Move move) {
        String status = inflictionText(move);
        MoveEffect effect = move.getEffect();
        if (effect == MoveEffect.NONE) {
            return status;
        }
        String fieldText;
        Weather weather = effect.toWeather();
        if (weather != null) {
            fieldText = "效果：开启" + weather.getDisplayName();
        } else {
            Terrain terrain = effect.toTerrain();
            fieldText = terrain != null ? "效果：开启" + terrain.getDisplayName() : "效果：附加";
        }
        return status.isEmpty() ? fieldText : fieldText + "\n" + status;
    }

    /** 异常状态说明行：按触发概率区分必然/概率文案；无附带异常返回空串。 */
    private static String inflictionText(Move move) {
        if (!move.hasInfliction()) {
            return "";
        }
        StatusCondition condition = move.getInflicts();
        String name = condition.getDisplayName();
        int chance = move.getInflictionChance();
        if (chance >= 100) {
            return "使目标陷入" + name + "状态";
        }
        return "可能使目标陷入" + name + "状态（" + chance + "%）";
    }

    /** 小号紧凑按钮（技能面板状态行右侧「返回」）。 */
    private Button compactButton(String text) {
        Button b = new Button(text);
        b.setStyle(YH + "-fx-font-size: 11px; -fx-padding: 2 10;"
                + "-fx-background-radius: 6; -fx-background-color: #ffffff;"
                + "-fx-border-color: #c9c9c9; -fx-border-radius: 6; -fx-text-fill: #222;");
        return b;
    }

    /** 背包中的一个可点击道具条目：{@code text} 为格内主文本，{@code detail} 为右卡说明文本。 */
    public record ItemButton(String text, boolean disabled, String detail) {

        /** 简化构造：无说明文本。 */
        public ItemButton(String text, boolean disabled) {
            this(text, disabled, "");
        }
    }

    /**
     * 显示背包面板：左块 [状态行 + 道具格（3 列，条目多于一屏时纵向滚动）]，右块道具信息卡（悬停联动）。
     * 点击可用道具即交回控制器（由控制器决定是直接对敌使用还是先选目标精灵）；
     * 无可用目标的道具格为灰格（可悬停查看说明，点击无动作）。
     */
    public void showBagMenu(List<ItemButton> items, IntConsumer onPick, Runnable onBack) {
        Button back = compactButton("返回");
        back.setOnAction(e -> onBack.run());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox statusRow = new HBox(8, fieldStatus, gap, back);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // 3 列道具格（115px/格，与精灵格同宽）：不撑高底栏，超出部分滚动
        GridPane grid = new GridPane();
        grid.setHgap(13);
        grid.setVgap(8);
        grid.setAlignment(Pos.TOP_CENTER);
        for (int i = 0; i < items.size(); i++) {
            ItemButton entry = items.get(i);
            int index = i;
            Button b = itemCell(entry.text(), entry.disabled());
            b.setOnMouseEntered(e -> updateItemInfo(entry));
            if (!entry.disabled()) {
                b.setOnAction(e -> onPick.accept(index));
            }
            grid.add(b, i % 3, i / 3);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(371);
        scroll.setPrefHeight(96);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        leftPanel.setStyle(""); // 背包面板态：左块是状态行+道具格，不套日志框（showLog 恢复）
        leftPanel.getChildren().setAll(statusRow, scroll);
        actionBox.getChildren().clear();
        actionBox.getChildren().add(buildItemInfoCard());
        updateItemInfo(items.isEmpty() ? null : items.get(0)); // 打开面板默认展示第一个道具
    }

    /** 道具格（一行：名称 ×数量）；grey=当前无可用目标（可悬停查看说明，点击无动作）。 */
    private Button itemCell(String text, boolean grey) {
        Button b = new Button(text);
        b.setFocusTraversable(false);
        b.setStyle(YH + "-fx-font-size: 12px; -fx-pref-width: 115px; -fx-pref-height: 32px;"
                + "-fx-background-color: " + (grey ? "#e9e9e9" : "#ffffff") + "; -fx-background-radius: 6;"
                + "-fx-border-color: " + (grey ? "#c8c8c8" : "#c9c9c9") + "; -fx-border-radius: 6;"
                + "-fx-text-fill: " + (grey ? "#8a8a8a" : "#222222") + "; -fx-padding: 2 4;");
        return b;
    }

    /** 道具信息卡（右块，宽度与精灵信息卡一致）：道具名 + 效果说明。 */
    private VBox buildItemInfoCard() {
        VBox card = battleCard();
        card.setPrefWidth(PARTY_INFO_CARD_WIDTH);
        card.setAlignment(Pos.TOP_LEFT);
        itemInfoName.setStyle(YH + "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1c1c1c;");
        itemInfoDesc.setWrapText(true);
        itemInfoDesc.setMaxWidth(210);
        itemInfoDesc.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #333;");
        card.getChildren().addAll(itemInfoName, itemInfoDesc);
        return card;
    }

    /** 联动刷新：把某道具的说明刷到右卡（null 清为占位）。 */
    private void updateItemInfo(ItemButton entry) {
        itemInfoName.setText(entry == null ? "—" : entry.text());
        itemInfoDesc.setText(entry == null || entry.detail().isEmpty() ? "选择道具查看说明" : entry.detail());
    }

    /**
     * 精灵面板（点主菜单「精灵」后）：与技能面板同构 —— 左块 [状态行 + 3×2 固定六格]，右块精灵信息卡。
     * 格内只显示 名称/等级/血量；悬停格子右侧联动显示该精灵完整信息（等级/属性/HP/异常状态/能力值/经验），
     * 点击健康且非当前出战的精灵即切换上场（倒下的灰格可看详情不可点，当前出战带 ★ 亦不可点）。
     * 队伍不足 6 只时空槽位置灰占位（固定六格保证布局不随队伍数量变化）。
     * 格宽较技能格窄（115 vs 168）：底栏内容区总宽 616 预算下，右卡需 235 才能完整容纳能力值最长行，
     * 三列网格 3×115+2×13=371 恰等于左块宽度（与技能格 2 列宽格互补）。
     */
    public void showPartyMenu(List<Pokemon> party, int activeIndex, IntConsumer onPick, Runnable onBack) {
        renderPartyGrid(party, activeIndex,
                idx -> !party.get(idx).isFainted() && idx != activeIndex, // 可点：健康且非当前出战
                idx -> party.get(idx).isFainted(),                        // 灰格：倒下
                onPick, onBack, null);
    }

    /**
     * 目标选择面板（背包用药后）：与精灵面板同构的 3×2 六格，格子可否点击由 {@code selectable} 决定
     * （如伤药只能选未满血且未倒下的精灵），不可选格以灰格呈现（仍可悬停查看详情）。
     * 点中合法目标即回调其队伍下标（由控制器转交 {@code useItem(item, partyIndex)}）。
     *
     * @param party       玩家队伍
     * @param activeIndex 当前出战精灵下标（仅用于 ★ 标记）
     * @param selectable  某下标是否可选作目标
     * @param onPick      选中目标时的回调（参数为队伍下标）
     * @param onBack      返回背包
     * @param hint        左块顶部提示文案（如「选择使用【伤药】的目标」），null 时不显示
     */
    public void showTargetMenu(List<Pokemon> party, int activeIndex, IntPredicate selectable,
                               IntConsumer onPick, Runnable onBack, String hint) {
        renderPartyGrid(party, activeIndex, selectable, idx -> !selectable.test(idx), onPick, onBack, hint);
    }

    /**
     * 队伍六格面板通用渲染（精灵面板 / 道具目标面板共用）：
     * 左块 [状态行 +（可选）提示行 + 3×2 固定六格]，右块精灵信息卡（悬停联动）。
     *
     * @param selectable 某下标是否可点击选中
     * @param grey       某下标是否灰格呈现（不可点但可查看详情）
     */
    private void renderPartyGrid(List<Pokemon> party, int activeIndex,
                                 IntPredicate selectable, IntPredicate grey,
                                 IntConsumer onPick, Runnable onBack, String hint) {
        leftPanel.setStyle("");
        Button back = compactButton("返回");
        back.setOnAction(e -> onBack.run());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox statusRow = new HBox(8, fieldStatus, gap, back);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        VBox head = new VBox(2, statusRow);
        if (hint != null && !hint.isEmpty()) {
            Label hintLabel = new Label(hint);
            hintLabel.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #2f5d9e;");
            head.getChildren().add(hintLabel);
        }

        // 3×2 固定六格（行优先）；列距 13 使三列网格恰填满左块（115×3+13×2=371）
        GridPane grid = new GridPane();
        grid.setHgap(13);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < 6; i++) {
            int index = i;
            Pokemon p = index < party.size() ? party.get(index) : null;
            if (p == null) {
                grid.add(partyCell("—", "空位", true, true), index % 3, index / 3);
                continue;
            }
            boolean fainted = p.isFainted();
            boolean active = index == activeIndex;
            // 格内第二行（115px 宽，文本紧凑防溢出）：Lv + HP（斜杠不带空格）/ 倒下 + 异常状态摘要
            String badge = statusBadgeText(p);
            String sub = fainted ? "已倒下" : "HP " + p.getCurrentHp() + "/" + p.getMaxHp();
            if (!fainted && !badge.isEmpty()) {
                sub = sub + "  " + badge;
            }
            Button b = partyCell((active ? "★ " : "") + p.getName(), "Lv." + p.getLevel() + "  " + sub,
                    grey.test(index), false);
            // 悬停联动：详情卡切换（倒下/出战也可查看）；点击仅合法目标可选中
            b.setOnMouseEntered(e -> updatePartyInfo(p));
            if (selectable.test(index)) {
                b.setOnAction(e -> onPick.accept(index));
            }
            grid.add(b, index % 3, index / 3);
        }

        leftPanel.getChildren().setAll(head, grid);
        actionBox.getChildren().clear();
        actionBox.getChildren().add(buildPartyInfoCard());
        updatePartyInfo(party.isEmpty() ? null : party.get(0)); // 打开面板默认展示第一只
    }

    /** 精灵格（两行：名称 / 等级+血量）：grey=倒下（可悬停查看详情、点击无动作）；空位真禁用（无详情可看）。 */
    private Button partyCell(String name, String sub, boolean grey, boolean disabled) {
        Button b = new Button(name + "\n" + sub);
        b.setFocusTraversable(false);
        b.setStyle(YH + "-fx-font-size: 12px; -fx-pref-width: 115px; -fx-pref-height: 38px;"
                + "-fx-background-color: " + (grey ? "#e9e9e9" : "#ffffff") + "; -fx-background-radius: 6;"
                + "-fx-border-color: " + (grey ? "#c8c8c8" : "#c9c9c9") + "; -fx-border-radius: 6;"
                + "-fx-text-fill: " + (grey ? "#8a8a8a" : "#222222") + ";"
                + "-fx-line-spacing: 1; -fx-padding: 2 4;");
        b.setDisable(disabled);
        return b;
    }

    /** 精灵信息卡（右块）固定宽度：内容最长行为能力值行（“攻击 100    防御 100    特攻 100”约 200px 逻辑）
     * + 卡 padding 24 + 余量 → 235。此宽度同时是背包态右空卡宽度（左右分界与精灵面板一致，切换不跳动）。 */
    private static final int PARTY_INFO_CARD_WIDTH = 235;

    /** 精灵信息卡（右块）：名称+属性+等级 / HP 条 / 异常状态 / 能力值两行 / 经验进度。
     * 卡宽与行数均固定：悬停不同精灵时文本变化只在卡内布局，不牵动左块网格（防抖原则，同技能卡）。 */
    private VBox buildPartyInfoCard() {
        VBox card = battleCard();
        card.setPrefWidth(PARTY_INFO_CARD_WIDTH);
        HBox line1 = new HBox(6);
        line1.setAlignment(Pos.CENTER_LEFT);
        partyInfoName.setStyle(YH + "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1c1c1c;");
        partyInfoType.setStyle(chip());
        partyInfoLv.setStyle(YH + "-fx-font-size: 13px; -fx-text-fill: #6a6a6a;");
        line1.getChildren().addAll(partyInfoName, partyInfoType, partyInfoLv);
        HBox line2 = new HBox(6);
        line2.setAlignment(Pos.CENTER_LEFT);
        partyInfoHpBar.setPrefWidth(118);
        partyInfoHpText.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #333; -fx-font-weight: bold;");
        line2.getChildren().addAll(partyInfoHpBar, partyInfoHpText);
        partyInfoStatus.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #777;");
        partyInfoStat1.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #333;");
        partyInfoStat2.setStyle(YH + "-fx-font-size: 12px; -fx-text-fill: #333;");
        partyInfoExp.setStyle(YH + "-fx-font-size: 11px; -fx-text-fill: #777;");
        card.getChildren().addAll(line1, line2, partyInfoStatus, partyInfoStat1, partyInfoStat2, partyInfoExp);
        return card;
    }

    /** 联动刷新：把某只精灵完整信息刷到右块详情卡（null 清为占位）。 */
    private void updatePartyInfo(Pokemon p) {
        if (p == null) {
            partyInfoName.setText("—");
            partyInfoType.setText("");
            partyInfoLv.setText("");
            partyInfoHpBar.setProgress(0);
            partyInfoHpText.setText("HP - / -");
            partyInfoStatus.setText("状态：无");
            partyInfoStat1.setText("");
            partyInfoStat2.setText("");
            partyInfoExp.setText("");
            return;
        }
        partyInfoName.setText(p.getName());
        partyInfoType.setText(typeOf(p));
        partyInfoLv.setText("Lv." + p.getLevel());
        refreshHp(partyInfoHpBar, partyInfoHpText, p);
        String badge = statusBadgeText(p);
        partyInfoStatus.setText(badge.isEmpty() ? "状态：无" : "状态：" + badge);
        Stats stats = p.getStats();
        partyInfoStat1.setText("攻击 " + stats.getAttack() + "    防御 " + stats.getDefense()
                + "    特攻 " + stats.getSpAttack());
        partyInfoStat2.setText("特防 " + stats.getSpDefense() + "    速度 " + stats.getSpeed()
                + (p.effectiveSpeed() == stats.getSpeed() ? "" : "（实际 " + p.effectiveSpeed() + "）"));
        long need = p.expToNextLevel();
        partyInfoExp.setText(need <= 0 ? "已满级" : "经验 " + p.getExp() + " / 再 " + need + " 升级");
    }


    /**
     * 显示「学会新技能」抉择：精灵已掌握 4 个技能，玩家点选一个当前技能将其遗忘并学习
     * 新技能，或点击「放弃学习」跳过。
     *
     * @param prompt    说明文字（含精灵与新技能名）
     * @param slots     当前已掌握的技能（必为 4 个）
     * @param onForget  选中槽位下标（0~3）时回调
     * @param onDecline 选择放弃学习时回调
     */
    public void showLearnMoveMenu(String prompt, List<MoveSlot> slots,
                                  IntConsumer onForget, Runnable onDecline) {
        actionBox.getChildren().clear();
        Label title = new Label(prompt);
        title.setWrapText(true);
        title.setMaxWidth(190);
        title.setStyle(YH + "-fx-font-size: 12px; -fx-font-weight: bold;");
        VBox list = new VBox(5);
        list.setAlignment(Pos.CENTER);
        for (int i = 0; i < slots.size(); i++) {
            MoveSlot slot = slots.get(i);
            int index = i;
            String pp = slot.exhausted() ? "PP 不足" : "PP " + slot.getPp() + "/" + slot.getMove().getMaxPp();
            Button b = wideButton("遗忘：" + slot.getMove().getName() + "  " + pp, false);
            b.setOnAction(e -> onForget.accept(index));
            list.getChildren().add(b);
        }
        Button decline = wideButton("放弃学习", false);
        decline.setOnAction(e -> onDecline.run());
        VBox v = new VBox(5, title, list, decline);
        v.setAlignment(Pos.CENTER);
        actionBox.getChildren().add(v);
    }

    public void clearActions() {
        actionBox.getChildren().clear();
    }

    /** 战斗结束：清空按钮，只显示结果与返回按钮。 */
    public void showResult(String summary) {
        actionBox.getChildren().clear();
        Label result = new Label(summary);
        result.setWrapText(true);
        result.setMaxWidth(190);
        result.setStyle(YH + "-fx-font-size: 14px; -fx-font-weight: bold;");
        Button back = styledButton("返回主菜单");
        back.setOnAction(e -> actions.onExit());
        VBox v = new VBox(8, result, back);
        v.setAlignment(Pos.CENTER);
        actionBox.getChildren().add(v);
    }

    // ------------------------------------------------------------------
    // 按钮工厂 / 样式
    // ------------------------------------------------------------------

    /** 通用按钮（子菜单使用，180 宽单行）。 */
    private Button wideButton(String text, boolean disabled) {
        Button b = new Button(text);
        b.setStyle(YH + "-fx-font-size: 13px; -fx-pref-width: 185px; -fx-pref-height: 30px;"
                + "-fx-background-color: #ffffff; -fx-background-radius: 6;"
                + "-fx-border-color: #c9c9c9; -fx-border-radius: 6; -fx-text-fill: #222;");
        b.setDisable(disabled);
        if (disabled) {
            b.setStyle(YH + "-fx-font-size: 13px; -fx-pref-width: 185px; -fx-pref-height: 30px;"
                    + "-fx-background-color: #d9d9d9; -fx-background-radius: 6;"
                    + "-fx-border-color: #c2c2c2; -fx-border-radius: 6; -fx-text-fill: #7f7f7f;");
        }
        return b;
    }

    private Button styledButton(String text) {
        Button b = new Button(text);
        b.setStyle(YH + "-fx-font-size: 13px;"
                + "-fx-padding: 5 16; -fx-background-radius: 6;"
                + "-fx-background-color: #ffffff; -fx-border-color: #c9c9c9;"
                + "-fx-border-radius: 6; -fx-text-fill: #222;");
        return b;
    }

    private static String typeOf(Pokemon p) {
        List<String> types = p.getSpecies().getTypes().stream()
                .map(t -> t.getDisplayName()).toList();
        return String.join(" / ", types);
    }

    private static void refreshHp(ProgressBar bar, Label text, Pokemon p) {
        double ratio = p.getMaxHp() <= 0 ? 0 : (double) p.getCurrentHp() / p.getMaxHp();
        bar.setProgress(Math.max(0, ratio));
        text.setText("HP " + p.getCurrentHp() + " / " + p.getMaxHp());
        String color;
        if (ratio > 0.5) {
            color = "limegreen";
        } else if (ratio > 0.2) {
            color = "#f0a020";
        } else {
            color = "#e04040";
        }
        bar.setStyle("-fx-accent: " + color + ";");
    }

}
