package org.example.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import org.example.model.Trainer;
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
     * <p><b>注意</b>：本重载绑定的是 {@link GrowthProgress#instance()} ——
     * <b>用户主目录</b>下的真实存档，战斗获胜即会把对战次数写入玩家存档。
     * <b>测试请改用 {@link #battleGrowthPort(BattleDataPort, GrowthProgress)}</b> 注入
     * 纯内存的 {@code new GrowthProgress()}，避免污染开发机上的真实进度。</p>
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

    /**
     * 将自定义战斗中选定的宝可梦队伍交给战斗系统（最多 6 只，第 0 只为首发）。
     *
     * <p>小队对战规则：成员满级满状态入场，故转换后统一 {@link Pokemon#fullRestore()}
     * 补满 HP 与技能 PP；携带道具与常规开局一致。</p>
     *
     * @param name  玩家名
     * @param squad 按出战顺序排列的队伍（超过 6 只时只取前 6 只）
     */
    public static Player createBattlePlayer(String name, List<org.example.pokemon.domain.Pokemon> squad) {
        Player player = new Player(name);
        for (org.example.pokemon.domain.Pokemon member : squad) {
            if (player.isPartyFull()) {
                break;
            }
            Pokemon battlePokemon = toBattlePokemon(member);
            battlePokemon.fullRestore(); // 满血 + 满 PP（小队对战规则）
            player.addPokemon(battlePokemon);
        }
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
     * <p>候选池按遭遇等级做 BST 分级筛选（见 {@link #wildCandidates(int)}），
     * 低等级只出基础形态，高强度宝可梦在游戏后期才出现。</p>
     *
     * @param aroundLevel 目标等级
     * @param progress    局外成长进度（决定个体值加成）
     */
    public static Optional<Pokemon> createWildPokemon(int aroundLevel, GrowthProgress progress) {
        PokemonService source = new PokemonServiceImpl(progress);
        List<org.example.pokemon.domain.Species> choices = wildCandidates(aroundLevel);
        if (choices.isEmpty()) {
            return Optional.empty();
        }
        org.example.pokemon.domain.Species species = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        return Optional.of(toBattlePokemon(source.createWildPokemon(species.getId(), aroundLevel)));
    }

    /**
     * 生成自定义战斗的对手训练家：从宝可梦库全部种族中随机不重复抽取 1~6 只指定等级的精灵，
     * 组成整队轮战（暂无完整 AI 训练家逻辑，仅按队伍顺序自动轮战）。
     *
     * @param name  对手名
     * @param count 队伍数量（1~6，超出按 6 截断）
     * @param level 精灵等级（小队对战统一满级 100）
     * @return 已加满成员的敌方训练家（均满状态入场）
     */
    public static Trainer createSquadTrainer(String name, int count, int level) {
        Trainer trainer = new Trainer(name);
        List<org.example.pokemon.domain.Species> pool =
                new ArrayList<>(org.example.pokemon.infrastructure.GameData.instance().getAllSpecies());
        Collections.shuffle(pool);
        PokemonService source = new PokemonServiceImpl();
        int picks = Math.min(Math.max(1, count), Math.min(Trainer.MAX_PARTY, pool.size()));
        for (int i = 0; i < picks; i++) {
            Pokemon battlePokemon = toBattlePokemon(source.createPokemon(pool.get(i).getId(), level));
            battlePokemon.fullRestore(); // 满血 + 满 PP
            trainer.addPokemon(battlePokemon);
        }
        return trainer;
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
     * 新体系种族 → 战斗模型种族（属性 / 种族经验值 / 捕获率 / 进化链 / 习得表）。供读档还原精灵时复用。
     */
    public static Species toBattleSpecies(org.example.pokemon.domain.Species source) {
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
                source.getBaseExpYield(),
                (int) source.getCaptureRate(), source.getMoveIds(), source.getEvolvesToId(), source.getEvolveLevel(), learnSchedule);
        for (Map.Entry<Integer, String> extra : extras) {
            battleSpecies.addLearnableMove(new org.example.model.LearnableMove(extra.getValue(), extra.getKey()));
        }
        return battleSpecies;
    }

    /** 新体系技能 → 战斗模型技能。供读档还原精灵技能时复用。 */
    public static Move toBattleMove(org.example.pokemon.domain.Move source) {
        return new Move(source.getId(), source.getName(), ElementType.valueOf(source.getType().name()),
                MoveCategory.valueOf(source.getCategory().name()), source.getPower(), source.getAccuracy(),
                source.getMaxPp(), source.getPriority(), MoveEffect.NONE,
                StatusCondition.parse(source.getInflicts()), source.getInflictionChance());
    }
}
