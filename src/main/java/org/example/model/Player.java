package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 玩家：持有背包与精灵队伍。
 * <p>战斗只派出一只精灵（{@link #getActive()}），但对战中倒下且队伍仍有健康精灵时
 * 会自动切换下一只继续（由引擎处理）。</p>
 */
public class Player {

    private final String name;
    private final Bag bag = new Bag();
    private final List<Pokemon> party = new ArrayList<>();
    private int activeIndex = 0;

    public Player(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getName() {
        return name;
    }

    public Bag getBag() {
        return bag;
    }

    public List<Pokemon> getParty() {
        return Collections.unmodifiableList(party);
    }

    /** 当前派出战斗的精灵。 */
    public Pokemon getActive() {
        if (party.isEmpty()) {
            return null;
        }
        return party.get(Math.min(activeIndex, party.size() - 1));
    }

    public void setActive(int index) {
        if (index >= 0 && index < party.size()) {
            this.activeIndex = index;
        }
    }

    public void addToParty(Pokemon pokemon) {
        party.add(Objects.requireNonNull(pokemon));
    }

    /** 是否还存在未倒下的精灵。 */
    public boolean hasHealthyPokemon() {
        return party.stream().anyMatch(p -> !p.isFainted());
    }

    /** 切换到队伍中指定编号的健康精灵；成功返回新出战精灵，失败返回 null。 */
    public Pokemon switchTo(int index) {
        if (index >= 0 && index < party.size() && !party.get(index).isFainted()) {
            activeIndex = index;
            return party.get(index);
        }
        return null;
    }

    /** 切换到下一只健康的精灵（无则返回 null，不改变状态）。 */
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

    /** 恢复整支队伍（HP 回满 + PP 补满）。 */
    public void healParty() {
        for (Pokemon p : party) {
            p.fullRestore();
        }
    }

    /** 将出战精灵设为第一只健康的精灵；全倒则保持原状。 */
    public void leadWithFirstHealthy() {
        if (party.isEmpty()) {
            return;
        }
        for (int i = 0; i < party.size(); i++) {
            if (!party.get(i).isFainted()) {
                activeIndex = i;
                return;
            }
        }
    }
}
