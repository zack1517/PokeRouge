package org.example.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.battle.BattleDataPort;
import org.example.model.Move;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;

/**
 * 以新宝可梦库为优先数据源、以战斗模块数据中心为回退的战斗只读数据端口。
 *
 * <p>技能与种族的查询（学招 / 进化）优先走宝可梦库（30 种族 / 36 技能，含异常状态效果与
 * 完整学招表），查不到时回退到战斗模块内建数据（{@code s_*} / {@code m_*} 精灵与技能），
 * 保证两条数据链路都能正常工作。</p>
 *
 * <p>背景：战斗模块数据中心的 CSV 解析格式与宝可梦库不同，直接加载宝可梦库的
 * species.csv 会产生学招表为空的劣化种族，导致进化后学不到新技能；本端口绕开该问题，
 * 让成长模块（{@link org.example.growth.GrowthService}）始终拿到宝可梦库的完整数据。</p>
 */
public final class PokemonLibraryDataPort implements BattleDataPort {

    /** 战斗模块数据中心（内建 s_* / m_* 数据），作为回退源。 */
    private final org.example.data.GameData fallback = org.example.data.GameData.instance();

    /** 新宝可梦库（30 种族 / 36 技能），优先数据源。 */
    private final org.example.pokemon.infrastructure.GameData library =
            org.example.pokemon.infrastructure.GameData.instance();

    @Override
    public Move findMove(String moveId) {
        if (moveId == null) {
            return null;
        }
        Optional<Move> fromLibrary = library.getMove(moveId)
                .map(PokemonBattleAdapter::toBattleMove);
        return fromLibrary.orElseGet(() -> fallback.move(moveId));
    }

    @Override
    public Species findSpecies(String speciesId) {
        if (speciesId == null) {
            return null;
        }
        Optional<Species> fromLibrary = library.getSpecies(speciesId)
                .map(PokemonBattleAdapter::toBattleSpecies);
        return fromLibrary.orElseGet(() -> fallback.species(speciesId));
    }

    /**
     * 野生遭遇可用的种族 id 列表：返回宝可梦库全部种族（神兽除外，神兽只出现在神兽偶遇）。
     *
     * <p>按等级分层（低种族值基础形态 → 高种族值最终形态）由遭遇生成层
     * （{@link PokemonBattleAdapter#createWildPokemon}）负责。</p>
     */
    @Override
    public List<String> wildSpeciesPool() {
        List<String> ids = new ArrayList<>();
        for (org.example.pokemon.domain.Species species : library.getAllSpecies()) {
            if (PokemonBattleAdapter.isLegendary(species.getId())) {
                continue;
            }
            ids.add(species.getId());
        }
        return ids;
    }

    /**
     * 按种族 id 与等级生成个体：宝可梦库种族按学招表解锁技能（含状态效果）；
     * 内建种族回退到战斗模块数据中心生成。
     */
    @Override
    public Optional<Pokemon> createPokemon(String speciesId, int level) {
        if (speciesId == null) {
            return Optional.empty();
        }
        if (library.getSpecies(speciesId).isPresent()) {
            PokemonService service = new PokemonServiceImpl();
            return Optional.of(PokemonBattleAdapter.toBattlePokemon(
                    service.createWildPokemon(speciesId, level)));
        }
        return fallback.createPokemon(speciesId, level);
    }
}
