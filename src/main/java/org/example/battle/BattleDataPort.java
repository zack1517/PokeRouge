package org.example.battle;

import org.example.model.Move;
import org.example.model.Pokemon;
import org.example.model.Species;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 战斗模块的<b>只读数据端口</b>。
 *
 * <p>战斗模块只消费数据、不拥有数据：本接口描述战斗相关的<b>外部数据入口</b>
 * （技能、种族、野生池、按种族生成个体），由外部数据模块实现，在组装层注入到
 * {@link BattleServices} / {@link BattleEngine}、{@link org.example.integration.WildEncounter}
 * 与外部成长模块。引擎内部不含任何内建数据，也不直接依赖具体的数据源实现。</p>
 *
 * <p>未注入时使用 {@link BattleDataPorts#none()}：所有查询都返回「查不到」，
 * 战斗规则照常运行，仅「随机野生遭遇」与成长模块的「到级学招 / 进化」降级为无操作。</p>
 */
public interface BattleDataPort {

    /**
     * 按 id 查询技能。
     *
     * @param moveId 技能 id
     * @return 对应技能；查不到返回 {@code null}
     */
    Move findMove(String moveId);

    /**
     * 按 id 查询种族。
     *
     * @param speciesId 种族 id
     * @return 对应种族；查不到返回 {@code null}
     */
    Species findSpecies(String speciesId);

    /**
     * 野生遭遇可用的种族 id 列表。
     *
     * @return 野生池；无可用野生种族时返回空列表
     */
    List<String> wildSpeciesPool();

    /**
     * 按种族 id 与等级生成个体。
     *
     * <p>默认实现依据种族自带的首批技能生成，数据模块可按自身规则覆盖
     * （例如按等级筛选可学技能）。</p>
     *
     * @param speciesId 种族 id
     * @param level     等级
     * @return 生成的个体；种族查不到或生成失败返回 {@link Optional#empty()}
     */
    default Optional<Pokemon> createPokemon(String speciesId, int level) {
        Species species = findSpecies(speciesId);
        if (species == null) {
            return Optional.empty();
        }
        List<Move> moves = new ArrayList<>();
        for (String moveId : species.getMoveIds()) {
            if (moves.size() >= Pokemon.MAX_MOVES) {
                break;
            }
            Move move = findMove(moveId);
            if (move != null) {
                moves.add(move);
            }
        }
        return Optional.of(Pokemon.create(species, level, moves));
    }
}
