package org.example.battle;

import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.Pokemon;

/**
 * 战斗<b>演出事件</b>：一次具备动画价值的战斗动作，供界面层（{@code BattleView}）驱动战斗动画。
 *
 * <p>与 {@link BattleService#getLog()} 的文本日志互补：日志只描述「发生了什么」（字符串行，无法
 * 结构化解读），事件则携带「谁、对谁、用什么属性/分类的招式」等结构信息，界面据此播放对应的
 * 动画（进场 / 放出 / 收回 / 技能释放 / 受击 / 倒下 / 投球 / 道具）。</p>
 *
 * <p><b>作用域</b>：事件是纯演出数据，<b>不参与任何战斗结算</b>；忽略全部事件的调用方（如控制台
 * 演示 {@code demo/BattleConsole}）行为与本特性引入前完全一致。事件由
 * {@link BattleService#drainEvents()} 一次性取走并清空，不重复投递。</p>
 *
 * @param kind     事件类型
 * @param side     事件主角所在阵营（{@link Kind#BATTLE_START} 为 {@code null}，表示双方）
 * @param actor    主角名（精灵名；{@link Kind#CAPTURE} 为球名、{@link Kind#ITEM} 为道具名）
 * @param moveName 招式名；非 {@link Kind#MOVE} 事件为空串
 * @param element  招式属性（决定动画配色）；无属性数据时为 {@code null}
 * @param category 招式分类（物理 / 特殊 / 变化，决定动画形态）；无分类数据时为 {@code null}
 * @param success  该事件是否成功（投球是否捕捉成功、逃跑是否成功）；其余事件恒为 {@code true}
 * @param hp       受影响一方的 HP 快照，供界面在播放该步动画<b>之前</b>刷新血条；
 *                 无 HP 变化的事件携带 {@link Hp#NONE}
 */
