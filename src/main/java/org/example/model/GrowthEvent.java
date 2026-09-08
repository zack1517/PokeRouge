package org.example.model;

/**
 * 成长事件（升级时由经验结算产生，供战斗/界面订阅展示）。
 * <p>契约：接口文档 v1.0 §3.2。</p>
 */
public class GrowthEvent {

    public enum EventType { LEVEL_UP, LEARN_MOVE, EVOLUTION }

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

    public EventType getType() { return type; }
    public int getOldLevel() { return oldLevel; }
    public int getNewLevel() { return newLevel; }
    public Move getLearnedMove() { return learnedMove; }
    public Species getEvolvedTo() { return evolvedTo; }
}
