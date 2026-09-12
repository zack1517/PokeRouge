package org.example.controller;

import javafx.scene.Scene;
import org.example.battle.BattleEvent;
import org.example.battle.BattleService;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.ItemStack;
import org.example.model.Move;
import org.example.model.MoveSlot;
import org.example.model.Pokemon;
import org.example.model.StatusCondition;
import org.example.view.BattleView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 战斗控制器：桥接 {@link BattleView} 与 {@link BattleService}。
 *
 * <p>流程：刷新精灵面板与日志 → 展示主菜单（技能/背包/精灵/逃跑）→ 引擎结算 → 依据状态
 * 继续或展示结局。己方出战精灵倒下而队伍仍有健康精灵时，引擎会挂起等待补位
 * （{@link BattleService#isAwaitingReplacement()}），此时界面强制展示队伍选择面板，玩家选出
 * 下一只上场精灵后战斗继续；行动回合中玩家也可主动切换，每次渲染都重新读取当前出战精灵。</p>
 *
 * <p>敌方面板统一取 {@link BattleService#foeActive()}：野生遭遇为野生精灵，训练师轮战为训练师
 * 当前出战精灵（{@link BattleService#getWild()} 为 {@code null}），因此训练师换宠后界面会自动
 * 跟随刷新。</p>
 */
public class BattleController implements BattleView.Actions {

    private final BattleService engine;
    private final Runnable onExit;
    private final int segment; // 当前地图段号（流程系统接入前由会话持有，仅用于右上角段文案）
    private final BattleView view = new BattleView(this);
    /** 演出进行中标志：动画未播完前丢弃一切行动输入，避免结算与画面错位。 */
    private boolean playing;
    /** 是否放弃了满队捕捉的精灵（结局文案与「成功捕捉」区分）。 */
    private boolean discardedCapture;

    /** @param engine 已就绪的战斗服务实例（玩家与敌方当前出战精灵均已非倒下，通常来自
     *                {@link org.example.battle.BattleServices#newBattle} 或
     *                {@link org.example.battle.BattleServices#newTrainerBattle}）
     *  @param onExit 战斗结束（含逃跑/捕捉/胜负）后返回主菜单的回调
     *  @param segment 当前地图段号（与主菜单/会话一致，用于战斗页右上角段展示） */
    public BattleController(BattleService engine, Runnable onExit, int segment) {
        this.engine = engine;
        this.onExit = onExit;
        this.segment = segment;
    }

    public Scene createScene() {
        view.setHud("第 " + segment + " 段 · " + encounterLabel(), 500); // 金币为桩值（流程系统接入后替换，TODO(dev)）
        Scene scene = view.createScene();
        render(); // 首屏：先填充双方面板/日志与行动区，入场动画在其上播放
        // 开场事件（BATTLE_START）由引擎构造时压入队列；缺失时也直接播一次进场，保证观感一致
        List<BattleEvent> opening = engine.drainEvents();
        view.setInputLocked(true);
        playing = true;
        if (opening.isEmpty()) {
            view.playEntrance(this::onPerformanceDone);
        } else {
            view.playEvents(opening, this::onPerformanceDone);
        }
        return scene;
    }

    // ------------------------------------------------------------------
    // Actions 实现
    // ------------------------------------------------------------------

    @Override
    public void onMoveSelected(MoveSlot slot) {
        if (playing) {
            return;
        }
        engine.useMove(slot);
        playEventsThenRender();
    }

    @Override
    public void onItemSelected(int stackIndex) {
        if (playing) {
            return;
        }
        List<ItemStack> stacks = engine.getBag().availableStacks();
        if (stackIndex < 0 || stackIndex >= stacks.size()) {
            render();
            return;
        }
        Item item = stacks.get(stackIndex).getItem();
        if (item.getCategory() == ItemCategory.POKE_BALL) {
            engine.useItem(item); // 精灵球始终投向敌方野生精灵，与队伍目标无关
            playEventsThenRender();
            return;
        }
        showTargetMenu(item);
    }

    /** 选择道具作用的目标精灵（回复/解除道具）：点中合法目标即使用并结算本回合。 */
    private void showTargetMenu(Item item) {
        List<Pokemon> party = engine.getPlayer().getParty();
        int activeIndex = party.indexOf(engine.playerActive());
        view.showTargetMenu(party, activeIndex,
                idx -> canTarget(item, party.get(idx)),
                idx -> {
                    engine.useItem(item, idx);
                    playEventsThenRender();
                },
                this::showBagMenu,
                "选择使用【" + item.getName() + "】的目标");
    }

    /** 某只精灵能否作为该道具的目标：回复道具要求未倒下且未满血；解除道具要求有可解除的异常状态。 */
    private static boolean canTarget(Item item, Pokemon p) {
        if (item.getCategory() == ItemCategory.HEAL) {
            return !p.isFainted() && p.getCurrentHp() < p.getMaxHp();
        }
        if (item.getCategory() == ItemCategory.CURE) {
            StatusCondition status = p.getStatus();
            return status != StatusCondition.NONE && item.canCure(status);
        }
        return false;
    }

    @Override
    public void onSwitchSelected(int partyIndex) {
        if (playing) {
            return;
        }
        engine.switchActive(partyIndex);
        playEventsThenRender();
    }

    /** 补位选择（己方出战精灵倒下后强制弹出）：选出下一只上场精灵，不消耗回合。 */
    @Override
    public void onReplacementSelected(int partyIndex) {
        if (playing) {
            return;
        }
        engine.chooseReplacement(partyIndex);
        playEventsThenRender();
    }

    @Override
    public void onRun() {
        if (playing) {
            return;
        }
        engine.tryRun();
        playEventsThenRender();
    }

    @Override
    public void onExit() {
        onExit.run();
    }

    // ------------------------------------------------------------------
    // 演出编排
    // ------------------------------------------------------------------

    /**
     * 播放上一条行动结算产生的演出事件，全部播完后 {@link #render()}。
     *
     * <p>日志与天气/场地行在动画<b>开始前</b>刷新，让本回合文本与演出同步可见。事件本身按「一方动作 →
     * 另一方受击 → 另一方的动作 → 这边受击」的顺序投递（见 {@code BattleEventTest}），界面在播放每一步
     * 动画前先按事件携带的 HP 快照刷新对应血条（{@code BattleView.applyEventHp}），因此血量按先后手
     * 逐段结算、不会等双方都演完才变化。精灵名、等级、异常状态等仍在演出结束后的 {@code render()}
     * 统一刷新，保证倒下与放出动画不会因为引擎已自动换宠而作用到新精灵身上（事件自带精灵名，动画按名切图）。</p>
     */
    private void playEventsThenRender() {
        List<BattleEvent> events = engine.drainEvents();
        view.showLog(engine.getLog());
        view.refreshFieldStatus(engine.getWeather(), engine.getTerrain());
        playing = true;
        view.setInputLocked(true);
        view.playEvents(events, this::onPerformanceDone);
    }

    /** 演出收尾：解锁输入并刷新界面（行动区按战斗状态回到主菜单/学招/结局）。 */
    private void onPerformanceDone() {
        playing = false;
        view.setInputLocked(false);
        render();
    }

    // ------------------------------------------------------------------
    // 界面编排
    // ------------------------------------------------------------------

    private void render() {
        view.refreshPokemon(engine.playerActive(), engine.foeActive());
        view.refreshFieldStatus(engine.getWeather(), engine.getTerrain());
        view.showLog(engine.getLog());
        if (!engine.pendingLearnChoices().isEmpty()) {
            // 升级在击倒当回合即时发生，故「技能栏已满」的抉择可能在战斗进行中就出现：
            // 先让玩家处理完再回到战斗菜单（未处理完就继续行动会积压多项抉择）。
            showLearnMenu();
            return;
        }
        if (!engine.isOngoing()) {
            if (engine.capturedAwaitingRelease() != null) {
                showReleaseMenu(); // 满队捕捉：先处理放生抉择，处理完再展示结局
            } else if (!engine.pendingLearnChoices().isEmpty()) {
                showLearnMenu(); // 获胜后还有待玩家抉择的学招，先处理完再展示结局
            } else {
                view.showResult(resultText());
            }
            return;
        }
        if (engine.isAwaitingReplacement()) {
            showReplacementMenu(); // 出战精灵倒下：必须选出替补才能继续
            return;
        }
        view.showMainMenu(this::showMoveMenu, this::showBagMenu, this::showPartyMenu);
    }

    /**
     * 满队捕捉后的放生面板：底部行动区自动切到「精灵」六格面板，提示需放生一只
     * 腾位；点选精灵即放生（携带装备返还装备库）并收下新精灵，或点「放弃捕捉」。
     */
    private void showReleaseMenu() {
        Pokemon captured = engine.capturedAwaitingRelease();
        List<Pokemon> party = engine.getPlayer().getParty();
        int activeIndex = party.indexOf(engine.playerActive());
        view.showReleaseMenu(party, activeIndex,
                idx -> {
                    engine.releaseToMakeRoom(idx);
                    render();
                },
                () -> {
                    engine.discardCaptured();
                    discardedCapture = true;
                    render();
                },
                "队伍已满！" + captured.getName() + " 需要入队，请选择一只精灵放生（携带的装备会返还装备库），或点「放弃捕捉」。");
    }

    /** 补位面板：列出玩家队伍，选出下一只上场精灵（倒下的精灵灰显不可点，不可返回主菜单）。 */
    private void showReplacementMenu() {
        List<Pokemon> party = engine.getPlayer().getParty();
        int activeIndex = party.indexOf(engine.playerActive());
        view.showReplacementMenu(party, activeIndex, this::onReplacementSelected);
    }

    /** 展示队首一项「技能满、想学新招」的抉择菜单。 */
    private void showLearnMenu() {
        BattleService.LearnChoice choice = engine.pendingLearnChoices().get(0);
        Pokemon p = choice.pokemon();
        Move move = choice.move();
        String prompt = p.getName() + " 想学会【" + move.getName() + "】，但它已经掌握 "
                + p.getMoveSlots().size() + " 个技能！请选择要遗忘的技能，或放弃学习。";
        view.showLearnMoveMenu(prompt, p.getMoveSlots(),
                index -> {
                    engine.decideLearn(index);
                    render();
                },
                () -> {
                    engine.decideLearn(-1);
                    render();
                });
    }

    private void showPartyMenu() {
        List<Pokemon> party = engine.getPlayer().getParty();
        int activeIndex = party.indexOf(engine.playerActive());
        view.showPartyMenu(party, activeIndex, this::onSwitchSelected, this::render);
    }

    private void showMoveMenu() {
        view.refreshPokemon(engine.playerActive(), engine.foeActive());
        view.showMoveMenu(engine.playerActive().getMoveSlots(), this::render);
    }

    private void showBagMenu() {
        List<ItemStack> stacks = engine.getBag().availableStacks();
        if (stacks.isEmpty()) {
            view.clearActions();
            view.showBagMenu(List.of(new BattleView.ItemButton("背包空空如也", true)),
                    i -> {
                    }, this::render);
            return;
        }
        List<BattleView.ItemButton> buttons = new ArrayList<>();
        for (ItemStack stack : stacks) {
            Item item = stack.getItem();
            String text = item.getName() + " ×" + stack.getCount();
            buttons.add(new BattleView.ItemButton(text, isItemDisabled(item), describe(item)));
        }
        view.showBagMenu(buttons, this::onItemSelected, this::render);
    }

    /** 道具当前是否不可用：回复/解除道具在队伍中没有任何合法目标时禁用；精灵球恒可用（能否捕捉由引擎判定）。 */
    private boolean isItemDisabled(Item item) {
        if (item.getCategory() == ItemCategory.POKE_BALL) {
            return false;
        }
        return engine.getPlayer().getParty().stream().noneMatch(p -> canTarget(item, p));
    }

    private String describe(Item item) {
        if (item.getCategory() == ItemCategory.HEAL) {
            return "回复 " + (int) item.getEffect() + " HP";
        }
        if (item.getCategory() == ItemCategory.CURE) {
            return "解除" + curesText(item);
        }
        if (item.getCategory() == ItemCategory.POKE_BALL) {
            return item.isAlwaysCatch() ? "必定捕捉" : "捕捉率 ×" + item.getEffect();
        }
        return "";
    }

    /** 解除道具的适用范围文案：万灵药显示「全部异常状态」，其余逐一列出具体状态名。 */
    private static String curesText(Item item) {
        if (item.curesAll()) {
            return "全部异常状态";
        }
        List<StatusCondition> conditions = item.curedStatuses();
        if (conditions.isEmpty()) {
            return "异常状态";
        }
        return conditions.stream()
                .map(StatusCondition::getDisplayName)
                .collect(Collectors.joining("/"))
                + "状态";
    }

    private String resultText() {
        return switch (engine.getStatus()) {
            case PLAYER_WIN -> isTrainerBattle()
                    ? "战斗胜利！" + engine.getTrainer().getName() + " 的精灵已全部倒下！"
                    : "战斗胜利！你获得了经验！";
            case PLAYER_LOSE -> "你已没有能战斗的精灵……";
            case FLED -> "成功逃离了战斗！";
            case CAUGHT -> discardedCapture
                    ? "你放走了捕捉到的精灵……"
                    : "成功捕捉！它加入了你的队伍！";
            case ONGOING -> "";
        };
    }

    /** 是否训练师轮战（敌方持有一整支队伍）。 */
    private boolean isTrainerBattle() {
        return engine.getTrainer() != null;
    }

    /** 右上角遭遇说明：训练师轮战显示训练师名，野生遭遇显示「野外遭遇」。 */
    private String encounterLabel() {
        return isTrainerBattle() ? "训练师 " + engine.getTrainer().getName() : "野外遭遇";
    }
}
