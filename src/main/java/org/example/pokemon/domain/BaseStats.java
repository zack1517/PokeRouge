package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 种族值，表示宝可梦种族的基础能力值。
 */
public class BaseStats implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int hp;
    private final int attack;
    private final int defense;
    private final int spAttack;
    private final int spDefense;
    private final int speed;

    public BaseStats(int hp, int attack, int defense, int spAttack, int spDefense, int speed) {
        this.hp = hp;
        this.attack = attack;
        this.defense = defense;
        this.spAttack = spAttack;
        this.spDefense = spDefense;
        this.speed = speed;
    }

    public int getHp() {
        return hp;
    }

    public int getAttack() {
        return attack;
    }

    public int getDefense() {
        return defense;
    }

    public int getSpAttack() {
        return spAttack;
    }

    public int getSpDefense() {
        return spDefense;
    }

    public int getSpeed() {
        return speed;
    }

    /**
     * 返回六项种族值的总和。
     *
     * @return 种族值总和
     */
    public int getTotal() {
        return hp + attack + defense + spAttack + spDefense + speed;
    }
}
