package com.bao01.config;

/**
 * 对战与养成的全局数值常量（数值平衡统一在此调整）。
 */
public final class BattleConfig {

    private BattleConfig() {
    }

    // ---------- 努力值（EV）规则 ----------
    /** 单项努力值上限。 */
    public static final int EV_CAP_PER_STAT = 252;
    /** 六项努力值合计上限。 */
    public static final int EV_TOTAL_CAP = 510;
    /** 能力公式中努力值折算除数：每 4 点 EV = 1 点能力。 */
    public static final int EV_DIVISOR = 4;

    // ---------- 等级与能力值公式 ----------
    public static final int LEVEL_MIN = 1;
    public static final int LEVEL_MAX = 100;
    /** 能力值公式常数：非 HP 项的保底值（对应原版 +5）。 */
    public static final int STAT_OFFSET = 5;
    /** 能力值公式常数：HP 项 = 等级 + 10（对应原版 +等级 +10）。 */
    public static final int STAT_HP_OFFSET = 10;

    // ---------- 伤害公式 ----------
    /** 同属性加成（STAB）。 */
    public static final double STAB = 1.5;
    /** 伤害随机浮动下限。 */
    public static final double DMG_RAND_MIN = 0.85;
    /** 伤害随机浮动上限。 */
    public static final double DMG_RAND_MAX = 1.0;
    /** 伤害公式「等级项」分母：floor(2×等级/5)。 */
    public static final int DMG_LEVEL_DIVISOR = 5;
    /** 伤害公式「等级项」常量 +2。 */
    public static final int DMG_LEVEL_ADD = 2;
    /** 伤害公式主分母（对应原版 /50）。 */
    public static final int DMG_BASE = 50;
    /** 伤害公式保底伤害（对应原版 +2）。 */
    public static final int DMG_FLAT = 2;

    // ---------- 招式 ----------
    /** 每只宝可梦最多携带招式数。 */
    public static final int MAX_MOVES = 4;

    // ---------- 队伍 ----------
    /** 队伍人数上限（与 {@code model.Team#MAX_MEMBERS} 一致）。 */
    public static final int MAX_TEAM_MEMBERS = 6;
    /** 界面 / 演示默认每队上阵人数。 */
    public static final int DEFAULT_TEAM_SIZE = 3;

    // ---------- 逃跑 ----------
    /** 逃跑基础成功率（双方速度相同时）。 */
    public static final double FLEE_CHANCE_BASE = 0.5;
    /** 速度差换算系数：每 100 点速度差影响 ±1.0（换算后越限截断）。 */
    public static final double FLEE_SPEED_FACTOR = 1.0 / 100.0;
    /** 逃跑成功率下限。 */
    public static final double FLEE_CHANCE_MIN = 0.25;
    /** 逃跑成功率上限。 */
    public static final double FLEE_CHANCE_MAX = 0.95;

    // ---------- 天气 ----------
    /** 天气持续时间（含施展回合）。 */
    public static final int WEATHER_TURNS = 5;
    /** 沙暴回合末环境伤害：最大 HP 的 1/16。 */
    public static final int WEATHER_CHIP_DIVISOR = 16;
    /** 沙暴 / 雨天等天气对招式威力的倍率。 */
    public static final double WEATHER_TYPE_BOOST = 1.5;
    /** 天气对防御能力值的倍率（沙暴岩石特防 / 雪天冰物防）。 */
    public static final double WEATHER_DEFENSE_BOOST = 1.5;
}
