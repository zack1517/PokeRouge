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
 * 并把日志文本行与「技能栏已满」的挂起学招项返回。战斗模块本身不含任何成长规则。</p>
 *
 * <p><b>数据来源</b>：本模块不内建数据，技能与种族一律经 {@link BattleDataPort} 查询，
 * 与战斗模块共用同一份外部数据。未注入数据端口时，到级学招与进化降级为无操作，
 * 经验与升级照常结算。</p>
 *
 * <p><b>经验规则</b>：按被击败精灵折算 —— 六维种族值总和 × 等级 / 5，单只不低于
 * {@value #MIN_EXP}；训练师轮战按整队被击败对手求和，一次性发放给每只参战精灵。</p>
 */
public final class GrowthService implements BattleGrowthPort {

    /** 单只被击败精灵折算出的经验下限。 */
    private static final int MIN_EXP = 30;

    /** 技能与种族数据的唯一来源。 */
    private final BattleDataPort dataPort;

    /**
     * 创建成长模块。
     *
     * @param dataPort 只读数据端口（技能 / 种族查询），不可为 {@code null}
     */
    public GrowthService(BattleDataPort dataPort) {
        this.dataPort = Objects.requireNonNull(dataPort, "dataPort");
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
