package org.example.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.GameSession;
import org.example.battle.BattleDataPort;
import org.example.battle.BattleService;
import org.example.battle.BattleServices;
import org.example.config.AppConfig;
import org.example.data.GameData;
import org.example.data.ShopStock;
import org.example.growth.GrowthProgress;
import org.example.growth.GrowthService;
import org.example.integration.PokemonBattleAdapter;
import org.example.model.HeldItem;
import org.example.model.Item;
import org.example.model.Option;
import org.example.model.OptionType;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.RouteConfig;
import org.example.model.RunData;
import org.example.model.Trainer;
import org.example.save.SaveFormatException;
import org.example.save.SaveManager;
import org.example.save.SaveSlot;
import org.example.util.LogUtil;
import org.example.util.MusicPlayer;
import org.example.view.CustomBattleSetupView;
import org.example.view.CustomBattleView;
import org.example.view.ItemDexView;
import org.example.view.MainView;
import org.example.view.PokedexView;
import org.example.view.PokemonDetailView;
import org.example.view.RogueFloorView;
import org.example.view.SaveSlotView;
import org.example.view.ShopView;
import org.example.view.StartView;
import org.example.view.StarterSelectionView;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Stage;

/**
 * 主控制器：负责窗口生命周期、训练家会话，以及 启动页 ⇄ 主菜单 ⇄ 战斗/肉鸽楼层 的场景切换。
 *
 * <p>玩家（队伍/背包）在应用生命周期内唯一持有，每次战斗后返回主菜单时重新构建
 * 主视图以反映最新状态。</p>
 *
 * <p>肉鸽流程：主菜单「进入层内事件」→ 楼层选项页（点数/事件）→ WILD/ENEMY 走真实战斗、
 * HOSPITAL/RANDOM 当场结算 → 点数耗尽与楼层 BOSS 决战 → 胜利推进下一层（地图段号同步）。</p>
 *
 * <p>存档：4 个存档位由 {@link SaveManager} 管理，每个档位各自持有一份进度快照与一份图鉴成长。
 * 存档仅在<b>未作战时</b>可用（主菜单手动保存 / 读档 / 回到主菜单与推进楼层时自动保存）；
 * 战斗场景接管舞台期间 {@link #battleInProgress} 为真，手动保存与读档都会被拒绝。</p>
 *
 * <p>回合边界与启动页：主菜单「返回主界面」与肉鸽一轮结束（通关 / 战败）都<b>不</b>退出程序，
 * 而是先落盘再回到启动页 —— 玩家在启动页选择「开始游戏」（新游戏）或「继续游戏」（读档）；
 * 真正退出仍只由窗口关闭按钮的二次确认负责。</p>
 */
public class MainController {

    private final Stage stage;
    private GameSession session;
    private Player player;

    /** 存档编排器：默认落在用户主目录下的存档目录。 */
    private final SaveManager saveManager = SaveManager.defaultManager();
    /** 当前占用的存档位；尚未选档（如首次进入选初始精灵前）为 null。 */
    private SaveSlot activeSlot;
    /** 是否正处于战斗中：战斗中不允许存档（战斗结果尚未落定，存下来会是半场状态）。 */
    private boolean battleInProgress;

    public MainController(Stage stage) {
        this.stage = stage;
    }

    /**
     * 组装层统一入口：创建野生战引擎并注入数据端口与成长端口。
     *
     * <p>成长端口由外部成长模块实现（经验 / 升级 / 学招 / 进化判定 + 图鉴进度），
     * 战斗模块自身不承担成长规则。</p>
     */
    private BattleService newWildBattle(Player player, Pokemon wild) {
        BattleDataPort dataPort = PokemonBattleAdapter.battleDataPort();
        return BattleServices.newBattle(player, wild, dataPort,
                PokemonBattleAdapter.battleGrowthPort(dataPort, growthProgress()));
    }

    /** 组装层统一入口：创建训练师轮战引擎并注入数据端口与成长端口。 */
    private BattleService newTrainerBattle(Player player, Trainer trainer) {
        BattleDataPort dataPort = PokemonBattleAdapter.battleDataPort();
        return BattleServices.newTrainerBattle(player, trainer, dataPort,
                PokemonBattleAdapter.battleGrowthPort(dataPort, growthProgress()));
    }

    /** 本次会话的局外成长进度：捕捉次数 / 对战次数 / 个体值加成（图鉴数据来源）。 */
    private GrowthProgress growthProgress() {
        return session != null ? session.getGrowthProgress() : GrowthProgress.instance();
    }

    /** 对手等级锚点：队伍中宝可梦的最高等级（空队伍兑底 1）。 */
    private int highestPartyLevel() {
        return player.getParty().stream().mapToInt(Pokemon::getLevel).max().orElse(1);
    }

    /** 游戏第一屏：启动页（「开始游戏」进入初始宝可梦选择；「继续游戏」选存档位后读档；「宝可梦图鉴」「道具图鉴」进入图鉴页；「自定义战斗」进入模式选择页）。 */
    public void showStartScreen() {
        MusicPlayer.playBgm(AppConfig.BGM_START); // 主界面 BGM（循环；文件缺失静默降级）
        stage.setScene(new StartView(this::showStarterSelection, this::showContinueSelection,
                saveManager.store().hasAnySave(), this::showCustomBattle, this::showPokedex,
                this::showItemDexFromStart).createScene());
    }

    /**
     * 道具图鉴页·启动页入口：此时尚未读档，没有 {@link Player}，因此传 {@code null} 让图鉴
     * 按「全部未拥有」只读展示（无穿戴 / 脱下操作，仅看效果与售价）。「返回」回到启动页；
     * 主菜单内的道具图鉴入口（{@link #showItemDex()}）仍带玩家数据，可直接穿脱。
     */
    public void showItemDexFromStart() {
        stage.setScene(new ItemDexView(null, null, this::showStartScreen, "返回主界面").createScene());
    }

