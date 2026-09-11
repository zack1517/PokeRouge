package org.example.battle;

import org.example.model.Bag;
import org.example.model.Item;
import org.example.model.Move;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Terrain;
import org.example.model.Trainer;
import org.example.model.Weather;

import java.util.List;

/**
 * 回合制战斗服务接口。
 *
 * <p>抽象一场「玩家 × 野生精灵」或「玩家 × 训练师」对战的完整生命周期。调用方（界面/控制器）
 * 只依赖本接口，具体结算规则由实现类承担（当前唯一实现为 {@link BattleEngine}）。</p>
 *
 * <p><b>野生遭遇</b>：敌方为单只野生精灵（{@link #getWild()} 非空、{@link #getTrainer()} 为
 * {@code null}）。规则：双方每回合各执行一次行动（技能 / 道具 / 逃跑 / 换宠）；野生精灵倒下、
 * 逃跑成功或捕捉成功即结束。</p>
 *
 * <p><b>训练师轮战</b>（{@link #getTrainer()} 非空、{@link #getWild()} 为 {@code null}）：
 * 敌方持有一整支队伍，双方都用技能时按速度快者先动（相同速度随机）。<b>不可逃跑、不可捕捉
 * 训练师的精灵</b>；敌方当前出战精灵倒下后自动派出下一只健康的（敌方不会主动换宠），直到某一方
 * 精灵<b>全部倒下</b>才结束战斗；天气/场地与技能 PP 跨整场持续；胜利时经验按整队被击败对手
 * 一次性结算。</p>
 *
 * <p><b>己方倒下后的补位</b>（野生遭遇与训练师轮战一致）：玩家出战精灵倒下且队伍仍有健康精灵时
 * <b>不会自动补位</b>，战斗进入「等待玩家选择替补」状态（{@link #isAwaitingReplacement()}）；
 * 此时所有行动方法都抛 {@link IllegalStateException}，须先调用 {@link #chooseReplacement(int)}
 * 选出一只接着上场（不消耗回合，敌方不会行动）。队伍已无健康精灵时判 {@link Status#PLAYER_LOSE}。</p>
 *
 * <p>战斗行为契约见 {@link BattleService}，实例统一由 {@link BattleServices} 工厂创建，
 * 调用方不应直接持有本实现类。</p>
 *
 * <p><b>天气与场地</b>：携带效果的变化类技能会开启对应天气/场地（见 {@link Weather}/
 * {@link Terrain}，通过 {@link #getWeather()}/{@link #getTerrain()} 查询）。生效期间按各自
 * 倍率调整招式威力；沙暴/冰雹每回合末对非免疫精灵扣血，青草场地每回合末回复场上精灵。</p>
 *
 * <p><b>使用方式</b>：通过 {@link BattleServices} 工厂获得实例；每回合在
 * {@link #isOngoing()} 为 {@code true} 且 {@link #isAwaitingReplacement()} 为 {@code false}
 * 时调用任意一个行动方法，行动结束后依据返回的日志行与 {@link #getStatus()} 驱动界面刷新；
 * 己方出战精灵倒下后先经 {@link #chooseReplacement(int)} 补位再继续。</p>
 *
 * <p><b>约定</b>：所有行动方法执行后返回<b>本回合新增</b>的日志行（调用前已产生的日志不含在内）；
 * 当战斗已结束（非 {@link Status#ONGOING}）或正等待玩家选择上场精灵时调用行动方法将抛出
 * {@link IllegalStateException}。
 * 返回的状态与方法内 {@link Player}/{@link Pokemon}/{@link Trainer} 对象引用均不应被修改。</p>
 */
public interface BattleService {

    /** 战斗状态。 */
    enum Status {
        /** 进行中。 */
        ONGOING,
        /** 玩家获胜（野生精灵倒下，或训练师整队全部倒下）。 */
        PLAYER_WIN,
        /** 玩家战败（队伍全部倒下）。 */
        PLAYER_LOSE,
        /** 逃跑成功（仅野生遭遇）。 */
        FLED,
        /** 捕捉成功（仅野生遭遇）。 */
        CAUGHT
    }

