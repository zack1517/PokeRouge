package com.bao01.flow;

import com.bao01.model.Battle;
import com.bao01.model.Team;

import java.util.List;

/**
 * 战斗桥：由战斗模块（{@code controller.BattleController} / UI）对接实现。
 *
 * <p>对应《游戏流程接口设计》3.3。流程模块不直接操作战斗内部状态，
 * 只通过本桥发起战斗并取回结果，保持模块边界干净。
 */
public interface BattleAdapter {

    /**
     * 开局一场流程战斗。
     *
     * @param playerTeam 我方队伍（流程持有的队伍实例）
     * @param foeTeam    流程按节点构造的敌方队伍（见 {@code FlowController#buildFoeTeam}）
     * @param kind       决定能否逃跑（WILD 可逃，其余不可）
     * @return 开局日志（由 UI 通过 {@link #currentBattle()} 继续驱动对局）
     */
    List<String> startBattle(Team playerTeam, Team foeTeam, Battle.Opponent kind);

    /** 当前正在进行的对局（供 UI 驱动与结算）。 */
    Battle currentBattle();

    /** 取回结局（IN_PROGRESS / PLAYER_WON / FOE_WON / PLAYER_FLED）。 */
    Battle.Outcome outcome();
}
