package org.example.save;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.integration.PokemonBattleAdapter;
import org.example.model.Move;
import org.example.model.MoveSlot;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.StatusCondition;

/**
 * 战斗模型精灵与存档数据之间的双向映射。
 *
 * <p>种族与技能都在读档时<b>按 id 重新查表</b>，而不是把整份定义塞进存档 —— 这样调整数据表
 * （改属性、改技能威力）后，老存档里的精灵会自动用上新数据，同时个体值、等级、当前 HP、
 * 经验、异常状态与技能剩余 PP 这些「属于这只精灵自己的东西」被原样还原。</p>
 *
 * <p>查表优先走新宝可梦体系的注册表（与 {@link PokemonBattleAdapter#createWildPokemon} 同源），
 * 查不到时回退到战斗模块的数据端口。因此读档还原出的种族定义与战斗中新生成的精灵一致。</p>
 */
public final class PokemonMapper {

    private PokemonMapper() {
    }

    /** 战斗模型精灵 → 存档数据（含技能剩余 PP）。 */
    public static SaveData.PokemonData toData(Pokemon pokemon) {
        Stats ivs = pokemon.getIvs() == null ? new Stats(0, 0, 0, 0, 0, 0) : pokemon.getIvs();
        List<SaveData.MoveData> moves = new ArrayList<>();
        for (MoveSlot slot : pokemon.getMoveSlots()) {
            moves.add(new SaveData.MoveData(slot.getMove().getId(), slot.getPp()));
        }
        return new SaveData.PokemonData(pokemon.getSpecies().getId(), pokemon.getLevel(),
                new SaveData.IvData(ivs.getHp(), ivs.getAttack(), ivs.getDefense(),
                        ivs.getSpAttack(), ivs.getSpDefense(), ivs.getSpeed()),
                pokemon.getExp(), pokemon.getStatus().name(), pokemon.getSleepTurns(),
                pokemon.getBadlyPoisonCounter(), pokemon.getConfusionTurns(),
                pokemon.getCurrentHp(), moves,
                pokemon.getHeldItem() == null ? "" : pokemon.getHeldItem().getId());
    }

    /**
     * 存档数据 → 战斗模型精灵（精确还原残血 / 异常 / PP 等中途状态）。
     *
     * @return 物种 id 在本作数据中已不存在时返回空；此时该精灵只能丢弃，不能凭空造一只
     */
    public static Optional<Pokemon> toPokemon(SaveData.PokemonData data) {
        if (data == null) {
            return Optional.empty();
        }
        Optional<Species> species = findSpecies(data.speciesId());
        if (species.isEmpty()) {
            return Optional.empty();
        }
        List<MoveSlot> slots = new ArrayList<>();
        for (SaveData.MoveData move : data.moves()) {
            findMove(move.moveId()).ifPresent(found -> slots.add(new MoveSlot(found, move.pp())));
        }
        SaveData.IvData iv = data.ivs() == null ? new SaveData.IvData(0, 0, 0, 0, 0, 0) : data.ivs();
        Stats ivs = new Stats(iv.hp(), iv.attack(), iv.defense(),
                iv.spAttack(), iv.spDefense(), iv.speed());
        int level = Math.max(1, Math.min(Pokemon.MAX_LEVEL, data.level()));
        Pokemon restored = Pokemon.restore(species.get(), level, ivs, slots, data.exp(),
                StatusCondition.parse(data.status()), data.sleepTurns(),
                data.badlyPoisonCounter(), data.confusionTurns(), data.currentHp());
        applyHeldItem(restored, data.heldItemId());
        return Optional.of(restored);
    }

    /** 还原携带装备：未记录（旧档）或装备已从注册表移除时保持未携带，不凭空造一件。 */
    private static void applyHeldItem(Pokemon pokemon, String heldItemId) {
        if (heldItemId == null || heldItemId.isBlank()) {
            return;
        }
        org.example.model.HeldItem equipment =
                org.example.data.GameData.instance().equipment(heldItemId);
        if (equipment != null) {
            pokemon.setHeldItem(equipment);
        }
    }

    /** 按 id 查种族：优先新体系注册表（与战斗生成同源），回退战斗数据端口。 */
    private static Optional<Species> findSpecies(String speciesId) {
        if (speciesId == null || speciesId.isBlank()) {
            return Optional.empty();
        }
        Optional<org.example.pokemon.domain.Species> source =
                org.example.pokemon.infrastructure.GameData.instance().getSpecies(speciesId);
        if (source.isPresent()) {
            return Optional.of(PokemonBattleAdapter.toBattleSpecies(source.get()));
        }
        return Optional.ofNullable(PokemonBattleAdapter.battleDataPort().findSpecies(speciesId));
    }

    /** 按 id 查技能：优先新体系注册表（与战斗生成同源），回退战斗数据端口。 */
    private static Optional<Move> findMove(String moveId) {
        if (moveId == null || moveId.isBlank()) {
            return Optional.empty();
        }
        Optional<org.example.pokemon.domain.Move> source =
                org.example.pokemon.infrastructure.GameData.instance().getMove(moveId);
        if (source.isPresent()) {
            return Optional.of(PokemonBattleAdapter.toBattleMove(source.get()));
        }
        return Optional.ofNullable(PokemonBattleAdapter.battleDataPort().findMove(moveId));
    }
}
