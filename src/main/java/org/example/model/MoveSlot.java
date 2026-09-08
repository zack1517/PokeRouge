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

    public Move getMove() {
        return move;
    }

    public int getPp() {
        return pp;
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

    /** 恢复 PP（最大 PP 封顶），用于道具等。 */
    public void restore(int amount) {
        pp = Math.min(move.getMaxPp(), pp + amount);
    }

    @Override
    public String toString() {
        return move.getName() + " PP " + pp + "/" + move.getMaxPp();
    }
}
