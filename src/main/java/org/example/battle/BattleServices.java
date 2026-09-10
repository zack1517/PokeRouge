package org.example.battle;

import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Trainer;

import java.util.Random;

/**
 * {@link BattleService} 的静态工厂。
 *
 * <p>负责按训练家与野生精灵创建一场新的对战。调用方（主界面/控制器）通过本工厂获取
 * {@link BattleService}，而无需依赖具体实现类 {@link BattleEngine}。</p>
 *
 * <p><b>数据来源</b>：本类不持有任何数据，需要通过 {@link BattleDataPort} 参数由外部
 * 数据模块注入（由组装层实现并传入）；未注入时使用
 * {@link BattleDataPorts#none()}，升级学招/进化降级为无操作。</p>
 *
 * <p><b>职责边界</b>：本类只做「创建对战」。野生遭遇生成（等级浮动、随机挑种族）属于
 * 流程/组装层职责，见 {@code org.example.integration.WildEncounter}。</p>
 */
public final class BattleServices {

    private BattleServices() {
        // 工具类，禁止实例化
    }

    // ------------------------------------------------------------------
    // 创建对战
    // ------------------------------------------------------------------

    /**
     * 创建一场默认随机源的新对战。
     *
     * @param player 玩家（当前出战精灵必须存活，否则抛异常）
     * @param wild   野生精灵（必须非倒下）
     * @return 一个已就绪的 {@link BattleService} 实例
     * @throws NullPointerException     参数为 {@code null}
     * @throws IllegalArgumentException 玩家无可用出战精灵，或野生精灵无效
     */
    public static BattleService newBattle(Player player, Pokemon wild) {
        return new BattleEngine(player, wild);
    }

    /**
     * 创建一场可指定随机源的新对战（便于测试复现）。
     *
     * @param player 玩家（当前出战精灵必须存活，否则抛异常）
     * @param wild   野生精灵（必须非倒下）
     * @param random 随机源
     * @return 一个已就绪的 {@link BattleService} 实例
     * @throws NullPointerException     参数为 {@code null}
     * @throws IllegalArgumentException 玩家无可用出战精灵，或野生精灵无效
     */
    public static BattleService newBattle(Player player, Pokemon wild, Random random) {
        return new BattleEngine(player, wild, random);
    }

    /**
     * 创建一场训练师轮战（默认随机源）：玩家 × 训练师整支队伍。不可逃跑、不可捕捉，
     * 直到某一方精灵全部倒下才结束。
     *
     * @param player  玩家（当前出战精灵必须存活，否则抛异常）
     * @param trainer 敌方训练师（队伍中须有健康精灵，否则抛异常）
     * @return 一个已就绪的 {@link BattleService} 实例
     * @throws NullPointerException     参数为 {@code null}
     * @throws IllegalArgumentException 玩家或训练师无可用出战精灵
     */
    public static BattleService newTrainerBattle(Player player, Trainer trainer) {
        return new BattleEngine(player, trainer);
    }

    /**
     * 创建一场可指定随机源的训练师轮战（便于测试复现）。
     *
     * @param player  玩家（当前出战精灵必须存活，否则抛异常）
     * @param trainer 敌方训练师（队伍中须有健康精灵，否则抛异常）
     * @param random  随机源
     * @return 一个已就绪的 {@link BattleService} 实例
     * @throws NullPointerException     参数为 {@code null}
     * @throws IllegalArgumentException 玩家或训练师无可用出战精灵
     */
    public static BattleService newTrainerBattle(Player player, Trainer trainer, Random random) {
        return new BattleEngine(player, trainer, random);
    }

    /**
     * 创建一场野生对战，并注入外部数据端口（升级学招 / 进化用）。
     *
     * @param player   玩家（当前出战精灵必须存活，否则抛异常）
     * @param wild     野生精灵（必须非倒下）
     * @param dataPort 只读数据端口，不可为 {@code null}
     * @return 一个已就绪的 {@link BattleService} 实例
     */
    public static BattleService newBattle(Player player, Pokemon wild, BattleDataPort dataPort) {
        return new BattleEngine(player, wild, dataPort);
    }

    /**
     * 创建一场野生对战，并注入随机源与外部数据端口。
     *
     * @param player   玩家（当前出战精灵必须存活，否则抛异常）
     * @param wild     野生精灵（必须非倒下）
     * @param random   随机源
     * @param dataPort 只读数据端口，不可为 {@code null}
     * @return 一个已就绪的 {@link BattleService} 实例
     */
    public static BattleService newBattle(Player player, Pokemon wild, Random random, BattleDataPort dataPort) {
        return new BattleEngine(player, wild, random, dataPort);
    }

    /**
     * 创建一场训练师轮战，并注入外部数据端口（升级学招 / 进化用）。
     *
     * @param player   玩家（当前出战精灵必须存活，否则抛异常）
     * @param trainer  敌方训练师（队伍中须有健康精灵，否则抛异常）
     * @param dataPort 只读数据端口，不可为 {@code null}
     * @return 一个已就绪的 {@link BattleService} 实例
     */
    public static BattleService newTrainerBattle(Player player, Trainer trainer, BattleDataPort dataPort) {
        return new BattleEngine(player, trainer, dataPort);
    }

    /**
     * 创建一场训练师轮战，并注入随机源与外部数据端口。
     *
     * @param player   玩家（当前出战精灵必须存活，否则抛异常）
     * @param trainer  敌方训练师（队伍中须有健康精灵，否则抛异常）
     * @param random   随机源
     * @param dataPort 只读数据端口，不可为 {@code null}
     * @return 一个已就绪的 {@link BattleService} 实例
     */
    public static BattleService newTrainerBattle(Player player, Trainer trainer, Random random,
                                                 BattleDataPort dataPort) {
        return new BattleEngine(player, trainer, random, dataPort);
    }

}
