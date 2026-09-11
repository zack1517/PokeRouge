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
 * <p>战斗模块结算出胜利后，把「参战且未倒下的己方精灵」与「被击败的对手」交给本模块
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
 * <p><b>经验规则</b>：按被击败精灵折算 —— 六维种族值总和 × 等级 / 5，单只不低于
 * {@value #MIN_EXP}；训练师轮战按整队被击败对手求和，一次性发放给每只参战精灵。</p>
 *
 * <p><b>图鉴进度</b>：本模块同时维护局外成长进度 {@link GrowthProgress} —— 战斗获胜时按
 * 参战精灵累计「对战次数」，野生遭遇被捕捉时（{@link #onCaptured(String)}）累计
 * 「捕捉次数」；捕捉次数经 {@link IvGrowthRule} 换算为个体值加成，由创建流程叠加到之后
 * 生成的所有宝可梦上（种族值不变）。局外可经 {@link #getProgress()} 直接查询。</p>
 */
public final class GrowthService implements BattleGrowthPort {

    /** 单只被击败精灵折算出的经验下限。 */
    private static final int MIN_EXP = 30;

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
        List<String> log = new ArrayList<>();
        List<BattleService.LearnChoice> pending = new ArrayList<>();
        recordBattles(survivors);
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

    /** 结算一只精灵的经验与成长：逐级追加升级日志，并处理到级学招与进化。 */
    private void grow(Pokemon p, int exp, List<String> log, List<BattleService.LearnChoice> pending) {
        int before = p.getLevel();
        int gainedLevels = p.addExp(exp);
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

    /** 申报参战：战斗获胜时按参战且未倒下的己方精灵逐只累计该族对战次数（图鉴展示用）。 */
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

    /** 整队经验之和：训练师轮战按全队被击败对手一次性结算。 */
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

    /** 依据被击败精灵的种族与等级折算经验：六维种族值总和 × 等级 / 5，不低于 {@value #MIN_EXP}。 */
    private static int expOf(Pokemon defeated) {
        Stats stats = defeated.getSpecies().getBaseStats();
        int total = stats.getHp() + stats.getAttack() + stats.getDefense()
                + stats.getSpAttack() + stats.getSpDefense() + stats.getSpeed();
        return Math.max(MIN_EXP, total * defeated.getLevel() / 5);
    }
}