    /**
     * 一次待玩家抉择的「学习新技能」：战斗结算后由外部成长模块（{@link BattleGrowthPort}）
     * 判定成长时，若精灵已掌握 4 个技能，不再自动遗忘，而是挂起等待玩家选择遗忘哪一招或
     * 放弃学习（见 {@link #decideLearn(int)}）。
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
     * 玩家选择技能攻击。敌方（野生精灵或训练师当前出战精灵）自动选择技能；按速度决定先后手。
     *
     * @param slot 玩家当前出战精灵的某个技能槽；为 {@code null} 或 PP 耗尽时会自动退回
     *             第一个可用技能，无可技能则本回合不行动
     * @return 本回合产生的新日志（每行一条消息）
     * @throws IllegalStateException 战斗已结束（非 {@link Status#ONGOING}）或正等待选择上场精灵时
     */
    List<String> useMove(MoveSlot slot);

    /**
     * 玩家使用道具于**指定队伍精灵**：回复道具回复目标 HP、解除道具治愈目标异常状态；
     * 精灵球始终投向敌方野生精灵（与目标无关）。道具不计先后手（视为先行动作），
     * 使用后若战斗未结束则敌方行动一次。
     *
     * <p><b>目标约束</b>：{@code partyIndex} 为玩家队伍下标，越界时本回合不行动；
     * 回复道具对**已倒下**的精灵无效（无法以此复活，原版行为），解除道具对已倒下精灵仍有效。</p>
     *
     * <p><b>训练师轮战不可捕捉</b>：对训练师使用精灵球时球不消耗，仅追加一条提示日志。</p>
     *
     * @param item       要使用的道具；为 {@code null} 或背包中数量不足时本回合不行动
     * @param partyIndex 目标精灵在玩家队伍中的下标
     * @return 本回合产生的新日志
     * @throws IllegalStateException 战斗已结束（非 {@link Status#ONGOING}）或正等待选择上场精灵时
     */
    List<String> useItem(Item item, int partyIndex);

    /**
     * 玩家对**当前出战精灵**使用道具（等价于 {@code useItem(item, 出战精灵下标)}）。
     *
     * @param item 要使用的道具；为 {@code null} 或背包中数量不足时本回合不行动
     * @return 本回合产生的新日志
     * @throws IllegalStateException 战斗已结束（非 {@link Status#ONGOING}）或正等待选择上场精灵时
     */
    default List<String> useItem(Item item) {
        return useItem(item, getPlayer().getParty().indexOf(playerActive()));
    }

    /**
     * 玩家尝试逃跑：速度越快成功率越高。失败则野生精灵行动一次。
     *
     * <p><b>仅野生遭遇可用</b>：训练师轮战中调用不消耗回合，仅追加「无法逃跑」提示日志。</p>
     *
     * @return 本回合产生的新日志
     * @throws IllegalStateException 战斗已结束（非 {@link Status#ONGOING}）或正等待选择上场精灵时
     */
    List<String> tryRun();

    /**
     * 玩家回合切换出战精灵：消耗本回合行动，收换完成后敌方行动一次。
     *
     * <p>注意：己方出战精灵倒下后本方法会被拒绝（{@link #isAwaitingReplacement()} 为 {@code true}
     * 时抛 {@link IllegalStateException}），补位请改用 {@link #chooseReplacement(int)}。</p>
     *
     * @param partyIndex 玩家队伍中目标精灵下标；目标为 {@code null}、下标非法或与当前
     *                   出战精灵相同则不行动
     * @return 本回合产生的新日志
     * @throws IllegalStateException 战斗已结束（非 {@link Status#ONGOING}）或正等待选择上场精灵时
     */
    List<String> switchActive(int partyIndex);

    // ------------------------------------------------------------------
    // 状态查询
    // ------------------------------------------------------------------

    /** @return 本场战斗的玩家（含队伍与背包） */
    Player getPlayer();

