package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 训练家，拥有姓名和最多 6 只宝可梦的队伍。
 */
public class Player implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 队伍容量上限。 */
    private static final int MAX_PARTY_SIZE = 6;

    private final String name;
    private final Bag bag = new Bag();
    private final List<Pokemon> party;
    private int activeIndex;

    public Player(String name) {
        this.name = name;
        this.party = new ArrayList<>();
        this.activeIndex = -1;
    }

    public String getName() {
        return name;
    }

    public Bag getBag() {
        return bag;
    }

    public void setActive(int index) {
        if (index >= 0 && index < party.size()) {
            activeIndex = index;
        }
    }

    /**
     * 返回队伍列表的副本。
     */
    public List<Pokemon> getParty() {
        return new ArrayList<>(party);
    }

    /**
     * 返回当前出战宝可梦。
     *
     * @return 出战精灵存在且未濒死时返回其包装，否则为空
     */
    public Optional<Pokemon> getActive() {
        if (activeIndex >= 0 && activeIndex < party.size()) {
            Pokemon active = party.get(activeIndex);
            if (!active.isFainted()) {
                return Optional.of(active);
            }
        }
        return Optional.empty();
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    /**
     * 切换出战宝可梦。索引无效或对应精灵已濒死时失败。
     *
     * @param index 目标出战索引
     * @return 切换成功返回 true，否则返回 false
     */
    public boolean switchActive(int index) {
        if (index < 0 || index >= party.size()) {
            return false;
        }
        if (party.get(index).isFainted()) {
            return false;
        }
        activeIndex = index;
        return true;
    }

    /**
     * 向队伍添加宝可梦。队伍已满时失败；添加成功后若无出战精灵则自动设为出战。
     *
     * @param pokemon 待添加宝可梦
     * @return 添加成功返回 true，否则返回 false
     */
    public boolean addPokemon(Pokemon pokemon) {
        if (pokemon == null || party.size() >= MAX_PARTY_SIZE) {
            return false;
        }
        party.add(pokemon);
        if (activeIndex == -1) {
            activeIndex = 0;
        }
        return true;
    }

    /** 兼容旧版 API：直接向队伍追加一只宝可梦。 */
    public boolean addToParty(Pokemon pokemon) {
        return addPokemon(pokemon);
    }

    /** 兼容旧版 API：返回当前出战宝可梦。 */
    public Pokemon getActivePokemon() {
        return getActive().orElse(null);
    }

    /** 兼容旧版 API：切换到指定队伍索引的宝可梦。 */
    public Pokemon switchTo(int index) {
        if (index >= 0 && index < party.size() && !party.get(index).isFainted()) {
            activeIndex = index;
            return party.get(index);
        }
        return null;
    }

    /** 兼容旧版 API：切换到下一只健康的宝可梦。 */
    public Pokemon switchToNextHealthy() {
        int size = party.size();
        for (int i = 0; i < size; i++) {
            int idx = (activeIndex + 1 + i) % size;
            Pokemon candidate = party.get(idx);
            if (!candidate.isFainted()) {
                activeIndex = idx;
                return candidate;
            }
        }
        return null;
    }

    public boolean isPartyFull() {
        return party.size() >= MAX_PARTY_SIZE;
    }

    public int getPartySize() {
        return party.size();
    }

    /**
     * 判断队伍中是否存在未濒死的宝可梦。
     */
    public boolean hasHealthyPokemon() {
        for (Pokemon pokemon : party) {
            if (!pokemon.isFainted()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回队伍中所有未濒死宝可梦的列表副本。
     */
    public List<Pokemon> getHealthyPokemon() {
        List<Pokemon> healthy = new ArrayList<>();
        for (Pokemon pokemon : party) {
            if (!pokemon.isFainted()) {
                healthy.add(pokemon);
            }
        }
        return healthy;
    }

    /**
     * 完全恢复队伍中所有宝可梦。
     */
    public void healParty() {
        for (Pokemon pokemon : party) {
            pokemon.fullHeal();
        }
    }

    /**
     * 判断队伍是否全员濒死。
     */
    public boolean isPartyAllFainted() {
        for (Pokemon pokemon : party) {
            if (!pokemon.isFainted()) {
                return false;
            }
        }
        return true;
    }
}
