package com.bao01.model;

import com.bao01.config.BattleConfig;

import java.util.EnumMap;
import java.util.StringJoiner;

/**
 * 努力值（Effort Values，EV）。
 *
 * <p>表示个体后天培养投入的属性点，随培养 / 抓捕同种宝可梦增长（规划中）。
 * 参与能力值派生：最终能力 = f(种族值, 努力值, 等级)。
 *
 * <p>分配限制：单项不超过 {@link BattleConfig#EV_CAP_PER_STAT}，
 * 六项合计不超过 {@link BattleConfig#EV_TOTAL_CAP}。
 */
public final class EffortValues {

    private final EnumMap<Stat, Integer> values = new EnumMap<>(Stat.class);

    /** 构造全 0 的努力值。 */
    public EffortValues() {
        for (Stat stat : Stat.values()) {
            values.put(stat, 0);
        }
    }

    /** 读取某一项努力值。 */
    public int get(Stat stat) {
        return values.get(stat);
    }

    /** 六项合计。 */
    public int total() {
        int sum = 0;
        for (int v : values.values()) {
            sum += v;
        }
        return sum;
    }

    /**
     * 设置某一项努力值（覆盖式写入）。
     *
     * @param stat  目标能力
     * @param value 非负且不超过单项上限，且总量不超过总上限
     * @throws IllegalArgumentException 越限时抛出
     */
    public void set(Stat stat, int value) {
        if (value < 0 || value > BattleConfig.EV_CAP_PER_STAT) {
            throw new IllegalArgumentException("单项努力值须在 [0, "
                    + BattleConfig.EV_CAP_PER_STAT + "] 区间: " + value);
        }
        int newTotal = total() - values.get(stat) + value;
        if (newTotal > BattleConfig.EV_TOTAL_CAP) {
            throw new IllegalArgumentException("六项努力值合计不得超过 "
                    + BattleConfig.EV_TOTAL_CAP + "，当前将超出: " + newTotal);
        }
        values.put(stat, value);
    }

    /** 在原有基础上追加努力值（追加后的限制同上）。 */
    public void add(Stat stat, int delta) {
        set(stat, values.get(stat) + delta);
    }

    /** 当前是否一项努力值都未分配。 */
    public boolean isEmpty() {
        return total() == 0;
    }

    /** 描述当前已分配的努力值，如「特攻252/速度252」；全 0 时返回「未分配」。 */
    public String describe() {
        if (isEmpty()) {
            return "未分配";
        }
        StringJoiner joiner = new StringJoiner("/");
        for (Stat stat : Stat.values()) {
            int v = values.get(stat);
            if (v > 0) {
                joiner.add(stat.getCode() + v);
            }
        }
        return joiner.toString();
    }
}
