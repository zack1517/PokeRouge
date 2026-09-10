package org.example.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.example.battle.BattleDataPort;
import org.example.battle.BattleGrowthPort;
import org.example.data.GameDataBattleDataPort;
import org.example.growth.GrowthProgress;
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

    /** 战斗模块所需的数据端口：由数据模块实现，在此组装层注入给战斗引擎。 */
    public static BattleDataPort battleDataPort() {
        return new GameDataBattleDataPort();
    }

    /**
     * 战斗结算所需的成长端口：经验增加 / 升级 / 学招 / 进化由成长模块判定（见
     * {@link GrowthService}）。与战斗模块共用同一份数据端口。
     *
     * @param dataPort 只读数据端口（技能 / 种族查询），不可为 {@code null}
     */
    public static BattleGrowthPort battleGrowthPort(BattleDataPort dataPort) {
        return battleGrowthPort(dataPort, GrowthProgress.instance());
    }

    /**
     * 战斗结算所需的成长端口（指定成长进度来源）。
     *
     * <p>传入的进度同时承载「图鉴数据」（种族捕捉次数 / 对战次数 / 个体值加成）与
     * 「捕捉次数驱动的个体值加成」：战斗胜利时累计对战次数，捕捉成功时累计捕捉次数，
     * 之后新建的精灵按累计加成提升个体值。</p>
     *
     * @param dataPort 只读数据端口（技能 / 种族查询），不可为 {@code null}
     * @param progress 局外成长进度，不可为 {@code null}
     */
    public static BattleGrowthPort battleGrowthPort(BattleDataPort dataPort, GrowthProgress progress) {
        return new GrowthService(dataPort, progress);
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

    /** 使用新宝可梦库生成一只可交给 battle 模块的野生精灵（个体值已含局外成长加成）。 */
    public static Optional<Pokemon> createWildPokemon(int aroundLevel) {
        return createWildPokemon(aroundLevel, GrowthProgress.instance());
    }

    /**
     * 使用新宝可梦库生成野生精灵，并指定成长进度来源（便于测试隔离）。
     *
     * @param aroundLevel 目标等级
     * @param progress    局外成长进度（决定个体值加成）
     */
    public static Optional<Pokemon> createWildPokemon(int aroundLevel, GrowthProgress progress) {
        PokemonService source = new PokemonServiceImpl(progress);
        List<org.example.pokemon.domain.Species> choices = source.getInitialPool();
        if (choices.isEmpty()) {
            return Optional.empty();
        }
        org.example.pokemon.domain.Species species = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        return Optional.of(toBattlePokemon(source.createWildPokemon(species.getId(), aroundLevel)));
    }

    /**
     * 新体系精灵 → 战斗模型精灵（含技能与个体值）。读档重建队伍时同样使用本方法，
     * 保证存档还原出的个体与战斗中新生成的个体同源。
     */
    public static Pokemon toBattlePokemon(org.example.pokemon.domain.Pokemon source) {
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
        return Pokemon.create(toBattleSpecies(origin), source.getLevel(), knownMoves, toBattleStats(source.getIvs()));
    }

    /** 个体值跨系统转换：新体系的个体值必须随个体一起进入战斗模型，否则成长加成不可见。 */
    private static Stats toBattleStats(org.example.pokemon.domain.Stats source) {
        return new Stats(source.getHp(), source.getAttack(), source.getDefense(),
                source.getSpAttack(), source.getSpDefense(), source.getSpeed());
    }

    /**
     * 新体系种族 → 战斗模型种族（属性 / 捕获率 / 进化链 / 习得表）。供读档还原精灵时复用。
     */
    public static Species toBattleSpecies(org.example.pokemon.domain.Species source) {
        List<org.example.pokemon.domain.ElementType> types = source.getTypes();
        org.example.pokemon.domain.BaseStats base = source.getBaseStats();
        Map<Integer, String> learnSchedule = source.getLearnSchedule().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        return new Species(source.getId(), source.getName(), ElementType.valueOf(types.get(0).name()),
                types.size() > 1 ? ElementType.valueOf(types.get(1).name()) : null,
                new Stats(base.getHp(), base.getAttack(), base.getDefense(), base.getSpAttack(), base.getSpDefense(), base.getSpeed()),
                (int) source.getCaptureRate(), source.getMoveIds(), source.getEvolvesToId(), source.getEvolveLevel(), learnSchedule);
    }

    /** 新体系技能 → 战斗模型技能。供读档还原精灵技能时复用。 */
    public static Move toBattleMove(org.example.pokemon.domain.Move source) {
        return new Move(source.getId(), source.getName(), ElementType.valueOf(source.getType().name()),
                MoveCategory.valueOf(source.getCategory().name()), source.getPower(), source.getAccuracy(),
                source.getMaxPp(), source.getPriority(), MoveEffect.NONE,
                StatusCondition.parse(source.getInflicts()), source.getInflictionChance());
    }
}
