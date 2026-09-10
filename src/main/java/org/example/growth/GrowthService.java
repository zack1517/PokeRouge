package org.example.growth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.example.battle.BattleDataPort;
import org.example.battle.BattleGrowthPort;
import org.example.battle.BattleService;
import org.example.model.Move;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;

/**
 * 成长模块：唯一负责「经验增加、升级、学招、进化」判定的模块。
 *
 * <p>战斗模块每击倒一只对手就把「参战且未倒下的己方精灵」与「刚被击败的那只对手」交给本模块
 * （见 {@link BattleGrowthPort}），由本模块决定经验数值、逐级升级、到级学招与进化，
 * 并把日志文本行返回。战斗模块本身不含任何成长规则。</p>
 *
 * <p><b>到级学招的取舍规则</b>：升级学到的新技能全部保留进精灵的<b>技能库</b>（无上限，不丢招）；
 * 出战槽有空位时自动携带新招，已带满 4 招时新招只入技能库，玩家可稍后在宝可梦详情界面
 * 自由更换出战技能（{@link Pokemon#swapBattleMove}）。因此本模块不再产生「技能满、待抉择」的
 * 挂起项（{@code pendingLearns} 恒为空）。</p>
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

    /** 捕捉经验奖励倍率（分子 / 分母）：相当于击败该宝可梦的 1.8 倍经验。 */
    private static final int CAPTURE_EXP_NUMERATOR = 9;
    private static final int CAPTURE_EXP_DENOMINATOR = 5;

    /** 训练家对战与火箭队事件的击倒经验倍率（分子 / 分母）：1.5 倍。 */
    public static final int TRAINER_EXP_NUMERATOR = 3;
    public static final int TRAINER_EXP_DENOMINATOR = 2;

    /** 道馆战的击倒经验倍率（分子 / 分母）：2.0 倍。 */
    public static final int GYM_EXP_NUMERATOR = 2;
    public static final int GYM_EXP_DENOMINATOR = 1;

    /** 技能与种族数据的唯一来源。 */
    private final BattleDataPort dataPort;

    /** 局外成长进度（捕捉次数 / 对战次数 / 个体值加成），跨单轮远征存活。 */
    private final GrowthProgress progress;

    /**
     * 创建成长模块，使用进程级共享成长进度（{@link GrowthProgress#instance()}）。
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
        return settle(survivors, defeated, 1, 1);
    }

    @Override
    public Settlement settle(List<Pokemon> survivors, List<Pokemon> defeated,
                             int expNumerator, int expDenominator) {
        List<String> log = new ArrayList<>();
        List<BattleService.LearnChoice> pending = new ArrayList<>();
        int exp = totalExp(defeated) * expNumerator / expDenominator;
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
    public Settlement settleCapture(List<Pokemon> survivors, Pokemon caught) {
        List<String> log = new ArrayList<>();
        List<BattleService.LearnChoice> pending = new ArrayList<>();
        // 捕捉次数照常累计（图鉴 / 个体值加成）
        onCaptured(caught == null ? null : caught.getSpecies().getId());
        if (caught == null) {
            return new Settlement(log, pending);
        }
        // 捕捉成功奖励 1.5 倍击倒经验，与击倒结算同口径（升级 / 学招 / 进化即时判定）；
        // 被捕捉的精灵本身不参与发放（奖励只给参战的己方精灵）
        int exp = captureExpOf(caught);
        if (exp > 0 && survivors != null) {
            for (Pokemon p : survivors) {
                if (p == null || p.isFainted() || p == caught) {
                    continue;
                }
                grow(p, exp, log, pending);
            }
        }
        return new Settlement(log, pending);
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
            // 新招先入技能库（永久保留），再替换出战槽；被换下的招仍留在库中可再换回
            p.learnMoveToPool(move);
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

    /** 结算一只精灵的经验与成长：先入账经验并输出「获得经验」日志，再逐级追加升级日志并处理到级学招与进化。 */
    private void grow(Pokemon p, int exp, List<String> log, List<BattleService.LearnChoice> pending) {
        if (p.getLevel() >= Pokemon.MAX_LEVEL) {
            return; // 满级不再累积经验，也不输出日志
        }
        int before = p.getLevel();
        int gainedLevels = p.addExp(exp);
        // 经验在击倒对手的当次结算即到账：无论是否升级都输出日志，让玩家在战斗中立刻看到经验获得
        log.add(p.getName() + " 获得了 " + exp + " 点经验！");
        if (gainedLevels <= 0) {
            return;
        }
        for (int lv = before + 1; lv <= p.getLevel(); lv++) {
            log.add(p.getName() + " 升到了 Lv." + lv + "！");
            learnAt(p, lv, log, pending);
            evolve(p, log);
        }
    }

    /** 等级达到习得表要求时尝试学会新技能：全部保留进技能库；出战槽有空位则自动携带。 */
    private void learnAt(Pokemon p, int level, List<String> log,
                         List<BattleService.LearnChoice> pending) {
        String moveId = p.getSpecies().moveLearnedAt(level);
        if (moveId == null) {
            return;
        }
        Move move = dataPort.findMove(moveId);
        if (move == null || p.knowsMove(move.getId())) {
            return;
        }
        boolean full = p.moveSlotsFull();
        p.learnMoveToPool(move);
        if (full) {
            log.add(p.getName() + " 学会了【" + move.getName()
                    + "】，已收入技能库（可在宝可梦详情界面更换出战技能）。");
        } else {
            log.add(p.getName() + " 记住了【" + move.getName() + "】！");
        }
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

    /** 捕捉成功的经验奖励：相当于击败该宝可梦的 {@value #CAPTURE_EXP_NUMERATOR}/{@value #CAPTURE_EXP_DENOMINATOR} 倍经验。 */
    private static int captureExpOf(Pokemon caught) {
        return expOf(caught) * CAPTURE_EXP_NUMERATOR / CAPTURE_EXP_DENOMINATOR;
    }
}
