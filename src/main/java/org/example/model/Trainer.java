package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 训练师：轮战（连续对战）中的敌方阵营。
 *
 * <p>与野生遭遇不同，训练师持有多只精灵、按顺序逐一派出（当前出战见
 * {@link #getActive()}）。规则（由战斗引擎强制执行）：
 * <ul>
 *   <li>当前出战精灵倒下且队伍仍有健康精灵时，自动派下一只（训练师不会主动换宠）；</li>
 *   <li>战斗直到某一方精灵全部倒下才结束；</li>
 *   <li>不能逃跑、不能捕捉训练师的精灵。</li>
 * </ul></p>
 *
 * <p>本类仅描述「对方队伍」，不含背包等玩家资源；与 {@link Player} 的队伍管理保持同一套
 * 契约（满 6 只、首个加入者自动成为出战精灵、倒下后自动切换下一只健康精灵）。</p>
 */
public class Trainer {

    /** 队伍上限。 */
    public static final int MAX_PARTY = 6;

    private final String name;
    private final List<Pokemon> party = new ArrayList<>();
    private int activeIndex = 0;

    /** 击倒该训练师宝可梦时的经验倍率（分子 / 分母），默认 1/1（1 倍）。 */
    private int expNumerator = 1;
    private int expDenominator = 1;

    public Trainer(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getName() {
        return name;
    }

    /**
     * 设置击倒该训练师宝可梦时的经验倍率（分子 / 分母，如 6/5 表示 1.2 倍）。
     * 战斗引擎在每只对手倒下时把倍率随击倒申报交给成长模块。
     */
    public void setExpMultiplier(int numerator, int denominator) {
        if (numerator <= 0 || denominator <= 0) {
            throw new IllegalArgumentException(
                    "经验倍率必须为正：" + numerator + "/" + denominator);
        }
        this.expNumerator = numerator;
        this.expDenominator = denominator;
    }

    /** 击倒经验倍率的分子（未设置时为 1）。 */
    public int getExpNumerator() {
        return expNumerator;
    }

    /** 击倒经验倍率的分母（未设置时为 1）。 */
    public int getExpDenominator() {
        return expDenominator;
    }

    public List<Pokemon> getParty() {
        return Collections.unmodifiableList(party);
    }

    /** 当前派出战斗的精灵；队伍为空时返回 {@code null}。 */
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

    /** 加入一只精灵（§2.12 同款契约）：仅当队伍未满时生效；首个加入者自动成为出战精灵。 */
    public boolean addPokemon(Pokemon pokemon) {
        if (pokemon == null || party.size() >= MAX_PARTY) {
            return false;
        }
        party.add(pokemon);
        if (activeIndex == -1) {
            activeIndex = 0;
        }
        return true;
    }

    /** 队伍是否已满。 */
    public boolean isPartyFull() {
        return party.size() >= MAX_PARTY;
    }

    /** 队伍当前数量。 */
    public int getPartySize() {
        return party.size();
    }

    /** 当前出战下标（无则 -1）。 */
    public int getActiveIndex() {
        return activeIndex;
    }

    /** 是否还存在未倒下的精灵。 */
    public boolean hasHealthyPokemon() {
        return party.stream().anyMatch(p -> !p.isFainted());
    }

    /** 当前未倒下的精灵列表。 */
    public List<Pokemon> getHealthyPokemon() {
        List<Pokemon> healthy = new ArrayList<>();
        for (Pokemon p : party) {
            if (!p.isFainted()) {
                healthy.add(p);
            }
        }
        return healthy;
    }

    /** 队伍是否全部倒下。 */
    public boolean isPartyAllFainted() {
        return party.stream().allMatch(Pokemon::isFainted);
    }

    /** 切换到指定下标的精灵（须健康且下标合法）。 */
    public boolean switchActive(int index) {
        if (index < 0 || index >= party.size()) {
            return false;
        }
        if (party.get(index).isFainted()) {
            return false;
        }
        this.activeIndex = index;
        return true;
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
