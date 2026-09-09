package com.bao01.flow;

/**
 * 战斗结算结果（对应《游戏流程接口设计》3.3 扩展点）。
 *
 * <p>流程模块不直接执行战斗；一场对局结束后由 UI / 对战模块把结果回填到
 * {@link FlowController#settleFight(NodeType, FightResult)}。输赢的判定完全由
 * 战斗模块负责，本枚举只表达「流程需要知道的对局结局」。</p>
 */
public enum FightResult {

    /** 玩家获胜（击倒敌方全部精灵）。 */
    PLAYER_WON,

    /** 玩家战败（我方队伍全部倒下）。 */
    PLAYER_LOST,

    /** 玩家成功逃跑（仅 WILD / LEGEND 节点允许）。 */
    PLAYER_FLED
}
