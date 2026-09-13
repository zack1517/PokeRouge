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
    /** 装备库：玩家拥有的全部可携带装备（含已穿戴的；一件装备全库唯一）。 */
    private final List<HeldItem> equipment = new ArrayList<>();
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

    /** 装备库（不可变快照，含已穿戴的装备）。 */
    public List<HeldItem> getEquipment() {
        return Collections.unmodifiableList(equipment);
    }

    /**
     * 装备入库（去重）。
     *
     * @return 是否新增（已拥有返回 {@code false}）
     */
    public boolean addEquipment(HeldItem item) {
        if (item == null || equipment.contains(item)) {
            return false;
        }
        equipment.add(item);
        return true;
    }

    /**
     * 把装备穿到指定精灵身上：先自动从其他精灵脱下（保证全队唯一穿戴），再设置给目标。
     *
     * @param target 目标精灵（可为已倒下精灵，装备在局内仍生效）
     * @return 是否穿戴成功（装备必须在装备库中）
     */
    public boolean equip(Pokemon target, HeldItem item) {
        if (target == null || item == null || !equipment.contains(item)) {
            return false;
        }
        for (Pokemon p : party) {
            if (p.getHeldItem() == item) {
                p.setHeldItem(null);
            }
        }
        target.setHeldItem(item);
        return true;
    }

    /** 从指定精灵脱下装备（装备仍在装备库中，可穿给其他精灵）。 */
    public boolean unequip(Pokemon target) {
        if (target == null || target.getHeldItem() == null) {
            return false;
        }
        target.setHeldItem(null);
        return true;
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
        addPokemon(Objects.requireNonNull(pokemon));
    }

    /** 契约补充：队伍是否已满（6 只）。 */
    public boolean isPartyFull() {
        return party.size() >= 6;
    }

    /** 契约补充：队伍当前数量。 */
    public int getPartySize() {
        return party.size();
    }

    /** 契约补充：当前出战下标（无则 -1）。 */
    public int getActiveIndex() {
        return activeIndex;
    }

    /**
     * 契约补充：加入一只精灵（§2.12）。仅当队伍未满时生效；首个加入者自动成为出战精灵。
     *
     * @return 是否成功加入
     */
    public boolean addPokemon(Pokemon pokemon) {
        if (pokemon == null || party.size() >= 6) {
            return false;
        }
        party.add(pokemon);
        if (activeIndex == -1) {
            activeIndex = 0;
        }
        return true;
    }

    /**
     * 放生队内精灵（移除出队伍；装备由调用方先用 {@link #unequip} 脱下返还装备库）。
     *
     * <p>出战下标修正：放生的不是出战精灵时出战下标随前移；放生的是出战精灵时
     * 出战下标指向其后一只（队空则为 -1，之后新精灵入队会自动成为出战）。</p>
     *
     * @return 是否移除成功（精灵不在队内返回 {@code false}）
     */
    public boolean removePokemon(Pokemon pokemon) {
        int idx = party.indexOf(pokemon);
        if (idx < 0) {
            return false;
        }
        party.remove(idx);
        if (activeIndex > idx) {
            activeIndex--;
        } else if (activeIndex == idx) {
            activeIndex = party.isEmpty() ? -1 : Math.min(activeIndex, party.size() - 1);
        }
        return true;
    }

    /** 契约补充：切换到指定下标的精灵（须健康且下标合法）。 */
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

    /**
     * 队伍成员交换（宝可梦交换事件）：用新的精灵替换队伍中指定下标的成员，队伍数量不变。
     * 若替换的是当前出战精灵，出战位不变、新成员直接成为出战精灵。
     *
     * @param index       被交换离队的成员下标（越界时失败）
     * @param replacement 入队的新成员（{@code null} 时失败）
     * @return 被替换离开队伍的原成员；失败返回 {@code null}
     */
    public Pokemon swapPartyMember(int index, Pokemon replacement) {
        if (replacement == null || index < 0 || index >= party.size()) {
            return null;
        }
        return party.set(index, replacement);
    }

    /** 是否还存在未倒下的精灵。 */
    public boolean hasHealthyPokemon() {
        return party.stream().anyMatch(p -> !p.isFainted());
    }

    /** 契约补充：当前未倒下的精灵列表。 */
    public List<Pokemon> getHealthyPokemon() {
        List<Pokemon> healthy = new ArrayList<>();
        for (Pokemon p : party) {
            if (!p.isFainted()) {
                healthy.add(p);
            }
        }
        return healthy;
    }

    /** 契约补充：队伍是否全部倒下。 */
    public boolean isPartyAllFainted() {
        return party.stream().allMatch(Pokemon::isFainted);
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
