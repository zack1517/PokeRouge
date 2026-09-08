package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.example.pokemon.util.ExperienceCalculator;

/**
 * 宝可梦个体，包含种族、等级、个体值、性格等运行时状态。
 */
public class Pokemon implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 队伍中最多可携带的技能数。 */
    private static final int MAX_MOVE_SLOTS = 4;

    private final String uuid;
    private final Species species;
    private final String nickname;
    private final Stats ivs;
    private final Nature nature;
    private final List<MoveSlot> moveSlots;

    private int level;
    private int exp;
    private int currentHp;
    private StatusCondition status;

    public Pokemon(Species species, int level, Stats ivs, Nature nature) {
        this.uuid = UUID.randomUUID().toString();
        this.species = species;
        this.nickname = null;
        this.ivs = ivs;
        this.nature = nature == null ? Nature.HARDY : nature;
        this.moveSlots = new ArrayList<>();
        this.level = level;
        this.exp = 0;
        this.status = StatusCondition.NONE;
        this.currentHp = calculateMaxHp();
    }

    /**
     * 计算 HP 能力值：floor((2 * 种族值 + IV) * 等级 / 100) + 等级 + 10。
     */
    private int calculateMaxHp() {
        BaseStats base = species.getBaseStats();
        return (int) Math.floor((2.0 * base.getHp() + ivs.getHpIv()) * level / 100.0) + level + 10;
    }

    /**
     * 计算非 HP 能力值：floor((floor((2 * 种族值 + IV) * 等级 / 100) + 5) * 性格修正)。
     */
    private int calculateStat(int base, int iv, int level, double natureModifier) {
        return (int) Math.floor((Math.floor((2.0 * base + iv) * level / 100.0) + 5) * natureModifier);
    }

    public String getUuid() {
        return uuid;
    }

    public Species getSpecies() {
        return species;
    }

    /**
     * 返回显示名：有昵称时优先返回昵称，否则返回种族名。
     */
    public String getName() {
        return nickname != null ? nickname : species.getName();
    }

    public int getLevel() {
        return level;
    }

    public int getExp() {
        return exp;
    }

    public Stats getIvs() {
        return ivs;
    }

    public Nature getNature() {
        return nature;
    }

    /**
     * 返回技能槽列表（不可变视图）。
     */
    public List<MoveSlot> getMoveSlots() {
        return Collections.unmodifiableList(moveSlots);
    }

    public StatusCondition getStatus() {
        return status;
    }

    public int getMaxHp() {
        return calculateMaxHp();
    }

    public int getCurrentHp() {
        return currentHp;
    }

    /**
     * 判断是否濒死：HP 归零或处于濒死状态。
     */
    public boolean isFainted() {
        return currentHp <= 0 || status == StatusCondition.FAINTED;
    }

    /**
     * 返回升到下一级所需经验。
     */
    public int expToNextLevel() {
        return ExperienceCalculator.expToLevel(level + 1) - exp;
    }

    /**
     * 计算并返回当前六项能力值。
     */
    public Stats getStats() {
        BaseStats base = species.getBaseStats();
        return new Stats(
                calculateMaxHp(),
                calculateStat(base.getAttack(), ivs.getAttackIv(), level, nature.getModifier(StatModifier.ATTACK)),
                calculateStat(base.getDefense(), ivs.getDefenseIv(), level, nature.getModifier(StatModifier.DEFENSE)),
                calculateStat(base.getSpAttack(), ivs.getSpAttackIv(), level, nature.getModifier(StatModifier.SP_ATTACK)),
                calculateStat(base.getSpDefense(), ivs.getSpDefenseIv(), level, nature.getModifier(StatModifier.SP_DEFENSE)),
                calculateStat(base.getSpeed(), ivs.getSpeedIv(), level, nature.getModifier(StatModifier.SPEED)));
    }

    /**
     * 判断该宝可梦是否具有指定属性。
     */
    public boolean hasType(ElementType type) {
        return species.getTypes().contains(type);
    }

    /**
     * 返回当前已装备的技能列表。
     */
    public List<Move> getMoves() {
        return moveSlots.stream().map(MoveSlot::getMove).collect(Collectors.toList());
    }

    /**
     * 受到伤害，HP 归零时进入濒死状态。
     *
     * @param damage 伤害值
     */
    public void takeDamage(int damage) {
        currentHp = Math.max(0, currentHp - damage);
        if (currentHp == 0) {
            status = StatusCondition.FAINTED;
        }
    }

    /**
     * 恢复 HP。濒死状态下无法通过此方法恢复。
     *
     * @param amount 恢复量
     */
    public void heal(int amount) {
        if (isFainted()) {
            return;
        }
        currentHp = Math.min(getMaxHp(), currentHp + amount);
        if (status == StatusCondition.FAINTED) {
            status = StatusCondition.NONE;
        }
    }

    /**
     * 完全恢复 HP 并解除所有异常状态。
     */
    public void fullHeal() {
        currentHp = getMaxHp();
        status = StatusCondition.NONE;
    }

    /**
     * 获得经验，若满足升级条件则连续升级，每次升级回满 HP。
     *
     * @param expGain 获得经验值
     */
    public void gainExp(int expGain) {
        exp += expGain;
        while (exp >= ExperienceCalculator.expToLevel(level + 1)) {
            level++;
            currentHp = getMaxHp();
        }
    }

    /**
     * 判断该宝可梦当前是否满足学习指定技能的条件。
     *
     * @param move 待学习技能
     * @return 可学习返回 true，否则返回 false
     */
    public boolean canLearnMove(Move move) {
        return species.getLearnableMoves().stream()
                .anyMatch(learnable -> learnable.getMoveId().equals(move.getId())
                        && learnable.getLevel() <= level);
    }

    /**
     * 学习技能：未满 4 个技能槽时追加，否则替换最后一个技能槽。
     *
     * @param move 待学习技能
     */
    public void learnMove(Move move) {
        MoveSlot slot = new MoveSlot(move);
        if (moveSlots.size() < MAX_MOVE_SLOTS) {
            moveSlots.add(slot);
        } else {
            moveSlots.set(moveSlots.size() - 1, slot);
        }
    }

    /**
     * 判断是否已学会指定 id 的技能。
     *
     * @param moveId 技能 id
     * @return 已学会返回 true，否则返回 false
     */
    public boolean hasMove(String moveId) {
        for (MoveSlot slot : moveSlots) {
            if (slot.getMove().getId().equals(moveId)) {
                return true;
            }
        }
        return false;
    }
}