public record BattleEvent(Kind kind, Side side, String actor, String moveName,
                          ElementType element, MoveCategory category, boolean success, Hp hp) {

    /** 事件类型：界面按类型分派动画。 */
    public enum Kind {
        /** 战斗开场（进场动画）：双方立绘出场，由战斗引擎构造时产生。 */
        BATTLE_START,
        /** 派出精灵（放出动画）：玩家换宠、倒下后自动换宠、训练师派出下一只。 */
        SEND_OUT,
        /** 收回精灵（收回动画）：玩家主动换宠时先收回当前精灵。 */
        RECALL,
        /** 技能释放（施法动画）：携带属性与分类，界面据此选择弹道形态。 */
        MOVE,
        /** 受击（命中动画）：携带被打一方的阵营与招式属性；招式无效时不产生本事件。 */
        HIT,
        /** 倒下（倒地动画）。 */
        FAINT,
        /** 投球（捕捉动画）：{@code success} 表示是否捕捉成功。 */
        CAPTURE,
        /** 使用回复/解除类道具（回复动画）。 */
        ITEM,
        /** 逃跑：{@code success} 表示是否成功。 */
        RUN
    }

    /** 事件主角阵营。 */
    public enum Side {
        /** 玩家一方。 */
        PLAYER,
        /** 对手一方（野生精灵或训练师当前出战精灵）。 */
        FOE
    }

    /**
     * HP 快照：某一步演出发生时主角一方<b>变化后</b>的当前 / 最大 HP。
     *
     * <p>界面据此在播放该步动画<b>之前</b>先把血条刷成变化后的数值，从而做到「招式命中先扣血、
     * 再播受击动画」；没有 HP 变化的事件携带 {@link #NONE}，界面跳过刷新。</p>
     *
     * @param current 变化后的当前 HP；{@link #NONE} 为 {@code -1}
     * @param max     最大 HP；{@link #NONE} 为 {@code -1}
     */
    public record Hp(int current, int max) {

        /** 无 HP 数据（事件不涉及 HP 变化）。 */
        public static final Hp NONE = new Hp(-1, -1);

        /** @return 是否携带可用的 HP 数据 */
        public boolean present() {
            return current >= 0 && max > 0;
        }
    }

    /** 无招式的攻击（PP 耗尽后的挣扎）使用的招式名。 */
    public static final String STRUGGLE_NAME = "挣扎";

    /** 战斗开场事件（主角为双方，故 {@code side} 为 {@code null}）。 */
    public static BattleEvent battleStart() {
        return new BattleEvent(Kind.BATTLE_START, null, "", "", null, null, true, Hp.NONE);
    }

    /** 派出精灵事件。 */
    public static BattleEvent sendOut(Side side, String name) {
        return sendOut(side, name, Hp.NONE);
    }

    /** 派出精灵事件（带 HP 快照，界面据此在放出动画前把血条切成新上场精灵的数值）。 */
    public static BattleEvent sendOut(Side side, String name, Hp hp) {
        return new BattleEvent(Kind.SEND_OUT, side, name, "", null, null, true, safe(hp));
    }

    /** 收回精灵事件。 */
    public static BattleEvent recall(Side side, String name) {
        return new BattleEvent(Kind.RECALL, side, name, "", null, null, true, Hp.NONE);
    }

    /**
     * 技能释放事件。
     *
     * @param move 使用的招式；为 {@code null} 表示无招式的挣扎（普通属性物理）
     */
    public static BattleEvent move(Side side, String actor, Move move) {
        if (move == null) {
            return new BattleEvent(Kind.MOVE, side, actor, STRUGGLE_NAME,
                    ElementType.NORMAL, MoveCategory.PHYSICAL, true, Hp.NONE);
        }
        return new BattleEvent(Kind.MOVE, side, actor, move.getName(), move.getType(),
                move.getCategory(), true, Hp.NONE);
    }

    /** 受击事件（被打一方的阵营与招式属性）。 */
    public static BattleEvent hit(Side side, String name, ElementType element, MoveCategory category) {
        return hit(side, name, element, category, Hp.NONE);
    }

    /**
     * 受击事件（带 HP 快照）。
     *
     * @param hp 被打一方受击<b>之后</b>的 HP；界面在抖动的同时先把血条刷成本数值
     */
    public static BattleEvent hit(Side side, String name, ElementType element, MoveCategory category,
                                  Hp hp) {
        return new BattleEvent(Kind.HIT, side, name, "", element, category, true, safe(hp));
    }

    /** 倒下事件。 */
    public static BattleEvent faint(Side side, String name) {
        return faint(side, name, Hp.NONE);
    }

    /** 倒下事件（带 HP 快照，通常为 0，界面据此先把血条清空再播倒地动画）。 */
    public static BattleEvent faint(Side side, String name, Hp hp) {
        return new BattleEvent(Kind.FAINT, side, name, "", null, null, false, safe(hp));
    }

    /** 投球事件（敌方为野生精灵，故阵营恒为 {@link Side#FOE}）。 */
    public static BattleEvent capture(String ballName, boolean success) {
        return new BattleEvent(Kind.CAPTURE, Side.FOE, ballName, "", null, null, success, Hp.NONE);
    }

    /** 使用回复/解除类道具事件。 */
    public static BattleEvent item(String itemName) {
        return item(itemName, Hp.NONE);
    }

    /**
     * 使用回复/解除类道具事件（带 HP 快照）。
     *
     * @param hp <b>场上</b>精灵使用道具后的 HP；道具可作用于后备精灵，此时场上精灵的 HP 并未变化，
     *           界面据此刷新得到仍是正确数值
     */
    public static BattleEvent item(String itemName, Hp hp) {
        return new BattleEvent(Kind.ITEM, Side.PLAYER, itemName, "", null, null, true, safe(hp));
    }

    /** 逃跑事件。 */
    public static BattleEvent run(boolean success) {
        return new BattleEvent(Kind.RUN, Side.PLAYER, "", "", null, null, success, Hp.NONE);
    }

    /** @return 精灵当前 HP 的快照；{@code p} 为 {@code null} 时返回 {@link Hp#NONE} */
    public static Hp hpOf(Pokemon p) {
        return p == null ? Hp.NONE : new Hp(p.getCurrentHp(), p.getMaxHp());
    }

    private static Hp safe(Hp hp) {
        return hp == null ? Hp.NONE : hp;
    }

    /** @return 与本事件阵营相对的另一方（{@link Kind#BATTLE_START} 返回 {@code null}） */
    public Side opponent() {
        if (side == Side.PLAYER) {
            return Side.FOE;
        }
        return side == Side.FOE ? Side.PLAYER : null;
    }
}
