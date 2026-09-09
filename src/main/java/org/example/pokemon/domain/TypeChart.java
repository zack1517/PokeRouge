package org.example.pokemon.domain;

import java.util.EnumSet;
import java.util.Set;

public final class TypeChart {
    private TypeChart() {}

    public static double effectiveness(ElementType attack, ElementType defend) {
        if (attack == null || defend == null) return 1.0;
        return switch (attack) {
            case NORMAL -> defend == ElementType.GHOST ? 0.0 : 1.0;
            case FIRE -> defend == ElementType.GRASS || defend == ElementType.ICE || defend == ElementType.BUG ? 2.0 :
                defend == ElementType.FIRE || defend == ElementType.WATER || defend == ElementType.ROCK || defend == ElementType.DRAGON ? 0.5 : 1.0;
            case WATER -> defend == ElementType.FIRE || defend == ElementType.GROUND || defend == ElementType.ROCK ? 2.0 :
                defend == ElementType.WATER || defend == ElementType.GRASS || defend == ElementType.DRAGON ? 0.5 : 1.0;
            case GRASS -> defend == ElementType.WATER || defend == ElementType.GROUND || defend == ElementType.ROCK ? 2.0 :
                defend == ElementType.FIRE || defend == ElementType.GRASS || defend == ElementType.POISON || defend == ElementType.FLYING || defend == ElementType.BUG || defend == ElementType.DRAGON ? 0.5 : 1.0;
            case ELECTRIC -> defend == ElementType.GROUND ? 0.0 : defend == ElementType.WATER || defend == ElementType.FLYING ? 2.0 :
                defend == ElementType.ELECTRIC || defend == ElementType.GRASS || defend == ElementType.DRAGON ? 0.5 : 1.0;
            case ICE -> defend == ElementType.GRASS || defend == ElementType.GROUND || defend == ElementType.FLYING || defend == ElementType.DRAGON ? 2.0 :
                defend == ElementType.FIRE || defend == ElementType.WATER || defend == ElementType.ICE ? 0.5 : 1.0;
            case FIGHTING -> defend == ElementType.GHOST ? 0.0 : defend == ElementType.NORMAL || defend == ElementType.ICE || defend == ElementType.ROCK ? 2.0 :
                defend == ElementType.POISON || defend == ElementType.FLYING || defend == ElementType.BUG || defend == ElementType.PSYCHIC || defend == ElementType.FAIRY ? 0.5 : 1.0;
            case POISON -> defend == ElementType.GRASS || defend == ElementType.FAIRY ? 2.0 :
                defend == ElementType.POISON || defend == ElementType.GROUND || defend == ElementType.ROCK || defend == ElementType.GHOST ? 0.5 : 1.0;
            case GROUND -> defend == ElementType.FLYING ? 0.0 : defend == ElementType.FIRE || defend == ElementType.ELECTRIC || defend == ElementType.POISON || defend == ElementType.ROCK ? 2.0 :
                defend == ElementType.GRASS || defend == ElementType.BUG ? 0.5 : 1.0;
            case FLYING -> defend == ElementType.GRASS || defend == ElementType.FIGHTING || defend == ElementType.BUG ? 2.0 :
                defend == ElementType.ELECTRIC || defend == ElementType.ROCK ? 0.5 : 1.0;
            case BUG -> defend == ElementType.GRASS || defend == ElementType.POISON || defend == ElementType.PSYCHIC ? 2.0 :
                defend == ElementType.FIRE || defend == ElementType.FLYING || defend == ElementType.ROCK || defend == ElementType.FAIRY ? 0.5 : 1.0;
            case ROCK -> defend == ElementType.FIRE || defend == ElementType.ICE || defend == ElementType.FLYING || defend == ElementType.BUG ? 2.0 :
                defend == ElementType.FIGHTING || defend == ElementType.GROUND ? 0.5 : 1.0;
            case GHOST -> defend == ElementType.NORMAL ? 0.0 : defend == ElementType.GHOST || defend == ElementType.PSYCHIC ? 2.0 : 1.0;
            case DRAGON -> defend == ElementType.FAIRY ? 0.0 : defend == ElementType.DRAGON ? 2.0 : 1.0;
            case PSYCHIC -> defend == ElementType.FIGHTING || defend == ElementType.POISON ? 2.0 : defend == ElementType.PSYCHIC ? 0.5 : 1.0;
            case FAIRY -> defend == ElementType.FIGHTING || defend == ElementType.DRAGON ? 2.0 : defend == ElementType.FIRE || defend == ElementType.POISON ? 0.5 : 1.0;
            default -> 1.0;
        };
    }

    public static Set<ElementType> allTypes() {
        return EnumSet.allOf(ElementType.class);
    }
}
