package com.bao01.model;

import com.bao01.config.BattleConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 宝可梦：由种族值 + 努力值 + 等级派生六项能力值。
 *
 * <p>能力值公式（能力=((2×种族值 + 努力值÷4)×等级÷100) 取整；HP 额外 +等级+10，其余 +5）：
 * <ul>
 *   <li>生命 (HP)：决定能承受多少伤害；</li>
 *   <li>物攻 / 物防：物理招式伤害计算项；</li>
 *   <li>特攻 / 特防：特殊招式伤害计算项；</li>
 *   <li>速度：同优先级下决定出手顺序（先手权），也可被招式等级变化影响。</li>
 * </ul>
 *
 * <p>战斗中可变的量：当前 HP（受伤 / 回复 / 濒死）与各能力「等级（阶级）」
 * （受能力变化招式影响，速度等级变化会直接影响出手先后）。
 * 等级变化区间为 [-{@value #STAGE_MAX}, {@value #STAGE_MAX}]。
 */
public final class Pokemon {

    /** 能力等级变化上下限。 */
    public static final int STAGE_MAX = 6;

    private final String name;
    private final PokeType type;
    private final int level;
    /** 种族值：顺序固定为 Stat.values()（HP 物攻 物防 特攻 特防 速度）。 */
    private final int[] baseStats;
    private final EffortValues effortValues;
    private final List<Move> moves;

    private int currentHp;
    private final Map<Stat, Integer> stages = new EnumMap<>(Stat.class);

    /**
     * 构造一只宝可梦。
     *
     * @param name   名称
     * @param type   属性（当前单属性）
     * @param level  等级（1~100）
     * @param base   六项种族值，顺序必须为 Stat.values()
     * @param evs    努力值（可全 0）
     * @param moves  招式 1~4 个
     */
    public Pokemon(String name, PokeType type, int level,
                   int[] base, EffortValues evs, List<Move> moves) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("属性不能为空");
        }
        if (level < BattleConfig.LEVEL_MIN || level > BattleConfig.LEVEL_MAX) {
            throw new IllegalArgumentException("等级必须在 "
                    + BattleConfig.LEVEL_MIN + "~" + BattleConfig.LEVEL_MAX + " 之间: " + level);
        }
        if (base == null || base.length != Stat.values().length) {
            throw new IllegalArgumentException("种族值必须提供全部六项");
        }
        for (int b : base) {
            if (b < 0) {
                throw new IllegalArgumentException("种族值不能为负");
            }
        }
        if (moves == null || moves.isEmpty() || moves.size() > BattleConfig.MAX_MOVES) {
            throw new IllegalArgumentException("招式数量必须在 1~" + BattleConfig.MAX_MOVES + " 之间");
        }
        this.name = name;
        this.type = type;
        this.level = level;
        this.baseStats = Arrays.copyOf(base, base.length);
        this.effortValues = evs == null ? new EffortValues() : evs;
        this.moves = new ArrayList<>(moves);
        for (Stat stat : Stat.values()) {
            stages.put(stat, 0);
        }
        this.currentHp = maxHp();
    }

    /** 便捷构造：无努力值。 */
    public static Pokemon build(String name, PokeType type, int level, int[] base, Move... moves) {
        return new Pokemon(name, type, level, base, new EffortValues(), List.of(moves));
    }

    public String getName() {
        return name;
    }

    public PokeType getType() {
        return type;
    }

    public int getLevel() {
        return level;
    }

    // ---------- 种族值 / 能力值 ----------

    public int baseOf(Stat stat) {
        return baseStats[stat.ordinal()];
    }

    /** 能力值派生（不包含等级变化）。 */
    public int stat(Stat stat) {
        int base = baseOf(stat);
        int ev = effortValues.get(stat);
        int inner = 2 * base + ev / BattleConfig.EV_DIVISOR;
        int scaled = inner * level / 100;
        if (stat.isHp()) {
            return scaled + level + BattleConfig.STAT_HP_OFFSET;
        }
        return scaled + BattleConfig.STAT_OFFSET;
    }

    /** 最大 HP。 */
    public int maxHp() {
        return stat(Stat.HP);
    }

    /** 计算含等级变化的当前实际能力（速度受等级影响后直接用于出手排序）。 */
    public int effectiveStat(Stat stat) {
        if (stat.isHp()) {
            return maxHp();
        }
        double raw = stat(stat);
        int stage = stages.get(stat);
        // 等级表系数：+6 即 2.0 倍，-6 即 2/8 倍（每级 ±1/2 步进）
        double mult;
        if (stage >= 0) {
            mult = (2.0 + stage) / 2.0;
        } else {
            mult = 2.0 / (2.0 - stage);
        }
        return Math.max(1, (int) Math.floor(raw * mult));
    }

    /** 出手排序用的速度（含速度等级变化）。 */
    public int speed() {
        return effectiveStat(Stat.SPEED);
    }

    // ---------- 努力值 ----------

    public EffortValues getEffortValues() {
        return effortValues;
    }

    public int getEffort(Stat stat) {
        return effortValues.get(stat);
    }

    // ---------- 招式 ----------

    public List<Move> getMoves() {
        return Collections.unmodifiableList(moves);
    }

    public Move move(int index) {
        return moves.get(index);
    }

    public int moveCount() {
        return moves.size();
    }

    // ---------- HP / 濒死 ----------

    public int currentHp() {
        return currentHp;
    }

    public boolean isFainted() {
        return currentHp <= 0;
    }

    /** HP 剩余比例 0.0~1.0。 */
    public double hpRatio() {
        return (double) currentHp / maxHp();
    }

    /** 结算受到的伤害（自然夹紧到 0），返回实际造成的伤害量。 */
    public int takeDamage(int damage) {
        int actual = Math.max(0, Math.min(damage, currentHp));
        currentHp -= actual;
        return actual;
    }

    /** 回复 HP（不超过上限），返回实际回复量。 */
    public int heal(int amount) {
        int actual = Math.max(0, Math.min(amount, maxHp() - currentHp));
        currentHp += actual;
        return actual;
    }

    /** 直接回满（全满药）。 */
    public void healFull() {
        currentHp = maxHp();
    }

    // ---------- 能力等级变化 ----------

    /** 读取某项能力当前等级（阶级），0 表示正常。 */
    public int stageOf(Stat stat) {
        return stages.get(stat);
    }

    /** 改变某项能力等级并夹紧到 ±{@value #STAGE_MAX}，返回实际变化量。 */
    public int changeStage(Stat stat, int delta) {
        int before = stages.get(stat);
        int after = Math.max(-STAGE_MAX, Math.min(STAGE_MAX, before + delta));
        stages.put(stat, after);
        return after - before;
    }

    /** 清除所有能力等级变化。 */
    public void resetStages() {
        for (Stat stat : Stat.values()) {
            stages.put(stat, 0);
        }
    }

    // ---------- 重置 / 描述 ----------

    /** 恢复到满状态并清空能力等级（开局 / 重新对局使用）。 */
    public void reset() {
        currentHp = maxHp();
        resetStages();
    }

    /** 例如「焰尾狐 Lv.50：HP 145/145 · 等级变化 攻击+1」的紧凑信息。 */
    public String describe() {
        StringBuilder sb = new StringBuilder(name).append(" Lv.").append(level)
                .append("(").append(type.getLabel()).append(")");
        int hp = Math.max(0, currentHp);
        sb.append(" HP ").append(hp).append('/').append(maxHp());
        for (Stat stat : Stat.values()) {
            if (stat.isHp()) {
                continue;
            }
            int stage = stages.get(stat);
            if (stage != 0) {
                sb.append(' ').append(stat.getCode()).append(stage > 0 ? "+" : "").append(stage);
            }
        }
        return sb.toString();
    }

    /** 状态面板用的种族值描述，如「HP120 物攻90 物防75 特攻110 特防80 速度105」。 */
    public String baseSummary() {
        StringBuilder sb = new StringBuilder();
        for (Stat stat : Stat.values()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(stat.getCode()).append(baseOf(stat));
        }
        return sb.toString();
    }
}
