package org.example.model;

import java.util.EnumSet;
import java.util.Set;

import static org.example.model.ElementType.BUG;
import static org.example.model.ElementType.DRAGON;
import static org.example.model.ElementType.ELECTRIC;
import static org.example.model.ElementType.FIGHTING;
import static org.example.model.ElementType.FIRE;
import static org.example.model.ElementType.FLYING;
import static org.example.model.ElementType.GHOST;
import static org.example.model.ElementType.GRASS;
import static org.example.model.ElementType.GROUND;
import static org.example.model.ElementType.ICE;
import static org.example.model.ElementType.NORMAL;
import static org.example.model.ElementType.POISON;
import static org.example.model.ElementType.ROCK;
import static org.example.model.ElementType.WATER;

/**
 * 属性克制表。
 * <p>倍率约定：2.0 克制、0.5 抵抗、0.0 免疫、1.0 普通。数值集中在 {@link #effectiveness} 一处维护，便于后续调参。</p>
 */
public final class TypeChart {

    private TypeChart() {
    }

    /** 攻击属性对单一防守属性的倍率。 */
    public static double effectiveness(ElementType attack, ElementType defend) {
        if (attack == null || defend == null) {
            return 1.0;
        }
        switch (attack) {
            case NORMAL:
                return immune(defend, GHOST) ? 0.0 : 1.0;
            case FIRE:
                if (in(defend, GRASS, ICE, BUG)) {
                    return 2.0;
                }
                return in(defend, FIRE, WATER, ROCK, DRAGON) ? 0.5 : 1.0;
            case WATER:
                if (in(defend, FIRE, GROUND, ROCK)) {
                    return 2.0;
                }
                return in(defend, WATER, GRASS, DRAGON) ? 0.5 : 1.0;
            case GRASS:
                if (in(defend, WATER, GROUND, ROCK)) {
                    return 2.0;
                }
                return in(defend, FIRE, GRASS, POISON, FLYING, BUG, DRAGON) ? 0.5 : 1.0;
            case ELECTRIC:
                if (immune(defend, GROUND)) {
                    return 0.0;
                }
                if (in(defend, WATER, FLYING)) {
                    return 2.0;
                }
                return in(defend, ELECTRIC, GRASS, DRAGON) ? 0.5 : 1.0;
            case ICE:
                if (in(defend, GRASS, GROUND, FLYING, DRAGON)) {
                    return 2.0;
                }
                return in(defend, FIRE, WATER, ICE) ? 0.5 : 1.0;
            case FIGHTING:
                if (immune(defend, GHOST)) {
                    return 0.0;
                }
                if (in(defend, NORMAL, ICE, ROCK)) {
                    return 2.0;
                }
                return in(defend, POISON, FLYING, BUG) ? 0.5 : 1.0;
            case POISON:
                if (defend == GRASS) {
                    return 2.0;
                }
                return in(defend, POISON, GROUND, ROCK, GHOST) ? 0.5 : 1.0;
            case GROUND:
                if (immune(defend, FLYING)) {
                    return 0.0;
                }
                if (in(defend, FIRE, ELECTRIC, POISON, ROCK)) {
                    return 2.0;
                }
                return in(defend, GRASS, BUG) ? 0.5 : 1.0;
            case FLYING:
                if (in(defend, GRASS, FIGHTING, BUG)) {
                    return 2.0;
                }
                return in(defend, ELECTRIC, ROCK) ? 0.5 : 1.0;
            case BUG:
                if (in(defend, GRASS, POISON)) {
                    return 2.0;
                }
                return in(defend, FIRE, FLYING, ROCK) ? 0.5 : 1.0;
            case ROCK:
                if (in(defend, FIRE, ICE, FLYING, BUG)) {
                    return 2.0;
                }
                return in(defend, FIGHTING, GROUND) ? 0.5 : 1.0;
            case GHOST:
                if (immune(defend, NORMAL)) {
                    return 0.0;
                }
                return defend == GHOST ? 2.0 : 1.0;
            case DRAGON:
                return defend == DRAGON ? 2.0 : 1.0;
            default:
                return 1.0;
        }
    }

    private static boolean in(ElementType value, ElementType... candidates) {
        return Set.of(candidates).contains(value);
    }

    private static boolean immune(ElementType value, ElementType immuneTo) {
        return value == immuneTo;
    }

    /** 全部元素枚举（供 UI 展示属性时遍历）。 */
    public static Set<ElementType> allTypes() {
        return EnumSet.allOf(ElementType.class);
    }
}
