package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 可学习技能，表示 Species 升级学招表中的一条记录。
 */
public class LearnableMove implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String moveId;
    private final int level;

    public LearnableMove(String moveId, int level) {
        this.moveId = moveId;
        this.level = level;
    }

    public String getMoveId() {
        return moveId;
    }

    public int getLevel() {
        return level;
    }
}
