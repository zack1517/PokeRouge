package org.example.model;

import java.io.Serializable;
import java.util.Optional;

/**
 * 性格：对六项能力中一项 +10%、另一项 -10%（或无修正）。
 * <p>契约：接口文档 v1.0 §2.6，共 20 个预定义常量。</p>
 */
public class Nature implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===== 预定义常量（20种） =====
    public static final Nature HARDY   = new Nature("hardy",   "勤奋", null, null);
    public static final Nature LONELY  = new Nature("lonely",  "怕寂寞", StatModifier.ATTACK, StatModifier.DEFENSE);
    public static final Nature BRAVE   = new Nature("brave",   "勇敢", StatModifier.ATTACK, StatModifier.SPEED);
    public static final Nature ADAMANT = new Nature("adamant", "固执", StatModifier.ATTACK, StatModifier.SP_ATTACK);
    public static final Nature NAUGHTY = new Nature("naughty", "顽皮", StatModifier.ATTACK, StatModifier.SP_DEFENSE);
    public static final Nature BOLD    = new Nature("bold",    "大胆", StatModifier.DEFENSE, StatModifier.ATTACK);
    public static final Nature RELAXED = new Nature("relaxed", "悠闲", StatModifier.DEFENSE, StatModifier.SPEED);
    public static final Nature IMPISH  = new Nature("impish",  "淘气", StatModifier.DEFENSE, StatModifier.SP_ATTACK);
    public static final Nature LAX     = new Nature("lax",     "乐天", StatModifier.DEFENSE, StatModifier.SP_DEFENSE);
    public static final Nature TIMID   = new Nature("timid",   "胆小", StatModifier.SPEED, StatModifier.ATTACK);
    public static final Nature HASTY   = new Nature("hasty",   "急躁", StatModifier.SPEED, StatModifier.DEFENSE);
    public static final Nature JOLLY   = new Nature("jolly",   "爽朗", StatModifier.SPEED, StatModifier.SP_ATTACK);
    public static final Nature NAIVE   = new Nature("naive",   "天真", StatModifier.SPEED, StatModifier.SP_DEFENSE);
    public static final Nature MODEST  = new Nature("modest",  "内敛", StatModifier.SP_ATTACK, StatModifier.ATTACK);
    public static final Nature MILD    = new Nature("mild",    "慢吞吞", StatModifier.SP_ATTACK, StatModifier.DEFENSE);
    public static final Nature QUIET   = new Nature("quiet",   "冷静", StatModifier.SP_ATTACK, StatModifier.SPEED);
    public static final Nature RASH    = new Nature("rash",    "马虎", StatModifier.SP_ATTACK, StatModifier.SP_DEFENSE);
    public static final Nature CALM    = new Nature("calm",    "温和", StatModifier.SP_DEFENSE, StatModifier.ATTACK);
    public static final Nature GENTLE  = new Nature("gentle",  "温顺", StatModifier.SP_DEFENSE, StatModifier.DEFENSE);
    public static final Nature SASSY   = new Nature("sassy",   "慎重", StatModifier.SP_DEFENSE, StatModifier.SPEED);
    public static final Nature CAREFUL = new Nature("careful", "仔细", StatModifier.SP_DEFENSE, StatModifier.SP_ATTACK);

    private final String id;
    private final String name;
    private final StatModifier increasedStat;
    private final StatModifier decreasedStat;

    public Nature(String id, String name, StatModifier increasedStat, StatModifier decreasedStat) {
        this.id = id;
        this.name = name;
        this.increasedStat = increasedStat;
        this.decreasedStat = decreasedStat;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Optional<StatModifier> getIncreasedStat() { return Optional.ofNullable(increasedStat); }
    public Optional<StatModifier> getDecreasedStat() { return Optional.ofNullable(decreasedStat); }

    /**
     * 返回该能力的性格修正系数：提升项 1.1、降低项 0.9、无变化 1.0。
     */
    public double getModifier(StatModifier stat) {
        if (stat == increasedStat) return 1.1;
        if (stat == decreasedStat) return 0.9;
        return 1.0;
    }

    @Override
    public String toString() {
        return name;
    }
}
