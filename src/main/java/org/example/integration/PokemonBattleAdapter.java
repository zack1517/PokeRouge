package org.example.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.pokemon.domain.LearnableMove;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;

/**
 * 新宝可梦系统与既有战斗系统之间唯一的数据边界。
 *
 * <p>角色创建、种族、技能和野生遭遇均读取 {@code org.example.pokemon}；
 * 战斗期间则交给既有 battle 模块的运行时模型处理。</p>
 */
public final class PokemonBattleAdapter {

    private PokemonBattleAdapter() {
    }

    /** 将玩家在新宝可梦库中选择的初始精灵交给战斗系统。 */
    public static Player createBattlePlayer(String name, org.example.pokemon.domain.Pokemon starter) {
        Player player = new Player(name);
        player.addPokemon(toBattlePokemon(starter));
        return player;
    }

    /** 使用新宝可梦库生成一只可交给 battle 模块的野生精灵。 */
    public static Optional<Pokemon> createWildPokemon(int aroundLevel) {
        PokemonService source = new PokemonServiceImpl();
        List<org.example.pokemon.domain.Species> choices = source.getInitialPool();
        if (choices.isEmpty()) {
            return Optional.empty();
        }
        org.example.pokemon.domain.Species species = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        return Optional.of(toBattlePokemon(source.createWildPokemon(species.getId(), aroundLevel)));
    }

    private static Pokemon toBattlePokemon(org.example.pokemon.domain.Pokemon source) {
        org.example.pokemon.domain.Species origin = source.getSpecies();
        List<Move> knownMoves = new ArrayList<>();
        for (LearnableMove learnable : origin.getLearnableMoves()) {
            if (learnable.getLevel() <= source.getLevel() && knownMoves.size() < Pokemon.MAX_MOVES) {
                org.example.pokemon.infrastructure.GameData.instance().getMove(learnable.getMoveId())
                        .map(PokemonBattleAdapter::toBattleMove)
                        .filter(move -> knownMoves.stream().noneMatch(existing -> existing.getId().equals(move.getId())))
                        .ifPresent(knownMoves::add);
            }
        }
        return Pokemon.create(toBattleSpecies(origin), source.getLevel(), knownMoves);
    }

    private static Species toBattleSpecies(org.example.pokemon.domain.Species source) {
        List<org.example.pokemon.domain.ElementType> types = source.getTypes();
        org.example.pokemon.domain.BaseStats base = source.getBaseStats();
        Map<Integer, String> learnSchedule = source.getLearnSchedule().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        return new Species(source.getId(), source.getName(), ElementType.valueOf(types.get(0).name()),
                types.size() > 1 ? ElementType.valueOf(types.get(1).name()) : null,
                new Stats(base.getHp(), base.getAttack(), base.getDefense(), base.getSpAttack(), base.getSpDefense(), base.getSpeed()),
                (int) source.getCaptureRate(), source.getMoveIds(), source.getEvolvesToId(), source.getEvolveLevel(), learnSchedule);
    }

    private static Move toBattleMove(org.example.pokemon.domain.Move source) {
        return new Move(source.getId(), source.getName(), ElementType.valueOf(source.getType().name()),
                MoveCategory.valueOf(source.getCategory().name()), source.getPower(), source.getAccuracy(),
                source.getMaxPp(), MoveEffect.NONE);
    }
}
