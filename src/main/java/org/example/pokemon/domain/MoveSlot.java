package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 技能槽，绑定一个技能并追踪其当前 PP。
 */
public class MoveSlot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Move move;

    /** 当前剩余 PP，可变。 */
    private int currentPp;

    public MoveSlot(Move move) {
        this.move = move;
        this.currentPp = move.getMaxPp();
    }

    public Move getMove() {
        return move;
    }

    public int getCurrentPp() {
        return currentPp;
    }

    public int getPp() {
        return currentPp;
    }

    public int getMaxPp() {
        return move.getMaxPp();
    }

    /**
     * 判断该技能槽的 PP 是否已耗尽。
     *
     * @return PP 耗尽返回 true，否则返回 false
     */
    public boolean isExhausted() {
        return currentPp <= 0;
    }

    /**
     * 兼容旧版 API：判断该技能槽是否仍可使用。
     */
    public boolean exhausted() {
        return isExhausted();
    }

    /**
     * 兼容旧版 API：消耗 1 点 PP；若 PP 足够则返回 true。
     */
    public boolean use() {
        if (currentPp <= 0) {
            return false;
        }
        currentPp--;
        return true;
    }

    /**
     * 消耗 1 点 PP。PP 已耗尽时不产生效果。
     */
    public void usePp() {
        if (currentPp > 0) {
            currentPp--;
        }
    }

    /**
     * 恢复指定数量的 PP，恢复后不超过最大 PP。
     *
     * @param amount 恢复的 PP 数量
     */
    public void restorePp(int amount) {
        currentPp = Math.min(currentPp + amount, getMaxPp());
    }

    /**
     * 兼容旧版 API：恢复指定 PP 上限。
     */
    public void restore(int amount) {
        restorePp(amount);
    }

    /**
     * 将 PP 完全恢复至最大 PP。
     */
    public void fullRestore() {
        currentPp = getMaxPp();
    }
}
