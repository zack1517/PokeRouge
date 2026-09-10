package org.example.controller;

import javafx.scene.Scene;
import org.example.battle.BattleService;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.ItemStack;
import org.example.model.Move;
import org.example.model.MoveSlot;
import org.example.model.Pokemon;
import org.example.view.BattleView;

import java.util.ArrayList;
import java.util.List;

/**
 * 战斗控制器：桥接 {@link BattleView} 与 {@link BattleService}。
 *
 * <p>流程：刷新精灵面板与日志 → 展示主菜单（技能/背包/精灵/逃跑）→ 引擎结算 → 依据状态
 * 继续或展示结局。战斗中精灵倒下会被引擎自动切换，玩家也可在行动回合主动切换，每次渲染都
 * 重新读取当前出战精灵。</p>
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
        render(); // 首屏：填充双方面板/日志并展示行动按钮
        return scene;
    }

    // ------------------------------------------------------------------
    // Actions 实现
    // ------------------------------------------------------------------

    @Override
    public void onMoveSelected(MoveSlot slot) {
        engine.useMove(slot);
        render();
    }

    @Override
    public void onItemSelected(int stackIndex) {
        List<ItemStack> stacks = engine.getBag().availableStacks();
        if (stackIndex >= 0 && stackIndex < stacks.size()) {
            engine.useItem(stacks.get(stackIndex).getItem());
        }
        render();
    }

    @Override
    public void onSwitchSelected(int partyIndex) {
        engine.switchActive(partyIndex);
        render();
    }

    @Override
    public void onRun() {
        engine.tryRun();
        render();
    }

    @Override
    public void onExit() {
        onExit.run();
    }

    // ------------------------------------------------------------------
    // 界面编排
    // ------------------------------------------------------------------

    private void render() {
        view.refreshPokemon(engine.playerActive(), engine.foeActive());
        view.refreshFieldStatus(engine.getWeather(), engine.getTerrain());
        view.showLog(engine.getLog());
        if (!engine.isOngoing()) {
            if (!engine.pendingLearnChoices().isEmpty()) {
                showLearnMenu(); // 获胜后还有待玩家抉择的学招，先处理完再展示结局
            } else {
                view.showResult(resultText());
            }
            return;
        }
        view.showMainMenu(this::showMoveMenu, this::showBagMenu, this::showPartyMenu);
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
        List<org.example.model.Pokemon> party = engine.getPlayer().getParty();
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
            String desc = describe(item);
            String text = item.getName() + " ×" + stack.getCount() + (desc.isEmpty() ? "" : "（" + desc + "）");
            boolean disabled = isItemDisabled(item);
            buttons.add(new BattleView.ItemButton(text, disabled));
        }
        view.showBagMenu(buttons, this::onItemSelected, this::render);
    }

    /** 道具当前是否不可用：回复道具在满血时禁用。 */
    private boolean isItemDisabled(Item item) {
        if (item.getCategory() == ItemCategory.HEAL) {
            return engine.playerActive().getCurrentHp() >= engine.playerActive().getMaxHp();
        }
        return false;
    }

    private String describe(Item item) {
        if (item.getCategory() == ItemCategory.HEAL) {
            return "回复 " + (int) item.getEffect() + " HP";
        }
        if (item.getCategory() == ItemCategory.POKE_BALL) {
            return item.isAlwaysCatch() ? "必定捕捉" : "捕捉率 ×" + item.getEffect();
        }
        return "";
    }

    private String resultText() {
        return switch (engine.getStatus()) {
            case PLAYER_WIN -> isTrainerBattle()
                    ? "战斗胜利！" + engine.getTrainer().getName() + " 的精灵已全部倒下！"
                    : "战斗胜利！你获得了经验！";
            case PLAYER_LOSE -> "你已没有能战斗的精灵……";
            case FLED -> "成功逃离了战斗！";
            case CAUGHT -> "成功捕捉！它加入了你的队伍！";
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
