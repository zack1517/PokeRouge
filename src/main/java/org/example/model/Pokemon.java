package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 精灵个体（等级 + 种族 + 技能槽 + 当前/最大 HP + 经验）。
 * <p>HP 上限与六项实际属性由种族值与等级演算而来，演算规则集中在 {@link #computeStats}。
 * 个体可以积累经验升级（属性随之提升），达到条件时进化并更换种族。</p>
 * <p>契约补充（接口文档 v1.0 §2.11）：提供 uuid/IV/性格/异常状态 字段与读取方法。
 * 现有个体由 {@link #create} 创建时以随机 IV + 勤奋性格初始化；异常状态当前仅作为
 * 状态字段维护，尚未接入战斗结算。</p>
 */
public class Pokemon {

    /** 最高等级。 */
    public static final int MAX_LEVEL = 100;
    /** 技能槽上限。 */
    public static final int MAX_MOVES = 4;

    private final String uuid;
    private Species species;
    private int level;
    private Stats stats;
    private Stats ivs;
    private Nature nature;
    private int maxHp;
    private final List<MoveSlot> moveSlots;
    private int currentHp;
    /** 异常状态（当前仅维护字段，战斗结算未接入）。 */
    private StatusCondition status;
    /** 当前等级内累积的经验（跨级归零后进入下一级）。 */
    private long exp;

    private Pokemon(Species species, int level, Stats stats, int maxHp, List<MoveSlot> slots) {
        this.uuid = UUID.randomUUID().toString();
        this.species = species;
        this.level = level;
        this.stats = stats;
        this.ivs = Stats.randomIv();
        this.nature = Nature.HARDY;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.moveSlots = slots;
        this.status = StatusCondition.NONE;
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

    /** 契约补充：个体唯一标识（§2.11）。 */
    public String getUuid() {
        return uuid;
    }

    /** 契约补充：个体值（0~31，创建时随机，§2.11）。 */
    public Stats getIvs() {
        return ivs;
    }

    /** 契约补充：性格（默认勤奋，§2.11）。 */
    public Nature getNature() {
        return nature;
    }

    /** 契约补充：当前异常状态（§2.11）。 */
    public StatusCondition getStatus() {
        return status;
    }

    /** 供战斗系统按需设置异常状态（当前战斗结算尚未消费该状态）。 */
    public void setStatus(StatusCondition status) {
        this.status = status == null ? StatusCondition.NONE : status;
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

    /** 契约补充：仅回满 HP 并清除异常状态（§2.11 fullHeal）。 */
    public void fullHeal() {
        currentHp = maxHp;
        status = StatusCondition.NONE;
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

    /** 契约补充：增加经验并结算升级；升级后按契约 §2.11 回满 HP（学习/进化由调用方处理）。 */
    public void gainExp(int amount) {
        int gained = addExp(amount);
        if (gained > 0) {
            currentHp = maxHp;
        }
    }

    /** 该技能是否已学会（返回被替换时配合判断，这里用于“已学会”场景）。 */
    public boolean hasMove(Move move) {
        if (move == null) {
            return false;
        }
        return hasMove(move.getId());
    }

    /** 契约补充：按技能 id 判断是否已学会（§2.11）。 */
    public boolean hasMove(String moveId) {
        if (moveId == null) {
            return false;
        }
        for (MoveSlot slot : moveSlots) {
            if (slot.getMove().getId().equals(moveId)) {
                return true;
            }
        }
        return false;
    }

    /** 契约补充：当前等级下能否习得指定技能（需在种族习得表且已到等级，§2.11）。 */
    public boolean canLearnMove(Move move) {
        if (move == null) {
            return false;
        }
        for (LearnableMove lm : species.getLearnableMoves()) {
            if (lm.getMoveId().equals(move.getId()) && lm.getLevel() <= level) {
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
