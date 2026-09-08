package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 精灵个体（等级 + 种族 + 技能槽 + 当前/最大 HP + 经验）。
 * <p>HP 上限与六项实际属性由种族值与等级演算而来，演算规则集中在 {@link #computeStats}。
 * 个体可以积累经验升级（属性随之提升），达到条件时进化并更换种族。</p>
 */
public class Pokemon {

    /** 最高等级。 */
    public static final int MAX_LEVEL = 100;
    /** 技能槽上限。 */
    public static final int MAX_MOVES = 4;

    private Species species;
    private int level;
    private Stats stats;
    private int maxHp;
    private final List<MoveSlot> moveSlots;
    private int currentHp;
    /** 当前等级内累积的经验（跨级归零后进入下一级）。 */
    private long exp;

    private Pokemon(Species species, int level, Stats stats, int maxHp, List<MoveSlot> slots) {
        this.species = species;
        this.level = level;
        this.stats = stats;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.moveSlots = slots;
    }

    /** 依据种族、等级创建满血个体。 */
    public static Pokemon create(Species species, int level, List<Move> movePool) {
        Stats actual = computeStats(species, level);
        List<MoveSlot> slots = new ArrayList<>();
        for (Move move : movePool) {
            slots.add(new MoveSlot(move));
        }
        return new Pokemon(species, level, actual, actual.getHp(), slots);
    }

    /** 由种族与等级演算六项实际属性（生命即 HP 上限）。 */
    private static Stats computeStats(Species species, int level) {
        Stats base = species.getBaseStats();
        int hp = (base.getHp() * 2 * level / 100) + level + 10;
        int atk = (base.getAttack() * 2 * level / 100) + 5;
        int def = (base.getDefense() * 2 * level / 100) + 5;
        int spa = (base.getSpAttack() * 2 * level / 100) + 5;
        int spd = (base.getSpDefense() * 2 * level / 100) + 5;
        int spe = (base.getSpeed() * 2 * level / 100) + 5;
        return new Stats(hp, atk, def, spa, spd, spe);
    }

    public Species getSpecies() {
        return species;
    }

    public String getName() {
        return species.getName();
    }

    public int getLevel() {
        return level;
    }

    public Stats getStats() {
        return stats;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public long getExp() {
        return exp;
    }

    /** 从当前等级升到下一级所需经验（满级为 0）。 */
    public long expToNextLevel() {
        if (level >= MAX_LEVEL) {
            return 0;
        }
        long n = level;
        return 3 * n * n + 3 * n + 1;
    }

    /** 增加经验并结算升级；返回提升的等级数（满级后不再累积）。 */
    public int addExp(long amount) {
        if (level >= MAX_LEVEL || amount <= 0) {
            return 0;
        }
        exp += amount;
        int gained = 0;
        while (level < MAX_LEVEL && exp >= expToNextLevel()) {
            exp -= expToNextLevel();
            level++;
            applyStatsOnLevelUp();
            gained++;
        }
        return gained;
    }

    /** 升级后按当前种族重算属性，并把新增长的上限 HP 补到当前 HP。 */
    private void applyStatsOnLevelUp() {
        stats = computeStats(species, level);
        int oldMax = maxHp;
        maxHp = stats.getHp();
        if (currentHp > 0) {
            currentHp += Math.max(0, maxHp - oldMax);
            currentHp = Math.min(currentHp, maxHp);
        }
    }

    public boolean isFainted() {
        return currentHp <= 0;
    }

    /** 承受伤害，返回实际扣除量（不低于剩余 HP）。 */
    public int takeDamage(int amount) {
        int real = Math.min(currentHp, Math.max(0, amount));
        currentHp -= real;
        return real;
    }

    /** 回复 HP，返回实际回复量。 */
    public int heal(int amount) {
        int real = Math.min(maxHp - currentHp, Math.max(0, amount));
        currentHp += real;
        return real;
    }

    /** 完全恢复：HP 回满并补满全部技能 PP。 */
    public void fullRestore() {
        currentHp = maxHp;
        for (MoveSlot slot : moveSlots) {
            slot.restore(slot.getMove().getMaxPp());
        }
    }

    /** 是否满足进化条件（定义了进化目标且达到等级）。 */
    public boolean canEvolve() {
        return species.canEvolveAt(level);
    }

    /** 进化为目标种族：更换种族并按新种族同等级重算属性、满状态；再尝试学习新技能。 */
    public void evolveTo(Species target) {
        if (target == null || target == species) {
            return;
        }
        this.species = target;
        applyStatsOnLevelUp();
        fullRestore();
    }

    /** 技能槽是否已满（已掌握 4 招）。 */
    public boolean moveSlotsFull() {
        return moveSlots.size() >= MAX_MOVES;
    }

    /**
     * 学会一个技能：仅当有空槽且尚未学过该技能时加入，绝不覆盖已有技能。
     *
     * @return 是否成功学会（空槽且未重复）
     */
    public boolean learnMove(Move move) {
        if (move == null || hasMove(move) || moveSlotsFull()) {
            return false;
        }
        moveSlots.add(new MoveSlot(move));
        return true;
    }

    /**
     * 用新技能替换指定槽位的已有技能（技能槽已满时玩家手动选择遗忘哪一招）。
     *
     * @return 被替换遗忘的技能；槽位越界、技能无效或该技能已学会时返回 {@code null}
     */
    public Move replaceMove(int slotIndex, Move move) {
        if (slotIndex < 0 || slotIndex >= moveSlots.size()
                || move == null || hasMove(move)) {
            return null;
        }
        Move forgotten = moveSlots.get(slotIndex).getMove();
        moveSlots.set(slotIndex, new MoveSlot(move));
        return forgotten;
    }

    /** 该技能是否已学会（返回被替换时配合判断，这里用于“已学会”场景）。 */
    public boolean hasMove(Move move) {
        if (move == null) {
            return false;
        }
        for (MoveSlot slot : moveSlots) {
            if (slot.getMove().getId().equals(move.getId())) {
                return true;
            }
        }
        return false;
    }

    public List<MoveSlot> getMoveSlots() {
        return Collections.unmodifiableList(moveSlots);
    }

    public List<Move> getMoves() {
        List<Move> moves = new ArrayList<>();
        for (MoveSlot slot : moveSlots) {
            moves.add(slot.getMove());
        }
        return moves;
    }

    /** 是否拥有某元素属性（用于 STAB 判定）。 */
    public boolean hasType(ElementType type) {
        return species.getTypes().contains(Objects.requireNonNull(type));
    }

    @Override
    public String toString() {
        return getName() + " Lv." + level + " HP " + currentHp + "/" + maxHp + " EXP " + exp;
    }
}