    /**
     * 宝可梦图鉴页：由启动页「宝可梦图鉴」进入。
     *
     * <p>数据 = 宝可梦库（种族 / 技能）+ 局外成长进度（捕捉 / 对战记录，供「图鉴记录」标签）。
     * 当前图鉴不启用解锁机制、直接全部展示（见 {@code PokedexData} 解锁开关）；队伍持有的种族
     * 仍照常传入，以便恢复解锁口径时无需改动本类。「返回」回到启动页。</p>
     */
    public void showPokedex() {
        Set<String> ownedSpeciesIds = session == null ? Set.of()
                : session.getPlayer().getParty().stream()
                        .map(pokemon -> pokemon.getSpecies().getId())
                        .collect(Collectors.toSet());
        stage.setScene(new PokedexView(growthProgress(), ownedSpeciesIds, this::showStartScreen).createScene());
    }

    /** 自定义战斗模式选择页：由启动页「自定义战斗」进入；四种模式均已接入真实战斗。 */
    public void showCustomBattle() {
        stage.setScene(new CustomBattleView(this::showStartScreen, this::showCustomBattleSetup).createScene());
    }

    /**
     * 自定义战斗·队伍配置入口：按模式决定双方出战数量
     * （1 vs 1 → 1 只；2 vs 2 → 2 只；小队对战 → 1~6 只任意；自定义数量 → 先选数量）。
     */
    private void showCustomBattleSetup(CustomBattleView.Mode mode) {
        switch (mode) {
            case ONE_V_ONE -> showCustomBattleSetup("1 vs 1", 1);
            case TWO_V_TWO -> showCustomBattleSetup("2 vs 2", 2);
            case SQUAD -> showCustomBattleSetup("小队对战", 0);
            case CUSTOM_COUNT -> askCustomBattleCount();
        }
    }

    /** 显示队伍配置页：requiredCount > 0 须恰好选满，为 0 按小队规则 1~6 只任意；「返回」回模式选择页。 */
    private void showCustomBattleSetup(String title, int requiredCount) {
        stage.setScene(new CustomBattleSetupView(title, requiredCount, this::showCustomBattle, this::startCustomBattle)
                .createScene());
    }

    /** 「自定义数量」：先选择双方出战数量（1~6），再进入队伍配置页；取消则留在模式选择页。 */
    private void askCustomBattleCount() {
        ChoiceDialog<Integer> dialog = new ChoiceDialog<>(3, 1, 2, 3, 4, 5, 6);
        dialog.setTitle("自定义数量战斗");
        dialog.setHeaderText(null);
        dialog.setContentText("选择双方出战数量（1~6 只）：");
        dialog.showAndWait().ifPresent(count -> showCustomBattleSetup("自定义数量（" + count + "）", count));
    }

    /**
     * 自定义战斗开战：选定队伍交给战斗系统（首只首发，战斗中可经「精灵」菜单换人），
     * 对手为随机生成的同数量同级整队（暂无完整 AI 训练家逻辑）；战斗结束回模式选择页。
     *
     * <p>独立模式：使用独立对战玩家，不影响主流程的 {@link #player} 与 {@link #session}。</p>
     */
    private void startCustomBattle(List<org.example.pokemon.domain.Pokemon> squad) {
        if (squad == null || squad.isEmpty()) {
            showCustomBattle();
            return;
        }
        Player squadPlayer = PokemonBattleAdapter.createBattlePlayer("自定义训练家", squad);
        Trainer foe = PokemonBattleAdapter.createSquadTrainer("自定义对手", squad.size(), squad.get(0).getLevel());
        try {
            BattleService engine = newTrainerBattle(squadPlayer, foe);
            MusicPlayer.stop(); // 对局界面暂无 BGM（与肉鸽战斗保持一致）
            int segment = session != null ? session.getSegment() : 1; // 独立模式：未走主线时用默认段号
            stage.setScene(new BattleController(engine, this::showCustomBattle, segment).createScene());
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
            showCustomBattle();
        }
    }

    /** 初始宝可梦选择页：使用新 pokemon 系统选择初始宝可梦（由启动页「开始游戏」进入）。 */
    public void showStarterSelection() {
        // 离开主界面即停 BGM：其它界面暂未配置音乐；
        // 后续各界面各有 BGM 时，改为在对应界面入口调 MusicPlayer.playBgm（自动停旧播新）
        MusicPlayer.stop();
        stage.setScene(new StarterSelectionView(this::chooseSlotForNewGame, this::showStartScreen).createScene());
    }

    /** 选好初始宝可梦后先选存档位：新游戏会清空所选的档位。 */
    private void chooseSlotForNewGame(String trainerName, org.example.pokemon.domain.Pokemon starter) {
        stage.setScene(new SaveSlotView(SaveSlotView.Purpose.NEW_GAME, saveManager.store().statuses(),
                null,
                slot -> confirmNewGame(slot, trainerName, starter),
                this::showStartScreen).createScene());
    }

    /** 覆盖已有档位需要玩家二次确认（旧进度到此不可恢复）。 */
    private void confirmNewGame(SaveSlot slot, String trainerName, org.example.pokemon.domain.Pokemon starter) {
        if (!saveManager.store().status(slot).empty() && !confirmOverwrite(slot)) {
            chooseSlotForNewGame(trainerName, starter);
            return;
        }
        GameSession created;
        try {
            created = saveManager.newGame(slot, trainerName, starter);
        } catch (RuntimeException ex) {
            LogUtil.info("[MainController] 开新游戏失败：" + slot + "（" + ex.getMessage() + "）");
            infoAlert("无法开始", "清空并初始化 " + slot.displayName() + " 时出错：\n" + ex.getMessage());
            chooseSlotForNewGame(trainerName, starter);
            return; // 失败时不动 activeSlot / session，避免把旧会话写进新档位
        }
        this.activeSlot = slot;
        this.session = created;
        this.player = created.getPlayer();
        autoSave(); // 立刻落一次盘，玩家此后即使直接关窗口也有档可继续
        showMainMenu();
    }

    /** 启动页「继续游戏」：选档位后读档进入主菜单。 */
    private void showContinueSelection() {
        MusicPlayer.stop();
        stage.setScene(new SaveSlotView(SaveSlotView.Purpose.CONTINUE, saveManager.store().statuses(),
                activeSlot,
                this::loadFromSlot,
                this::showStartScreen).createScene());
    }

