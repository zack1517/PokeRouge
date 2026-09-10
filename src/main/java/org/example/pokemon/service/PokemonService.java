package org.example.pokemon.service;

import java.util.List;
import java.util.Optional;
import org.example.pokemon.domain.GrowthEvent;
import org.example.pokemon.domain.Nature;
import org.example.pokemon.domain.Player;
import org.example.pokemon.domain.Pokemon;
import org.example.pokemon.domain.PokemonState;
import org.example.pokemon.domain.Species;
import org.example.pokemon.domain.StatModifier;

/**
 * 宝可梦游戏核心服务接口，封装玩家、队伍、精灵创建与成长等业务操作。
 */
public interface PokemonService {

    /**
     * 获取初始可选宝可梦池。
     *
     * @return 初始可选种族列表
     */
    List<Species> getInitialPool();

    /**
     * 获取当前玩家。
     *
     * @return 当前玩家，尚未创建时为 null
     */
    Player getPlayer();

    /**
     * 创建新玩家并设为当前玩家。
     *
     * @param name 玩家姓名
     * @return 新创建的玩家
     */
    Player createPlayer(String name);

    /**
     * 重置当前玩家（置为 null）。
     */
    void resetPlayer();

    /**
     * 向当前玩家队伍添加宝可梦。
     *
     * @param pokemon 待添加宝可梦
     * @return 添加成功返回 true，否则返回 false
     */
    boolean addPokemonToParty(Pokemon pokemon);

    /**
     * 切换当前玩家的出战宝可梦。
     *
     * @param index 目标出战索引
     * @return 切换成功返回 true，否则返回 false
     */
    boolean switchActivePokemon(int index);

    /**
     * 获取当前玩家的出战宝可梦。
     *
     * @return 出战宝可梦包装，不存在时为空
     */
    Optional<Pokemon> getActivePokemon();

    /**
     * 获取当前玩家队伍中所有未濒死的宝可梦。
     *
     * @return 未濒死宝可梦列表
     */
    List<Pokemon> getHealthyPokemon();

    /**
     * 判断当前玩家队伍是否全员濒死。
     *
     * @return 全员濒死返回 true，否则返回 false
     */
    boolean isPartyAllFainted();

    /**
     * 按种族 id 创建宝可梦，个体值随机、性格为默认性格。
     *
     * @param speciesId 种族 id
     * @param level 初始等级
     * @return 新创建的宝可梦
     */
    Pokemon createPokemon(String speciesId, int level);

    /**
     * 按种族 id 与指定性格创建宝可梦，个体值随机。
     *
     * @param speciesId 种族 id
     * @param level 初始等级
     * @param nature 指定性格
     * @return 新创建的宝可梦
     */
    Pokemon createPokemon(String speciesId, int level, Nature nature);

    /**
     * 创建野生宝可梦：等级在目标等级 ±2 内随机（最低 1 级），性格随机。
     *
     * @param speciesId 种族 id
     * @param aroundLevel 目标等级
     * @return 新创建的野生宝可梦
     */
    Pokemon createWildPokemon(String speciesId, int aroundLevel);

    /**
     * 为宝可梦增加经验，返回升级、学招、进化等成长事件列表。
     *
     * @param pokemon 目标宝可梦
     * @param exp 获得的经验值
     * @return 成长事件列表
     */
    List<GrowthEvent> gainExp(Pokemon pokemon, int exp);

    /**
     * 判断宝可梦当前是否满足进化条件。
     *
     * @param pokemon 目标宝可梦
     * @return 可进化返回 true，否则返回 false
     */
    boolean canEvolve(Pokemon pokemon);

    /**
     * 返回宝可梦的进化目标种族。具体替换由调用方处理。
     *
     * @param pokemon 目标宝可梦
     * @return 进化目标种族，不满足进化条件或目标不存在时为 null
     */
    Species evolve(Pokemon pokemon);

    /**
     * 完全恢复指定宝可梦的 HP 与异常状态。
     *
     * @param pokemon 目标宝可梦
     */
    void healPokemon(Pokemon pokemon);

    /**
     * 完全恢复当前玩家队伍中所有宝可梦。
     */
    void healParty();

    /**
     * 获取全部性格列表。
     *
     * @return 全部性格
     */
    List<Nature> getAllNatures();

    /**
     * 获取默认性格（勤奋 HARDY）。
     *
     * @return 默认性格
     */
    Nature getDefaultNature();

    /**
     * 设置宝可梦的性格。暂不实现（Pokemon 的性格字段不可变）。
     *
     * @param pokemon 目标宝可梦
     * @param nature 新性格
     */
    void setNature(Pokemon pokemon, Nature nature);

    /**
     * 获取指定性格对指定能力项的修正倍率。
     *
     * @param nature 性格
     * @param stat 能力项
     * @return 修正倍率（1.1 / 0.9 / 1.0）
     */
    double getNatureModifier(Nature nature, StatModifier stat);

    /**
     * 导出当前游戏状态为存档对象。
     *
     * @return 存档状态
     */
    PokemonState exportState();

    /**
     * 从存档对象导入并恢复游戏状态。
     *
     * @param state 存档状态
     */
    void importState(PokemonState state);
}
