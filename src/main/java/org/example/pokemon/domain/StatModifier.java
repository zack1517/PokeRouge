package org.example.pokemon.domain;

/**
 * 能力修正枚举，表示宝可梦的六项能力值。
 *
 * <p>枚举类型天然实现 {@link java.io.Serializable}，无需显式声明。</p>
 */
public enum StatModifier {

    HP,
    ATTACK,
    DEFENSE,
    SP_ATTACK,
    SP_DEFENSE,
    SPEED
}
