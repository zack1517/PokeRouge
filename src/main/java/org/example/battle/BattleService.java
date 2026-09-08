package org.example.battle;

import org.example.model.Bag;
import org.example.model.Item;
import org.example.model.Move;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Terrain;
import org.example.model.Weather;

import java.util.List;

/**
 * 回合制战斗服务接口。
 *
 * <p>抽象一场「玩家 × 野生精灵」对战的完整生命周期。调用方（界面/控制器）只依赖本接口，
 * 具体结算规则由实现类承担（当前唯一实现为 {@link BattleEngine}）。</p>
 *
 * <p><b>回合规则</b>：双方每回合各执行一次行动（技能 / 道具 / 逃跑 / 换宠）。双方都用技能时
 * 按速度快者先动（相同速度随机）。物理技能取 物攻 vs 物防，特殊技能取 特攻 vs 特防，伤害受
 * 克制倍率、STAB(本系加成) 与随机浮动影响。捕捉成功、逃跑成功或一方全灭即结束。</p>
 *
 * <p><b>天气与场地</b>：携带效果的变化类技能会开启对应天气/场地（见 {@link Weather}/
 * {@link Terrain}，通过 {@link #getWeather()}/{@link #getTerrain()} 查询）。生效期间按各自
 * 倍率调整招式威力；沙暴/冰雹每回合末对非免疫精灵扣血，青草场地每回合末回复场上精灵。</p>
 *
 * <p><b>使用方式</b>：通过 {@link BattleServices} 工厂获得实例；每回合在
 * {@link #isOngoing()} 为 {@code true} 时调用任意一个行动方法，行动结束后依据返回的日志行
 * 与 {@link #getStatus()} 驱动界面刷新。</p>
 *
 * <p><b>约定</b>：所有行动方法执行后返回<b>本回合新增</b>的日志行（调用前已产生的日志不含在内）；
 * 当战斗已结束时（非 {@link Status#ONGOING}）调用行动方法将抛出 {@link IllegalStateException}。
 * 返回的状态与方法内 {@link Player}/{@link Pokemon} 对象引用均不应被修改。</p>
 */
public interface BattleService {

    /** 战斗状态。 */
    enum Status {
        /** 进行中。 */
        ONGOING,
        /** 玩家获胜（野生精灵倒下）。 */
        PLAYER_WIN,
        /** 玩家战败（队伍全部倒下）。 */
        PLAYER_LOSE,
        /** 逃跑成功。 */
        FLED,
        /** 捕捉成功。 */
        CAUGHT
    }

    /**
     * 一次待玩家抉择的「学习新技能」：获胜发放经验升级后，若精灵已掌握 4 个技能，
     * 不再自动遗忘，而是挂起等待玩家选择遗忘哪一招或放弃学习（见
     * {@link #decideLearn(int)}）。
     *
     * @param pokemon 受益精灵
     * @param move    想学习的新技能
     */
    record LearnChoice(Pokemon pokemon, Move move) {
    }

    // ------------------------------------------------------------------
    // 回合行动入口
    // ------------------------------------------------------------------

    /**
     * 玩家选择技能攻击。野生精灵会自动选择可用技能；按速度决定先后手。
     *
     * @param slot 玩家当前出战精灵的某个技能槽；为 {@code null} 或 PP 耗尽时会自动退回
     *             第一个可用技能，无可技能则本回合不行动
     * @return 本回合产生的新日志（每行一条消息）
     * @throws IllegalStateException 战斗已结束（非 {@link Status#ONGOING}）时
     */
    List<String> useMove(MoveSlot slot);

    /**
     * 玩家使用道具：回复道具回复当前精灵 HP；精灵球尝试捕捉野生精灵。道具不计先后手
     * （视为先行动作），使用后若战斗未结束则野生精灵行动一次。
     *
     * @param item 要使用的道具；为 {@code null} 或背包中数量不足时本回合不行动
     * @return 本回合产生的新日志
     * @throws IllegalStateException 战斗已结束（非 {@link Status#ONGOING}）时
     */
    List<String> useItem(Item item);

    /**
     * 玩家尝试逃跑：速度越快成功率越高。失败则野生精灵行动一次。
     *
     * @return 本回合产生的新日志
     * @throws IllegalStateException 战斗已结束（非 {@link Status#ONGOING}）时
     */
    List<String> tryRun();

    /**
     * 玩家回合切换出战精灵：消耗本回合行动，收换完成后野生精灵行动一次。
     *
     * @param partyIndex 玩家队伍中目标精灵下标；目标为 {@code null}、下标非法或与当前
     *                   出战精灵相同则不行动
     * @return 本回合产生的新日志
     * @throws IllegalStateException 战斗已结束（非 {@link Status#ONGOING}）时
     */
    List<String> switchActive(int partyIndex);

    // ------------------------------------------------------------------
    // 状态查询
    // ------------------------------------------------------------------

    /** @return 本场战斗的玩家（含队伍与背包） */
    Player getPlayer();

    /** @return 本场战斗的野生精灵 */
    Pokemon getWild();

    /** @return 玩家当前出战精灵（引擎会在倒下后自动切换，可能为 {@code null}） */
    Pokemon playerActive();

    /** @return 当前战斗状态 */
    Status getStatus();

    /** @return 战斗是否仍在进行中（{@code status == Status.ONGOING}） */
    boolean isOngoing();

    /** @return 当前天气（无天气为 {@link Weather#NONE}），由携带天气效果的变化类技能开启 */
    Weather getWeather();

    /** @return 当前场地（无场地为 {@link Terrain#NONE}），由携带场地效果的变化类技能开启 */
    Terrain getTerrain();

    /** @return 完整战斗日志（只读） */
    List<String> getLog();

    /** @return 玩家背包（便捷转发，等价于 {@code getPlayer().getBag()}） */
    Bag getBag();

    // ------------------------------------------------------------------
    // 获胜结算：学招抉择
    // ------------------------------------------------------------------

    /**
     * 战斗胜利（{@link Status#PLAYER_WIN}）且发放经验升级后，尚未由玩家决定的
     * 「学习新技能」请求，按产生顺序排列。
     *
     * <p>精灵有空格时新技能已被直接学会，不会进入本队列；仅当 4 招全满时才挂起等待
     * 玩家决定。</p>
     *
     * @return 只读的待抉择列表；为空表示没有待处理的学招抉择
     */
    List<LearnChoice> pendingLearnChoices();

    /**
     * 处理队首一项待抉择学招（见 {@link #pendingLearnChoices()}）。
     *
     * @param forgetSlotIndex 要遗忘（替换）的技能槽下标，取值 0~3；传 -1 表示放弃学习
     * @return 本次抉择产生的新日志行
     * @throws IllegalStateException 当前没有待抉择的学招请求时
     */
    List<String> decideLearn(int forgetSlotIndex);
}