    /**
     * 读档并进入主菜单；档位无存档、存档损坏、或那一轮远征已经结束时给出提示并留在选档页。
     *
     * <p>「本轮已结束」的档位不载入：结束的一轮（通关 / 战败）没有可继续的内容，载进去只会
     * 卡在楼层页。此时局内读档保持当前会话不动，从启动页进来的则回到启动页去开新游戏。</p>
     */
    private void loadFromSlot(SaveSlot slot) {
        try {
            Optional<GameSession> loaded = saveManager.load(slot);
            if (loaded.isEmpty()) {
                infoAlert("无法继续", slot.displayName() + " 里没有可用的存档（或队伍已全部失效）。");
                return;
            }
            if (loaded.get().isRogueRunFinished()) {
                infoAlert("本轮已结束", slot.displayName() + " 里的一轮远征已经结束，无法继续。\n"
                        + "请选择其它存档位，或返回后开始新游戏。");
                if (session == null || player == null) {
                    showStartScreen(); // 启动页进来的：直接回启动页选「开始游戏」
                }
                return; // 局内读档：留在选档页，当前进度不受影响
            }
            this.activeSlot = slot;
            this.session = loaded.get();
            this.player = session.getPlayer();
            showMainMenu();
        } catch (SaveFormatException ex) {
            LogUtil.info("[MainController] 读档失败：" + slot + "（" + ex.getMessage() + "）");
            infoAlert("存档损坏", slot.displayName() + " 的内容无法解析：\n" + ex.getMessage());
        } catch (RuntimeException ex) {
            LogUtil.info("[MainController] 读档异常：" + slot + "（" + ex.getMessage() + "）");
            infoAlert("读档失败", "读取 " + slot.displayName() + " 时出错：" + ex.getMessage());
        }
    }

    /** 显示主菜单（重新构建，反映最新的队伍/背包），并在进入时自动保存一次。 */
    public void showMainMenu() {
        autoSave(); // 回到主菜单意味着不在战斗中，可安全落盘
        MainView view = new MainView(player, new MainView.Actions() {
            @Override
            public void onStartRogueFloor() {
                startRogueFloor();
            }

            @Override
            public void onSetActive(int index) {
                session.setActive(index);
                showMainMenu();
            }

            @Override
            public void onSaveGame() {
                manualSave();
            }

            @Override
            public void onLoadGame() {
                showLoadSelection();
            }

            @Override
            public void onExit() {
                returnToStartScreen();
            }

            @Override
            public void onShowPokemonDetail(int index) {
                showPokemonDetail(index);
            }

            @Override
            public void onShowItemDex() {
                showItemDex();
            }

            @Override
            public void onBackToStart() {
                showStartScreen();
            }
        }, session.mapBackgroundPath(), session.getSegment(),
                session.getRogueRunData().isNotStarted() ? -1 : session.getRogueRunData().getGold(),
                activeSlot == null ? null : activeSlot.displayName());
        stage.setScene(view.createScene());
    }

    /** 精灵详情页：由主菜单点击精灵名进入；左列表切换精灵、右侧属性/技能/装备（穿戴立即生效）；「返回」重建主菜单。 */
    public void showPokemonDetail(int initialIndex) {
        stage.setScene(new PokemonDetailView(player, initialIndex, session.mapBackgroundPath(), this::showMainMenu)
                .createScene());
    }

    /**
     * 道具图鉴页：由主菜单「道具图鉴」按钮进入。
     *
     * <p>全量列出商店商品目录（16 件消耗品 + 77 件装备），标注已拥有 / 未拥有与穿戴者；
     * 已拥有的装备可在本页直接穿戴 / 脱下（写的就是玩家装备库，与详情页共用同一模型方法），
     * 因此这里不做二次校验，也不与金币 / 存档交互。「返回」重建主菜单以同步队伍变化。</p>
     */
    public void showItemDex() {
        stage.setScene(new ItemDexView(player, session.mapBackgroundPath(), this::showMainMenu).createScene());
    }

    /**
     * 离开当前这一局、回到初始主界面（启动页）：先落盘再释放会话，玩家可在启动页
     * 选择「开始游戏」（新游戏）或「继续游戏」（读档）。
     *
     * <p>本方法<b>不</b>退出程序 —— 主菜单的「返回主界面」按钮与一轮远征结束（通关 / 战败）
     * 都走这里；主动退出仍只由窗口关闭按钮的二次确认负责。</p>
     */
    private void returnToStartScreen() {
        autoSave(); // 离开前把当前进度落盘，避免「返回主界面」被误解为丢档
        session = null;
        player = null;
        activeSlot = null;
        showStartScreen();
    }

    // ------------------------------------------------------------------
    // 存档：手动保存 / 读取存档 / 自动保存（均仅在未作战时进行）
    // ------------------------------------------------------------------

    /**
     * 读取存档：先让玩家在 4 个档位里选一个再载入（由主菜单「读取存档」进入）。
     *
     * <p>列表只提供可解析的档位（空档与损坏档不可选）；载入前会先把当前进度自动
     * 落盘到 {@link #activeSlot}，因此中途换档不会丢掉本局进展。载入成功后所选档位
     * 成为当前档位，之后的自动存档都记到它名下。</p>
     */
    private void showLoadSelection() {
        if (battleInProgress) {
            infoAlert("战斗中无法读档", "请先结束当前战斗，回到主菜单后再读取存档。");
            return;
        }
        if (session == null || player == null) {
            return;
        }
        autoSave(); // 换档前保护当前进度：读档后内存里的这一局将被替换
        stage.setScene(new SaveSlotView(SaveSlotView.Purpose.CONTINUE, saveManager.store().statuses(),
                activeSlot,
                this::loadFromSlot,
                this::showMainMenu).createScene());
    }

    /**
     * 手动保存：先让玩家在 4 个档位里选一个再写入。
     *
     * <p>每次手动保存都会经过选档页（当前档位标注「当前」），因此既能把进度写回当前档，
     * 也能写到其他档位；写入后所选档位成为当前档位，之后的自动存档都记到它名下。</p>
     */
    private void manualSave() {
        if (battleInProgress) {
            infoAlert("战斗中无法存档", "请先结束当前战斗，回到主菜单后再保存。");
            return;
        }
        if (session == null || player == null) {
            return;
        }
        chooseSlotToSave();
    }

