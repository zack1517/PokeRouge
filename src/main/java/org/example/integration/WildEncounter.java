package org.example.integration;

import org.example.battle.BattleDataPort;
import org.example.model.Pokemon;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 野生遭遇生成助手（组装/流程侧）。
 *
 * <p>遭遇生成不属于战斗演算与结算，因此**不放在战斗模块**：战斗模块只负责「给定双方进行演算
 * 与结算」，而「遇到谁、几级」由本类（流程/肉鸽侧）决定。数据同样由外部经
 * {@link BattleDataPort} 注入，随机源可注入以便复现。</p>
 */
public final class WildEncounter {

    /** 等级浮动半宽：遭遇等级在 {@code [level-2, level+2]} 内随机。 */
    public static final int LEVEL_SPREAD = 2;

    /** 遭遇等级下限。 */
    public static final int MIN_LEVEL = 2;

    private WildEncounter() {
        // 工具类，禁止实例化
    }

    /**
     * 野生等级围绕玩家等级浮动（{@code [level-2, level+2]} 内随机，最低 {@value #MIN_LEVEL}）。
     *
     * @param playerLevel 玩家等级
     * @return 野生精灵建议等级
     */
    public static int levelAround(int playerLevel) {
        return levelAround(playerLevel, ThreadLocalRandom.current());
    }

    /**
     * 同上，使用指定的随机源（便于复现）。
     *
     * @param playerLevel 玩家等级
     * @param random      随机源
     * @return 野生精灵建议等级
     */
    public static int levelAround(int playerLevel, Random random) {
        int span = LEVEL_SPREAD * 2 + 1;
        return Math.max(MIN_LEVEL, playerLevel + random.nextInt(span) - LEVEL_SPREAD);
    }

    /**
     * 从数据端口随机挑选一只野生精灵。
     *
     * @param aroundLevel 目标等级（用于构造精灵个体）
     * @param dataPort    只读数据端口
     * @return 野生精灵；端口无野生池数据时返回 {@link Optional#empty()}
     */
    public static Optional<Pokemon> randomWild(int aroundLevel, BattleDataPort dataPort) {
        return randomWild(aroundLevel, dataPort, ThreadLocalRandom.current());
    }

    /**
     * 同上，使用指定的随机源（便于复现）。
     *
     * @param aroundLevel 目标等级（用于构造精灵个体）
     * @param dataPort    只读数据端口
     * @param random      随机源
     * @return 野生精灵；端口无野生池数据时返回 {@link Optional#empty()}
     */
    public static Optional<Pokemon> randomWild(int aroundLevel, BattleDataPort dataPort, Random random) {
        List<String> pool = dataPort.wildSpeciesPool();
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        String id = pool.get(random.nextInt(pool.size()));
        return dataPort.createPokemon(id, aroundLevel);
    }
}
