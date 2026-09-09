package org.example.pokemon.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.example.pokemon.domain.GrowthEvent;
import org.example.pokemon.domain.LearnableMove;
import org.example.pokemon.domain.Nature;
import org.example.pokemon.domain.Player;
import org.example.pokemon.domain.Pokemon;
import org.example.pokemon.domain.PokemonState;
import org.example.pokemon.domain.Species;
import org.example.pokemon.domain.StatModifier;
import org.example.pokemon.domain.Stats;
import org.example.pokemon.infrastructure.GameData;

/**
 * {@link PokemonService} 的默认实现，基于 {@link GameData} 单例提供业务操作。
 */
public class PokemonServiceImpl implements PokemonService {

    /** 野生宝可梦等级相对目标等级的浮动范围。 */
    private static final int WILD_LEVEL_OFFSET = 2;

    private Player currentPlayer;
    private final GameData gameData;

    public PokemonServiceImpl() {
        this.gameData = GameData.instance();
    }

    /**
     * 获取初始可选宝可梦池。
     *
     * @return 初始可选种族列表
     */
    @Override
    public List<Species> getInitialPool() {
        return gameData.getInitialPool();
    }

    /**
     * 获取当前玩家。
     *
     * @return 当前玩家，尚未创建时为 null
     */
    @Override
    public Player getPlayer() {
        return currentPlayer;
    }

    /**
     * 创建新玩家并设为当前玩家。
     *
     * @param name 玩家姓名
     * @return 新创建的玩家
     */
    @Override
    public Player createPlayer(String name) {
        currentPlayer = new Player(name);
        return currentPlayer;
    }

    /**
     * 重置当前玩家（置为 null）。
     */
    @Override
    public void resetPlayer() {
        currentPlayer = null;
    }

    /**
     * 向当前玩家队伍添加宝可梦。
     *
     * @param pokemon 待添加宝可梦
     * @return 添加成功返回 true，否则返回 false
     */
    @Override
    public boolean addPokemonToParty(Pokemon pokemon) {
        if (currentPlayer == null) {
            return false;
        }
        return currentPlayer.addPokemon(pokemon);
    }

    /**
     * 切换当前玩家的出战宝可梦。
     *
     * @param index 目标出战索引
     * @return 切换成功返回 true，否则返回 false
     */
    @Override
    public boolean switchActivePokemon(int index) {
        if (currentPlayer == null) {
            return false;
        }
        return currentPlayer.switchActive(index);
    }

    /**
     * 获取当前玩家的出战宝可梦。
     *
     * @return 出战宝可梦包装，不存在时为空
     */
    @Override
    public Optional<Pokemon> getActivePokemon() {
        if (currentPlayer == null) {
            return Optional.empty();
        }
        return currentPlayer.getActive();
    }

    /**
     * 获取当前玩家队伍中所有未濒死的宝可梦。
     *
     * @return 未濒死宝可梦列表
     */
    @Override
    public List<Pokemon> getHealthyPokemon() {
        if (currentPlayer == null) {
            return new ArrayList<>();
        }
        return currentPlayer.getHealthyPokemon();
    }

    /**
     * 判断当前玩家队伍是否全员濒死。
     *
     * @return 全员濒死返回 true，否则返回 false
     */
    @Override
    public boolean isPartyAllFainted() {
        if (currentPlayer == null) {
            return false;
        }
        return currentPlayer.isPartyAllFainted();
    }

    /**
     * 按种族 id 创建宝可梦，个体值随机、性格为默认性格。
     *
     * @param speciesId 种族 id
     * @param level 初始等级
     * @return 新创建的宝可梦
     */
    @Override
    public Pokemon createPokemon(String speciesId, int level) {
        return gameData.createPokemon(speciesId, level);
    }

    /**
     * 按种族 id 与指定性格创建宝可梦，个体值随机。
     *
     * @param speciesId 种族 id
     * @param level 初始等级
     * @param nature 指定性格
     * @return 新创建的宝可梦
     */
    @Override
    public Pokemon createPokemon(String speciesId, int level, Nature nature) {
        Species species = gameData.getSpecies(speciesId)
                .orElseThrow(() -> new IllegalArgumentException("未知的种族id: " + speciesId));
        return new Pokemon(species, level, Stats.randomIv(), nature);
    }

    /**
     * 创建野生宝可梦：等级在目标等级 ±2 内随机（最低 1 级），性格随机。
     *
     * @param speciesId 种族 id
     * @param aroundLevel 目标等级
     * @return 新创建的野生宝可梦
     */
    @Override
    public Pokemon createWildPokemon(String speciesId, int aroundLevel) {
        Species species = gameData.getSpecies(speciesId)
                .orElseThrow(() -> new IllegalArgumentException("未知的种族id: " + speciesId));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int level = Math.max(1, aroundLevel + random.nextInt(-WILD_LEVEL_OFFSET, WILD_LEVEL_OFFSET + 1));
        List<Nature> natures = gameData.getAllNatures();
        Nature nature = natures.get(random.nextInt(natures.size()));
        return new Pokemon(species, level, Stats.randomIv(), nature);
    }