    /**
     * 选择要写入的档位。
     *
     * <p>写到自己以外的**已有**档位才需要二次确认（覆盖会删掉那边的旧进度与图鉴成长）；
     * 写回当前档是常规保存，不打扰玩家。保存成功后回到主菜单 —— 此时主菜单标题栏
     * 显示的是新档位名，自动存档也随之改到新档。</p>
     */
    private void chooseSlotToSave() {
        stage.setScene(new SaveSlotView(SaveSlotView.Purpose.SAVE, saveManager.store().statuses(),
                activeSlot,
                slot -> {
                    if (slot != activeSlot && !saveManager.store().status(slot).empty()
                            && !confirmOverwrite(slot)) {
                        chooseSlotToSave(); // 取消覆盖：留在选档页重选
                        return;
                    }
                    if (!saveToSlot(slot)) {
                        chooseSlotToSave(); // 写入失败：留在选档页（原因已由 saveToSlot 弹窗说明）
                        return;
                    }
                    activeSlot = slot; // 选定后该档位成为当前档位，后续自动存档都写这里
                    showMainMenu();
                    infoAlert("保存成功", "进度已保存到 " + slot.displayName()
                            + "，之后的自动存档也会记录到这个档位。");
                },
                this::showMainMenu).createScene());
    }

    /** 手动保存，失败时弹窗告知原因（不吞异常）。 */
    private boolean saveToSlot(SaveSlot slot) {
        try {
            saveManager.save(slot, player, session);
            return true;
        } catch (RuntimeException ex) {
            LogUtil.info("[MainController] 手动存档失败：" + slot + "（" + ex.getMessage() + "）");
            infoAlert("保存失败", "写入 " + slot.displayName() + " 时出错：\n" + ex.getMessage());
            return false;
        }
    }

    /** 自动保存：未选档位或资源缺失时静默跳过（不能因为存档打断游戏）。 */
    private void autoSave() {
        if (activeSlot == null || session == null || player == null || battleInProgress) {
            return;
        }
        saveManager.autoSave(activeSlot, player, session);
    }

    /** 覆盖确认：两个选项分别是「覆盖」与「取消」，默认取消。 */
    private boolean confirmOverwrite(SaveSlot slot) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("覆盖存档");
        alert.setHeaderText(null);
        alert.setContentText("覆盖会永久删除 " + slot.displayName() + " 的旧进度与图鉴成长，确定继续吗？");
        ButtonType overwrite = new ButtonType("覆盖", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(overwrite, cancel);
        return alert.showAndWait().filter(overwrite::equals).isPresent();
    }

    // ------------------------------------------------------------------
    // 路线节点流程：节点页 ⇄ 真实战斗 ⇄ 必然节点（道馆 / 四天王 / 冠军）
    // ------------------------------------------------------------------

    /** 进入路线节点页：无进行中的一轮则新开，有则继续当前进度。 */
    public void startRogueFloor() {
        if (session == null || player == null) {
            return;
        }
        if (player.getParty().isEmpty()) {
            infoAlert("队伍为空", "先选择并加入宝可梦再开始路线推进。");
            return;
        }
        RunData data = session.getRogueRunData();
        boolean inProgress = !data.isNotStarted() && !session.isRogueRunFinished();
        if (!inProgress) {
            runEndReason = null;
            session.startRogueRun(); // 新开一轮：从第 1 段起，行动点重置，队伍快照进 RogueTurnManager
        } else {
            session.syncRogueTeam(); // 继续当前轮：把中途新入队的精灵同步进快照
        }
        showRogueFloorScene();
    }

    /** 显示路线节点页（每次重绘反映最新行动点 / 金币 / 节点状态）。 */
    private void showRogueFloorScene() {
        stage.setScene(new RogueFloorView(session, this::handleRogueOption, this::showMainMenu).createScene());
    }

    /**
     * 路线节点统一入口：必然节点（道馆 / 四天王 / 冠军）直接开战且不消耗行动点；
     * 其余节点先扣行动点，再按类型分发——战斗节点接管为真实战斗，医院 / 特殊事件当场结算，
     * 商店打开购买界面。
     */
    private void handleRogueOption(Option option) {
        if (option == null || session == null) {
            return;
        }
        OptionType type = option.getType();
        if (type != null && type.isMandatory()) {
            startMandatoryBattle(type);
            return;
        }
        if (!session.enterRogueNode(option)) {
            showRogueFloorScene(); // 已走过 / 行动点不足：重绘提示
            return;
        }
        switch (type) {
            case WILD -> startRogueWildBattle(option);
            case TRAINER -> startRogueTrainerBattle(option);
            case ROCKET -> startRocketBattle();
            case ROCKET_CAPTURE -> startRocketCaptureBattle();
            case LEGENDARY -> startLegendaryBattle();
            case HOSPITAL -> resolveNonBattleNode(option);
            case SHOP -> openShop();
            case REWARD -> {
                resolveRogueEquipmentReward();
                finishNodeStep(true);
            }
            case TRADE -> {
                resolveTradeEvent();
                finishNodeStep(true);
            }
            default -> showRogueFloorScene();
        }
    }

    /** REWARD 事件：随机装备入库（已拥有则落空提示）。 */
    private void resolveRogueEquipmentReward() {
        List<HeldItem> pool = GameData.instance().allEquipment();
        if (pool.isEmpty()) {
            infoAlert("装备补给", "装备数据缺失，本次补给落空。");
            return;
        }
        HeldItem reward = pool.get((int) (Math.random() * pool.size()));
        if (!player.addEquipment(reward)) {
            infoAlert("装备补给", "你已经拥有【" + reward.getName() + "】了，补给落空。");
            return;
        }
        infoAlert("装备补给", "获得装备【" + reward.getName() + "】：" + reward.getDescription()
                + "\n可在主菜单点击精灵名，在详情页中穿戴。");
    }

