package org.example.data;

import org.example.battle.BattleDataPort;
import org.example.model.Move;
import org.example.model.Pokemon;
import org.example.model.Species;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 数据模块对战斗数据端口 {@link BattleDataPort} 的实现（适配器）。
 *
 * <p>把 {@link GameData} 这个数据注册表暴露给战斗模块，使战斗模块只依赖端口接口而不依赖
 * 具体数据源。由组装层（控制器 / 演示入口）创建并注入到
 * {@code BattleServices} 或 {@code BattleEngine}。</p>
 */
public final class GameDataBattleDataPort implements BattleDataPort {

    private final GameData data;

    /** 使用全局数据注册表 {@link GameData#instance()} 创建。 */
    public GameDataBattleDataPort() {
        this(GameData.instance());
    }

    /**
     * 使用指定的数据注册表创建。
     *
     * @param data 数据注册表，不可为 {@code null}
     */
    public GameDataBattleDataPort(GameData data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    @Override
    public Move findMove(String moveId) {
        return data.move(moveId);
    }

    @Override
    public Species findSpecies(String speciesId) {
        return data.species(speciesId);
    }

    @Override
    public List<String> wildSpeciesPool() {
        return data.wildPool();
    }

    @Override
    public Optional<Pokemon> createPokemon(String speciesId, int level) {
        return data.createPokemon(speciesId, level);
    }
}
