package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 成长事件，记录宝可梦升级、学习技能或进化等成长过程。
 */
public class GrowthEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成长事件类型。
     */
    public enum EventType {
        LEVEL_UP,
        LEARN_MOVE,
        EVOLUTION
    }

    private final EventType type;
    private final int oldLevel;
    private final int newLevel;
    private final Move learnedMove;
    private final Species evolvedTo;

    public GrowthEvent(EventType type, int oldLevel, int newLevel, Move learnedMove, Species evolvedTo) {
        this.type = type;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.learnedMove = learnedMove;
        this.evolvedTo = evolvedTo;
    }

    public EventType getType() {
        return type;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public Move getLearnedMove() {
        return learnedMove;
    }

    public Species getEvolvedTo() {
        return evolvedTo;
    }
}
