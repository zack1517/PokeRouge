package org.example.growth;

import org.example.battle.BattleDataPort;
import org.example.battle.BattleGrowthPort;
import org.example.battle.BattleService;
import org.example.model.Move;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 成长模块：唯一负责「经验增加、升级、学招、进化」判定的模块。
 *
 * <p>战斗模块每击倒一只对手就把「参战且未倒下的己方精灵」与「刚被击败的那只对手」交给本模块
 * （见 {@link BattleGrowthPort}），由本模块决定经验数值、逐级升级、到级学招与进化，
 * 并把日志文本行与「技能栏已满」的挂起学招项返回。战斗模块本身不含任何成长规则。</p>
 *
 * <p><b>数据来源</b>：本模块不内建数据，技能与种族一律经 {@link BattleDataPort} 查询，
 * 与战斗模块共用同一份外部数据。未注入数据端口时，到级学招与进化降级为无操作，
 * 经验与升级照常结算。</p>
 *
 * <p><b>经验规则</b>：按被击败精灵折算 —— <b>种族经验值 baseExp × 对方等级 / 7</b>，单只不低于
 * {@value #MIN_EXP}，与该只对手的等级和种族挂钩；每击倒一只对手结算一次（训练师轮战中倒下的
 * 每一只都会单独结算），结果发给当次参战且未倒下的己方精灵。种族未提供 baseExp 时退化为
 * 「六维种族值总和 × 等级 / 5」，保证自定义数据仍能结算。</p>
 *
 * <p><b>可见反馈</b>：每次结算都会为每只获得经验的精灵追加一行
 * 「{@code 名字 获得了 N 点经验！}」日志（未升级时附带 {@code （当前/升级所需）} 进度），
 * 升级时再逐行追加「{@code 名字 升到了 Lv.N！}」。因此即便一次击倒不足以升级，
 * 玩家也能从战斗日志中确认经验已经入账。</p>
 *
 * <p><b>图鉴进度</b>：本模块同时维护局外成长进度 {@link GrowthProgress} —— 玩家获胜时按
 * 参战精灵累计「对战次数」（{@link #onBattleWon(List)}，每场一次），野生遭遇被捕捉时
 * （{@link #onCaptured(String)}）累计「捕捉次数」；捕捉次数经 {@link IvGrowthRule} 换算为
 * 个体值加成，由创建流程叠加到之后生成的所有宝可梦上（种族值不变）。局外可经
 * {@link #getProgress()} 直接查询。</p>
 */
public final class GrowthService implements BattleGrowthPort {

    /** 单只被击败精灵折算出的经验下限。 */
    private static final int MIN_EXP = 30;

    /** 种族经验值折算系数：经验 = baseExp × 对方等级 ÷ {@value}（对齐原作公式）。 */
    private static final int BASE_EXP_DIVISOR = 7;

    /** 技能与种族数据的唯一来源。 */
    private final BattleDataPort dataPort;

    /** 局外成长进度（捕捉次数 / 对战次数 / 个体值加成），跨单轮远征存活。 */
    private final GrowthProgress progress;

    /**
     * 创建成长模块，使用进程级共享成长进度（{@link GrowthProgress#instance()}）。
     *
     * <p><b>注意</b>：该共享实例绑定的是<b>用户主目录</b>下的真实存档
     * （{@code <用户目录>/.pokerouge/growth-progress.txt}），一旦发生
     * {@link #settle} / {@link #onBattleWon} / 捕捉申报等写入就会<b>直接落盘到玩家存档</b>。
     * 因此<b>测试与临时实例必须改用 {@link #GrowthService(BattleDataPort, GrowthProgress)}
     * 注入一份纯内存的 {@code new GrowthProgress()}</b>，否则会污染开发机上的真实进度
     * （症状：主目录存档里出现测试用的假物种 id）。</p>
     *
     * @param dataPort 只读数据端口（技能 / 种族查询），不可为 {@code null}
     */
    public GrowthService(BattleDataPort dataPort) {
        this(dataPort, GrowthProgress.instance());
    }

    /**
     * 创建成长模块，并注入指定成长进度（便于隔离测试）。
     *
     * @param dataPort 只读数据端口（技能 / 种族查询），不可为 {@code null}
     * @param progress 局外成长进度，不可为 {@code null}
     */
    public GrowthService(BattleDataPort dataPort, GrowthProgress progress) {
        this.dataPort = Objects.requireNonNull(dataPort, "dataPort");
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    /** 本模块持有的局外成长进度（图鉴数据接口，可局外查询）。 */
    public GrowthProgress getProgress() {
        return progress;
    }

    @Override
    public Settlement settle(List<Pokemon> survivors, List<Pokemon> defeated) {
        List<String> log = new ArrayList<>();
        List<BattleService.LearnChoice> pending = new ArrayList<>();
        int exp = totalExp(defeated);
        if (exp > 0 && survivors != null) {
            for (Pokemon p : survivors) {
                if (p == null || p.isFainted()) {
                    continue;
                }
                grow(p, exp, log, pending);
            }
        }
        return new Settlement(log, pending);
    }

    @Override
    public void onBattleWon(List<Pokemon> survivors) {
        recordBattles(survivors);
    }

    @Override
    public void onCaptured(String speciesId) {
        if (speciesId == null || speciesId.isBlank()) {
            return;
        }
        progress.recordCapture(speciesId);
    }

    @Override
    public List<String> resolveLearn(BattleService.LearnChoice choice, int forgetSlotIndex) {
        List<String> log = new ArrayList<>();
        if (choice == null) {
            return log;
        }
        Pokemon p = choice.pokemon();
        Move move = choice.move();
        if (p == null || move == null) {
            return log;
        }
        if (forgetSlotIndex >= 0 && forgetSlotIndex < p.getMoveSlots().size()) {
            Move forgotten = p.replaceMove(forgetSlotIndex, move);
            if (forgotten == null) {
                log.add(p.getName() + " 想学习【" + move.getName() + "】，但没有可遗忘的槽位。");
            } else {
                log.add(p.getName() + " 忘记了【" + forgotten.getName()
                        + "】，学会了【" + move.getName() + "】！");
            }
        } else {
            log.add(p.getName() + " 没有学习【" + move.getName() + "】。");
        }
        return log;
    }

    // ------------------------------------------------------------------
    // 成长结算
    // ------------------------------------------------------------------

    /**
     * 结算一只精灵的经验与成长：<b>先追加一行「获得了 N 点经验」的可见反馈</b>
     * （未升级时附带「当前经验 / 升级所需」进度，便于玩家确认经验确实入账），
     * 再逐级追加升级日志，并处理到级学招与进化。
     */
    private void grow(Pokemon p, int exp, List<String> log, List<BattleService.LearnChoice> pending) {
        if (p.getLevel() >= Pokemon.MAX_LEVEL) {
            return;
        }
        int before = p.getLevel();
        int gainedLevels = p.addExp(exp);
        if (gainedLevels <= 0) {
            log.add(p.getName() + " 获得了 " + exp + " 点经验！（"
                    + p.getExp() + "/" + p.expToNextLevel() + "）");
            return;
        }
        log.add(p.getName() + " 获得了 " + exp + " 点经验！");
        for (int lv = before + 1; lv <= p.getLevel(); lv++) {
            log.add(p.getName() + " 升到了 Lv." + lv + "！");
            learnAt(p, lv, log, pending);
            evolve(p, log);
        }
    }

    /** 等级达到习得表要求时尝试学会新技能：有空槽直接学会；4 招全满则挂起等待玩家抉择。 */
    private void learnAt(Pokemon p, int level, List<String> log,
                         List<BattleService.LearnChoice> pending) {
        String moveId = p.getSpecies().moveLearnedAt(level);
        if (moveId == null) {
            return;
        }
        Move move = dataPort.findMove(moveId);
        if (move == null || p.hasMove(move)) {
            return;
        }
        if (p.moveSlotsFull()) {
            pending.add(new BattleService.LearnChoice(p, move));
            return;
        }
        p.learnMove(move);
        log.add(p.getName() + " 记住了【" + move.getName() + "】！");
    }

    /** 达到进化等级时进化为目标形态（种族由数据端口提供；重新演算属性并回满状态）。 */
    private void evolve(Pokemon p, List<String> log) {
        if (!p.canEvolve()) {
            return;
        }
        Species target = dataPort.findSpecies(p.getSpecies().getEvolvesToId());
        if (target == null) {
            return;
        }
        log.add(p.getName() + " 进化成了 " + target.getName() + "！");
        p.evolveTo(target);
    }

    /** 申报参战：玩家获胜时按参战且未倒下的己方精灵逐只累计该族对战次数（图鉴展示用，每场一次）。 */
    private void recordBattles(List<Pokemon> survivors) {
        if (survivors == null) {
            return;
        }
        for (Pokemon p : survivors) {
            if (p == null || p.getSpecies() == null) {
                continue;
            }
            progress.recordBattle(p.getSpecies().getId());
        }
    }

    /** 整队经验之和：战斗模块按「每击倒一只对手」逐次申报时列表通常只有一只。 */
    private static int totalExp(List<Pokemon> defeated) {
        if (defeated == null) {
            return 0;
        }
        int sum = 0;
        for (Pokemon p : defeated) {
            if (p != null) {
                sum += expOf(p);
            }
        }
        return sum;
    }

    /**
     * 依据被击败精灵折算经验：<b>种族经验值 baseExp × 等级 ÷ 7</b>，不低于 {@value #MIN_EXP}。
     *
     * <p>经验多少同时取决于「对手等级」与「对手种族」：等级越高、种族经验值越大则获得越多。
     * 种族未提供 baseExp（{@code 0}）时退化为「六维种族值总和 × 等级 / 5」，与旧口径保持一致。</p>
     */
    private static int expOf(Pokemon defeated) {
        Species species = defeated.getSpecies();
        int baseExp = species.getBaseExpYield();
        if (baseExp > 0) {
            return Math.max(MIN_EXP, baseExp * defeated.getLevel() / BASE_EXP_DIVISOR);
        }
        Stats stats = species.getBaseStats();
        int total = stats.getHp() + stats.getAttack() + stats.getDefense()
                + stats.getSpAttack() + stats.getSpDefense() + stats.getSpeed();
        return Math.max(MIN_EXP, total * defeated.getLevel() / 5);
    }
}
