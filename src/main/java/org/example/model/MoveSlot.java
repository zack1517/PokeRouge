package org.example.model;

/**
 * 精灵已学会的技能槽（技能 + 当前 PP）。
 * <p>一个精灵最多 4 个技能槽，每个槽位独立记录剩余 PP。</p>
 */
public class MoveSlot {

    private final Move move;
    private int pp;

    public MoveSlot(Move move) {
        this.move = move;
        this.pp = move.getMaxPp();
    }

    /**
     * 以指定剩余 PP 构造技能槽（读档还原用）。
     * <p>传入值会裁剪到 {@code [0, 最大PP]}，使被手工改坏的存档不会产生非法 PP。</p>
     *
     * @param move 技能
     * @param pp   当前剩余 PP
     */
    public MoveSlot(Move move, int pp) {
        this.move = java.util.Objects.requireNonNull(move);
        this.pp = Math.max(0, Math.min(move.getMaxPp(), pp));
    }

    public Move getMove() {
        return move;
    }

    public int getPp() {
        return pp;
    }

    /** 契约别名：当前 PP（文档 §2.8）。 */
    public int getCurrentPp() {
        return pp;
    }

    /** 契约别名：当前 PP 上限（即技能最大 PP）。 */
    public int getMaxPp() {
        return move.getMaxPp();
    }

    /** PP 不足则返回 false，不消耗。 */
    public boolean use() {
        if (pp <= 0) {
            return false;
        }
        pp--;
        return true;
    }

    public boolean exhausted() {
        return pp <= 0;
    }

    /** 契约别名：技能是否耗尽（PP 为 0）。 */
    public boolean isExhausted() {
        return pp <= 0;
    }

    /** 契约别名：消耗 1 点 PP（已耗尽则不变）。 */
    public void usePp() {
        if (pp > 0) {
            pp--;
        }
    }

    /** 恢复 PP（最大 PP 封顶），用于道具等。 */
    public void restore(int amount) {
        pp = Math.min(move.getMaxPp(), pp + amount);
    }

    /** 契约别名：恢复 PP（最大 PP 封顶）。 */
    public void restorePp(int amount) {
        restore(amount);
    }

    /** 契约别名：PP 回满。 */
    public void fullRestore() {
        pp = move.getMaxPp();
    }

    @Override
    public String toString() {
        return move.getName() + " PP " + pp + "/" + move.getMaxPp();
    }
}
