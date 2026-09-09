package com.bao01.flow;

import org.example.model.Player;
import org.example.model.Pokemon;

import java.util.List;

/**
 * 战斗桥：由对战模块 / UI（{@code org.example.battle.BattleService} 的接线方）
 * 对接实现。
 *
 * <p>对应《游戏流程接口设计》3.3。流程模块不直接操作战斗内部状态，只通过本桥
 * 发起战斗；一场对局结束（胜负 / 逃跑）后，由调用方把结果通过
 * {@link FlowController#settleFight(NodeType, FightResult)} 回填结算。对战的实际
 * 推进与输赢判定归属 feature/battle 分支，不在本模块实现。</p>
 */
public interface BattleAdapter {

    /**
     * 开局一场流程战斗。
     *
     * @param player   我方玩家（流程持有的同一实例，战斗中对 HP / 背包的修改直接反映到流程）
     * @param foes     敌方精灵列表（流程按节点构造，见 {@code FlowController#buildFoeTeam}，
     *                 多只时由战斗模块按「顺序单打 / 连打」自行驱动）
     * @param canFlee  该节点是否允许逃跑（WILD / LEGEND 可逃，其余节点逃跑视为败北）
     * @return 开局日志（供 UI 展示；对局的后续驱动由 UI / 战斗模块接管）
     */
    List<String> startBattle(Player player, List<Pokemon> foes, boolean canFlee);
}

