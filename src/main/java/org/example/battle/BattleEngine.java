package org.example.battle;

import org.example.model.ElementType;
import org.example.model.HeldItem;
import org.example.model.HeldItemEffect;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.StatChange;
import org.example.model.StatusCondition;
import org.example.model.Terrain;
import org.example.model.Trainer;
import org.example.model.TypeChart;
import org.example.model.Weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 回合制对战引擎：{@link BattleService} 的默认实现。
 *
 * <p>规则：玩家与对手每回合各执行一次行动（技能 / 道具 / 逃跑 / 换宠）。对手可以是单只野生
 * 精灵，也可以是持有一整支队伍的训练师（训练师轮战）。双方都用技能时按速度快者先动（相同速度
 * 随机）。物理技能取 物攻 vs 物防，特殊技能取 特攻 vs 特防，伤害受克制倍率、STAB(本系加成)
 * 与随机浮动影响。捕捉成功、逃跑成功或一方精灵全部倒下即结束。</p>
 *
 * <p><b>命中判定</b>：招式按 {@link Move#getAccuracy()} 掷骰，未命中仅追加日志、不造成伤害
 * 也不施加任何附加效果（PP 已在行动前扣除）。命中率为 {@code -1}（必中）或 {@code >= 100}
 * 时恒命中，且不消耗随机数。</p>
 *
 * <p><b>能力等级</b>：变化类技能可增减自身或对方的物攻、物防、特攻、特防、速度等级
 * （{@link Move#getStatChanges()}，幅度 -6 ~ +6，按 {@link Pokemon#stageMultiplier(int)} 换算），
 * 等级为挥发性状态，离场（换宠、倒下）与战斗开始时清零。</p>
 *
 * <p><b>训练师轮战</b>（{@link #getTrainer()} 非空）：战斗持续到某一方队伍精灵全部倒下为止。
 * 不可逃跑、不可捕捉训练师的精灵；敌方当前出战精灵倒下后自动派出下一只健康的（敌方不会主动
 * 换宠），天气/场地与技能 PP 跨整场持续。胜利时经验按整队被击败对手一次性结算。</p>
 *
 * <p><b>己方倒下后的补位</b>：玩家出战精灵倒下且队伍仍有健康精灵时，引擎<b>不自动补位</b>，
 * 而是进入「等待玩家选择替补」状态（{@link #isAwaitingReplacement()}）：此时所有回合行动方法
 * 都抛 {@link IllegalStateException}，必须先调用 {@link #chooseReplacement(int)} 选出一只接着
 * 上场（不消耗回合、敌方不会行动）。队伍已无健康精灵时直接判 {@link Status#PLAYER_LOSE}。</p>
 *
 * <p><b>职责边界</b>：本引擎只做战斗演算与胜负结算，<b>不负责经验增加、升级、学招与进化</b>。
 * 结算出胜利后把「参战且未倒下的己方精灵」与「被击败的对手」交给外部成长模块
 * （{@link BattleGrowthPort}）判定；成长模块返回的日志文本行原样进入战斗日志，返回的
 * 「技能栏已满」挂起学招项由本引擎转发给调用方（{@link #pendingLearnChoices()}），
 * 玩家经 {@link #decideLearn(int)} 选择遗忘哪一招或放弃学习后再交回成长模块执行。</p>
 *
 * <p>战斗支持天气与场地（原版宝可梦风格）：携带 {@link MoveEffect} 的变化类技能会开启对应
 * 天气/场地，持续 {@value Weather#DURATION_TURNS} 回合（含开启当回合）后自然消退；生效期间
 * 按各自倍率调整招式威力，沙暴/冰雹回合末对非免疫精灵扣血，青草场地回合末回复场上精灵。</p>
 *
 * <p><b>异常状态</b>（原版宝可梦风格，规则集中在 {@link StatusCondition}）：技能可附带异常状态
 * （{@link Move#getInflicts()}，按 {@link Move#getInflictionChance()} 概率触发，属性免疫的精灵
 * 不会陷入）。行动前判定睡眠/冰冻（无法行动）、麻痹（{@value StatusCondition#PARALYSIS_SKIP_CHANCE}
 * 概率无法行动）与混乱（{@value StatusCondition#CONFUSION_SELF_HIT_CHANCE} 概率自伤）；麻痹使实际
 * 速度减半（影响先后手与逃跑），灼伤使物理攻击减半；中毒/灼伤/剧毒在回合末扣血（剧毒逐回合递增），
 * 混乱在回合末递减。换宠清除混乱（挥发性异常），主要异常保留至治愈。</p>
 *
 * <p><b>数据来源</b>：引擎只消费外部数据与外部判定，通过 {@link BattleDataPort} 查询技能与种族
 * （伤害计算 / 野生个体生成），通过 {@link BattleGrowthPort} 申报成长；未注入时相关功能降级为
 * 无操作，引擎本身不内建任何数据，也不含任何成长规则。</p>
 *
 * <p>战斗行为契约见 {@link BattleService}，实例统一由 {@link BattleServices} 工厂创建，
 * 调用方不应直接持有本实现类。</p>
 */
public class BattleEngine implements BattleService {

    /** 目标陷入睡眠 / 麻痹时的捕捉率加成系数（其余状态无加成）。 */
    private static final double CAPTURE_STATUS_BONUS = 2.0;

    /** 守住首次使用的成功百分比；连续使用每多一次右移一位（50%、25%、12%……），最低 1%。 */
    private static final int PROTECT_BASE_CHANCE = 100;

    private final Player player;
    /** 野生战斗中的敌方野生精灵；训练师轮战（{@code trainer} 非空）时为 {@code null}。 */
    private final Pokemon wild;
    /** 训练师轮战中的敌方训练师；野生战斗时为 {@code null}。 */
    private final Trainer trainer;
    private final Random random;
    /** 外部注入的只读数据端口：伤害计算 / 野生个体生成等所需技能与种族数据的唯一来源。 */
    private final BattleDataPort dataPort;
    /** 外部注入的成长申报端口：经验 / 升级 / 学招 / 进化判定全部交由其实施。 */
    private final BattleGrowthPort growthPort;
    /** 全程日志（按行累积）。 */
    private final List<String> log = new ArrayList<>();
    /** 待取走的演出事件队列（界面动画数据源，见 {@link #drainEvents()}）。 */
    private final List<BattleEvent> events = new ArrayList<>();
    /** 获胜升级后「技能满、待玩家抉择是否/如何学习」的请求队列。 */
    private final List<LearnChoice> pendingLearns = new ArrayList<>();

    private Status status = Status.ONGOING;
    /** 是否正等待玩家为倒下的出战精灵选择替补（战斗仍未结束，但一切回合行动都被挂起）。 */
    private boolean awaitingReplacement = false;
    /** 当前天气（无天气为 {@link Weather#NONE}）。 */
    private Weather weather = Weather.NONE;
    /** 当前场地（无场地为 {@link Terrain#NONE}）。 */
    private Terrain terrain = Terrain.NONE;
    /** 当前天气剩余回合数（含开启当回合；0 表示无天气）。 */
    private int weatherTurnsLeft = 0;
    /** 当前场地剩余回合数（含开启当回合；0 表示无场地）。 */
    private int terrainTurnsLeft = 0;

    public BattleEngine(Player player, Pokemon wild) {
        this(player, wild, null, new Random(), BattleDataPorts.none(), BattleGrowthPort.none());
    }

    public BattleEngine(Player player, Pokemon wild, Random random) {
        this(player, wild, null, random, BattleDataPorts.none(), BattleGrowthPort.none());
    }

    /**
     * 创建一场野生战斗，并注入外部数据端口。
     *
     * @param dataPort 只读数据端口，不可为 {@code null}
     */
    public BattleEngine(Player player, Pokemon wild, BattleDataPort dataPort) {
        this(player, wild, null, new Random(), dataPort, BattleGrowthPort.none());
    }

    /**
     * 创建一场野生战斗，并注入随机源与外部数据端口。
     *
     * @param random   随机源
     * @param dataPort 只读数据端口，不可为 {@code null}
     */
    public BattleEngine(Player player, Pokemon wild, Random random, BattleDataPort dataPort) {
        this(player, wild, null, random, dataPort, BattleGrowthPort.none());
    }

    /**
     * 创建一场野生战斗，并注入外部数据端口与成长申报端口。
     *
     * @param random     随机源
     * @param dataPort   只读数据端口，不可为 {@code null}
     * @param growthPort 成长申报端口（经验 / 升级 / 学招 / 进化判定），不可为 {@code null}
     */
    public BattleEngine(Player player, Pokemon wild, Random random, BattleDataPort dataPort,
                        BattleGrowthPort growthPort) {
        this(player, wild, null, random, dataPort, growthPort);
    }

    /** 创建一场训练师轮战（玩家 × 训练师）：不可逃跑、不可捕捉，一方精灵全部倒下才结束。 */
    public BattleEngine(Player player, Trainer trainer) {
        this(player, null, trainer, new Random(), BattleDataPorts.none(), BattleGrowthPort.none());
    }

    public BattleEngine(Player player, Trainer trainer, Random random) {
        this(player, null, trainer, random, BattleDataPorts.none(), BattleGrowthPort.none());
    }

    /**
     * 创建一场训练师轮战，并注入外部数据端口。
     *
     * @param dataPort 只读数据端口，不可为 {@code null}
     */
    public BattleEngine(Player player, Trainer trainer, BattleDataPort dataPort) {
        this(player, null, trainer, new Random(), dataPort, BattleGrowthPort.none());
    }

    /**
     * 创建一场训练师轮战，并注入随机源与外部数据端口。
     *
     * @param random   随机源
     * @param dataPort 只读数据端口，不可为 {@code null}
     */
    public BattleEngine(Player player, Trainer trainer, Random random, BattleDataPort dataPort) {
        this(player, null, trainer, random, dataPort, BattleGrowthPort.none());
    }

    /**
     * 创建一场训练师轮战，并注入外部数据端口与成长申报端口。
     *
     * @param random     随机源
     * @param dataPort   只读数据端口，不可为 {@code null}
     * @param growthPort 成长申报端口（经验 / 升级 / 学招 / 进化判定），不可为 {@code null}
     */
    public BattleEngine(Player player, Trainer trainer, Random random, BattleDataPort dataPort,
                        BattleGrowthPort growthPort) {
        this(player, null, trainer, random, dataPort, growthPort);
    }

    private BattleEngine(Player player, Pokemon wild, Trainer trainer, Random random,
                         BattleDataPort dataPort, BattleGrowthPort growthPort) {
        this.player = Objects.requireNonNull(player);
        this.wild = wild;
        this.trainer = trainer;
        this.random = Objects.requireNonNull(random);
        this.dataPort = Objects.requireNonNull(dataPort);
        this.growthPort = Objects.requireNonNull(growthPort);
        if (trainer != null && wild != null) {
            throw new IllegalArgumentException("野生精灵与训练师不能同时存在");
        }
        if (player.getActive() == null || player.getActive().isFainted()) {
            throw new IllegalArgumentException("玩家没有可用精灵出战");
        }
        if (trainer != null) {
            trainer.leadWithFirstHealthy();
            if (trainer.getActive() == null || trainer.getActive().isFainted()) {
                throw new IllegalArgumentException("训练师没有可用精灵出战");
            }
        } else if (wild == null || wild.isFainted()) {
            throw new IllegalArgumentException("野生精灵无效");
        }
        // 挥发性战斗状态（能力等级、守住、寄生种子）不写入存档，每场战斗都从干净状态开始
        player.getActive().clearVolatileState();
        Pokemon opponent = trainer != null ? trainer.getActive() : wild;
        if (opponent != null) {
            opponent.clearVolatileState();
        }
        events.add(BattleEvent.battleStart()); // 开场事件：界面据此播放双方进场动画
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public Pokemon getWild() {
        return wild;
    }

    /** @return 敌方训练师；野生战斗返回 {@code null}。 */
    @Override
    public Trainer getTrainer() {
        return trainer;
    }

    /** 当前敌方出战精灵：野生战斗为野生精灵，训练师轮战为训练师当前出战精灵。 */
    @Override
    public Pokemon foeActive() {
        return trainer != null ? trainer.getActive() : wild;
    }

    @Override
    public Pokemon playerActive() {
        return player.getActive();
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public boolean isOngoing() {
        return status == Status.ONGOING;
    }

    @Override
    public boolean isAwaitingReplacement() {
        return awaitingReplacement;
    }

    /**
     * 己方出战精灵倒下后由玩家选择下一只上场精灵：不消耗回合，敌方不会行动。
     *
     * <p>上场精灵从中立状态开始（清除能力等级、守住、寄生种子等挥发性状态），并压入一条
     * 「放出」演出事件供界面播放换宠动画。</p>
     */
    @Override
    public List<String> chooseReplacement(int partyIndex) {
        int mark = log.size();
        if (!awaitingReplacement) {
            throw new IllegalStateException("当前不需要选择上场的精灵");
        }
        Pokemon target = player.switchTo(partyIndex);
        if (target == null) {
            append("倒下的精灵不能上场，请选择其他精灵。");
            return slice(mark);
        }
        awaitingReplacement = false;
        append("你派出了 " + target.getName() + "！");
        target.clearVolatileState(); // 上场即从中立状态开始
        events.add(BattleEvent.sendOut(BattleEvent.Side.PLAYER, target.getName(), BattleEvent.hpOf(target)));
        return slice(mark);
    }

    @Override
    public Weather getWeather() {
        return weather;
    }

    @Override
    public Terrain getTerrain() {
        return terrain;
    }

    /** 完整战斗日志（只读）。 */
    @Override
    public List<String> getLog() {
        return Collections.unmodifiableList(log);
    }

    /**
     * 取走并清空演出事件队列：界面在每次行动结算后播放这些事件对应的动画。
     * <p>事件不参与任何战斗结算，忽略它们的调用方行为与本特性引入前一致。</p>
     */
    @Override
    public List<BattleEvent> drainEvents() {
        List<BattleEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    /** 背包（从玩家处转发，便捷）。 */
    @Override
    public org.example.model.Bag getBag() {
        return player.getBag();
    }

    // ------------------------------------------------------------------
    // 获胜结算：学招抉择
    // ------------------------------------------------------------------

    @Override
    public List<LearnChoice> pendingLearnChoices() {
        return List.copyOf(pendingLearns);
    }

    /**
     * 处理队首一项待抉择学招：替换指定槽位（0~3），或传 -1 放弃学习。
     *
     * <p>本引擎只做转发：抉择仍由外部成长模块执行（见 {@link BattleGrowthPort#resolveLearn}），
     * 返回的日志文本行原样追加。</p>
     */
    @Override
    public List<String> decideLearn(int forgetSlotIndex) {
        int mark = log.size();
        if (pendingLearns.isEmpty()) {
            throw new IllegalStateException("当前没有待抉择的新技能学习");
        }
        LearnChoice choice = pendingLearns.remove(0);
        for (String line : growthPort.resolveLearn(choice, forgetSlotIndex)) {
            append(line);
        }
        return slice(mark);
    }

    // ------------------------------------------------------------------
    // 回合行动入口
    // ------------------------------------------------------------------

    /**
     * 玩家选择技能。敌方自动选择可用技能；按计入异常状态后的实际速度决定先后。
     *
     * @return 本回合产生的新日志
     */
    @Override
    public List<String> useMove(MoveSlot slot) {
        int mark = log.size();
        requirePlayerAction();
        MoveSlot usable = usableSlot(playerActive(), slot);
        if (usable == null) {
            return slice(mark);
        }

        // 决定本回合先后手：双方都行动，先比较先制度，再比较计入异常状态后的实际速度
        Pokemon foe = foeActive();
        Move playerMove = usable.getMove();
        // 敌方技能由随机抽取产生；仅当必须知道它才能比较先制度时才预先取出，
        // 避免在「双方先制度都是 0」这一绝大多数情况下改变随机数消耗顺序
        MoveSlot foeSlot = needsFoeMoveForOrder(foe, playerMove) ? pickFoeMove() : null;
        boolean playerFirst = firstMover(playerActive(), playerMove, foe,
                foeSlot == null ? null : foeSlot.getMove());

        if (playerFirst) {
            playerAct(usable, foe);
            if (isOngoing() && !foe.isFainted() && !playerActive().isFainted()) {
                foeTurn(foeSlot);
            }
        } else {
            foeTurn(foeSlot);
            if (isOngoing() && !playerActive().isFainted() && !foe.isFainted()) {
                playerAct(usable, foe);
            }
        }
        finishRound();
        return slice(mark);
    }

    /**
     * 本回合先后手。
     *
     * <p>先比较先制度（{@link Move#getPriority()}，高者无视速度先手）；先制度相同时才按计入异常
     * 状态后的实际速度比较，速度相同则随机（先制之爪在速度判定阶段生效）。</p>
     *
     * @param foeMove 敌方本回合使用的技能；{@code null} 表示未预先抽取，此时要求敌方全部可用技能
     *                的先制度一致（由 {@link #needsFoeMoveForOrder} 保证），或敌方只能挣扎
     */
    private boolean firstMover(Pokemon playerPokemon, Move playerMove, Pokemon foe, Move foeMove) {
        if (foe == null) {
            return true;
        }
        int playerPriority = playerMove == null ? 0 : playerMove.getPriority();
        if (foeMove != null) {
            int foePriority = foeMove.getPriority();
            return playerPriority != foePriority ? playerPriority > foePriority
                    : speedOrder(playerPokemon, foe);
        }
        int[] range = foePriorityRange(foe);
        if (playerPriority < range[0]) {
            return false; // 敌方全部可用技能的先制度都高于玩家
        }
        if (playerPriority > range[1]) {
            return true; // 玩家先制度高于敌方全部可用技能
        }
        return speedOrder(playerPokemon, foe); // 先制度相同：回退速度判定
    }

    /** 敌方可用技能的先制度区间 {@code [min, max]}；无可用技能（只能挣扎）时为 {@code [0, 0]}。 */
    private static int[] foePriorityRange(Pokemon foe) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (MoveSlot slot : foe.getMoveSlots()) {
            if (slot.exhausted()) {
                continue;
            }
            int priority = slot.getMove().getPriority();
            min = Math.min(min, priority);
            max = Math.max(max, priority);
        }
        return min == Integer.MAX_VALUE ? new int[]{0, 0} : new int[]{min, max};
    }

    /**
     * 是否需要预先抽取敌方技能才能判定先后手：仅当敌方可用技能的先制度不唯一
     * （有高有低）且玩家的先制度落在该区间内时为真。
     * <p>绝大多数战斗里双方技能先制度都是 0，此处返回 {@code false}，随机数的消耗顺序与
     * 未实现先制度时完全一致。</p>
     */
    private static boolean needsFoeMoveForOrder(Pokemon foe, Move playerMove) {
        if (foe == null) {
            return false;
        }
        int[] range = foePriorityRange(foe);
        if (range[0] == range[1]) {
            return false;
        }
        int playerPriority = playerMove == null ? 0 : playerMove.getPriority();
        return playerPriority >= range[0] && playerPriority <= range[1];
    }

    /** 先制度相同时的出手顺序：先制之爪概率触发；未分出高下时按计入异常状态后的实际速度比较；速度相同则随机。 */
    private boolean speedOrder(Pokemon playerPokemon, Pokemon foe) {
        boolean playerClaw = quickClawTriggers(playerPokemon);
        boolean foeClaw = quickClawTriggers(foe);
        if (playerClaw != foeClaw) {
            // 仅一方触发先制之爪：无视速度先手
            if (playerClaw) {
                append(playerPokemon.getName() + " 的先制之爪抢先行动！");
                return true;
            }
            append(foe.getName() + " 的先制之爪抢先行动！");
            return false;
        }
        int playerSpeed = playerPokemon.effectiveSpeed();
        int foeSpeed = foe.effectiveSpeed();
        return playerSpeed > foeSpeed || (playerSpeed == foeSpeed && random.nextBoolean());
    }

    /** 先制之爪判定：携带者按概率触发（0 概率视为不触发）；双方同时触发时回退到速度判定。 */
    private boolean quickClawTriggers(Pokemon p) {
        if (p == null || p.getHeldItem() == null
                || p.getHeldItem().getEffectType() != HeldItemEffect.FIRST_STRIKE) {
            return false;
        }
        return random.nextInt(100) < p.getHeldItem().chanceParam();
    }

    /** 玩家执行行动：未通过异常状态判定则不消耗 PP，通过后才播报并使用技能。 */
    private void playerAct(MoveSlot slot, Pokemon foe) {
        Pokemon self = playerActive();
        if (!canAct(self)) {
            return;
        }
        if (!slot.use()) {
            append(self.getName() + " 的【" + slot.getMove().getName() + "】PP 不足！");
            return;
        }
        append(self.getName() + " 使用了【" + slot.getMove().getName() + "】！");
        events.add(BattleEvent.move(BattleEvent.Side.PLAYER, self.getName(), slot.getMove()));
        executeMove(self, foe, slot.getMove());
    }

    /**
     * 玩家使用道具于指定队伍精灵：回复道具回复目标 HP、解除道具治愈目标异常状态；
     * 精灵球尝试捕捉野生精灵（与目标无关）。道具使用不计先后手（视为先行动作），
     * 使用后若战斗未结束则敌方行动一次。训练师轮战中投掷精灵球会被拒绝（球不消耗）。
     *
     * @param item       要使用的道具；为 {@code null} 或背包数量不足时本回合不行动
     * @param partyIndex 目标精灵在玩家队伍中的下标；越界时本回合不行动
     * @return 本回合产生的新日志
     */
    @Override
    public List<String> useItem(Item item, int partyIndex) {
        int mark = log.size();
        requirePlayerAction();
        if (item == null || player.getBag().countOf(item) <= 0) {
            return slice(mark);
        }
        if (item.getCategory() == ItemCategory.POKE_BALL) {
            // 训练师轮战不可捕捉（即使 alwaysCatch 的球也不行）；野生遭遇才能捕捉
            if (trainer != null) {
                append("训练师的精灵无法被捕捉！");
                return slice(mark);
            }
            player.getBag().consume(item);
            append("向 " + wild.getName() + " 投出了【" + item.getName() + "】！");
            boolean caught = tryCapture(item);
            events.add(BattleEvent.capture(item.getName(), caught));
            if (!caught && isOngoing() && !wild.isFainted() && !playerActive().isFainted()) {
                foeTurn(null);
            }
            finishRound();
            return slice(mark);
        }
        Pokemon target = partyAt(partyIndex);
        if (target == null) {
            append("没有可以使用的目标。");
            return slice(mark);
        }
        if (item.getCategory() == ItemCategory.HEAL) {
            if (target.isFainted()) {
                append(target.getName() + " 已经倒下了，【" + item.getName() + "】无法使用。");
                return slice(mark);
            }
            int healed = target.heal((int) item.getEffect());
            if (healed <= 0) {
                append(target.getName() + " 的 HP 是满的，【" + item.getName() + "】没有使用。");
                return slice(mark);
            }
            player.getBag().consume(item);
            append("使用了【" + item.getName() + "】，" + target.getName() + " 回复了 " + healed + " HP");
            events.add(BattleEvent.item(item.getName(), BattleEvent.hpOf(playerActive())));
        } else if (item.getCategory() == ItemCategory.CURE) {
            if (!cureWithItem(target, item)) {
                append(target.getName() + " 没有可解除的异常状态，【" + item.getName() + "】没有使用。");
                return slice(mark);
            }
            player.getBag().consume(item);
            append("使用了【" + item.getName() + "】");
            events.add(BattleEvent.item(item.getName(), BattleEvent.hpOf(playerActive())));
        } else {
            append("该道具暂时无法使用");
            return slice(mark);
        }
        if (isOngoing() && !foeActive().isFainted() && !playerActive().isFainted()) {
            foeTurn(null);
        }
        finishRound();
        return slice(mark);
    }

    /** 取玩家队伍中指定下标的精灵；下标越界返回 {@code null}。 */
    private Pokemon partyAt(int partyIndex) {
        List<Pokemon> party = player.getParty();
        return partyIndex >= 0 && partyIndex < party.size() ? party.get(partyIndex) : null;
    }

    /**
     * 玩家尝试逃跑：速度越快成功率越高。失败则敌方行动一次。
     * 训练师轮战中无法逃跑，仅追加提示日志。
     *
     * @return 本回合产生的新日志
     */
    @Override
    public List<String> tryRun() {
        int mark = log.size();
        requirePlayerAction();
        if (trainer != null) {
            append("与训练师的对战中无法逃跑！");
            return slice(mark);
        }
        int playerSpeed = playerActive().effectiveSpeed();
        int wildSpeed = wild.effectiveSpeed();
        double ratio = (double) playerSpeed / Math.max(1, playerSpeed + wildSpeed);
        double chance = 0.35 + 0.6 * ratio; // 速度相当约 0.65，远超时接近 0.95
        if (random.nextDouble() < chance) {
            append("成功逃跑了！");
            status = Status.FLED;
            events.add(BattleEvent.run(true));
            return slice(mark);
        }
        append("逃跑失败……");
        events.add(BattleEvent.run(false));
        if (!wild.isFainted() && !playerActive().isFainted()) {
            foeTurn(null);
        }
        finishRound();
        return slice(mark);
    }

    /**
     * 玩家回合切换出战精灵：消耗本回合行动，收换完成后敌方行动一次。
     *
     * @return 本回合产生的新日志
     */
    @Override
    public List<String> switchActive(int partyIndex) {
        int mark = log.size();
        requirePlayerAction();
        Pokemon current = playerActive();
        Pokemon target = player.switchTo(partyIndex);
        if (current == null || target == null || target == current) {
            return slice(mark);
        }
        append(player.getName() + " 收回了 " + current.getName() + "！");
        events.add(BattleEvent.recall(BattleEvent.Side.PLAYER, current.getName()));
        if (current.clearConfusion()) {
            append(current.getName() + " 的混乱解除了！");
        }
        append("你派出了 " + target.getName() + "！");
        events.add(BattleEvent.sendOut(BattleEvent.Side.PLAYER, target.getName()));
        // 能力等级与守住/寄生种子都是挥发性状态：下场的精灵放弃自己的状态，上场的精灵从中立状态开始
        current.clearVolatileState();
        target.clearVolatileState();
        if (!foeActive().isFainted() && !target.isFainted()) {
            foeTurn(null);
        }
        finishRound();
        return slice(mark);
    }

    // ------------------------------------------------------------------
    // 内部流程
    // ------------------------------------------------------------------

    /**
     * 敌方回合行动。
     *
     * @param preselected 先后手判定阶段已预先抽取的敌方技能；{@code null} 表示在此处再抽取
     */
    private void foeTurn(MoveSlot preselected) {
        if (status != Status.ONGOING) {
            return;
        }
        Pokemon foe = foeActive();
        if (foe == null) {
            return;
        }
        if (!canAct(foe)) {
            return;
        }
        MoveSlot usable = preselected != null && !preselected.exhausted()
                ? preselected : pickFoeMove();
        if (usable == null) {
            append(foe.getName() + " 没有可用技能了，正在挣扎！");
            events.add(BattleEvent.move(BattleEvent.Side.FOE, foe.getName(), null));
            int dmg = Math.max(1, foe.getLevel() / 4);
            int dealt = playerActive().takeDamage(dmg);
            append("对 " + playerActive().getName() + " 造成了 " + dealt + " 点伤害");
            events.add(BattleEvent.hit(BattleEvent.Side.PLAYER, playerActive().getName(),
                    ElementType.NORMAL, MoveCategory.PHYSICAL, BattleEvent.hpOf(playerActive())));
            if (playerActive().isFainted()) {
                append(playerActive().getName() + " 倒下了！");
                events.add(BattleEvent.faint(BattleEvent.Side.PLAYER, playerActive().getName(),
                        BattleEvent.hpOf(playerActive())));
            }
        } else {
            usable.use();
            append(foe.getName() + " 使用了【" + usable.getMove().getName() + "】！");
            events.add(BattleEvent.move(BattleEvent.Side.FOE, foe.getName(), usable.getMove()));
            executeMove(foe, playerActive(), usable.getMove());
        }
    }

    /** 敌方自动选择技能：从仍有 PP 的技能中随机挑一个。 */
    private MoveSlot pickFoeMove() {
        Pokemon foe = foeActive();
        if (foe == null) {
            return null;
        }
        List<MoveSlot> usable = foe.getMoveSlots().stream()
                .filter(s -> !s.exhausted())
                .toList();
        if (usable.isEmpty()) {
            return null;
        }
        return usable.get(random.nextInt(usable.size()));
    }

    private MoveSlot usableSlot(Pokemon pokemon, MoveSlot preferred) {
        // 优先使用玩家指定的槽位（必须是当前精灵且仍有 PP）
        if (preferred != null && !preferred.exhausted()) {
            for (MoveSlot s : pokemon.getMoveSlots()) {
                if (s == preferred) {
                    return s;
                }
            }
        }
        // 否则（如指定的精灵已倒下被切换）退回当前精灵第一个可用技能
        return pokemon.getMoveSlots().stream()
                .filter(s -> !s.exhausted())
                .findFirst()
                .orElse(null);
    }

    /**
     * 执行一次行动：先做命中判定；变化类技能应用能力等级变化、天气/场地效果并按概率施加异常状态，
     * 其余（物理/特殊）技能正常造成伤害。
     */
    private void executeMove(Pokemon attacker, Pokemon defender, Move move) {
        // 守住优先于命中判定：被守住挡下的技能既不掷命中骰子，也不产生命中演出
        if (blocksWithProtect(defender)) {
            return;
        }
        if (!moveHits(attacker, move)) {
            return;
        }
        // 连续使用守住的成功率递减，「守住连续次数」在改用其它技能后清零
        if (attacker != null && move.getEffect() != MoveEffect.PROTECT) {
            attacker.setProtectStreak(0);
        }
        if (move.isStatus()) {
            // 纯变化招：能力等级 / 天气场地 / 专属效果 / 异常状态互不排斥；都没有时提示无效果
            if (move.hasStatChanges()) {
                applyStatChanges(attacker, defender, move);
            }
            if (move.getEffect() != MoveEffect.NONE
                    || (!move.hasInfliction() && !move.hasStatChanges())) {
                applyFieldEffect(attacker, defender, move.getEffect());
            }
            tryInflict(move, defender);
            events.add(BattleEvent.hit(sideOf(defender), nameOf(defender), move.getType(),
                    MoveCategory.STATUS, BattleEvent.hpOf(defender)));
            return;
        }
        performAttack(attacker, defender, move);
    }

    /**
     * 守住判定：目标当回合处于守住状态时完全免疫本次技能。
     *
     * @return 是否被守住挡下（为真时调用方需直接返回）
     */
    private boolean blocksWithProtect(Pokemon defender) {
        if (defender == null || defender.isFainted() || !defender.isProtected()) {
            return false;
        }
        append(defender.getName() + " 用守住挡下了攻击！");
        return true;
    }

    /**
     * 命中判定。
     *
     * <p>命中率为 {@code -1}（必中）或 {@code >= 100} 时恒判定命中，且<b>不消耗任何随机数</b>
     * （保证无命中判定的历史随机序列完全不变）；仅 {@code 0 ~ 99} 的命中率会掷一次骰。</p>
     *
     * <p>未命中不产生演出事件（招式动画已在行动开始时播报），仅追加日志；PP 已在行动前扣除。</p>
     *
     * @return 本次行动是否命中（未命中时调用方需直接返回）
     */
    private boolean moveHits(Pokemon attacker, Move move) {
        int accuracy = move.getAccuracy();
        if (accuracy < 0 || accuracy >= 100) {
            return true;
        }
        if (random.nextInt(100) < accuracy) {
            return true;
        }
        append(attacker.getName() + " 的【" + move.getName() + "】没有命中！");
        return false;
    }

    /**
     * 应用招式附带的能力等级增减，并按<b>实际</b>变化量播报。
     *
     * <p>幅度 ≥2 时用「大幅提高/下降」描述；等级已达 {@value Pokemon#MAX_STAT_STAGE}（或
     * {@value Pokemon#MIN_STAT_STAGE}）时提示「已经无法再提高/下降」。</p>
     */
    private void applyStatChanges(Pokemon attacker, Pokemon defender, Move move) {
        for (StatChange change : move.getStatChanges()) {
            Pokemon target = change.recipient() == StatChange.Recipient.SELF ? attacker : defender;
            if (target == null || target.isFainted()) {
                continue;
            }
            int actual = target.changeStatStage(change.stat(), change.delta());
            String stat = change.stat().getDisplayName();
            if (actual > 0) {
                append(target.getName() + " 的" + stat + (actual >= 2 ? "大幅" : "") + "提高了！");
            } else if (actual < 0) {
                append(target.getName() + " 的" + stat + (actual <= -2 ? "大幅" : "") + "下降了！");
            } else {
                append(target.getName() + " 的" + stat
                        + (change.isIncrease() ? "已经无法再提高了！" : "已经无法再下降了！"));
            }
        }
    }

    /**
     * 行动前异常状态判定。
     *
     * <p>睡眠/冰冻彻底无法行动（冰冻每回合有 {@value StatusCondition#FREEZE_THAW_CHANCE} 概率解冻），
     * 麻痹有 {@value StatusCondition#PARALYSIS_SKIP_CHANCE} 概率无法行动，混乱有
     * {@value StatusCondition#CONFUSION_SELF_HIT_CHANCE} 概率攻击自己。</p>
     *
     * @return 本回合能否正常行动
     */
    private boolean canAct(Pokemon p) {
        if (p == null || p.isFainted()) {
            return false;
        }
        if (p.getStatus() == StatusCondition.SLEEP) {
            if (p.tickSleep()) {
                append(p.getName() + " 正在呼呼大睡……");
                return false;
            }
            append(p.getName() + " 醒过来了！");
        }
        if (p.getStatus() == StatusCondition.FREEZE) {
            if (random.nextDouble() < StatusCondition.FREEZE_THAW_CHANCE) {
                p.cureStatus();
                append(p.getName() + " 的冰冻解除了！");
            } else {
                append(p.getName() + " 被冻住了，无法行动！");
                return false;
            }
        }
        if (p.getStatus() == StatusCondition.PARALYSIS
                && random.nextDouble() < StatusCondition.PARALYSIS_SKIP_CHANCE) {
            append(p.getName() + " 因麻痹而无法行动！");
            return false;
        }
        if (p.isConfused()) {
            if (random.nextDouble() < StatusCondition.CONFUSION_SELF_HIT_CHANCE) {
                selfHit(p);
                return false;
            }
            append(p.getName() + " 虽然混乱，但还是行动了！");
        }
        return true;
    }

    /** 混乱自伤：按威力 {@value StatusCondition#CONFUSION_SELF_HIT_POWER} 的无属性物理招式对自身结算。 */
    private void selfHit(Pokemon p) {
        append(p.getName() + " 因混乱攻击了自己！");
        double base = (2.0 * p.getLevel() / 5.0 + 2.0) * StatusCondition.CONFUSION_SELF_HIT_POWER
                * ((double) p.effectiveAttack() / Math.max(1, p.effectiveDefense())) / 50.0 + 2.0;
        int dealt = p.takeDamage(Math.max(1, (int) base));
        events.add(BattleEvent.hit(sideOf(p), nameOf(p), ElementType.NORMAL, MoveCategory.PHYSICAL,
                BattleEvent.hpOf(p)));
        append("自伤了 " + dealt + " 点伤害");
        if (p.isFainted()) {
            events.add(BattleEvent.faint(sideOf(p), nameOf(p), BattleEvent.hpOf(p)));
            append(p.getName() + " 倒下了！");
        }
    }

    /**
     * 招式生效后按概率对目标施加其附带的异常状态。
     * <p>属性免疫（如电系不会麻痹）、已有主要异常、已混乱时不会生效（判定顺序与原版一致：先免疫后概率）。</p>
     */
    private void tryInflict(Move move, Pokemon defender) {
        if (!move.hasInfliction() || defender == null || defender.isFainted()
                || move.getInflictionChance() <= 0) {
            return;
        }
        StatusCondition condition = move.getInflicts();
        if (!condition.canApply(defender)) {
            ElementType immune = condition.immunityType();
            if (immune != null && defender.hasType(immune)) {
                append(defender.getName() + " 因属性免疫，不会陷入" + condition.getDisplayName() + "！");
            } else if (condition == StatusCondition.CONFUSION) {
                append(defender.getName() + " 已经混乱了！");
            } else if (defender.getStatus() != StatusCondition.NONE) {
                append(defender.getName() + " 已经处于" + defender.getStatus().getDisplayName()
                        + "状态，无法再陷入" + condition.getDisplayName() + "！");
            }
            return;
        }
        if (random.nextInt(100) >= move.getInflictionChance()) {
            return;
        }
        int turns = switch (condition) {
            case SLEEP -> randomTurns(StatusCondition.SLEEP_MIN_TURNS, StatusCondition.SLEEP_MAX_TURNS);
            case CONFUSION -> randomTurns(StatusCondition.CONFUSION_MIN_TURNS, StatusCondition.CONFUSION_MAX_TURNS);
            default -> 0;
        };
        if (defender.tryApplyStatus(condition, turns)) {
            append(defender.getName() + " 陷入了" + condition.getDisplayName() + "状态！");
        }
    }

    /** 用解除道具治疗精灵的主要异常状态；返回是否确实解除了异常。 */
    private boolean cureWithItem(Pokemon target, Item item) {
        StatusCondition current = target.getStatus();
        if (current == StatusCondition.NONE || !item.canCure(current)) {
            return false;
        }
        target.cureStatus();
        append(target.getName() + " 的" + current.getDisplayName() + "治愈了！");
        return true;
    }

    private int randomTurns(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /**
     * 应用变化类技能的专属效果。
     *
     * <p>天气 / 场地类技能切换全场天气或场地；{@link MoveEffect#PROTECT}、{@link MoveEffect#LEECH_SEED}、
     * {@link MoveEffect#REST} 为招式专属效果，分别实现守住、寄生种子与睡觉；其余情况视为无效果。</p>
     */
    private void applyFieldEffect(Pokemon attacker, Pokemon defender, MoveEffect effect) {
        if (effect == null || effect == MoveEffect.NONE) {
            append("但是什么也没有发生……");
            return;
        }
        Weather w = effect.toWeather();
        if (w != null) {
            setWeather(w);
            return;
        }
        Terrain t = effect.toTerrain();
        if (t != null) {
            setTerrain(t);
            return;
        }
        switch (effect) {
            case PROTECT -> applyProtect(attacker);
            case LEECH_SEED -> applyLeechSeed(defender);
            case REST -> applyRest(attacker);
            default -> append("但是什么也没有发生……");
        }
    }

    /**
     * 守住：当回合免疫所有指向自己的技能；连续使用成功率递减（{@value #PROTECT_BASE_CHANCE} 按
     * 连续次数右移，最低 1%）。失败时不进入守住状态，但仍计入连续次数。
     */
    private void applyProtect(Pokemon p) {
        if (p == null || p.isFainted()) {
            return;
        }
        int streak = p.getProtectStreak();
        int chance = Math.max(1, PROTECT_BASE_CHANCE >> Math.min(streak, 30));
        if (random.nextInt(100) >= chance) {
            append(p.getName() + " 的守住没有成功……");
            p.setProtectStreak(streak + 1);
            return;
        }
        p.setProtected(true);
        p.setProtectStreak(streak + 1);
        append(p.getName() + " 保护了自己！");
    }

    /**
     * 寄生种子：让目标被种下种子，之后每回合结束被吸取最大 HP 的 1/8。草系免疫，重复使用无效。
     */
    private void applyLeechSeed(Pokemon defender) {
        if (defender == null || defender.isFainted()) {
            return;
        }
        if (defender.hasType(ElementType.GRASS)) {
            append(defender.getName() + " 因属性免疫，寄生种子没有效果！");
            return;
        }
        if (defender.isSeeded()) {
            append(defender.getName() + " 已经被种下了寄生种子！");
            return;
        }
        defender.setSeeded(true);
        append(defender.getName() + " 被种下了寄生种子！");
    }

    /**
     * 睡觉：HP 回满并陷入 {@value StatusCondition#REST_SLEEP_TURNS} 回合睡眠。HP 已满或已处于
     * 其它主要异常状态时失败（并提示原因）。
     */
    private void applyRest(Pokemon p) {
        if (p == null || p.isFainted()) {
            return;
        }
        if (p.getCurrentHp() >= p.getMaxHp()) {
            append(p.getName() + " 的 HP 已经是满的，睡觉失败了……");
            return;
        }
        if (p.getStatus() != StatusCondition.NONE) {
            append(p.getName() + " 已经处于" + p.getStatus().getDisplayName() + "状态，无法睡觉……");
            return;
        }
        int healed = p.heal(p.getMaxHp() - p.getCurrentHp());
        append(p.getName() + " 的 HP 恢复了 " + healed + " 点！");
        if (p.tryApplyStatus(StatusCondition.SLEEP, StatusCondition.REST_SLEEP_TURNS)) {
            append(p.getName() + " 睡着了！");
        }
    }

    /** 开启指定天气并重置持续回合数。 */
    private void setWeather(Weather target) {
        boolean refreshing = weather == target;
        weather = target;
        weatherTurnsLeft = Weather.DURATION_TURNS;
        append(target.getStartMessage());
        if (refreshing) {
            append("持续回合重置了！");
        }
    }

    /** 开启指定场地并重置持续回合数。 */
    private void setTerrain(Terrain target) {
        boolean refreshing = terrain == target;
        terrain = target;
        terrainTurnsLeft = Terrain.DURATION_TURNS;
        append(target.getStartMessage());
        if (refreshing) {
            append("持续回合重置了！");
        }
    }

    private void performAttack(Pokemon attacker, Pokemon defender, Move move) {
        double effectiveness = typeEffectiveness(move.getType(), defender);
        if (effectiveness <= 0) {
            append("这招对 " + defender.getName() + " 没有效果……");
            return;
        }
        int damage = computeDamage(attacker, defender, move);
        int dealt = defender.takeDamage(damage);
        events.add(BattleEvent.hit(sideOf(defender), nameOf(defender), move.getType(),
                move.getCategory(), BattleEvent.hpOf(defender)));
        applyLifeSteal(attacker, dealt);
        StringBuilder sb = new StringBuilder();
        sb.append("造成 ").append(dealt).append(" 点伤害");
        if (effectiveness > 1.0) {
            sb.append("，效果拔群！");
        } else if (effectiveness < 1.0) {
            sb.append("，效果不太理想……");
        }
        append(sb.toString());
        if (defender.isFainted()) {
            events.add(BattleEvent.faint(sideOf(defender), nameOf(defender), BattleEvent.hpOf(defender)));
            append(defender.getName() + " 倒下了！");
            return;
        }
        tryInflict(move, defender);
    }

    private int computeDamage(Pokemon attacker, Pokemon defender, Move move) {
        double atk;
        double def;
        if (move.getCategory() == MoveCategory.PHYSICAL) {
            atk = attacker.effectiveAttack();
            def = defender.effectiveDefense();
        } else {
            atk = attacker.effectiveSpAttack();
            def = defender.effectiveSpDefense();
        }
        // 进化辉石：未最终进化（仍有进化目标）的携带者双防提升
        if (defender.getHeldItem() != null
                && defender.getHeldItem().getEffectType() == HeldItemEffect.EVOLITE
                && defender.getSpecies().getEvolvesToId() != null) {
            def *= defender.getHeldItem().doubleParam();
        }
        int level = attacker.getLevel();
        double base = (2.0 * level / 5.0 + 2.0) * move.getPower()
                * (atk / Math.max(1.0, def)) / 50.0 + 2.0;
        double stab = attacker.hasType(move.getType()) ? 1.5 : 1.0;
        double effectiveness = typeEffectiveness(move.getType(), defender);
        // 天气与场地对招式威力的加成
        double field = weather.moveTypeMultiplier(move.getType())
                * terrain.moveTypeMultiplier(move.getType());
        // 携带装备加成：属性强化道具（木炭等）与达人带（效果拔群增伤）
        double equipment = heldItemDamageMultiplier(attacker, move, effectiveness);
        double randomFactor = 0.85 + random.nextDouble() * 0.15;
        int raw = (int) Math.floor(base * stab * effectiveness * field * equipment * randomFactor);
        return Math.max(1, raw);
    }

    /** 攻击方携带装备的伤害倍率：DAMAGE_TYPE 属性匹配时生效；SUPER_EFFECTIVE 仅在克制时生效。 */
    private static double heldItemDamageMultiplier(Pokemon attacker, Move move, double effectiveness) {
        HeldItem item = attacker.getHeldItem();
        if (item == null) {
            return 1.0;
        }
        return switch (item.getEffectType()) {
            case DAMAGE_TYPE -> {
                String typeName = item.typeParam();
                yield typeName != null && ElementType.parse(typeName) == move.getType()
                        ? item.doubleParam() : 1.0;
            }
            case SUPER_EFFECTIVE -> effectiveness > 1.0 ? item.doubleParam() : 1.0;
            default -> 1.0;
        };
    }

    private boolean tryCapture(Item ball) {
        boolean caught;
        if (ball.isAlwaysCatch()) {
            caught = true;
        } else {
            int maxHp = wild.getMaxHp();
            int curHp = wild.getCurrentHp();
            // 血量越低、捕获率越高、球倍率越大、目标陷入睡眠/麻痹则越容易
            double hpFactor = Math.max(0.0, (3.0 * maxHp - 2.0 * curHp) / (3.0 * maxHp));
            double a = hpFactor * wild.getSpecies().getCatchRate() * ball.getEffect()
                    * captureStatusBonus(wild.getStatus());
            double chance = Math.min(0.98, a / 255.0);
            caught = random.nextDouble() < chance;
        }
        if (caught) {
            append("咔哒…… 球停止了晃动！");
            append("成功捕捉了野生的 " + wild.getName() + "！");
            status = Status.CAUGHT;
            // 被捕捉的精灵加入玩家队伍，后续可再次派出；统一走受保护的 addPokemon 入口以维护队伍容量。
            if (!player.getParty().contains(wild)) {
                player.addPokemon(wild);
            }
            // 申报捕捉事件：捕捉次数驱动的局外成长（个体值加成）由成长模块自行判定
            growthPort.onCaptured(wild.getSpecies().getId());
            return true;
        }
        append("野生的 " + wild.getName() + " 挣脱了出来！");
        return false;
    }

    /**
     * 捕捉时的异常状态加成：目标陷入睡眠或麻痹时更容易被收入球中
     * （×{@value #CAPTURE_STATUS_BONUS}），其余状态与无状态均为 ×1。
     */
    private static double captureStatusBonus(StatusCondition status) {
        return status == StatusCondition.SLEEP || status == StatusCondition.PARALYSIS
                ? CAPTURE_STATUS_BONUS : 1.0;
    }

    /** 回合结束结算：胜负判定、敌方出战倒下后的自动替换/续战、玩家精灵倒下后的补位挂起。 */
    private void resolveRoundEnd() {
        if (status != Status.ONGOING) {
            return;
        }
        Pokemon pa = playerActive();
        Pokemon foe = foeActive();
        boolean foeDown = foe != null && foe.isFainted();
        boolean playerDown = pa != null && pa.isFainted();

        if (foeDown) {
            if (trainer == null) {
                // 野生战斗：野生精灵倒下即获胜
                status = Status.PLAYER_WIN;
                append("野生的 " + foe.getName() + " 倒下了！你赢了！");
                settleGrowth();
                return;
            }
            // 训练师轮战：出战精灵倒下后自动派出下一只健康的；没有了 → 玩家获胜
            Pokemon next = trainer.switchToNextHealthy();
            if (next == null) {
                status = Status.PLAYER_WIN;
                append("训练师 " + trainer.getName() + " 的所有精灵都倒下了！你赢了！");
                settleGrowth();
                return;
            }
            append(trainer.getName() + " 派出了 " + next.getName() + "！");
            next.clearVolatileState(); // 上场即从中立状态开始
            events.add(BattleEvent.sendOut(BattleEvent.Side.FOE, next.getName(),
                    BattleEvent.hpOf(next)));
        }

        if (playerDown) {
            append(pa.getName() + " 倒下了……");
            if (player.hasHealthyPokemon()) {
                // 不自动补位：挂起等待玩家选择下一只上场精灵（见 chooseReplacement）
                awaitingReplacement = true;
                append("请选择接下来上场的精灵！");
            } else {
                status = Status.PLAYER_LOSE;
                append("你已没有能战斗的精灵，战败了……");
            }
        }
    }

    /**
     * 回合收尾：双方都仍站场时先结算天气/场地回合效果（沙暴/冰雹扣血、青草回复），
     * 再做胜负判定与自动换宠，最后推进天气/场地持续回合。
     */
    private void finishRound() {
        // 守住只覆盖当回合：回合结束后立即解除，下一回合需要重新使用
        Pokemon pa = playerActive();
        Pokemon foe = foeActive();
        if (pa != null) {
            pa.setProtected(false);
        }
        if (foe != null) {
            foe.setProtected(false);
        }
        if (status != Status.ONGOING) {
            return;
        }
        boolean bothStanding = foe != null && !foe.isFainted() && pa != null && !pa.isFainted();
        if (bothStanding) {
            applyFieldEndEffects(pa);
        }
        resolveRoundEnd();
        if (status == Status.ONGOING) {
            tickFields();
        }
    }

    /**
     * 回合末天气/场地效果：沙暴/冰雹对双方扣血，青草场地对双方回复；
     * 随后结算寄生种子、携带装备与异常状态。
     */
    private void applyFieldEndEffects(Pokemon pa) {
        Pokemon foe = foeActive();
        weatherChip(pa);
        weatherChip(foe);
        if (terrain == Terrain.GRASSY) {
            grassyHeal(pa);
            grassyHeal(foe);
        }
        leftoversHeal(pa);
        leftoversHeal(foe);
        leechSeedDrain(pa, foe);
        leechSeedDrain(foe, pa);
        statusEndTurn(pa);
        statusEndTurn(foe);
    }

    /**
     * 寄生种子回合末结算：被种下的精灵损失最大 HP 的 1/8，等量回复对方出战精灵。
     *
     * @param drained     被种下的精灵
     * @param beneficiary 吸取方（对方出战精灵）
     */
    private void leechSeedDrain(Pokemon drained, Pokemon beneficiary) {
        if (drained == null || drained.isFainted() || !drained.isSeeded()) {
            return;
        }
        int dealt = drained.takeDamage(Math.max(1, drained.getMaxHp() / 8));
        append("寄生种子吸取了 " + drained.getName() + " 的养分，失去了 " + dealt + " HP！");
        if (drained.isFainted()) {
            events.add(BattleEvent.faint(sideOf(drained), nameOf(drained), BattleEvent.hpOf(drained)));
            append(drained.getName() + " 倒下了！");
            return;
        }
        if (beneficiary == null || beneficiary.isFainted()) {
            return;
        }
        int healed = beneficiary.heal(dealt);
        if (healed > 0) {
            append(beneficiary.getName() + " 恢复了 " + healed + " HP！");
        }
    }

    /** 剩饭（END_TURN_HEAL）回合末回血：按最大 HP 比例回复，已倒下不结算。 */
    private void leftoversHeal(Pokemon p) {
        if (p == null || p.isFainted() || p.getHeldItem() == null
                || p.getHeldItem().getEffectType() != HeldItemEffect.END_TURN_HEAL) {
            return;
        }
        int healed = p.heal(Math.max(1, (int) (p.getMaxHp() * p.getHeldItem().doubleParam())));
        if (healed > 0) {
            append(p.getName() + " 的" + p.getHeldItem().getName() + " 恢复了 " + healed + " HP！");
        }
    }

    /** 贝壳之铃（LIFE_STEAL）吸血：攻击造成伤害后按比例回复自身 HP。 */
    private void applyLifeSteal(Pokemon attacker, int dealt) {
        if (dealt <= 0 || attacker == null || attacker.isFainted() || attacker.getHeldItem() == null
                || attacker.getHeldItem().getEffectType() != HeldItemEffect.LIFE_STEAL) {
            return;
        }
        int healed = attacker.heal(Math.max(1, (int) (dealt * attacker.getHeldItem().doubleParam())));
        if (healed > 0) {
            append(attacker.getName() + " 用" + attacker.getHeldItem().getName() + " 恢复了 " + healed + " HP！");
        }
    }

    /**
     * 回合末异常状态结算：中毒/剧毒/灼伤按比例扣血（剧毒计数递增），混乱剩余回合递减。
     */
    private void statusEndTurn(Pokemon p) {
        if (p == null || p.isFainted()) {
            return;
        }
        StatusCondition condition = p.getStatus();
        double ratio = condition.residualDamageRatio(p.getBadlyPoisonCounter());
        if (ratio > 0) {
            int damage = Math.max(1, (int) (p.getMaxHp() * ratio));
            int dealt = p.takeDamage(damage);
            append(p.getName() + " 受到" + condition.residualMessage(p.getBadlyPoisonCounter())
                    + "的伤害，失去了 " + dealt + " HP！");
            if (condition == StatusCondition.BADLY_POISON) {
                p.increaseBadlyPoisonCounter();
            }
            if (p.isFainted()) {
                events.add(BattleEvent.faint(sideOf(p), nameOf(p), BattleEvent.hpOf(p)));
                append(p.getName() + " 倒下了！");
                return;
            }
        }
        if (p.isConfused() && p.tickConfusion()) {
            append(p.getName() + " 的混乱解除了！");
        }
    }

    /** 沙暴/冰雹回合末扣血：岩石/地面（沙暴）、冰系（冰雹）免疫，扣最大 HP 的 1/16。 */
    private void weatherChip(Pokemon p) {
        if (p == null || p.isFainted() || weather == Weather.NONE || weather.chipRatio() <= 0) {
            return;
        }
        boolean immune = switch (weather) {
            case SANDSTORM -> p.hasType(ElementType.ROCK) || p.hasType(ElementType.GROUND);
            case HAIL -> p.hasType(ElementType.ICE);
            default -> false;
        };
        if (immune) {
            return;
        }
        int dealt = p.takeDamage(Math.max(1, (int) (p.getMaxHp() * weather.chipRatio())));
        append(weather.getDisplayName() + " 侵蚀着 " + p.getName() + "，造成了 " + dealt + " 点伤害！");
        if (p.isFainted()) {
            events.add(BattleEvent.faint(sideOf(p), nameOf(p), BattleEvent.hpOf(p)));
            append(p.getName() + " 倒下了！");
        }
    }

    /** 青草场地回合末回复：飞行系不受益，回复最大 HP 的 1/16。 */
    private void grassyHeal(Pokemon p) {
        if (p == null || p.isFainted() || p.hasType(ElementType.FLYING)) {
            return;
        }
        int healed = p.heal(Math.max(1, (int) (p.getMaxHp() * Terrain.GRASSY.healRatio())));
        if (healed > 0) {
            append("青草场地 让 " + p.getName() + " 回复了 " + healed + " HP！");
        }
    }

    /** 推进天气/场地剩余回合数，归零时消退并广播结束消息。 */
    private void tickFields() {
        if (weather != Weather.NONE) {
            weatherTurnsLeft--;
            if (weatherTurnsLeft <= 0) {
                append(weather.getEndMessage());
                weather = Weather.NONE;
                weatherTurnsLeft = 0;
            }
        }
        if (terrain != Terrain.NONE) {
            terrainTurnsLeft--;
            if (terrainTurnsLeft <= 0) {
                append(terrain.getEndMessage());
                terrain = Terrain.NONE;
                terrainTurnsLeft = 0;
            }
        }
    }

    /**
     * 胜利结算：把「参战且未倒下的己方精灵」与「被击败的对手」交给外部成长模块判定
     * 经验增加、升级、学招与进化（见 {@link BattleGrowthPort}）。
     *
     * <p>本引擎不自行计算经验、不判定升级 / 学招 / 进化：成长模块返回的日志文本行原样追加，
     * 返回的「技能栏已满」挂起学招项进入待抉择队列。未注入成长端口时本方法无任何副作用。</p>
     */
    private void settleGrowth() {
        if (status != Status.PLAYER_WIN) {
            return;
        }
        List<Pokemon> survivors = new ArrayList<>();
        for (Pokemon p : player.getParty()) {
            if (!p.isFainted()) {
                survivors.add(p);
            }
        }
        List<Pokemon> defeated = trainer == null ? List.of(wild) : List.copyOf(trainer.getParty());
        BattleGrowthPort.Settlement settlement = growthPort.settle(survivors, defeated);
        for (String line : settlement.log()) {
            append(line);
        }
        pendingLearns.addAll(settlement.pendingLearns());
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static double typeEffectiveness(ElementType attack, Pokemon defender) {
        double result = 1.0;
        for (ElementType t : defender.getSpecies().getTypes()) {
            result *= TypeChart.effectiveness(attack, t);
        }
        return result;
    }

    /**
     * 校验当前可以发起回合行动：战斗未结束，且没有等待玩家选择的替补精灵
     * （己方出战精灵倒下后必须先调用 {@link #chooseReplacement(int)}）。
     */
    private void requirePlayerAction() {
        if (status != Status.ONGOING) {
            throw new IllegalStateException("战斗已结束，状态: " + status);
        }
        if (awaitingReplacement) {
            throw new IllegalStateException("需要先选择上场的精灵");
        }
    }

    /** 某只场上精灵所属阵营（玩家当前出战为 {@link BattleEvent.Side#PLAYER}，其余为 FOE）。 */
    private BattleEvent.Side sideOf(Pokemon p) {
        return p != null && p == player.getActive() ? BattleEvent.Side.PLAYER : BattleEvent.Side.FOE;
    }

    /** 事件用的精灵名（防御空引用：精灵恒存在，此处仅作兜底）。 */
    private static String nameOf(Pokemon p) {
        return p == null ? "" : p.getName();
    }

    private void append(String message) {
        log.add(message);
    }

    /** 返回自 mark 起新增的日志行。 */
    private List<String> slice(int mark) {
        return new ArrayList<>(log.subList(Math.max(0, mark), log.size()));
    }
}
