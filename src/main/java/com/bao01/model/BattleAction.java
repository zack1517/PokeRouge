package com.bao01.model;

/**
 * 一回合中某一方选择执行的行动。
 * 行动在回合内按 逃跑 → 道具 → 轮换 → 攻击(按速度) 的顺序结算。
 */
public sealed interface BattleAction {

    /** 使用第 moveIndex 个招式攻击对方当前出战宝可梦。 */
    record Attack(int moveIndex) implements BattleAction {
    }

    /** 轮换：换第 memberIndex 只（存活且非当前出战）上场。 */
    record SwitchTo(int memberIndex) implements BattleAction {
    }

    /** 对己方当前出战宝可梦使用道具（从队伍背包扣除）。 */
    record UseItem(Item item) implements BattleAction {
    }

    /** 尝试逃跑（训练家对战中相当于直接撤退，本场结束）。 */
    record Flee() implements BattleAction {
    }
}