    /**
     * 为宝可梦增加经验，返回升级、学招、进化等成长事件列表。
     *
     * @param pokemon 目标宝可梦
     * @param exp 获得的经验值
     * @return 成长事件列表
     */
    @Override
    public List<GrowthEvent> gainExp(Pokemon pokemon, int exp) {
        List<GrowthEvent> events = new ArrayList<>();
        int oldLevel = pokemon.getLevel();
        pokemon.gainExp(exp);
        int newLevel = pokemon.getLevel();

        if (newLevel > oldLevel) {
            events.add(new GrowthEvent(GrowthEvent.EventType.LEVEL_UP, oldLevel, newLevel, null, null));

            // 检查升级区间内可学习的新技能
            for (LearnableMove learnable : pokemon.getSpecies().getLearnableMoves()) {
                if (learnable.getLevel() > oldLevel && learnable.getLevel() <= newLevel
                        && !pokemon.hasMove(learnable.getMoveId())) {
                    gameData.getMove(learnable.getMoveId()).ifPresent(move -> {
                        pokemon.learnMove(move);
                        events.add(new GrowthEvent(GrowthEvent.EventType.LEARN_MOVE, oldLevel, newLevel, move, null));
                    });
                }
            }

            // 检查是否满足进化条件
            Species species = pokemon.getSpecies();
            if (species.canEvolveAt(newLevel)) {
                Species evolvedTo = gameData.getSpecies(species.getEvolutionTarget()).orElse(null);
                events.add(new GrowthEvent(GrowthEvent.EventType.EVOLUTION, oldLevel, newLevel, null, evolvedTo));
            }
        }
        return events;
    }

    /**
     * 判断宝可梦当前是否满足进化条件。
     *
     * @param pokemon 目标宝可梦
     * @return 可进化返回 true，否则返回 false
     */
    @Override
    public boolean canEvolve(Pokemon pokemon) {
        return pokemon.getSpecies().canEvolveAt(pokemon.getLevel());
    }

    /**
     * 返回宝可梦的进化目标种族。具体替换由调用方处理。
     *
     * @param pokemon 目标宝可梦
     * @return 进化目标种族，不满足进化条件或目标不存在时为 null
     */
    @Override
    public Species evolve(Pokemon pokemon) {
        Species species = pokemon.getSpecies();
        if (!species.canEvolveAt(pokemon.getLevel())) {
            return null;
        }
        return gameData.getSpecies(species.getEvolutionTarget()).orElse(null);
    }

    /**
     * 完全恢复指定宝可梦的 HP 与异常状态。
     *
     * @param pokemon 目标宝可梦
     */
    @Override
    public void healPokemon(Pokemon pokemon) {
        pokemon.fullHeal();
    }

    /**
     * 完全恢复当前玩家队伍中所有宝可梦。
     */
    @Override
    public void healParty() {
        if (currentPlayer != null) {
            currentPlayer.healParty();
        }
    }

    /**
     * 获取全部性格列表。
     *
     * @return 全部性格
     */
    @Override
    public List<Nature> getAllNatures() {
        return gameData.getAllNatures();
    }

    /**
     * 获取默认性格（勤奋 HARDY）。
     *
     * @return 默认性格
     */
    @Override
    public Nature getDefaultNature() {
        return Nature.HARDY;
    }

    /**
     * 设置宝可梦的性格。暂不实现（Pokemon 的性格字段不可变）。
     *
     * @param pokemon 目标宝可梦
     * @param nature 新性格
     */
    @Override
    public void setNature(Pokemon pokemon, Nature nature) {
        // TODO: 暂不实现，Pokemon 的性格字段不可变
    }

    /**
     * 获取指定性格对指定能力项的修正倍率。
     *
     * @param nature 性格
     * @param stat 能力项
     * @return 修正倍率（1.1 / 0.9 / 1.0）
     */
    @Override
    public double getNatureModifier(Nature nature, StatModifier stat) {
        return nature.getModifier(stat);
    }

    /**
     * 导出当前游戏状态为存档对象。
     *
     * @return 存档状态
     */
    @Override
    public PokemonState exportState() {
        return new PokemonState(currentPlayer, System.currentTimeMillis());
    }

    /**
     * 从存档对象导入并恢复游戏状态。
     *
     * @param state 存档状态
     */
    @Override
    public void importState(PokemonState state) {
        currentPlayer = state.getPlayer();
    }
}
