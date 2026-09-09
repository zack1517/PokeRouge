package org.example.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.example.battle.BattleDataPort;
import org.example.battle.BattleGrowthPort;
import org.example.data.GameDataBattleDataPort;
import org.example.growth.GrowthService;
import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveEffect;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.StatusCondition;
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

    /**
     * 战斗模块所需的数据端口：优先读取新宝可梦库（30 种族 / 36 技能，含异常状态与完整学招表），
     * 查不到时回退到战斗模块内建数据（{@code s_*} / {@code m_*}）。
     *
     * <p>战斗引擎与成长模块共用该端口：到级学招（{@link GrowthService}）与进化查询
     * 均拿到宝可梦库的完整数据，避免走战斗模块数据中心对宝可梦 CSV 的劣化解析。</p>
     */
    public static BattleDataPort battleDataPort() {
        return new PokemonLibraryDataPort();
    }

    /**
     * 战斗结算所需的成长端口：经验增加 / 升级 / 学招 / 进化由成长模块判定（见
     * {@link GrowthService}）。与战斗模块共用同一份数据端口。
     *
     * @param dataPort 只读数据端口（技能 / 种族查询），不可为 {@code null}
     */
    public static BattleGrowthPort battleGrowthPort(BattleDataPort dataPort) {
        return new GrowthService(dataPort);
    }

    /** 将玩家在新宝可梦库中选择的初始精灵交给战斗系统。 */
    public static Player createBattlePlayer(String name, org.example.pokemon.domain.Pokemon starter) {
        Player player = new Player(name);
        player.addPokemon(toBattlePokemon(starter));
        grantStartingItems(player);
        return player;
    }

    /** 发放初始携带道具：回复、捕捉，以及各类异常状态解除道具。 */
    private static void grantStartingItems(Player player) {
        org.example.data.GameData data = org.example.data.GameData.instance();
        org.example.model.Bag bag = player.getBag();
        addItem(bag, data, "i_potion", 5);
        addItem(bag, data, "i_poke_ball", 5);
        addItem(bag, data, "i_antidote", 2);
        addItem(bag, data, "i_paralyze_heal", 2);
        addItem(bag, data, "i_burn_heal", 2);
        addItem(bag, data, "i_ice_heal", 1);
        addItem(bag, data, "i_awakening", 1);
        addItem(bag, data, "i_full_heal", 1);
    }

    private static void addItem(org.example.model.Bag bag, org.example.data.GameData data,
                                String itemId, int count) {
        org.example.model.Item item = data.item(itemId);
        if (item != null) {
            bag.add(item, count);
        }
    }

    /** 使用新宝可梦库生成一只可交给 battle 模块的野生精灵。 */
    public static Optional<Pokemon> createWildPokemon(int aroundLevel) {
        PokemonService source = new PokemonServiceImpl();
        List<org.example.pokemon.domain.Species> choices = wildCandidates(aroundLevel);
        if (choices.isEmpty()) {
            return Optional.empty();
        }
        org.example.pokemon.domain.Species species = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        return Optional.of(toBattlePokemon(source.createWildPokemon(species.getId(), aroundLevel)));
    }

    /**
     * 按遭遇等级从宝可梦库全部种族中筛选候选。
     *
     * <p>种族值总和（BST）越低出现越早：≤350 的基础形态任意等级可遇；
     * 350<BST≤500 的二段/中等形态需等级≥12；BST>500 的最终形态/强力宝可梦需等级≥20，
     * 保证高强度宝可梦在游戏后期才出现。</p>
     *
     * @param aroundLevel 目标遭遇等级
     * @return 符合条件的种族列表（数据缺失时为空列表）
     */
    private static List<org.example.pokemon.domain.Species> wildCandidates(int aroundLevel) {
        List<org.example.pokemon.domain.Species> all =
                org.example.pokemon.infrastructure.GameData.instance().getAllSpecies();
        List<org.example.pokemon.domain.Species> candidates = new ArrayList<>();
        for (org.example.pokemon.domain.Species species : all) {
            int bst = species.getBaseStats().getTotal();
            int minLevel = bst <= 350 ? 1 : (bst <= 500 ? 12 : 20);
            if (aroundLevel >= minLevel) {
                candidates.add(species);
            }
        }
        return candidates;
    }

    static Pokemon toBattlePokemon(org.example.pokemon.domain.Pokemon source) {
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

    static Species toBattleSpecies(org.example.pokemon.domain.Species source) {
        List<org.example.pokemon.domain.ElementType> types = source.getTypes();
        org.example.pokemon.domain.BaseStats base = source.getBaseStats();
        Map<Integer, String> learnSchedule = new LinkedHashMap<>();
        List<Map.Entry<Integer, String>> extras = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : source.getLearnSchedule()) {
            if (learnSchedule.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                // 同等级多技能（如皮卡丘 1 级同时学会电击与叫声）：等级表只保留首个，
                // 其余追加为额外可学条目，保证学招数据不丢失。
                extras.add(entry);
            }
        }
        Species battleSpecies = new Species(source.getId(), source.getName(),
                ElementType.valueOf(types.get(0).name()),
                types.size() > 1 ? ElementType.valueOf(types.get(1).name()) : null,
                new Stats(base.getHp(), base.getAttack(), base.getDefense(), base.getSpAttack(), base.getSpDefense(), base.getSpeed()),
                (int) source.getCaptureRate(), source.getMoveIds(), source.getEvolvesToId(), source.getEvolveLevel(), learnSchedule);
        for (Map.Entry<Integer, String> extra : extras) {
            battleSpecies.addLearnableMove(new org.example.model.LearnableMove(extra.getValue(), extra.getKey()));
        }
        return battleSpecies;
    }

    static Move toBattleMove(org.example.pokemon.domain.Move source) {
        return new Move(source.getId(), source.getName(), ElementType.valueOf(source.getType().name()),
                MoveCategory.valueOf(source.getCategory().name()), source.getPower(), source.getAccuracy(),
                source.getMaxPp(), source.getPriority(), MoveEffect.NONE,
                StatusCondition.parse(source.getInflicts()), source.getInflictionChance());
    }
}
