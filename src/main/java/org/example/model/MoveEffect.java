package org.example.model;

import java.util.Locale;

/**
 * 技能附带效果（数据驱动：由技能数据/技能效果列指定）。
 * <p>分为两类：</p>
 * <ul>
 *     <li><b>天气/场地</b>：使用该效果的变化类技能会开启对应天气/场地（持续回合见
 *     {@link Weather}/{@link Terrain}）。</li>
 *     <li><b>专属效果</b>：守住、寄生种子、睡觉等只有一个招式具备的特殊效果，由战斗引擎
 *     单独结算（见 {@code BattleEngine#applyMoveEffect}）。</li>
 * </ul>
 */
public enum MoveEffect {

    /** 无特殊效果。 */
    NONE,
    /** 开启大晴天。 */
    SUNNY_DAY,
    /** 开启求雨（下雨）。 */
    RAIN_DANCE,
    /** 开启沙暴。 */
    SANDSTORM,
    /** 开启冰雹。 */
    HAIL,
    /** 开启电气场地。 */
    ELECTRIC_TERRAIN,
    /** 开启青草场地。 */
    GRASSY_TERRAIN,
    /** 开启薄雾场地。 */
    MISTY_TERRAIN,
    /** 开启精神场地。 */
    PSYCHIC_TERRAIN,
    /** 守住：本回合挡下对方的一切招式，连续使用成功率递减。 */
    PROTECT,
    /** 寄生种子：每回合末吸取目标最大 HP 的 1/8 转给施加者，草系免疫。 */
    LEECH_SEED,
    /** 睡觉：回复全部 HP 并陷入 2 回合睡眠，HP 全满或已有主要异常时失败。 */
    REST;

    /**
     * 解析技能效果名（大小写不敏感、忽略首尾空白）；空串/未知返回 {@link #NONE}。
     *
     * @param name 如 "SUNNY_DAY" 或 "sunny_day"
     */
    public static MoveEffect parse(String name) {
        if (name == null || name.trim().isEmpty()) {
            return NONE;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (MoveEffect e : values()) {
            if (e.name().equals(normalized)) {
                return e;
            }
        }
        return NONE;
    }

    /** 该效果对应的天气（非天气效果返回 null）。 */
    public Weather toWeather() {
        return switch (this) {
            case SUNNY_DAY -> Weather.SUNNY;
            case RAIN_DANCE -> Weather.RAIN;
            case SANDSTORM -> Weather.SANDSTORM;
            case HAIL -> Weather.HAIL;
            default -> null;
        };
    }

    /** 该效果对应的场地（非场地效果返回 null）。 */
    public Terrain toTerrain() {
        return switch (this) {
            case ELECTRIC_TERRAIN -> Terrain.ELECTRIC;
            case GRASSY_TERRAIN -> Terrain.GRASSY;
            case MISTY_TERRAIN -> Terrain.MISTY;
            case PSYCHIC_TERRAIN -> Terrain.PSYCHIC;
            default -> null;
        };
    }

    /** 是否为天气/场地类效果（由战斗引擎开启对应天气或场地）。 */
    public boolean isFieldEffect() {
        return toWeather() != null || toTerrain() != null;
    }
}
