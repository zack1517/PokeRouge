package com.bao01.model;

import com.bao01.config.BattleConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 1v1 回合制对战引擎：双方各带最多 6 只宝可梦，同时只有一只在场。
 *
 * <p>每回合双方各自选择一个行动，回合内行动优先级如下：
 * <ol>
 *   <li><b>逃跑</b>（仅玩家；野生对战按成功率结算，训练家对战必然失败）；</li>
 *   <li><b>道具 / 轮换</b>——行动优先级 4，先于所有攻击招式结算；</li>
 *   <li><b>攻击招式</b>——按招式自身优先级降序出手（普通默认 1，先制招式更高）；
 *       优先级相同则比较双方当前实际速度（含速度等级变化），仍相同则随机；
 *       先手方若将对手击倒，对手本回合未出手的招式即告取消。</li>
 * </ol>
 *
 * <p>回合结束时若一方在场宝可梦濒死且仍有后备，则必须先派出下一只（我方由界面选择，
 * 敌方自动补位）；某一方全体濒死即分出胜负。若为野生对战也可通过逃跑直接结束战斗。
 */
public final class Battle {

    /** 对战结果。 */
    public enum Outcome { IN_PROGRESS, PLAYER_WON, FOE_WON, PLAYER_FLED }

    /** 对战对象类型：决定能否逃跑。 */
    public enum Opponent {
        /** 野生宝可梦：可以尝试逃跑（按成功率结算）。 */
        WILD("野生宝可梦"),
        /** 训练家（路人 / 火箭队等）：逃跑必然失败。 */
        TRAINER("训练家");

        private final String label;

