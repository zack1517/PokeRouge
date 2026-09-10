package org.example.battle;

import org.example.model.Move;
import org.example.model.Pokemon;
import org.example.model.Species;

import java.util.List;
import java.util.Optional;

/**
 * {@link BattleDataPort} 的工厂与默认实现。
 *
 * <p>{@link #none()} 提供一个「空数据端口」：任何查询都返回查不到，
 * 使战斗模块在不注入外部数据时依然可以独立运行与测试。</p>
 */
public final class BattleDataPorts {

    private static final BattleDataPort NONE = new BattleDataPort() {
        @Override
        public Move findMove(String moveId) {
            return null;
        }

        @Override
        public Species findSpecies(String speciesId) {
            return null;
        }

        @Override
        public List<String> wildSpeciesPool() {
            return List.of();
        }

        @Override
        public Optional<Pokemon> createPokemon(String speciesId, int level) {
            return Optional.empty();
        }
    };

    private BattleDataPorts() {
    }

    /**
     * 空数据端口：用于不注入外部数据的场景（例如纯规则测试）。
     *
     * @return 空实现，所有查询均返回查不到
     */
    public static BattleDataPort none() {
        return NONE;
    }
}
