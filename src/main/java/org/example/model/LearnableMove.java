package org.example.model;

import java.io.Serializable;

/**
 * 某精灵在某等级可习得的技能条目。
 * <p>契约：接口文档 v1.0 §2.10。</p>
 */
public class LearnableMove implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String moveId;
    private final int level;

    public LearnableMove(String moveId, int level) {
        this.moveId = moveId;
        this.level = level;
    }

    public String getMoveId() { return moveId; }
    public int getLevel() { return level; }

    @Override
    public String toString() {
        return moveId + "@" + level;
    }
}
