package org.example.battle;

import org.example.model.Pokemon;

import java.util.List;

/**
 * 战斗模块的<b>成长申报端口</b>。
 *
 * <p>战斗模块只负责「战斗演算」与「胜负结算」，<b>不负责经验增加、升级、学招与进化</b>。
 * 结算出胜利后，战斗模块把「参战且未倒下的己方精灵」与「被击败的对手」交给本端口，
 * 由外部成长模块自行判定经验数值、逐级升级、到级学招与进化，并把需要写入战斗日志的
 * 文本行返回。引擎内部不含经验曲线、习得表或进化规则。</p>
 *
 * <p>发生「技能栏已满、无法自动学招」时，成长模块把该项作为待抉择请求返回，战斗模块
 * 只做转发（见 {@link BattleService#pendingLearnChoices()}）；玩家做出选择后再经
 * {@link #resolveLearn} 交回成长模块执行。</p>
 *
 * <p>未注入时使用 {@link #none()}：战斗规则照常运行，仅「经验 / 升级 / 学招 / 进化」
 * 降级为无操作（战斗日志中不会出现成长相关文本）。</p>
 */
public interface BattleGrowthPort {

    /**
     * 一场战斗的成长申报结果。
     *
     * @param log           需要追加到战斗日志的文本行（升级 / 学招 / 进化等），无变化时为空列表
     * @param pendingLearns 技能栏已满、需玩家抉择遗忘的挂起学招项，无挂起时为空列表
     */
    record Settlement(List<String> log, List<BattleService.LearnChoice> pendingLearns) {
    }

    /**
     * 战斗胜利结算申报：由成长模块自行判定经验数值并结算升级、学招与进化。
     *
     * @param survivors 参战且未倒下的己方精灵（引擎已过滤，可能为空列表）
     * @param defeated  被击败的对手（野生战斗为单只野生精灵；训练师轮战为整支被击败的队伍）
     * @return 成长申报结果，不可为 {@code null}
     */
    Settlement settle(List<Pokemon> survivors, List<Pokemon> defeated);

    /**
     * 处理一项挂起的学招抉择：替换指定技能槽，或传 {@code -1} 表示放弃学习。
     *
     * @param choice          待抉择项（来自 {@link #settle} 的 {@code pendingLearns}）
     * @param forgetSlotIndex 要遗忘并替换的槽位下标（0~3）；{@code -1} 表示放弃学习
     * @return 需要追加到战斗日志的文本行
     */
    List<String> resolveLearn(BattleService.LearnChoice choice, int forgetSlotIndex);

    /** 空端口：不判定任何成长（经验 / 升级 / 学招 / 进化均降级为无操作）。 */
    static BattleGrowthPort none() {
        return new BattleGrowthPort() {

            @Override
            public Settlement settle(List<Pokemon> survivors, List<Pokemon> defeated) {
                return new Settlement(List.of(), List.of());
            }

            @Override
            public List<String> resolveLearn(BattleService.LearnChoice choice, int forgetSlotIndex) {
                return List.of();
            }
        };
    }
}
