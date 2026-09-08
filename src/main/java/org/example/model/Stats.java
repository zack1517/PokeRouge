package org.example.model;

/**
 * 六项基础属性数值。
 * <p>种族值/个体实际属性共用同一结构：生命(HP)、物攻、物防、特攻、特防、速度。</p>
 */
public final class Stats {

    private final int hp;
    private final int attack;
    private final int defense;
    private final int spAttack;
    private final int spDefense;
    private final int speed;

    public Stats(int hp, int attack, int defense, int spAttack, int spDefense, int speed) {
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

    @Override
    public String toString() {
        return "Stats{HP=" + hp + ", 物攻=" + attack + ", 物防=" + defense
                + ", 特攻=" + spAttack + ", 特防=" + spDefense + ", 速度=" + speed + '}';
    }
}