        Opponent(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 一次回合结算的产物：新产生的日志（尚未并入全局日志）。 */
    public record RoundResult(List<String> logs) {
        public static RoundResult rejected(String why) {
            return new RoundResult(List.of(why));
        }
    }

    private static final int FAST_ACTION_PRIORITY = 4;

    private final Team player;
    private final Team foe;
    private final Opponent opponent;
    private final Random random = new Random();

    private final List<String> fullLog = new ArrayList<>();
    private Outcome outcome = Outcome.IN_PROGRESS;
    private int roundNo;
    private boolean playerPendingSendout;
    private boolean playerKoSwitchPending;
    /** 当前天气（无天气为 {@link Weather#NONE}）。 */
    private Weather weather = Weather.NONE;
    /** 当前天气剩余回合数（含本回合，0 表示无天气）。 */
    private int weatherTurnsLeft;

    public Battle(Team player, Team foe, Opponent opponent) {
        this(player, foe, opponent, true);
    }

    /** 内部构造：fresh=true 时按新对局重置双方队伍；false 用于从存档恢复（沿用现有 HP/等级/出战位）。 */
    private Battle(Team player, Team foe, Opponent opponent, boolean fresh) {
        this.player = Objects.requireNonNull(player, "player");
        this.foe = Objects.requireNonNull(foe, "foe");
        this.opponent = Objects.requireNonNull(opponent, "opponent");
        if (fresh) {
            player.resetAll();
            foe.resetAll();
        }
    }

    /**
     * 从存档恢复一场进行中的对局：不重置双方队伍状态，
     * 直接沿用存档时的回合数、天气、等待轮换等标记，使游戏可以「断线重连」。
     */
    public static Battle resumed(Team player, Team foe, Opponent opponent,
                                 int roundNo, Weather weather, int weatherTurnsLeft,
                                 boolean playerPendingSendout, boolean playerKoSwitchPending,
                                 List<String> history) {
        Battle b = new Battle(player, foe, opponent, false);
        b.roundNo = Math.max(0, roundNo);
        b.weather = weather == null ? Weather.NONE : weather;
        b.weatherTurnsLeft = Math.max(0, weatherTurnsLeft);
        b.playerPendingSendout = playerPendingSendout;
        b.playerKoSwitchPending = playerKoSwitchPending;
        if (history != null) {
            b.fullLog.addAll(history);
        }
        return b;
    }

    // ---------- 只读状态 ----------

    public Team player() {
        return player;
    }

    public Team foe() {
        return foe;
    }

    public Pokemon playerActive() {
        return player.active();
    }

    public Pokemon foeActive() {
        return foe.active();
    }

    public Opponent opponent() {
        return opponent;
    }

    public boolean isWildBattle() {
        return opponent == Opponent.WILD;
    }

    public boolean isOver() {
        return outcome != Outcome.IN_PROGRESS;
    }

    public Outcome outcome() {
        return outcome;
    }

    public int roundNo() {
        return roundNo;
    }

    /** 当前天气（无天气为 {@link Weather#NONE}）。 */
    public Weather weather() {
        return weather;
    }

    /** 当前天气剩余回合数（含本回合；0 表示无天气）。 */
    public int weatherTurnsLeft() {
        return weatherTurnsLeft;
    }

    /** 是否有天气存在（非 NONE）。 */
    public boolean hasWeather() {
        return weather != Weather.NONE;
    }

    /** 玩家在场宝可梦濒死后，是否正等待玩家派出下一只。 */
    public boolean isPlayerPendingSendout() {
        return playerPendingSendout;
    }

    /** 我方击倒对方宝可梦后，是否正等待玩家决定是否免费轮换。 */
    public boolean isPlayerKoSwitchPending() {
        return playerKoSwitchPending;
    }

    /** 全局战斗日志（自对局开始累计）。 */
    public List<String> history() {
        return List.copyOf(fullLog);
    }

    public boolean canFlee() {
        return isWildBattle() && !isOver() && !playerPendingSendout && !playerKoSwitchPending;
    }

    // ---------- 回合驱动 ----------

    /**
     * 玩家提交本回合行动；敌方行动由内置 AI 决策。
     *
     * @return 新产生的日志；若为非法行动，返回单条错误提示且回合不推进
     */
    public RoundResult resolve(BattleAction playerAction) {
        if (isOver()) {
            return RoundResult.rejected("对战已经结束。");
        }
        if (playerPendingSendout) {
            return RoundResult.rejected("请先选择下一只宝可梦上场！");
        }
        if (playerKoSwitchPending) {
            return RoundResult.rejected("请先决定是否轮换宝可梦！");
        }
        if (playerAction == null) {
            return RoundResult.rejected("请选择行动。");
        }
        String err = validate(player, playerAction, true);
        if (err != null) {
            return RoundResult.rejected(err);
        }
        BattleAction foeAction = foeDecide();
        return runRound(playerAction, foeAction);
    }

    /**
     * 双方都交给 AI（自动对战演示 / 测试用）。
     */
    public RoundResult resolveAuto() {
        if (isOver()) {
            return RoundResult.rejected("对战已经结束。");
        }
        if (playerPendingSendout) {
            int idx = pickReplacement(player);
            return sendOut(idx);
        }
        if (playerKoSwitchPending) {
            return autoKoSwitch();
        }
        BattleAction pa = playerDecide();
        BattleAction fa = foeDecide();
        return runRound(pa, fa);
    }

    /** 玩家在我方濒死后派出替补（index 为队伍下标）。 */
    public RoundResult sendOut(int memberIndex) {
        if (isOver()) {
            return RoundResult.rejected("对战已经结束。");
        }
        if (!playerPendingSendout) {
            return RoundResult.rejected("当前不需要派出宝可梦。");
        }
        if (!player.isAlive(memberIndex)) {
            return RoundResult.rejected("该宝可梦已濒死，无法上场。");
        }
        if (memberIndex == player.activeIndex()) {
            return RoundResult.rejected("它就是当前出战的宝可梦。");
        }
        String text = "我方派出了 " + player.member(memberIndex).getName() + "！";
        player.switchTo(memberIndex);
        playerPendingSendout = false;
        fullLog.add(text);
        return new RoundResult(List.of(text));
    }

    /**
     * 我方击倒对方宝可梦后的免费轮换：index 为我方队伍下标（须与当前在场不同）。
     * 轮换不消耗回合行动。
     */
    public RoundResult koSwitch(int memberIndex) {
        if (isOver()) {
            return RoundResult.rejected("对战已经结束。");
        }
        if (!playerKoSwitchPending) {
            return RoundResult.rejected("当前没有轮换机会。");
        }
        if (memberIndex < 0 || memberIndex >= player.size()
                || !player.isAlive(memberIndex) || memberIndex == player.activeIndex()) {
            return RoundResult.rejected("无法派出这只宝可梦。");
        }
        Pokemon old = player.active();
        player.switchTo(memberIndex);
        old.resetStages();
        playerKoSwitchPending = false;
        String text = "我方收回了 " + old.getName() + "，派出了 "
                + player.active().getName() + "！";
        fullLog.add(text);
        return new RoundResult(List.of(text));
    }

    /** 我方击倒对方宝可梦后选择不轮换、继续战斗。 */
    public RoundResult koSwitchKeep() {
        if (isOver()) {
            return RoundResult.rejected("对战已经结束。");
        }
        if (!playerKoSwitchPending) {
            return RoundResult.rejected("当前没有轮换机会。");
        }
        playerKoSwitchPending = false;
        String text = "我方 " + player.active().getName() + " 选择继续战斗！";
        fullLog.add(text);
        return new RoundResult(List.of(text));
    }

    // ---------- AI ----------

    /** 玩家方 AI（自动演示用）：低血回血 / 换人，否则攻击。 */
    public BattleAction playerDecide() {
        return decide(player, foe);
    }

    /** 自动模式下的击倒轮换决策：血量告急且尚有后备则换上更健康的后备，否则继续。 */
    private RoundResult autoKoSwitch() {
        Pokemon self = player.active();
        if (self.hpRatio() < 0.25 && player.aliveCount() > 1) {
            int idx = pickReplacement(player);
            if (idx >= 0) {
                return koSwitch(idx);
            }
        }
        return koSwitchKeep();
    }

    /** 敌方 AI。 */
    public BattleAction foeDecide() {
        return decide(foe, player);
    }

    private BattleAction decide(Team self, Team other) {
        Pokemon selfP = self.active();
        Pokemon foeP = other.active();
        // 血量低且有可用道具 → 道具（优先级 4，先出手）
        if (selfP.hpRatio() < 0.35) {
            Item item = self.bag().bestUsableFor(selfP);
            if (item != null) {
                return new BattleAction.UseItem(item);
            }
        }
        // 血量告急且还有后备 → 轮换
        if (selfP.hpRatio() < 0.25 && self.aliveCount() > 1) {
            int idx = pickReplacement(self);
            if (idx >= 0) {
                return new BattleAction.SwitchTo(idx);
            }
        }
        // 攻击：取期望伤害最高的招式
        int best = bestAttackIndex(selfP, foeP);
        if (best >= 0) {
            return new BattleAction.Attack(best);
        }
        return new BattleAction.Attack(0);
    }

    // ---------- 回合结算核心 ----------

    private RoundResult runRound(BattleAction playerAction, BattleAction foeAction) {
        List<String> logs = new ArrayList<>();
        roundNo++;
        logs.add("————— 第 " + roundNo + " 回合 —————");

        // 1) 逃跑（优先级高于一切：若成功立即结束对局）
        if (playerAction instanceof BattleAction.Flee) {
            boolean fled = attemptFlee(logs);
            if (fled) {
                return finishRound(logs);
            }
            playerAction = null; // 逃跑失败：本回合玩家不再行动
        }

        // 2) 生成双方行动顺序：道具/轮换 优先级 4 > 招式优先级
        List<ActionSlot> order = orderActions(playerAction, foeAction);
        for (ActionSlot slot : order) {
            if (isOver()) {
                break;
            }
            execute(slot, logs);
        }

        // 3) 天气回合末结算：环境伤害 / 持续时间倒计时（在濒死补位之前）
        weatherEndOfRound(logs);

        // 4) 回合末：濒死补位 / 胜负判定
        resolveEndOfRound(logs);
        return finishRound(logs);
    }

    /** 天气回合末结算：环境伤害 → 剩余回合递减 → 到期自然消散。 */
    private void weatherEndOfRound(List<String> logs) {
        if (weather == Weather.NONE || weatherTurnsLeft <= 0) {
            return;
        }
        if (weather.hasEndOfTurnDamage()) {
            applyWeatherChip(player.active(), "我方", logs);
            applyWeatherChip(foe.active(), "对方", logs);
        }
        weatherTurnsLeft--;
        if (weatherTurnsLeft <= 0) {
            logs.add(weather.getEndMessage());
            weather = Weather.NONE;
        }
    }

    /**
     * 沙暴环境伤害：损失 1/16 最大 HP；岩石 / 地面 / 钢免疫；
     * 不会击倒，最低保留 1 HP。
     */
    private void applyWeatherChip(Pokemon p, String side, List<String> logs) {
        if (p == null || p.isFainted()) {
            return;
        }
        if (weather.isEndOfTurnDamageImmune(p.getType())) {
            return;
        }
        int loss = Math.max(1, p.maxHp() / BattleConfig.WEATHER_CHIP_DIVISOR);
        int canLose = Math.max(0, p.currentHp() - 1); // 保留 1 HP
        int actual = p.takeDamage(Math.min(loss, canLose));
        if (actual > 0) {
            logs.add(side + " " + p.getName() + " 受到了沙暴的伤害，损失了 "
                    + actual + " 点 HP！");
        }
    }

    private ActionSlot[] snapshotSlots(BattleAction playerAction, BattleAction foeAction) {
        return new ActionSlot[]{
                new ActionSlot(true, playerAction),
                new ActionSlot(false, foeAction)
        };
    }

    /** 返回按优先级 / 速度排序后的行动（仅含非 null）。 */
    private List<ActionSlot> orderActions(BattleAction playerAction, BattleAction foeAction) {
        List<ActionSlot> slots = new ArrayList<>();
        for (ActionSlot s : snapshotSlots(playerAction, foeAction)) {
            if (s.action() != null) {
                slots.add(s);
            }
        }
        if (slots.size() < 2) {
            return slots;
        }
        // 先快照速度：速度变化（如高速移动）在同回合内不影响本次出手顺序判定
        double playerSpeed = player.active().speed();
        double foeSpeed = foe.active().speed();

        slots.sort((a, b) -> {
            int cmp = Integer.compare(actionPriority(b), actionPriority(a));
            if (cmp != 0) {
                return cmp;
            }
            double sa = a.isPlayer() ? playerSpeed : foeSpeed;
            double sb = b.isPlayer() ? playerSpeed : foeSpeed;
            cmp = Double.compare(sb, sa);
            if (cmp != 0) {
                return cmp;
            }
            return random.nextBoolean() ? 1 : -1;
        });
        return slots;
    }

    /** 行动类别优先级：道具 / 轮换 = 4；攻击招式 = 招式优先级（普通默认 1）。 */
    private int actionPriority(ActionSlot slot) {
        BattleAction a = slot.action();
        if (a instanceof BattleAction.Attack atk) {
            Pokemon p = slot.isPlayer() ? player.active() : foe.active();
            return p.move(atk.moveIndex()).getPriority();
        }
        if (a instanceof BattleAction.UseItem || a instanceof BattleAction.SwitchTo) {
            return FAST_ACTION_PRIORITY;
        }
        return FAST_ACTION_PRIORITY;
    }

    /** 按顺序执行单个行动。 */
    private void execute(ActionSlot slot, List<String> logs) {
        Team team = slot.isPlayer() ? player : foe;
        Team targetTeam = slot.isPlayer() ? foe : player;
        Pokemon self = team.active();
        if (self.isFainted()) {
            return; // 已倒下：本回合无法行动
        }
        BattleAction action = slot.action();
        if (action instanceof BattleAction.Attack atk) {
            executeAttack(slot.isPlayer(), atk.moveIndex(), logs);
        } else if (action instanceof BattleAction.UseItem use) {
            executeItem(slot.isPlayer(), use.item(), logs);
        } else if (action instanceof BattleAction.SwitchTo sw) {
            executeSwitch(slot.isPlayer(), sw.memberIndex(), logs);
        }
    }

    // ---------- 各行动执行 ----------

    private void executeAttack(boolean isPlayer, int moveIndex, List<String> logs) {
        Pokemon atk = isPlayer ? player.active() : foe.active();
        Pokemon def = isPlayer ? foe.active() : player.active();
        if (atk == null || atk.isFainted() || def == null || def.isFainted()) {
            return;
        }
        Move move = atk.move(moveIndex);
        String prefix = sideName(isPlayer) + " " + atk.getName();
        logs.add(prefix + " 使用了 " + move.getName() + "！");

        if (random.nextDouble() >= move.getAccuracy()) {
            logs.add("但是攻击没有命中！");
            return;
        }
        if (move.isWeatherMove()) {
            castWeather(move.getWeather(), logs);
            return;
        }
        if (move.isDamaging()) {
            double eff = PokeType.effectiveness(move.getType(), def.getType());
            int dmg = rollDamage(move, atk, def);
            int dealt = def.takeDamage(dmg);
            logs.add("命中" + sideName(!isPlayer) + " " + def.getName()
                    + "，造成 " + dealt + " 点伤害"
                    + effectNote(eff) + "！");
            if (def.isFainted()) {
                logs.add(sideName(!isPlayer) + "的 " + def.getName() + " 倒下了！");
            }
        }
        applyMoveEffect(atk, def, move, isPlayer, logs);
    }

    /** 施展天气招式：切换到目标天气（含覆盖）并重新计时 5 回合。 */
    private void castWeather(Weather target, List<String> logs) {
        if (target == null || target == Weather.NONE) {
            logs.add("但是没有任何效果！");
            return;
        }
        if (weather == target) {
            logs.add("但是天气已经处于" + target.getLabel() + "，没有生效。");
            return;
        }
        weather = target;
        weatherTurnsLeft = BattleConfig.WEATHER_TURNS;
        logs.add(target.getStartMessage());
    }

    private void executeItem(boolean isPlayer, Item item, List<String> logs) {
        Team team = isPlayer ? player : foe;
        Pokemon self = team.active();
        if (!team.bag().take(item)) {
            logs.add("背包中没有「" + item.getName() + "」了。");
            return;
        }
        int healed = item.apply(self);
        logs.add(sideName(isPlayer) + " " + self.getName() + " 使用了 "
                + item.getName() + "，回复 " + healed + " 点 HP。");
    }

    private void executeSwitch(boolean isPlayer, int memberIndex, List<String> logs) {
        Team team = isPlayer ? player : foe;
        if (!team.isAlive(memberIndex) || memberIndex == team.activeIndex()) {
            logs.add("无法派出这只宝可梦。");
            return;
        }
        Pokemon old = team.active();
        team.switchTo(memberIndex);
        old.resetStages();
        logs.add(sideName(isPlayer) + "收回了 " + old.getName()
                + "，派出了 " + team.active().getName() + "！");
    }

    /** 尝试逃跑（仅玩家在野生对战时可用）。返回是否成功（成功即结束对局）。 */
    private boolean attemptFlee(List<String> logs) {
        if (!isWildBattle()) {
            logs.add("与" + opponent.getLabel() + "的对战中无法逃跑！");
            return false;
        }
        Pokemon self = player.active();
        Pokemon wild = foe.active();
        double chance = BattleConfig.FLEE_CHANCE_BASE
                + (self.speed() - wild.speed()) * BattleConfig.FLEE_SPEED_FACTOR;
        chance = Math.max(BattleConfig.FLEE_CHANCE_MIN,
                Math.min(BattleConfig.FLEE_CHANCE_MAX, chance));
        if (random.nextDouble() < chance) {
            logs.add("成功从野生宝可梦身边逃走了！");
            outcome = Outcome.PLAYER_FLED;
            return true;
        }
        logs.add("没能成功逃走……");
        return false;
    }

    /** 结算招式的能力等级变化效果（例如高速移动提升速度等级）。 */
    private void applyMoveEffect(Pokemon atk, Pokemon def, Move move,
                                 boolean attackerIsPlayer, List<String> logs) {
        Move.Effect effect = move.getEffect();
        if (effect == null) {
            return;
        }
        Pokemon target = effect.target() == Move.EffectTarget.SELF ? atk : def;
        if (target.isFainted()) {
            return;
        }
        int delta = target.changeStage(effect.stat(), effect.stageDelta());
        if (delta == 0) {
            logs.add(target.getName() + " 的 " + effect.stat().getCode()
                    + " 已经无法再变化了。");
            return;
        }
        String who = effect.target() == Move.EffectTarget.SELF
                ? sideName(attackerIsPlayer) + " " + target.getName()
                : sideName(!attackerIsPlayer) + " " + target.getName();
        logs.add(who + " 的 " + effect.stat().getCode()
                + (delta > 0 ? " 提升了 " : " 降低了 ")
                + Math.abs(delta) + " 级！");
    }

    /** 回合末：濒死则补位（我方等待玩家选择；敌方自动），并判定胜负。 */
    private void resolveEndOfRound(List<String> logs) {
        boolean foeFaintedThisRound = foe.active().isFainted();
        // 敌方濒死 → 自动补位
        if (foeFaintedThisRound && foe.aliveCount() > 0) {
            int idx = pickReplacement(foe);
            if (idx >= 0) {
                Pokemon old = foe.active();
                foe.switchTo(idx);
                old.resetStages();
                logs.add("对方派出了 " + foe.active().getName() + " 迎战！");
            }
        }
        // 我方濒死 → 等待玩家选择（自动演示时立即补位）
        if (player.active().isFainted()) {
            if (player.aliveCount() == 0) {
                outcome = Outcome.FOE_WON;
            } else {
                playerPendingSendout = true;
                logs.add("我方 " + player.active().getName()
                        + " 倒下了，请派出下一只宝可梦！");
            }
        }
        // 敌方全灭判定（含刚才补位失败的情形）
        if (foe.aliveCount() == 0) {
            outcome = Outcome.PLAYER_WON;
        }
        // 我方击倒对方在场宝可梦、对方仍有后备、我方在场者存活且还有后备时，
        // 提供一次免费轮换机会（不占用回合行动）。
        if (foeFaintedThisRound && foe.aliveCount() > 0
                && !player.active().isFainted() && player.aliveCount() > 1
                && outcome == Outcome.IN_PROGRESS) {
            playerKoSwitchPending = true;
            logs.add("你击倒了对方！可以选择是否轮换己方宝可梦。");
        }
    }

    private RoundResult finishRound(List<String> logs) {
        if (outcome == Outcome.PLAYER_WON) {
            logs.add("🎉 我方获胜！");
        } else if (outcome == Outcome.FOE_WON) {
            logs.add("我方所有宝可梦都倒下了……");
        } else if (outcome == Outcome.PLAYER_FLED) {
            logs.add("对战结束（逃跑成功）。");
        }
        fullLog.addAll(logs);
        return new RoundResult(logs);
    }

    // ---------- 伤害 / 决策工具 ----------

    /** 掷一次伤害（含 0.85~1.0 随机浮动），不低于 1。 */
    public int rollDamage(Move move, Pokemon atk, Pokemon def) {
        double raw = rawDamage(move, atk, def);
        double roll = BattleConfig.DMG_RAND_MIN
                + random.nextDouble() * (BattleConfig.DMG_RAND_MAX - BattleConfig.DMG_RAND_MIN);
        return Math.max(1, (int) Math.floor(raw * roll));
    }

    /** 理论伤害（未浮动），供 AI 与界面预览使用。 */
    public double rawDamage(Move move, Pokemon atk, Pokemon def) {
        if (!move.isDamaging()) {
            return 0;
        }
        boolean physical = move.getCategory() == MoveCategory.PHYSICAL;
        Stat atkStat = physical ? Stat.ATTACK : Stat.SP_ATTACK;
        Stat defStat = physical ? Stat.DEFENSE : Stat.SP_DEFENSE;
        double levelTerm = Math.floor(2.0 * atk.getLevel() / BattleConfig.DMG_LEVEL_DIVISOR)
                + BattleConfig.DMG_LEVEL_ADD;
        double eff = PokeType.effectiveness(move.getType(), def.getType());
        double stab = move.getType() == atk.getType() ? BattleConfig.STAB : 1.0;
        // 天气修正：威力倍率（雨/晴对水/火招式）；防御方能力值倍率（沙暴岩石特防、雪冰物防）
        double powerMod = weather.movePowerModifier(move.getType());
        double defStatMod = weather.defenseModifier(def.getType(), defStat);
        double defValue = Math.max(1, def.effectiveStat(defStat) * defStatMod);
        double dmg = levelTerm * move.getPower()
                * ((double) atk.effectiveStat(atkStat) / defValue)
                / BattleConfig.DMG_BASE;
        return Math.max(1, Math.floor(dmg * eff * stab * powerMod) + BattleConfig.DMG_FLAT);
    }

    /** 选出对目标期望伤害最高的攻击招式下标；无攻击招式返回 -1。 */
    public int bestAttackIndex(Pokemon atk, Pokemon def) {
        int best = -1;
        double bestDmg = 0;
        for (int i = 0; i < atk.moveCount(); i++) {
            Move move = atk.move(i);
            if (!move.isDamaging()) {
                continue;
            }
            double dmg = rawDamage(move, atk, def) * move.getAccuracy();
            if (dmg > bestDmg) {
                bestDmg = dmg;
                best = i;
            }
        }
        return best;
    }

    /** 选一只替补：优先当前 HP 比例最高的存活后备。 */
    public int pickReplacement(Team team) {
        int best = -1;
        double bestRatio = -1;
        for (int i = 0; i < team.size(); i++) {
            if (i == team.activeIndex() || !team.isAlive(i)) {
                continue;
            }
            double ratio = team.member(i).hpRatio();
            if (ratio > bestRatio) {
                bestRatio = ratio;
                best = i;
            }
        }
        return best;
    }

    // ---------- 校验 ----------

    private String validate(Team team, BattleAction action, boolean isPlayer) {
        if (action instanceof BattleAction.Attack atk) {
            if (atk.moveIndex() < 0 || atk.moveIndex() >= team.active().moveCount()) {
                return "没有这个招式。";
            }
        } else if (action instanceof BattleAction.SwitchTo sw) {
            if (sw.memberIndex() < 0 || sw.memberIndex() >= team.size()) {
                return "没有这只宝可梦。";
            }
            if (sw.memberIndex() == team.activeIndex()) {
                return "不能换出当前出战的宝可梦。";
            }
            if (!team.isAlive(sw.memberIndex())) {
                return "该宝可梦已经濒死，无法上场。";
            }
        } else if (action instanceof BattleAction.UseItem use) {
            Item item = use.item();
            if (item == null || !item.isBattleUsable()) {
                return "该道具无法在对战中使用。";
            }
            if (team.bag().count(item) <= 0) {
                return "背包中没有这个道具。";
            }
            if (!item.isUsableOn(team.active())) {
                return "当前宝可梦无法使用该道具（可能 HP 已满）。";
            }
        } else if (action instanceof BattleAction.Flee && !isWildBattle()) {
            return "与训练家的对战中不能逃跑。";
        }
        return null;
    }

    private String sideName(boolean isPlayer) {
        return isPlayer ? "我方" : "对方";
    }

    private String effectNote(double eff) {
        if (eff > 1.0) {
            return "（效果拔群！）";
        }
        if (eff < 1.0) {
            return "（效果不佳…）";
        }
        return "";
    }

    /** 记录行动槽：来源方 + 行动。 */
    private record ActionSlot(boolean isPlayer, BattleAction action) {
    }
}
