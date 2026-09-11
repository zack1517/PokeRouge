package org.example.battle;

import org.example.model.Pokemon;

import java.util.List;

/**
 * 战斗模块的<b>成长申报端口</b>。
 *
 * <p>战斗模块只负责「战斗演算」与「胜负结算」，<b>不负责经验增加、升级、学招与进化</b>。
 * 每击倒一只对手，战斗模块就把「参战且未倒下的己方精灵」与「刚被击败的那只对手」交给本端口，
 * 由外部成长模块自行判定经验数值、逐级升级、到级学招与进化，并把需要写入战斗日志的
 * 文本行返回。引擎内部不含经验曲线、习得表或进化规则。</p>
 *
 * <p>「参战且未倒下的己方精灵」在整场战斗结束时另经 {@link #onBattleWon} 申报一次，
 * 供成长模块累计图鉴进度（对战次数按场次而非按击倒数计）。</p>
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
     * 击倒一只对手的经验申报：<b>每击倒一只对手调用一次</b>（训练师轮战中倒下的每一只都会
     * 单独申报），由成长模块自行判定经验数值并结算升级、学招与进化。
     *
     * <p>即时结算意味着「打输也不回收已经拿到的经验」；本方法在战斗落败时同样会被调用过。</p>
     *
     * @param survivors 参战且未倒下的己方精灵（引擎已过滤，可能为空列表）
     * @param defeated  刚被击败的对手（野生战斗为单只野生精灵；训练师轮战为刚倒下的那一只）
     * @return 成长申报结果，不可为 {@code null}
     */
    Settlement settle(List<Pokemon> survivors, List<Pokemon> defeated);

    /**
     * 玩家获胜申报（整场战斗只调用一次，用于在图鉴中累计「对战次数」等按场次计的进度）。
     *
     * <p>默认实现为空操作，因此 {@link #none()} 与既有实现无需改动即保持原有行为。</p>
     *
     * @param survivors 参战且未倒下的己方精灵（引擎已过滤，可能为空列表）
     */
    default void onBattleWon(List<Pokemon> survivors) {
        // 默认不记录
    }

    /**
     * 处理一项挂起的学招抉择：替换指定技能槽，或传 {@code -1} 表示放弃学习。
     *
     * @param choice          待抉择项（来自 {@link #settle} 的 {@code pendingLearns}）
     * @param forgetSlotIndex 要遗忘并替换的槽位下标（0~3）；{@code -1} 表示放弃学习
     * @return 需要追加到战斗日志的文本行
     */
    List<String> resolveLearn(BattleService.LearnChoice choice, int forgetSlotIndex);

    /**
     * 野生遭遇被<b>成功捕捉</b>时的申报（用于累计「图鉴捕捉次数」等局外成长进度）。
     *
     * <p>战斗模块只负责判定「是否捕捉成功」并申报物种 id，不承担任何成长演算
     * （捕捉次数如何换算成个体值加成由成长模块决定，见 {@code org.example.growth}）。
     * 默认实现为空操作，因此 {@link #none()} 与既有实现无需改动即保持原有行为。</p>
     *
     * @param speciesId 被捕捉野生精灵的物种 id
     */
    default void onCaptured(String speciesId) {
        // 默认不记录
    }

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
