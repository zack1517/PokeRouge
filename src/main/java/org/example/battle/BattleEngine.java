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
import org.example.model.StatusCondition;
import org.example.model.Terrain;
import org.example.model.Trainer;
import org.example.model.TypeChart;
import org.example.model.Weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * <p><b>训练师轮战</b>（{@link #getTrainer()} 非空）：战斗持续到某一方队伍精灵全部倒下为止。
 * 不可逃跑、不可捕捉训练师的精灵；敌方当前出战精灵倒下后自动派出下一只健康的（敌方不会主动
 * 换宠），天气/场地与技能 PP 跨整场持续。胜利时经验按整队被击败对手一次性结算。</p>
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
    /** 当前天气（无天气为 {@link Weather#NONE}）。 */
    private Weather weather = Weather.NONE;
    /** 当前场地（无场地为 {@link Terrain#NONE}）。 */
    private Terrain terrain = Terrain.NONE;
    /** 当前天气剩余回合数（含开启当回合；0 表示无天气）。 */
    private int weatherTurnsLeft = 0;
    /** 当前场地剩余回合数（含开启当回合；0 表示无场地）。 */
    private int terrainTurnsLeft = 0;
    /**
     * 讲究系装备的招式锁定表：精灵 uuid → 本场战斗第一个成功使用过的招式 id。
     * 仅本场战斗内有效（换宠、战斗结束即失效），不落入存档。
     */
    private final Map<String, String> choiceLocks = new HashMap<>();

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
        requireOngoing();
        MoveSlot usable = usableSlot(playerActive(), slot);
        if (usable == null) {
            // 苹野果：招式 PP 全部耗尽时先补 PP 再重试（补不上才放弃本回合，与 PP 不足同样处理）
            healPpWithBerry(playerActive(), slot);
            usable = usableSlot(playerActive(), slot);
        }
        if (usable == null) {
            return slice(mark);
        }
        // 讲究系装备的招式锁定：不合法时不消耗回合，让调用方改选（与 PP 不足同样处理）
        if (!choiceAllows(playerActive(), usable)) {
            return slice(mark);
        }

        // 决定本回合先后手：双方都行动，比较计入异常状态后的实际速度
        Pokemon foe = foeActive();
        boolean playerFirst = firstMover(playerActive(), foe);

        if (playerFirst) {
            playerAct(usable, foe);
            if (isOngoing() && !foe.isFainted() && !playerActive().isFainted()) {
                foeTurn();
            }
        } else {
            foeTurn();
            if (isOngoing() && !playerActive().isFainted() && !foe.isFainted()) {
                playerAct(usable, foe);
            }
        }
        finishRound();
        return slice(mark);
    }

    /**
     * 讲究系装备（{@link HeldItemEffect#CHOICE}）的招式锁定判定：携带者本场使用过的第一个招式成为
     * 唯一可用招式。
     *
     * <p>为避免「锁定招式 PP 耗尽后无招可用」的死锁（本引擎无「挣扎」兜底），锁定招式 PP 用尽时
     * 解除限制，允许改用其它招式（改用的招式随即成为新的锁定招式）。</p>
     *
     * @return 本回合是否允许使用该招式
     */
    private boolean choiceAllows(Pokemon pokemon, MoveSlot slot) {
        if (pokemon == null || !holds(pokemon, HeldItemEffect.CHOICE)) {
            return true;
        }
        String locked = choiceLocks.get(pokemon.getUuid());
        if (locked == null || locked.equals(slot.getMove().getId())) {
            return true;
        }
        MoveSlot lockedSlot = slotOf(pokemon, locked);
        if (lockedSlot == null || lockedSlot.exhausted()) {
            return true;
        }
        append(pokemon.getName() + " 因" + pokemon.getHeldItem().getName() + "的效果，只能使用【"
                + lockedSlot.getMove().getName() + "】！");
        return false;
    }

    /** 记录讲究系装备的招式锁定：首次成功使用招式时登记，已锁定其它招式时改锁到新招式。 */
    private void lockChoiceMove(Pokemon pokemon, Move move) {
        if (pokemon != null && move != null && holds(pokemon, HeldItemEffect.CHOICE)) {
            choiceLocks.put(pokemon.getUuid(), move.getId());
        }
    }

    /** 取指定精灵持有指定 id 的招式槽；没有该招式时返回 {@code null}。 */
    private static MoveSlot slotOf(Pokemon pokemon, String moveId) {
        for (MoveSlot s : pokemon.getMoveSlots()) {
            if (s.getMove().getId().equals(moveId)) {
                return s;
            }
        }
        return null;
    }

    /**
     * 本回合先后手：先制之爪概率触发；其次后攻之尾（仅一方携带时携带方后出手）；
     * 未分胜负时按计入异常状态后的实际速度比较；速度相同则随机。
     */
    private boolean firstMover(Pokemon playerPokemon, Pokemon foe) {
        if (foe == null) {
            return true;
        }
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
        boolean playerLast = holds(playerPokemon, HeldItemEffect.MOVE_LAST);
        boolean foeLast = holds(foe, HeldItemEffect.MOVE_LAST);
        if (playerLast != foeLast) {
            // 仅一方携带后攻之尾：携带方在速度比较之前即后出手
            if (playerLast) {
                append(playerPokemon.getName() + " 因后攻之尾而最后行动！");
            } else {
                append(foe.getName() + " 因后攻之尾而最后行动！");
            }
            return !playerLast;
        }
        int playerSpeed = playerPokemon.effectiveSpeed();
        int foeSpeed = foe.effectiveSpeed();
        return playerSpeed > foeSpeed || (playerSpeed == foeSpeed && random.nextBoolean());
    }

    /** 携带指定效果类型的装备判定。 */
    private static boolean holds(Pokemon p, HeldItemEffect effect) {
        return p != null && p.getHeldItem() != null && p.getHeldItem().getEffectType() == effect;
    }

    /** 携带者是否被「地面化」（黑色铁球类装备第 2 段参数为 {@code GROUND}）。 */
    private static boolean isGrounded(Pokemon p) {
        return holds(p, HeldItemEffect.SPEED_MULTIPLIER)
                && "GROUND".equalsIgnoreCase(p.getHeldItem().textPart(1));
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
        lockChoiceMove(self, slot.getMove());
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
        requireOngoing();
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
                foeTurn();
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
            events.add(BattleEvent.item(item.getName()));
        } else if (item.getCategory() == ItemCategory.CURE) {
            if (!cureWithItem(target, item)) {
                append(target.getName() + " 没有可解除的异常状态，【" + item.getName() + "】没有使用。");
                return slice(mark);
            }
            player.getBag().consume(item);
            append("使用了【" + item.getName() + "】");
            events.add(BattleEvent.item(item.getName()));
        } else {
            append("该道具暂时无法使用");
            return slice(mark);
        }
        if (isOngoing() && !foeActive().isFainted() && !playerActive().isFainted()) {
            foeTurn();
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
        requireOngoing();
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
            foeTurn();
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
        requireOngoing();
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
        if (!foeActive().isFainted() && !target.isFainted()) {
            foeTurn();
        }
        finishRound();
        return slice(mark);
    }

    // ------------------------------------------------------------------
    // 内部流程
    // ------------------------------------------------------------------

    private void foeTurn() {
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
        MoveSlot usable = pickFoeMove();
        if (usable == null) {
            // 苹野果：全部招式 PP 耗尽时先补 PP，补不上才挣扎
            usable = healPpWithBerry(foe, null);
        }
        if (usable == null) {
            append(foe.getName() + " 没有可用技能了，正在挣扎！");
            events.add(BattleEvent.move(BattleEvent.Side.FOE, foe.getName(), null));
            int dmg = Math.max(1, foe.getLevel() / 4);
            int dealt = playerActive().takeDamage(dmg);
            append("对 " + playerActive().getName() + " 造成了 " + dealt + " 点伤害");
            events.add(BattleEvent.hit(BattleEvent.Side.PLAYER, playerActive().getName(),
                    ElementType.NORMAL, MoveCategory.PHYSICAL));
            if (playerActive().isFainted()) {
                append(playerActive().getName() + " 倒下了！");
                events.add(BattleEvent.faint(BattleEvent.Side.PLAYER, playerActive().getName()));
            }
        } else {
            usable.use();
            append(foe.getName() + " 使用了【" + usable.getMove().getName() + "】！");
            events.add(BattleEvent.move(BattleEvent.Side.FOE, foe.getName(), usable.getMove()));
            lockChoiceMove(foe, usable.getMove());
            executeMove(foe, playerActive(), usable.getMove());
        }
    }

    /**
     * 敌方自动选择技能：讲究系装备锁定的招式仍有 PP 时优先使用，否则从仍有 PP 的技能中随机挑一个。
     */
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
        if (holds(foe, HeldItemEffect.CHOICE)) {
            String locked = choiceLocks.get(foe.getUuid());
            if (locked != null) {
                for (MoveSlot s : usable) {
                    if (s.getMove().getId().equals(locked)) {
                        return s;
                    }
                }
            }
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
     * 执行一次行动：变化类技能应用天气/场地效果并按概率施加异常状态，其余（物理/特殊）技能正常造成伤害。
     */
    private void executeMove(Pokemon attacker, Pokemon defender, Move move) {
        if (move.isStatus()) {
            // 纯变化招：天气/场地效果与异常状态互不排斥；两者都没有时提示无效果
            if (move.getEffect() != MoveEffect.NONE || !move.hasInfliction()) {
                applyFieldEffect(attacker, move.getEffect());
            }
            tryInflict(move, defender);
            events.add(BattleEvent.hit(sideOf(defender), nameOf(defender), move.getType(),
                    MoveCategory.STATUS));
            return;
        }
        performAttack(attacker, defender, move);
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
        events.add(BattleEvent.hit(sideOf(p), nameOf(p), ElementType.NORMAL, MoveCategory.PHYSICAL));
        double base = (2.0 * p.getLevel() / 5.0 + 2.0) * StatusCondition.CONFUSION_SELF_HIT_POWER
                * ((double) p.effectiveAttack() / Math.max(1, p.getStats().getDefense())) / 50.0 + 2.0;
        int dealt = p.takeDamage(Math.max(1, (int) base));
        append("自伤了 " + dealt + " 点伤害");
        if (p.isFainted()) {
            events.add(BattleEvent.faint(sideOf(p), nameOf(p)));
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
            cureStatusWithBerry(defender, condition);
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

    /** 应用变化类技能的天气/场地效果；无对应效果视为失败。 */
    private void applyFieldEffect(Pokemon attacker, MoveEffect effect) {
        if (effect == null || effect == MoveEffect.NONE) {
            append("但是什么也没有发生……");
            return;
        }
        Weather w = effect.toWeather();
        if (w != null) {
            setWeather(attacker, w);
            return;
        }
        Terrain t = effect.toTerrain();
        if (t != null) {
            setTerrain(t);
            return;
        }
        append("但是什么也没有发生……");
    }

    /**
     * 开启指定天气并重置持续回合数。开启者携带对应天气延长装备
     * （{@link HeldItemEffect#WEATHER_DURATION}，如炽热岩石）时，按装备参数延长回合数。
     */
    private void setWeather(Pokemon setter, Weather target) {
        boolean refreshing = weather == target;
        weather = target;
        weatherTurnsLeft = weatherDuration(setter, target);
        append(target.getStartMessage());
        if (refreshing) {
            append("持续回合重置了！");
        }
    }

    /**
     * 计算天气持续回合：默认 {@link Weather#DURATION_TURNS}；开启者携带的 WEATHER_DURATION 装备
     * 匹配当前天气时取其参数回合数（大于默认值才生效）。
     *
     * @return 本次天气的持续回合数
     */
    private static int weatherDuration(Pokemon setter, Weather target) {
        if (setter == null || !holds(setter, HeldItemEffect.WEATHER_DURATION)) {
            return Weather.DURATION_TURNS;
        }
        HeldItem rock = setter.getHeldItem();
        int turns = rock.durationTurns();
        if (parseWeather(rock.weatherParam()) != target || turns <= Weather.DURATION_TURNS) {
            return Weather.DURATION_TURNS;
        }
        return turns;
    }

    /** 解析装备参数中的天气英文名；无法解析时返回 {@code null}。 */
    private static Weather parseWeather(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Weather.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
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
        // 标靶：携带者失去属性免疫，原本 0 倍（无效）的招式改为 1 倍命中
        if (effectiveness <= 0 && holds(defender, HeldItemEffect.IGNORE_IMMUNITY)) {
            effectiveness = 1.0;
        }
        // 气球：携带者免疫地面系招式，被地面系招式命中后消耗
        if (effectiveness > 0 && move.getType() == ElementType.GROUND
                && holds(defender, HeldItemEffect.GROUND_IMMUNE)) {
            append(defender.getName() + " 借助气球浮在空中，地面系招式没有命中！");
            consumeHeldItem(defender);
            return;
        }
        if (effectiveness <= 0) {
            append("这招对 " + defender.getName() + " 没有效果……");
            return;
        }
        double resist = resistBerryMultiplier(defender, move, effectiveness);
        int damage = computeDamage(attacker, defender, move, effectiveness);
        // 属性减伤树果：受对应属性（默认要求效果拔群）招式时减伤并消耗，必须在保命判定之前生效
        String resistMessage = null;
        if (resist < 1.0) {
            HeldItem berry = defender.getHeldItem();
            damage = Math.max(1, (int) Math.floor(damage * resist));
            consumeHeldItem(defender);
            resistMessage = defender.getName() + " 的" + berry.getName() + " 减轻了招式伤害！";
        }
        String surviveMessage = survivedByHeldItem(defender, damage);
        int dealt = defender.takeDamage(surviveMessage == null ? damage : defender.getCurrentHp() - 1);
        events.add(BattleEvent.hit(sideOf(defender), nameOf(defender), move.getType(),
                move.getCategory()));
        applyLifeSteal(attacker, dealt);
        applyLifeOrbRecoil(attacker, dealt);
        StringBuilder sb = new StringBuilder();
        sb.append("造成 ").append(dealt).append(" 点伤害");
        if (effectiveness > 1.0) {
            sb.append("，效果拔群！");
        } else if (effectiveness < 1.0) {
            sb.append("，效果不太理想……");
        }
        append(sb.toString());
        if (resistMessage != null) {
            append(resistMessage);
        }
        if (surviveMessage != null) {
            append(surviveMessage);
        }
        if (defender.isFainted()) {
            events.add(BattleEvent.faint(sideOf(defender), nameOf(defender)));
            append(defender.getName() + " 倒下了！");
            return;
        }
        tryInflict(move, defender);
        // 受击后立即结算 HP 回复树果（未倒下才触发）
        healHpWithBerry(defender);
    }

    /**
     * 保命装备判定：招式伤害足以令携带者倒下时，气势披带（{@link HeldItemEffect#FOCUS_SASH}，
     * 满 HP 时必定保留 1 HP，触发后消耗）或气势头带（{@link HeldItemEffect#FOCUS_BAND}，按概率保留 1 HP）
     * 可让其以 1 HP 存活。
     *
     * <p>只有「被招式命中」的伤害会触发；天气 / 异常状态 / 反伤等回合末或自身伤害不触发。</p>
     *
     * @param incoming 本次招式将要扣除的 HP
     * @return 触发时的播报文本；未触发返回 {@code null}
     */
    private String survivedByHeldItem(Pokemon target, int incoming) {
        if (incoming < target.getCurrentHp() || target.getHeldItem() == null) {
            return null;
        }
        HeldItem item = target.getHeldItem();
        // 注意短路顺序：只有气势头带才会消耗随机数，避免影响其它装备的随机序列
        boolean sash = item.getEffectType() == HeldItemEffect.FOCUS_SASH
                && target.getCurrentHp() == target.getMaxHp();
        boolean band = item.getEffectType() == HeldItemEffect.FOCUS_BAND
                && random.nextInt(100) < item.chanceParam();
        if (!sash && !band) {
            return null;
        }
        if (sash) {
            consumeHeldItem(target);
        }
        return target.getName() + " 的" + item.getName() + " 让它撑住了！";
    }

    /** 消耗一次性装备（如已触发的气势披带、已生效的气球）。 */
    private static void consumeHeldItem(Pokemon p) {
        p.setHeldItem(null);
    }

    /**
     * 生命宝珠（{@link HeldItemEffect#LIFE_ORB}）反伤：命中造成伤害后按最大 HP 比例扣血。
     * 反伤不会由其自身的保命装备救回（保命装备只对招式命中的伤害生效）。
     */
    private void applyLifeOrbRecoil(Pokemon attacker, int dealt) {
        if (dealt <= 0 || attacker == null || attacker.isFainted()
                || !holds(attacker, HeldItemEffect.LIFE_ORB)) {
            return;
        }
        HeldItem orb = attacker.getHeldItem();
        int recoil = Math.max(1, (int) (attacker.getMaxHp() * orb.recoilRatio()));
        int lost = attacker.takeDamage(recoil);
        if (lost > 0) {
            append(attacker.getName() + " 因" + orb.getName() + " 失去了 " + lost + " HP！");
        }
        if (attacker.isFainted()) {
            events.add(BattleEvent.faint(sideOf(attacker), nameOf(attacker)));
            append(attacker.getName() + " 倒下了！");
        }
    }

    private int computeDamage(Pokemon attacker, Pokemon defender, Move move, double effectiveness) {
        double atk;
        double def;
        if (move.getCategory() == MoveCategory.PHYSICAL) {
            atk = attacker.effectiveAttack();
            def = defender.getStats().getDefense();
        } else {
            atk = attacker.getStats().getSpAttack();
            def = defender.getStats().getSpDefense();
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
        // 天气与场地对招式威力的加成
        double field = weather.moveTypeMultiplier(move.getType())
                * terrain.moveTypeMultiplier(move.getType());
        // 携带装备加成：属性强化道具（木炭等）、达人带、力量头带/博识眼镜、生命宝珠、讲究眼镜
        double equipment = heldItemDamageMultiplier(attacker, move, effectiveness);
        double randomFactor = 0.85 + random.nextDouble() * 0.15;
        int raw = (int) Math.floor(base * stab * effectiveness * field * equipment * randomFactor);
        return Math.max(1, raw);
    }

    /**
     * 攻击方携带装备的伤害倍率：DAMAGE_TYPE 属性匹配时生效；SUPER_EFFECTIVE 仅在克制时生效；
     * PHYSICAL_DAMAGE / SPECIAL_DAMAGE 按招式类别生效；LIFE_ORB 无条件生效；
     * CHOICE 仅在修正项为 {@code SPECIAL} 且招式属特殊类时生效。
     */
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
            case PHYSICAL_DAMAGE -> move.getCategory() == MoveCategory.PHYSICAL ? item.doubleParam() : 1.0;
            case SPECIAL_DAMAGE -> move.getCategory() == MoveCategory.SPECIAL ? item.doubleParam() : 1.0;
            case LIFE_ORB -> item.damageMultiplier();
            case CHOICE -> "SPECIAL".equals(item.choiceKind())
                    && move.getCategory() == MoveCategory.SPECIAL ? item.choiceMultiplier() : 1.0;
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

    /** 回合结束结算：胜负判定、敌方出战倒下后的自动替换/续战、玩家精灵倒下后的自动换宠。 */
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
                settleGrowth(foe);
                reportBattleWon();
                return;
            }
            // 训练师轮战：每只倒下的对手都即时结算一次经验（不等整场结束）
            settleGrowth(foe);
            // 出战精灵倒下后自动派出下一只健康的；没有了 → 玩家获胜
            Pokemon next = trainer.switchToNextHealthy();
            if (next == null) {
                status = Status.PLAYER_WIN;
                append("训练师 " + trainer.getName() + " 的所有精灵都倒下了！你赢了！");
                reportBattleWon();
                return;
            }
            append(trainer.getName() + " 派出了 " + next.getName() + "！");
            events.add(BattleEvent.sendOut(BattleEvent.Side.FOE, next.getName()));
        }

        if (playerDown) {
            append(pa.getName() + " 倒下了……");
            if (player.switchToNextHealthy() != null) {
                append("你派出了 " + playerActive().getName() + "！");
                events.add(BattleEvent.sendOut(BattleEvent.Side.PLAYER, playerActive().getName()));
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
        if (status != Status.ONGOING) {
            return;
        }
        Pokemon pa = playerActive();
        Pokemon foe = foeActive();
        boolean bothStanding = foe != null && !foe.isFainted() && pa != null && !pa.isFainted();
        if (bothStanding) {
            applyFieldEndEffects(pa);
        }
        resolveRoundEnd();
        if (status == Status.ONGOING) {
            tickFields();
        }
    }

    /** 回合末天气/场地效果：沙暴/冰雹对双方扣血，青草场地对双方回复；随后结算携带装备与异常状态。 */
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
        poisonSludgeEffect(pa);
        poisonSludgeEffect(foe);
        statusEndTurn(pa);
        statusEndTurn(foe);
        // 宝珠类装备在异常状态结算之后生效，因此本回合不会立即吃到新异常状态的扣血
        endTurnStatusOrb(pa);
        endTurnStatusOrb(foe);
        // HP 回复树果放在最后结算，使回合末的天气/异常扣血也能触发果实回复
        healHpWithBerry(pa);
        healHpWithBerry(foe);
    }

    /**
     * 黑色污泥（{@link HeldItemEffect#POISON_HEAL}）回合末结算：毒属性回复、非毒属性扣血。
     * 参数为 {@code 回血比例|扣血比例}。
     */
    private void poisonSludgeEffect(Pokemon p) {
        if (p == null || p.isFainted() || !holds(p, HeldItemEffect.POISON_HEAL)) {
            return;
        }
        HeldItem sludge = p.getHeldItem();
        if (p.hasType(ElementType.POISON)) {
            int healed = p.heal(Math.max(1, (int) (p.getMaxHp() * sludge.healRatio())));
            if (healed > 0) {
                append(p.getName() + " 的" + sludge.getName() + " 恢复了 " + healed + " HP！");
            }
            return;
        }
        int dealt = p.takeDamage(Math.max(1, (int) (p.getMaxHp() * sludge.damageRatio())));
        append(p.getName() + " 受到" + sludge.getName() + "的伤害，失去了 " + dealt + " HP！");
        if (p.isFainted()) {
            events.add(BattleEvent.faint(sideOf(p), nameOf(p)));
            append(p.getName() + " 倒下了！");
        }
    }

    /**
     * 宝珠类装备（{@link HeldItemEffect#END_TURN_STATUS}）回合末结算：尚无主要异常时陷入指定异常
     * （火焰宝珠 → 灼伤、剧毒宝珠 → 剧毒）。
     */
    private void endTurnStatusOrb(Pokemon p) {
        if (p == null || p.isFainted() || !holds(p, HeldItemEffect.END_TURN_STATUS)
                || p.getStatus() != StatusCondition.NONE) {
            return;
        }
        HeldItem orb = p.getHeldItem();
        StatusCondition condition = StatusCondition.parse(orb.statusParam());
        if (condition == null || condition == StatusCondition.NONE) {
            return;
        }
        if (p.tryApplyStatus(condition, 0)) {
            append(p.getName() + " 因" + orb.getName() + "陷入了" + condition.getDisplayName() + "状态！");
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

    /**
     * 属性减伤树果（{@link HeldItemEffect#RESIST_TYPE}）判定：招式属性与参数属性一致，且（默认要求）
     * 本次结算为「效果拔群」时返回承伤倍率；否则返回 {@code 1.0}。
     *
     * <p>param 第 3 段为 {@code ALWAYS} 时不看克制关系（一般属性无克制对象，故灯浆果用该标记）。
     * 本方法只判定不消耗，实际消耗由调用方在伤害打折后执行。</p>
     */
    private static double resistBerryMultiplier(Pokemon defender, Move move, double effectiveness) {
        if (defender == null || !holds(defender, HeldItemEffect.RESIST_TYPE)) {
            return 1.0;
        }
        HeldItem berry = defender.getHeldItem();
        ElementType berryType = ElementType.parse(berry.resistTypeParam());
        if (berryType == null || berryType != move.getType()) {
            return 1.0;
        }
        if (!berry.resistUnconditional() && effectiveness <= 1.0) {
            return 1.0;
        }
        return berry.resistMultiplier();
    }

    /**
     * 异常治疗树果（{@link HeldItemEffect#CURE_STATUS}）判定：携带者刚陷入参数所列异常时立即治愈并消耗
     * （樱子果治麻痹、桃桃果治中毒、木子果治全部）。
     *
     * <p>仅为「刚被施加」的异常触发：换人上场时已带异常、或战斗开始时已带异常都不会触发。</p>
     */
    private void cureStatusWithBerry(Pokemon pokemon, StatusCondition condition) {
        if (pokemon == null || condition == null || condition == StatusCondition.NONE
                || !holds(pokemon, HeldItemEffect.CURE_STATUS)) {
            return;
        }
        HeldItem berry = pokemon.getHeldItem();
        if (!berry.cureStatuses().contains(condition)) {
            return;
        }
        if (condition == StatusCondition.CONFUSION) {
            pokemon.clearConfusion();
        } else {
            pokemon.cureStatus();
        }
        consumeHeldItem(pokemon);
        append(pokemon.getName() + " 因" + berry.getName() + "治愈了" + condition.getDisplayName() + "！");
    }

    /**
     * HP 回复树果（{@link HeldItemEffect#HEAL_HP}）判定：当前 HP 不高于参数阈值比例时回复并消耗
     * （橙橙果回复 10 HP、文柚果回复 1/4、危果树果回复 1/8）。
     *
     * <p>已倒下、HP 已满（回复量为 0）时不触发，因此不会白白消耗。</p>
     */
    private void healHpWithBerry(Pokemon p) {
        if (p == null || p.isFainted() || !holds(p, HeldItemEffect.HEAL_HP)) {
            return;
        }
        HeldItem berry = p.getHeldItem();
        double threshold = berry.healThresholdRatio();
        double amount = berry.healAmount();
        if (threshold <= 0 || amount <= 0
                || p.getCurrentHp() > p.getMaxHp() * threshold) {
            return;
        }
        // 回复量小于 1 视为最大 HP 的比例，不小于 1 视为固定点数
        int restore = amount < 1.0 ? (int) (p.getMaxHp() * amount) : (int) amount;
        int healed = p.heal(Math.max(1, restore));
        if (healed <= 0) {
            return;
        }
        consumeHeldItem(p);
        append(p.getName() + " 的" + berry.getName() + " 恢复了 " + healed + " HP！");
    }

    /**
     * PP 回复树果（{@link HeldItemEffect#HEAL_PP}）判定：指定招式槽 PP 已归零时回复其 PP 并消耗（苹野果 10 PP）。
     *
     * @param p    携带者
     * @param slot 已耗尽 PP 的招式槽；为 {@code null} 或该槽仍有 PP 时，改为取携带者第一个已耗尽的招式槽
     * @return 实际完成回复的招式槽；未触发返回 {@code null}
     */
    private MoveSlot healPpWithBerry(Pokemon p, MoveSlot slot) {
        if (p == null || !holds(p, HeldItemEffect.HEAL_PP)) {
            return null;
        }
        MoveSlot target = slot;
        if (target == null || !target.exhausted()) {
            target = p.getMoveSlots().stream().filter(MoveSlot::exhausted).findFirst().orElse(null);
        }
        if (target == null || p.getHeldItem().ppRestoreAmount() <= 0) {
            return null;
        }
        HeldItem berry = p.getHeldItem();
        int before = target.getCurrentPp();
        target.restore(berry.ppRestoreAmount());
        int restored = target.getCurrentPp() - before;
        if (restored <= 0) {
            return null;
        }
        consumeHeldItem(p);
        append(p.getName() + " 的" + berry.getName() + " 让【" + target.getMove().getName()
                + "】回复了 " + restored + " PP！");
        return target;
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
                events.add(BattleEvent.faint(sideOf(p), nameOf(p)));
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
            events.add(BattleEvent.faint(sideOf(p), nameOf(p)));
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
     * 击倒一只对手后的<b>即时</b>成长结算：把「参战且未倒下的己方精灵」与「刚被击败的那只对手」
     * 交给外部成长模块判定经验增加、升级、学招与进化（见 {@link BattleGrowthPort}）。
     *
     * <p>每击倒一只对手调用一次，因此训练师轮战中倒下的每一只都会单独结算经验（打输也不会回收
     * 已经拿到的经验）。本引擎不自行计算经验、不判定升级 / 学招 / 进化：成长模块返回的日志文本行
     * 原样追加，返回的「技能栏已满」挂起学招项进入待抉择队列。未注入成长端口时本方法无副作用。</p>
     *
     * @param defeated 刚被击败的对手（野生精灵或训练师队伍中倒下的一只）
     */
    private void settleGrowth(Pokemon defeated) {
        if (defeated == null) {
            return;
        }
        BattleGrowthPort.Settlement settlement = growthPort.settle(survivors(), List.of(defeated));
        for (String line : settlement.log()) {
            append(line);
        }
        pendingLearns.addAll(settlement.pendingLearns());
    }

    /** 参战且未倒下的己方精灵（经验与图鉴申报的接收者）。 */
    private List<Pokemon> survivors() {
        List<Pokemon> survivors = new ArrayList<>();
        for (Pokemon p : player.getParty()) {
            if (!p.isFainted()) {
                survivors.add(p);
            }
        }
        return survivors;
    }

    /** 玩家获胜申报：图鉴「对战次数」按场次累计，故整场战斗只申报一次。 */
    private void reportBattleWon() {
        growthPort.onBattleWon(survivors());
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static double typeEffectiveness(ElementType attack, Pokemon defender) {
        double result = 1.0;
        boolean grounded = isGrounded(defender);
        for (ElementType t : defender.getSpecies().getTypes()) {
            double single = TypeChart.effectiveness(attack, t);
            // 地面化（黑色铁球）：地面系招式不再被飞行属性免疫
            if (single == 0 && grounded && attack == ElementType.GROUND) {
                single = 1.0;
            }
            result *= single;
        }
        return result;
    }

    private void requireOngoing() {
        if (status != Status.ONGOING) {
            throw new IllegalStateException("战斗已结束，状态: " + status);
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
