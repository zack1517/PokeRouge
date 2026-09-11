package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 精灵个体（等级 + 种族 + 技能槽 + 当前/最大 HP + 经验）。
 * <p>HP 上限与六项实际属性由种族值、<b>个体值</b>与等级演算而来，演算规则集中在 {@link #computeStats}。
 * 个体可以积累经验升级（属性随之提升），达到条件时进化并更换种族。</p>
 * <p>契约补充（接口文档 v1.0 §2.11）：提供 uuid/IV/性格/异常状态 字段与读取方法。
 * 现有个体由 {@link #create} 创建时以随机 IV + 勤奋性格初始化；个体值参与属性演算，
 * 由局外成长机制提升的个体值会真实反映到面板上（种族值不变）。异常状态（主要异常 + 混乱）
 * 由战斗引擎按 {@link StatusCondition} 规则施加与结算，见 {@code BattleEngine}。</p>
 */
public class Pokemon {

    /** 最高等级。 */
    public static final int MAX_LEVEL = 100;
    /** 技能槽上限。 */
    public static final int MAX_MOVES = 4;

    /** 个体值缺省值（全 0）：属性演算时等价于不叠加个体值。 */
    private static final Stats ZERO_IV = new Stats(0, 0, 0, 0, 0, 0);

    private final String uuid;
    private Species species;
    private int level;
    private Stats stats;
    private Stats ivs;
    private Nature nature;
    private int maxHp;
    private final List<MoveSlot> moveSlots;
    private int currentHp;
    /** 异常状态（主要异常；混乱为挥发性状态，另见 {@link #confusionTurns}）。 */
    private StatusCondition status;
    /** 睡眠剩余回合数（非睡眠状态为 0）；回合末/行动前递减，归零即醒来。 */
    private int sleepTurns;
    /** 剧毒计数（自 1 起逐回合递增；非剧毒为 0），决定回合末扣血比例 n/16。 */
    private int badlyPoisonCounter;
    /** 混乱剩余回合数（0 表示未混乱），换宠或倒下即清零。 */
    private int confusionTurns;
    /** 当前等级内累积的经验（跨级归零后进入下一级）。 */
    private long exp;
    /**
     * 携带装备（未穿戴为 {@code null}）。
     * <p>装备不是战斗引擎的数据：由 UI/奖励流程从外部设置（{@link #setHeldItem}），
     * 引擎只读取（{@link #getHeldItem}）并按 {@link HeldItemEffect} 应用效果；
     * 同一件装备同时只能被一只精灵持有，穿戴唯一性由 {@link Player#equip} 保证。</p>
     */
    private HeldItem heldItem;
    /**
     * 能力等级（-6 ~ +6），由战斗引擎按 {@link StatChange} 施加。
     * <p>这是<b>挥发性</b>状态：离场（换宠、倒下）或战斗开始即清零，因此不写入存档，
     * {@link #restore} 恢复的个体一律从中立等级开始。</p>
     */
    private final Map<Stat, Integer> statStages = new EnumMap<>(Stat.class);

    private Pokemon(Species species, int level, Stats stats, Stats ivs, int maxHp, List<MoveSlot> slots) {
        this.uuid = UUID.randomUUID().toString();
        this.species = species;
        this.level = level;
        this.stats = stats;
        this.ivs = ivs;
        this.nature = Nature.HARDY;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.moveSlots = slots;
        this.status = StatusCondition.NONE;
    }

    /** 依据种族、等级创建满血个体（个体值随机 0~31）。 */
    public static Pokemon create(Species species, int level, List<Move> movePool) {
        return create(species, level, movePool, Stats.randomIv());
    }

    /**
     * 依据种族、等级与指定个体值创建满血个体。
     *
     * <p>个体值参与属性演算（见 {@link #computeStats}），因此由外部成长机制提升的个体值
     * 会真实反映到战斗面板上；基种族值不变。</p>
     *
     * @param species  种族
     * @param level    等级
     * @param movePool 技能池（全部装入技能槽）
     * @param ivs      个体值（0~31，六项）
     */
    public static Pokemon create(Species species, int level, List<Move> movePool, Stats ivs) {
        Stats actual = computeStats(species, level, ivs);
        List<MoveSlot> slots = new ArrayList<>();
        for (Move move : movePool) {
            slots.add(new MoveSlot(move));
        }
        return new Pokemon(species, level, actual, ivs, actual.getHp(), slots);
    }

    /**
     * 读档还原：以存档中记录的原始内部状态构造个体，不经过任何带副作用的 setter。
     *
     * <p>与 {@link #create} 的区别：{@code create} 只产出「满血、无异常、PP 全满」的新个体，
     * 而本方法用于把存档里的中途状态（残血、异常状态与各计数、技能剩余 PP、当前经验）
     * 精确还原。属性仍按 {@link #computeStats} 由种族/等级/个体值现算，因此个体值成长加成
     * 在读档后依然生效。</p>
     *
     * @param species            种族
     * @param level              等级
     * @param ivs                个体值（0~31，六项）
     * @param slots              技能槽（含各自剩余 PP），{@code null} 视为无技能
     * @param exp                当前等级内累积的经验
     * @param status             主要异常状态，{@code null} 视为无异常
     * @param sleepTurns         睡眠剩余回合数
     * @param badlyPoisonCounter 剧毒计数
     * @param confusionTurns     混乱剩余回合数
     * @param currentHp          当前 HP（裁剪到 {@code [0, 上限]}）
     */
    public static Pokemon restore(Species species, int level, Stats ivs, List<MoveSlot> slots,
                                  long exp, StatusCondition status, int sleepTurns,
                                  int badlyPoisonCounter, int confusionTurns, int currentHp) {
        Stats actual = computeStats(species, level, ivs);
        Pokemon pokemon = new Pokemon(species, level, actual, ivs, actual.getHp(),
                slots == null ? new ArrayList<>() : new ArrayList<>(slots));
        pokemon.exp = Math.max(0, exp);
        pokemon.status = status == null ? StatusCondition.NONE : status;
        pokemon.sleepTurns = Math.max(0, sleepTurns);
        pokemon.badlyPoisonCounter = Math.max(0, badlyPoisonCounter);
        pokemon.confusionTurns = Math.max(0, confusionTurns);
        pokemon.currentHp = Math.max(0, Math.min(actual.getHp(), currentHp));
        return pokemon;
    }

    /**
     * 由种族、等级与个体值演算六项实际属性（生命即 HP 上限）。
     *
     * <p>采用标准公式：HP = (2×种族值 + 个体值) × 等级 / 100 + 等级 + 10，其余五项为
     * (2×种族值 + 个体值) × 等级 / 100 + 5。个体值全 0 时与旧公式等价。</p>
     */
    private static Stats computeStats(Species species, int level, Stats ivs) {
        Stats base = species.getBaseStats();
        Stats iv = ivs == null ? ZERO_IV : ivs;
        int hp = ((base.getHp() * 2 + iv.getHp()) * level) / 100 + level + 10;
        int atk = ((base.getAttack() * 2 + iv.getAttack()) * level) / 100 + 5;
        int def = ((base.getDefense() * 2 + iv.getDefense()) * level) / 100 + 5;
        int spa = ((base.getSpAttack() * 2 + iv.getSpAttack()) * level) / 100 + 5;
        int spd = ((base.getSpDefense() * 2 + iv.getSpDefense()) * level) / 100 + 5;
        int spe = ((base.getSpeed() * 2 + iv.getSpeed()) * level) / 100 + 5;
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

    /** 当前主要异常状态（未陷入主要异常时为 {@link StatusCondition#NONE}；混乱见 {@link #isConfused()}）。 */
    public StatusCondition getStatus() {
        return status;
    }

    /**
     * 直接设置主要异常状态（内部按状态初始化计数：睡眠取最短回合、剧毒从 1 层起算）。
     * <p>战斗中的施加应优先使用 {@link #tryApplyStatus(StatusCondition, int)} 以走属性免疫与互斥判定。</p>
     */
    public void setStatus(StatusCondition status) {
        StatusCondition target = status == null ? StatusCondition.NONE : status;
        if (target == StatusCondition.CONFUSION) {
            confusionTurns = StatusCondition.CONFUSION_MIN_TURNS;
            return;
        }
        this.status = target;
        this.sleepTurns = target == StatusCondition.SLEEP ? StatusCondition.SLEEP_MIN_TURNS : 0;
        this.badlyPoisonCounter = target == StatusCondition.BADLY_POISON
                ? StatusCondition.BADLY_POISON_START : 0;
    }

    /**
     * 按原版规则施加异常状态（属性免疫、已有主要异常不可覆盖、混乱不可叠加）。
     *
     * @param condition 目标异常状态
     * @param turns     持续回合数（睡眠/混乱使用；其他状态忽略）
     * @return 是否成功施加
     */
    public boolean tryApplyStatus(StatusCondition condition, int turns) {
        if (condition == null || !condition.canApply(this)) {
            return false;
        }
        if (condition == StatusCondition.CONFUSION) {
            confusionTurns = Math.max(1, turns);
            return true;
        }
        status = condition;
        sleepTurns = condition == StatusCondition.SLEEP ? Math.max(1, turns) : 0;
        badlyPoisonCounter = condition == StatusCondition.BADLY_POISON
                ? StatusCondition.BADLY_POISON_START : 0;
        return true;
    }

    /** 解除主要异常状态并清理计数；返回被解除的状态（本无异常则为 {@link StatusCondition#NONE}）。 */
    public StatusCondition cureStatus() {
        StatusCondition cured = status;
        status = StatusCondition.NONE;
        sleepTurns = 0;
        badlyPoisonCounter = 0;
        return cured;
    }

    /** 是否处于混乱（挥发性异常）。 */
    public boolean isConfused() {
        return confusionTurns > 0;
    }

    /** 混乱剩余回合数（0 表示未混乱）。 */
    public int getConfusionTurns() {
        return confusionTurns;
    }

    /** 设置混乱持续回合数（供战斗引擎按随机值施加）。 */
    public void applyConfusion(int turns) {
        confusionTurns = Math.max(0, turns);
    }

    /** 清除混乱（换宠、倒下或道具治疗时调用），返回本次是否真的有混乱被清除。 */
    public boolean clearConfusion() {
        boolean had = confusionTurns > 0;
        confusionTurns = 0;
        return had;
    }

    /** 同时清除主要异常与混乱（万灵药等全解道具）。 */
    public void clearAllStatus() {
        cureStatus();
        clearConfusion();
    }

    /** 睡眠剩余回合数（0 表示未睡眠）。 */
    public int getSleepTurns() {
        return sleepTurns;
    }

    /** 剧毒计数（自 1 起逐回合递增；0 表示未中毒/非剧毒）。 */
    public int getBadlyPoisonCounter() {
        return badlyPoisonCounter;
    }

    /** 推进剧毒计数（回合末结算后调用），返回新的计数。 */
    public int increaseBadlyPoisonCounter() {
        if (status == StatusCondition.BADLY_POISON) {
            badlyPoisonCounter++;
        }
        return badlyPoisonCounter;
    }

    /**
     * 行动前推进睡眠：仍睡则返回 {@code true}（本回合无法行动）；刚好睡满则醒来并返回 {@code false}。
     */
    public boolean tickSleep() {
        if (status != StatusCondition.SLEEP) {
            return false;
        }
        sleepTurns--;
        if (sleepTurns > 0) {
            return true;
        }
        cureStatus();
        return false;
    }

    /** 回合末推进混乱回合数；返回本次是否因回合耗尽而解除混乱。 */
    public boolean tickConfusion() {
        if (confusionTurns <= 0) {
            return false;
        }
        confusionTurns--;
        return confusionTurns <= 0;
    }

    /** 计入能力等级与异常状态后的实际速度（麻痹减半，最低 1）。 */
    public int effectiveSpeed() {
        return applyModifiers(stats.getSpeed(), Stat.SPEED, status.speedMultiplier());
    }

    /** 计入能力等级与异常状态后的实际物理攻击（灼伤减半，最低 1）。 */
    public int effectiveAttack() {
        return applyModifiers(stats.getAttack(), Stat.ATTACK, status.attackMultiplier());
    }

    /** 计入能力等级后的实际物理防御（最低 1）。 */
    public int effectiveDefense() {
        return applyModifiers(stats.getDefense(), Stat.DEFENSE, 1.0);
    }

    /** 计入能力等级后的实际特殊攻击（最低 1）。 */
    public int effectiveSpAttack() {
        return applyModifiers(stats.getSpAttack(), Stat.SP_ATTACK, 1.0);
    }

    /** 计入能力等级后的实际特殊防御（最低 1）。 */
    public int effectiveSpDefense() {
        return applyModifiers(stats.getSpDefense(), Stat.SP_DEFENSE, 1.0);
    }

    /** 能力等级的最大值。 */
    public static final int MAX_STAT_STAGE = 6;

    /** 能力等级的最小值。 */
    public static final int MIN_STAT_STAGE = -6;

    /**
     * 计算某项能力的实际数值：{@code 面板值 × 能力等级倍率 × 异常状态倍率}，下限 1。
     *
     * @param base           面板值（{@link #getStats()} 中的对应项）
     * @param stat           能力项（决定能力等级倍率）
     * @param statusMultiplier 异常状态倍率（无影响为 {@code 1.0}）
     */
    private int applyModifiers(int base, Stat stat, double statusMultiplier) {
        double value = base * stageMultiplier(getStatStage(stat)) * statusMultiplier;
        return Math.max(1, (int) Math.round(value));
    }

    /**
     * 能力等级 {@code -6 ~ +6} 对应的数值倍率：正等级为 {@code (2 + n) / 2}，
     * 负等级为 {@code 2 / (2 - n)}（即 0 级 1.0、+2 级 2.0、-2 级 0.5，与正作一致）。
     */
    public static double stageMultiplier(int stage) {
        int clamped = Math.max(MIN_STAT_STAGE, Math.min(MAX_STAT_STAGE, stage));
        return clamped >= 0 ? (2.0 + clamped) / 2.0 : 2.0 / (2.0 - clamped);
    }

    /** 当前能力等级（未变化为 0）。 */
    public int getStatStage(Stat stat) {
        return stat == null ? 0 : statStages.getOrDefault(stat, 0);
    }

    /**
     * 增减能力等级，结果裁剪到 {@code [-6, +6]}。
     *
     * @return 本次<b>实际</b>变化量（已受上下限裁剪；例如已 +6 时再 +1 返回 0）
     */
    public int changeStatStage(Stat stat, int delta) {
        if (stat == null || delta == 0) {
            return 0;
        }
        int before = getStatStage(stat);
        int after = Math.max(MIN_STAT_STAGE, Math.min(MAX_STAT_STAGE, before + delta));
        statStages.put(stat, after);
        return after - before;
    }

    /** 是否所有能力等级均为 0（中立）。 */
    public boolean hasNoStatStages() {
        return statStages.isEmpty();
    }

    /** 清空全部能力等级（离场/倒下/战斗开始时调用）。 */
    public void clearStatStages() {
        statStages.clear();
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

    /** 当前携带的装备（未穿戴为 {@code null}）。 */
    public HeldItem getHeldItem() {
        return heldItem;
    }

    /** 设置携带装备（{@code null} 表示脱下）。装备数据由外部注入，本方法不做唯一性校验。 */
    public void setHeldItem(HeldItem item) {
        this.heldItem = item;
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

    /** 升级后按当前种族与个体值重算属性，并把新增长的上限 HP 补到当前 HP。 */
    private void applyStatsOnLevelUp() {
        stats = computeStats(species, level, ivs);
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

    /** 承受伤害，返回实际扣除量（不低于剩余 HP）；倒下时清除混乱（挥发性状态）。 */
    public int takeDamage(int amount) {
        int real = Math.min(currentHp, Math.max(0, amount));
        currentHp -= real;
        if (currentHp <= 0) {
            confusionTurns = 0;
        }
        return real;
    }

    /** 回复 HP，返回实际回复量。 */
    public int heal(int amount) {
        int real = Math.min(maxHp - currentHp, Math.max(0, amount));
        currentHp += real;
        return real;
    }

    /** 完全恢复：HP 回满、补满全部技能 PP，并清除异常状态、混乱与能力等级。 */
    public void fullRestore() {
        currentHp = maxHp;
        for (MoveSlot slot : moveSlots) {
            slot.restore(slot.getMove().getMaxPp());
        }
        clearAllStatus();
        clearStatStages();
    }

    /** 契约补充：仅回满 HP 并清除异常状态（§2.11 fullHeal；混乱与能力等级一并清除）。 */
    public void fullHeal() {
        currentHp = maxHp;
        clearAllStatus();
        clearStatStages();
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
