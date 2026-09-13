package org.example.pokemon.domain;

import java.util.Locale;

public enum MoveEffect {
    NONE,
    SUNNY_DAY,
    RAIN_DANCE,
    SANDSTORM,
    HAIL,
    ELECTRIC_TERRAIN,
    GRASSY_TERRAIN,
    MISTY_TERRAIN,
    PSYCHIC_TERRAIN,
    /** 守住：当回合挡下对方一切招式（专属效果，由战斗引擎结算）。 */
    PROTECT,
    /** 寄生种子：每回合末吸取目标最大 HP 的 1/8（专属效果，由战斗引擎结算）。 */
    LEECH_SEED,
    /** 睡觉：回满 HP 并陷入睡眠（专属效果，由战斗引擎结算）。 */
    REST;

    public static MoveEffect parse(String name) {
        if (name == null || name.trim().isEmpty()) return NONE;
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (MoveEffect effect : values()) {
            if (effect.name().equals(normalized)) return effect;
        }
        return NONE;
    }

    public Weather toWeather() {
        return switch (this) {
            case SUNNY_DAY -> Weather.SUNNY;
            case RAIN_DANCE -> Weather.RAIN;
            case SANDSTORM -> Weather.SANDSTORM;
            case HAIL -> Weather.HAIL;
            default -> null;
        };
    }

    public Terrain toTerrain() {
        return switch (this) {
            case ELECTRIC_TERRAIN -> Terrain.ELECTRIC;
            case GRASSY_TERRAIN -> Terrain.GRASSY;
            case MISTY_TERRAIN -> Terrain.MISTY;
            case PSYCHIC_TERRAIN -> Terrain.PSYCHIC;
            default -> null;
        };
    }
}