    /**
     * @return 本场战斗的野生精灵；训练师轮战中为 {@code null}（敌方精灵经
     * {@link #foeActive()} 与 {@link #getTrainer()} 访问）
     */
    Pokemon getWild();

    /** @return 本场战斗的训练师；野生遭遇中为 {@code null} */
    Trainer getTrainer();

    /** @return 当前敌方出战精灵：野生遭遇为野生精灵，训练师轮战为训练师当前出战精灵 */
    Pokemon foeActive();

    /** @return 玩家当前出战精灵（可能为已倒下精灵：倒下后须经 {@link #chooseReplacement(int)} 补位） */
    Pokemon playerActive();

    /**
     * 是否需要玩家为倒下的出战精灵选择替补。
     *
     * <p>己方出战精灵倒下而队伍仍有健康精灵时为 {@code true}：此时战斗仍未结束
     * （{@link #isOngoing()} 仍为 {@code true}），但所有行动方法都会抛
     * {@link IllegalStateException}，界面应改为展示队伍选择面板并调用
     * {@link #chooseReplacement(int)}。选完即恢复为 {@code false}。</p>
     *
     * @return 是否正等待玩家选择上场精灵
     */
    boolean isAwaitingReplacement();

    /**
     * 己方出战精灵倒下后，由玩家选择下一只上场精灵。
     *
     * <p><b>不消耗回合</b>：补位只是把新精灵派上场，敌方不会因此行动（区别于
     * {@link #switchActive(int)}）。上场精灵从中立状态开始，并产生一条「放出」演出事件。</p>
     *
     * @param partyIndex 玩家队伍中目标精灵下标；目标为已倒下精灵或下标非法时只追加提示日志、
     *                   保持等待状态不变
     * @return 本次补位产生的新日志行
     * @throws IllegalStateException 当前并不需要选择上场精灵时（{@link #isAwaitingReplacement()}
     *                               为 {@code false}）
     */
    List<String> chooseReplacement(int partyIndex);

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

    /**
     * <b>取走</b>自上一条行动方法调用以来累积的演出事件（战斗动画数据源），并清空内部队列。
     *
     * <p>与 {@link #getLog()} 互补：日志是给人读的文本行，事件是给界面驱动的结构数据（谁对谁、
     * 用什么属性/分类的招式、派出还是收回、投球是否成功，见 {@link BattleEvent}）。同一事件只
     * 投递一次，取走后不再返回；事件不参与任何战斗结算，忽略它们的调用方行为与不实现本特性时
     * 完全一致。</p>
     *
     * <p>典型用法：调用任一行动方法 → 取出事件播放动画 → 动画结束后再刷新界面（避免结算结果
     * 先于演出出现）。</p>
     *
     * @return 自上次调用以来新产生的演出事件，按产生顺序排列；无新事件时返回空列表
     */
    List<BattleEvent> drainEvents();

    /** @return 玩家背包（便捷转发，等价于 {@code getPlayer().getBag()}） */
    Bag getBag();

    // ------------------------------------------------------------------
    // 获胜结算：学招抉择
    // ------------------------------------------------------------------

    /**
     * 战斗胜利（{@link Status#PLAYER_WIN}）结算后，外部成长模块（{@link BattleGrowthPort}）
     * 返回的、尚未由玩家决定的「学习新技能」请求，按产生顺序排列。
     *
     * <p>精灵有空格时新技能已被直接学会，不会进入本队列；仅当 4 招全满时才挂起等待
     * 玩家决定。战斗模块只转发本队列，学习判定本身由成长模块负责。</p>
     *
     * @return 只读的待抉择列表；为空表示没有待处理的学招抉择
     */
    List<LearnChoice> pendingLearnChoices();

    /**
     * 处理队首一项待抉择学招（见 {@link #pendingLearnChoices()}），转发给外部成长模块执行。
     *
     * @param forgetSlotIndex 要遗忘（替换）的技能槽下标，取值 0~3；传 -1 表示放弃学习
     * @return 本次抉择产生的新日志行
     * @throws IllegalStateException 当前没有待抉择的学招请求时
     */
    List<String> decideLearn(int forgetSlotIndex);
}