    /**
     * TRADE 事件：宝可梦交换——系统提供一只「队伍平均等级（向下取整）+1 或 2」的宝可梦，
     * 玩家可用队伍中的一只与其交换，也可放弃（均不返还行动点）。
     */
    private void resolveTradeEvent() {
        if (player == null || player.getParty().isEmpty()) {
            infoAlert("宝可梦交换", "队伍为空，无法进行交换。");
            return;
        }
        List<Pokemon> party = player.getParty();
        int avgLevel = party.stream().mapToInt(Pokemon::getLevel).sum() / party.size();
        int level = avgLevel + 1 + (int) (Math.random() * 2);
        Optional<Pokemon> offered = PokemonBattleAdapter.createWildPokemonExact(level, growthProgress());
        if (offered.isEmpty()) {
            infoAlert("数据异常", "宝可梦交换事件无法生成交换对象（数据缺失）。");
            return;
        }
        Pokemon offer = offered.get();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("宝可梦交换");
        alert.setHeaderText("神秘商人带来了一只 Lv." + offer.getLevel() + " 的 " + offer.getName() + "！");
        alert.setContentText("可用队伍中的一只宝可梦与其交换，也可以放弃（无论是否交换都不返还行动点）。");
        ButtonType trade = new ButtonType("交换", ButtonBar.ButtonData.OK_DONE);
        ButtonType giveUp = new ButtonType("不交换", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(trade, giveUp);
        alert.showAndWait().ifPresent(choice -> {
            if (choice == trade) {
                askWhichPokemonToTrade(offer);
            }
        });
    }

    /** 交换对象选择：从队伍中选一只被交换离队（交换完成后同步肉鸽队伍快照）。 */
    private void askWhichPokemonToTrade(Pokemon offered) {
        List<Pokemon> party = player.getParty();
        List<String> choices = new ArrayList<>();
        for (int i = 0; i < party.size(); i++) {
            Pokemon p = party.get(i);
            choices.add((i == player.getActiveIndex() ? "▶ " : "   ") + p.getName()
                    + " Lv." + p.getLevel() + (p.isFainted() ? "（濒死）" : ""));
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle("选择交换对象");
        dialog.setHeaderText("用队伍中的哪一只交换 Lv." + offered.getLevel() + " 的 " + offered.getName() + "？");
        dialog.setContentText("被交换的宝可梦将离开队伍：");
        dialog.showAndWait().ifPresent(selected -> {
            int index = choices.indexOf(selected);
            Pokemon gone = index >= 0 ? player.swapPartyMember(index, offered) : null;
            if (gone != null) {
                session.syncRogueTeam();
                infoAlert("交换完成", gone.getName() + " 离开了队伍，" + offered.getName()
                        + "（Lv." + offered.getLevel() + "）加入了队伍！");
            }
        });
    }

    /** 非战斗节点：当场效果（医院治疗 / 特殊事件金币）结算后走节点收尾。 */
    private void resolveNonBattleNode(Option option) {
        session.resolveRogueOptionEffect(option);
        finishNodeStep(true);
    }

    /**
     * 节点收尾：自回血 → 刷新本段路线节点 → 可能触发道馆战 → 统一推进。
     * 战斗节点由战后回调调用。
     *
     * @param refreshRoute 是否刷新本段节点。走完一个<b>路线节点</b>后为 {@code true}；
     *                     必然节点（道馆 / 四天王 / 冠军 / 首领侵略战）结束后为 {@code false}，
     *                     因为此时节点列表已由换段或阶段流转重新生成，不应再被覆盖
     */
    private void finishNodeStep(boolean refreshRoute) {
        session.applyRogueNodeHeal();
        if (refreshRoute) {
            session.refreshRogueRoute(); // §4.1：每走完一个路线节点，本段剩余节点重新随机生成
        }
        session.advanceRogueNode();
        afterRogueStep();
    }

    /** 一次节点（含战斗）结束后的统一推进：已结束→结算；必然节点→开战；否则落盘并重绘。 */
    private void afterRogueStep() {
        if (session.isRogueRunFinished()) {
            finishRogueRun();
            return;
        }
        if (session.getRogueRunData().getPhase().isMandatoryBattle()) {
            startMandatoryBattle(session.getRogueRunData().getPhase().toOptionType()); // 行动点耗尽：必然节点
            return;
        }
        autoSave(); // 每推进一步就落盘：存档点即「未作战」的节点之间
        showRogueFloorScene();
    }

    /** 本轮结束时的补充说明（如「火箭队节点不可失败」）；为 null 时用默认文案。 */
    private String runEndReason;

    /** 本轮远征结束（通关或战败）：弹窗反馈后回<b>初始主界面</b>，玩家在那里选择新游戏或读档。 */
    private void finishRogueRun() {
        if (session.isRogueRunCleared()) {
            infoAlert("通关", session.isRogueAggressionTriggered()
                    ? "击败来袭的火箭队首领，本轮远征通关！"
                    : "击败冠军，本轮远征通关！");
        } else {
            infoAlert("本轮结束", runEndReason == null
                    ? "队伍倒下了……肉鸽远征到此为止。"
                    : runEndReason + "，肉鸽远征到此为止。");
        }
        runEndReason = null;
        returnToStartScreen(); // 内含 autoSave：结束的一轮也落盘后再回启动页
    }

    // ------------------------------------------------------------------
    // 商店（§4.2 商店节点：随机出现，段数越靠后商品种类与数量越多）
    // ------------------------------------------------------------------

    /** 本次商店的商品库存；为 null 表示当前不在商店。 */
    private ShopStock currentShopStock;

    /** 打开商店：按当前段生成库存（装备池排除已拥有的装备）。 */
    private void openShop() {
        currentShopStock = ShopStock.forSegment(session.getSegment(), new Random(), ownedEquipmentIds());
        showShopScene();
    }

    /** 玩家已拥有的装备 id（装备全库唯一，已拥有者不再上架）。 */
    private Set<String> ownedEquipmentIds() {
        if (player == null) {
            return Set.of();
        }
        return player.getEquipment().stream().map(HeldItem::getId).collect(Collectors.toSet());
    }

    private void showShopScene() {
        if (currentShopStock == null) {
            showRogueFloorScene();
            return;
        }
        stage.setScene(new ShopView(session, currentShopStock, this::buyFromShop, this::leaveShop).createScene());
    }

    /** 购买：校验金币 → 扣款 → 消耗品入背包 / 装备入库 → 刷新货架。 */
    private void buyFromShop(ShopStock.Entry entry) {
        if (entry == null || player == null) {
            return;
        }
        if (entry.isEquipment()) {
            buyEquipment(entry);
            return;
        }
        Item item = GameData.instance().item(entry.itemId());
        if (item == null) {
            infoAlert("数据异常", "商店物品不存在：" + entry.itemId());
            return;
        }
        if (!session.getRogueRunData().spendGold(entry.price())) {
            infoAlert("金币不足", "还需 " + (entry.price() - session.getRogueRunData().getGold()) + " 金币。");
            return;
        }
        player.getBag().add(item, 1);
        LogUtil.info("商店购买: " + entry.itemName() + " x1，花费 " + entry.price() + " 金币");
        showShopScene();
    }

    /**
     * 购买装备：装备全库唯一，已拥有则提示并直接下架；否则扣款入库。
     * 入库后把该件移出货架，避免同一件重复购买。
     */
    private void buyEquipment(ShopStock.Entry entry) {
        HeldItem equipment = GameData.instance().equipment(entry.itemId());
        if (equipment == null) {
            infoAlert("数据异常", "商店装备不存在：" + entry.itemId());
            return;
        }
        if (player.getEquipment().contains(equipment)) {
            infoAlert("已拥有", "你已经拥有【" + equipment.getName() + "】了，本次不上架该装备。");
            currentShopStock = currentShopStock.withoutEntry(entry.itemId());
            showShopScene();
            return;
        }
        if (!session.getRogueRunData().spendGold(entry.price())) {
            infoAlert("金币不足", "还需 " + (entry.price() - session.getRogueRunData().getGold()) + " 金币。");
            return;
        }
        player.addEquipment(equipment);
        LogUtil.info("商店购买装备: " + equipment.getName() + "，花费 " + entry.price() + " 金币");
        LogUtil.info("获得装备【" + equipment.getName() + "】：" + equipment.getDescription()
                + "\n可在主菜单点击精灵名，在详情页中穿戴。");
        currentShopStock = currentShopStock.withoutEntry(entry.itemId());
        showShopScene();
    }

    /** 离开商店：视为完成该商店节点，走节点收尾。 */
    private void leaveShop() {
        currentShopStock = null;
        finishNodeStep(true);
    }

    /** 战斗前队伍就绪检查：无健康精灵直接判负结束；当前先发倒下则换首只健康精灵。 */
    private boolean ensureRogueBattleReady() {
        if (!session.hasHealthyPokemon()) {
            session.endRogueRun();
            infoAlert("本轮结束", "队伍已全部倒下，肉鸽远征结束。");
            returnToStartScreen(); // 一轮结束统一回初始主界面
            return false;
        }
        Pokemon active = session.getActive();
        if (active == null || active.isFainted()) {
            session.leadWithFirstHealthy();
        }
        return true;
    }

    /** 路线战斗结束回调：按节点类型处理胜负与金币奖惩（§4.3 / §5.2 失败与惩罚规则）。 */
    private Runnable rogueBattleFinished(BattleService engine, OptionType type) {
        return () -> {
            switch (engine.getStatus()) {
                case PLAYER_WIN, CAUGHT -> {
                    if (type.isMandatory()) {
                        // 必然节点：胜利金币与阶段流转（下一段 / 四天王 / 冠军 / 通关）由本方法一并结算
                        session.resolveRogueMandatoryVictory();
                        finishNodeStep(false);
                    } else {
                        session.awardRogueWinGold(type);
                        applyNodeReward(type);
                        finishNodeStep(true);
                    }
                }
                case PLAYER_LOSE -> {
                    if (type.isMandatory()) {
                        handleMandatoryDefeat(type);
                    } else {
                        handleRouteDefeat(type);
                    }
                }
                default -> {
                    // 逃跑（FLED）等非胜负终态：节点已消耗，直接走收尾
                    finishNodeStep(true);
                }
            }
        };
    }

    /**
     * 节点胜利后的额外奖励（§5.2 / §5.3）：
     * 火箭队队员有概率掉落特殊道具；击败火箭队首领则获得大师球，并把一次 0 点、
     * 必然出现的神兽偶遇追加进本段路线。
     */
    private void applyNodeReward(OptionType type) {
        if (player == null || type == null) {
            return;
        }
        switch (type) {
            case ROCKET -> {
                if (Math.random() * 100 < RouteConfig.ROCKET_DROP_PERCENT) {
                    grantItem(RouteConfig.ROCKET_DROP_ITEM_ID, "火箭队战利品");
                }
            }
            case ROCKET_CAPTURE -> {
                grantItem(RouteConfig.ROCKET_BOSS_REWARD_ITEM_ID, "击败火箭队首领");
                session.resolveRogueRocketBossVictory();
                infoAlert("获得大师球", "击败火箭队首领，缴获大师球！\n本段路线出现一次"
                        + "不消耗行动点的神兽偶遇。");
            }
            default -> {
                // 其余节点没有额外奖励
            }
        }
    }

    /** 把道具放进背包并记日志；道具数据缺失时只记日志，不打断流程。 */
    private void grantItem(String itemId, String reason) {
        Item item = GameData.instance().item(itemId);
        if (item == null) {
            LogUtil.info(reason + " 的道具数据缺失：" + itemId);
            return;
        }
        player.getBag().add(item, 1);
        LogUtil.info(reason + "：获得 " + item.getName());
    }

    /** 必然节点战败（§4.3）：道馆 / 四天王可失败一次（重试再败结束），冠军与首领侵略战不可失败。 */
    private void handleMandatoryDefeat(OptionType type) {
        if (session.resolveRogueMandatoryDefeat()) {
            infoAlert("挑战失败", type.getDisplayName() + "战败，已扣除金币；本段还可再挑战一次。");
            autoSave();
            showRogueFloorScene();
        } else {
            runEndReason = type.getDisplayName() + "不可失败，挑战失败";
            finishRogueRun();
        }
    }

    /**
     * 路线节点战败（§4.3 / §5.2）：普通节点仅扣金币；火箭队线节点不可失败，直接结束本轮。
     * 野生 / 路人训练家全灭不结束游戏：消耗 {@link RouteConfig#DEFEAT_RESCUE_AP_COST} 点行动点
     * 恢复全队状态（不足则行动点置 0），本轮继续。
     */
    private void handleRouteDefeat(OptionType type) {
        int lost = session.applyRogueDefeatPenalty(type);
        if (type != null && type.defeatEndsRun()) {
            runEndReason = type.getDisplayName() + "不可失败，挑战失败";
            session.endRogueRun();
            finishRogueRun();
            return;
        }
        if (!session.hasHealthyPokemon()) {
            if (type == OptionType.WILD || type == OptionType.TRAINER) {
                // 全灭救援：消耗行动点恢复全状态，本轮不结束
                session.applyRogueDefeatApPenalty();
                player.healParty();
                session.leadWithFirstHealthy();
                infoAlert("战败救援", "队伍全部倒下！消耗 " + RouteConfig.DEFEAT_RESCUE_AP_COST
                        + " 点行动点恢复了全队状态（行动点不足则归零），本轮继续。");
                finishNodeStep(true);
                return;
            }
            session.endRogueRun();
            finishRogueRun();
            return;
        }
        session.leadWithFirstHealthy();
        infoAlert("战斗失败", "仅扣除 " + lost + " 金币，本轮继续。");
        finishNodeStep(true);
    }

    /** WILD 节点：生成野生精灵，进入真实遭遇战（可捕获）。等级锚定队伍最高等级 ±2 浮动。 */
    private void startRogueWildBattle(Option option) {
        if (!ensureRogueBattleReady()) {
            return;
        }
        int level = highestPartyLevel() + RouteConfig.wildLevelBonus(session.getSegment());
        Optional<Pokemon> wild = PokemonBattleAdapter.createWildPokemon(level, growthProgress());
        if (wild.isEmpty()) {
            infoAlert("数据异常", "没有可遭遇的野生精灵（数据缺失）。");
            showRogueFloorScene();
            return;
        }
        try {
            BattleService engine = newWildBattle(player, wild.get());
            enterBattle(engine, rogueBattleFinished(engine, OptionType.WILD));
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
            showRogueFloorScene();
        }
    }

    /** TRAINER 节点：路人训练家轮战（不可逃/不可捕），队伍数量按段配置（1 段 1 只、2 段 1~2 只、3 段 2 只、4 段 3 只），等级锚定队伍最高等级 ±2 浮动，击倒经验 1.5 倍。 */
    private void startRogueTrainerBattle(Option option) {
        if (!ensureRogueBattleReady()) {
            return;
        }
        int segment = session.getSegment();
        int level = highestPartyLevel() + RouteConfig.trainerLevelBonus(segment);
        Trainer trainer = new Trainer("路人训练家");
        trainer.setExpMultiplier(GrowthService.TRAINER_EXP_NUMERATOR, GrowthService.TRAINER_EXP_DENOMINATOR);
        int min = RouteConfig.trainerPartyMin(segment);
        int max = RouteConfig.trainerPartyMax(segment);
        int count = min + (int) (Math.random() * (max - min + 1));
        for (int i = 0; i < count; i++) {
            PokemonBattleAdapter.createWildPokemon(level, growthProgress()).ifPresent(trainer::addPokemon);
        }
        if (trainer.getParty().isEmpty()) {
            infoAlert("数据异常", "没有可遭遇的精灵（数据缺失）。");
            showRogueFloorScene();
            return;
        }
        try {
            BattleService engine = newTrainerBattle(player, trainer);
            enterBattle(engine, rogueBattleFinished(engine, OptionType.TRAINER));
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
            showRogueFloorScene();
        }
    }

    /** ROCKET 节点：火箭队队员（§5.2），不可失败；胜利有概率掉落特殊道具，等级锚定队伍最高等级 ±2 浮动，击倒经验 1.5 倍。 */
    private void startRocketBattle() {
        if (!ensureRogueBattleReady()) {
            return;
        }
        int segment = session.getSegment();
        int level = highestPartyLevel() + RouteConfig.rocketLevelBonus(segment);
        Trainer rocket = new Trainer("火箭队队员");
        rocket.setExpMultiplier(GrowthService.TRAINER_EXP_NUMERATOR, GrowthService.TRAINER_EXP_DENOMINATOR);
        for (int i = 0; i < RouteConfig.rocketPartySize(segment); i++) {
            PokemonBattleAdapter.createWildPokemon(level, growthProgress()).ifPresent(rocket::addPokemon);
        }
        startRocketNodeBattle(rocket, OptionType.ROCKET);
    }

    /**
     * ROCKET_CAPTURE 节点：「火箭队抓捕神兽」（§5.3），不可失败；
     * 胜利缴获大师球，并把一次 0 点的神兽偶遇追加进本段路线。
     */
    private void startRocketCaptureBattle() {
        if (!ensureRogueBattleReady()) {
            return;
        }
        int segment = session.getSegment();
        int level = highestPartyLevel() + RouteConfig.rocketBossLevelBonus(segment);
        Trainer boss = new Trainer("火箭队首领");
        boss.setExpMultiplier(GrowthService.TRAINER_EXP_NUMERATOR, GrowthService.TRAINER_EXP_DENOMINATOR);
        for (int i = 0; i < RouteConfig.rocketBossPartySize(segment); i++) {
            PokemonBattleAdapter.createWildPokemon(level, growthProgress()).ifPresent(boss::addPokemon);
        }
        startRocketNodeBattle(boss, OptionType.ROCKET_CAPTURE);
    }

    /** LEGENDARY 节点：野生神兽（§5.1），判定与普通遭遇一致，可战斗也可捕获，战败仅扣金币。 */
    private void startLegendaryBattle() {
        if (!ensureRogueBattleReady()) {
            return;
        }
        int level = highestPartyLevel() + RouteConfig.legendaryLevelBonus(session.getSegment());
        Optional<Pokemon> legendary = PokemonBattleAdapter.createWildPokemon(level, growthProgress());
        if (legendary.isEmpty()) {
            infoAlert("数据异常", "没有可遭遇的神兽（数据缺失）。");
            showRogueFloorScene();
            return;
        }
        try {
            BattleService engine = newWildBattle(player, legendary.get());
            enterBattle(engine, rogueBattleFinished(engine, OptionType.LEGENDARY));
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
            showRogueFloorScene();
        }
    }

    /** 火箭队系节点（不可失败）的公共开战流程：对手为空则提示数据缺失并返回路线页。 */
    private void startRocketNodeBattle(Trainer opponent, OptionType type) {
        if (opponent.getParty().isEmpty()) {
            infoAlert("数据异常", type.getDisplayName() + "对手数据缺失，无法开战。");
            showRogueFloorScene();
            return;
        }
        try {
            BattleService engine = newTrainerBattle(player, opponent);
            enterBattle(engine, rogueBattleFinished(engine, type));
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
            showRogueFloorScene();
        }
    }

    /**
     * 必然节点战斗（道馆战 / 四天王连打 / 冠军战 / 首领侵略战）：不消耗行动点，队伍规模与等级随段数增强。
     * 1~4 段道馆主为固定配置（2/3/3/4 只、等级 11/17/24/32，精确无浮动）；第 5 段道馆主与
     * 四天王 / 冠军 / 侵略战锚定队伍最高等级 ±2 浮动。道馆与四天王战败可再挑战一次，冠军战败本轮结束。
     */
    private void startMandatoryBattle(OptionType type) {
        if (type == null || !ensureRogueBattleReady()) {
            return;
        }
        int segment = session.getSegment();
        // 1~4 段道馆主：固定等级（11/17/24/32）且精确无浮动；其余必然节点（含第 5 段道馆主）锚定队伍最高等级 ±2 浮动
        boolean gymFixed = type == OptionType.GYM && segment <= 4;
        int level = gymFixed
                ? RouteConfig.gymFixedLevel(segment)
                : Math.max(5, highestPartyLevel() + mandatoryLevelBonus(type, segment));
        Trainer opponent = new Trainer(mandatoryOpponentName(type, segment));
        // 击倒经验倍率：道馆战 2.0 倍、首领侵略战（火箭队系）1.5 倍；四天王 / 冠军保持 1 倍
        if (type == OptionType.GYM) {
            opponent.setExpMultiplier(GrowthService.GYM_EXP_NUMERATOR, GrowthService.GYM_EXP_DENOMINATOR);
        } else if (type == OptionType.ROCKET_INVASION) {
            opponent.setExpMultiplier(GrowthService.TRAINER_EXP_NUMERATOR, GrowthService.TRAINER_EXP_DENOMINATOR);
        }
        int count = mandatoryPartySize(type, segment);
        for (int i = 0; i < count; i++) {
            Optional<Pokemon> foe = gymFixed
                    ? PokemonBattleAdapter.createWildPokemonExact(level, growthProgress())
                    : PokemonBattleAdapter.createWildPokemon(level, growthProgress());
            foe.ifPresent(opponent::addPokemon);
        }
        if (opponent.getParty().isEmpty()) {
            infoAlert("数据异常", type.getDisplayName() + "对手数据缺失，无法开战。");
            showRogueFloorScene();
            return;
        }
        try {
            BattleService engine = newTrainerBattle(player, opponent);
            enterBattle(engine, rogueBattleFinished(engine, type));
        } catch (IllegalArgumentException ex) {
            LogUtil.info("无法开始战斗: " + ex.getMessage());
            infoAlert("无法开始战斗", ex.getMessage());
            showRogueFloorScene();
        }
    }

    private int mandatoryLevelBonus(OptionType type, int segment) {
        return switch (type) {
            case GYM -> RouteConfig.gymLevelBonus(segment);
            case ELITE_FOUR -> RouteConfig.eliteFourLevelBonus(segment);
            case CHAMPION -> RouteConfig.championLevelBonus(segment);
            case ROCKET_INVASION -> RouteConfig.bossAggressionLevelBonus(segment);
            default -> RouteConfig.trainerLevelBonus(segment);
        };
    }

    private int mandatoryPartySize(OptionType type, int segment) {
        return switch (type) {
            case GYM -> RouteConfig.gymPartySize(segment);
            case ELITE_FOUR -> RouteConfig.eliteFourPartySize(segment);
            case CHAMPION -> RouteConfig.championPartySize(segment);
            case ROCKET_INVASION -> RouteConfig.bossAggressionPartySize(segment);
            default -> 1;
        };
    }

    private String mandatoryOpponentName(OptionType type, int segment) {
        return switch (type) {
            case GYM -> "第 " + segment + " 段道馆馆主";
            case ELITE_FOUR -> "四天王";
            case CHAMPION -> "冠军";
            case ROCKET_INVASION -> "火箭队首领（持有神兽）";
            default -> "训练家";
        };
    }

    /**
     * 进入战斗场景：战斗期间 {@link #battleInProgress} 为真（存档被拒），
     * 战斗结束后回调前先复位，保证「未作战时才可存档」这一约束成立。
     */
    private void enterBattle(BattleService engine, Runnable onFinished) {
        battleInProgress = true;
        stage.setScene(new BattleController(engine, () -> {
            battleInProgress = false;
            onFinished.run();
        }, session.getSegment()).createScene());
    }

    /** 绑定窗口事件：关闭确认与生命周期日志。 */
    public void bindStageEvents() {
        // 窗口关闭时先确认
        stage.setOnCloseRequest(event -> {
            event.consume();
            if (confirmExit()) {
                Platform.exit();
            }
        });

        stage.setOnHiding(e -> LogUtil.info("setOnHiding...."));
        stage.setOnHidden(e -> LogUtil.info("setOnHidden...."));
        stage.setOnShowing(e -> LogUtil.info("setOnShowing....."));
        stage.setOnShown(e -> LogUtil.info("setOnShown....."));
    }

    private boolean confirmExit() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(AppConfig.EXIT_CONFIRM_TITLE);
        alert.setHeaderText(null);
        alert.setContentText(AppConfig.EXIT_CONFIRM_CONTENT);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void infoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
