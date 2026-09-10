package org.example.growth;

import java.util.Objects;

/**
 * 单一种族的成长记录（不可变）：图鉴条目，同时也是个体值加成的来源。
 *
 * <p>{@code captureCount} 是成长机制的驱动量；{@code battleCount} 只作展示，不参与加成演算。
 * 个体值加成由 {@link IvGrowthRule#speciesBonus(int)} 从捕捉次数派生。</p>
 */
public final class SpeciesGrowthRecord {

    /** 物种 id（与数据模块的种族 id 一致）。 */
    private final String speciesId;
    /** 累计捕捉次数。 */
    private final int captureCount;
    /** 累计对战次数（参战获胜场次）。 */
    private final int battleCount;

    SpeciesGrowthRecord(String speciesId, int captureCount, int battleCount) {
        this.speciesId = Objects.requireNonNull(speciesId, "speciesId");
        this.captureCount = Math.max(0, captureCount);
        this.battleCount = Math.max(0, battleCount);
    }

    /** 该种族尚无任何记录时的空记录。 */
    static SpeciesGrowthRecord empty(String speciesId) {
        return new SpeciesGrowthRecord(speciesId, 0, 0);
    }

    public String getSpeciesId() {
        return speciesId;
    }

    public int getCaptureCount() {
        return captureCount;
    }

    public int getBattleCount() {
        return battleCount;
    }

    /** 该族的个体值加成（0 ~ {@link IvGrowthRule#MAX_IV}），由图鉴展示。 */
    public int getIvBonus() {
        return IvGrowthRule.speciesBonus(captureCount);
    }

    /** 累计捕捉次数 +1。 */
    SpeciesGrowthRecord plusCapture() {
        return new SpeciesGrowthRecord(speciesId, captureCount + 1, battleCount);
    }

    /** 累计对战次数 +1。 */
    SpeciesGrowthRecord plusBattle() {
        return new SpeciesGrowthRecord(speciesId, captureCount, battleCount + 1);
    }

    /** 是否有实际记录（捕捉或对战至少一次）。 */
    public boolean isBlank() {
        return captureCount == 0 && battleCount == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SpeciesGrowthRecord)) {
            return false;
        }
        SpeciesGrowthRecord other = (SpeciesGrowthRecord) o;
        return captureCount == other.captureCount
                && battleCount == other.battleCount
                && speciesId.equals(other.speciesId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(speciesId, captureCount, battleCount);
    }

    @Override
    public String toString() {
        return "SpeciesGrowthRecord{" + speciesId + ", 捕捉=" + captureCount
                + ", 对战=" + battleCount + ", 个体值加成=" + getIvBonus() + '}';
    }
}
