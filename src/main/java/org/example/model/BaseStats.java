package org.example.model;

import java.io.Serializable;

/**
 * 种族值（六项基础种族值）。
 * <p>契约：接口文档 v1.0 §2.4。现有 {@link Species} 内部用 {@link Stats} 承载种族值，
 * 该类型为契约补充，供后续数据层/外部模块使用。</p>
 */
public class BaseStats implements Serializable {

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

    /** 便捷：由现有六维数值构建。 */
    public static BaseStats from(Stats stats) {
        return new BaseStats(stats.getHp(), stats.getAttack(), stats.getDefense(),
                stats.getSpAttack(), stats.getSpDefense(), stats.getSpeed());
    }

    public int getHp() { return hp; }
    public int getAttack() { return attack; }
    public int getDefense() { return defense; }
    public int getSpAttack() { return spAttack; }
    public int getSpDefense() { return spDefense; }
    public int getSpeed() { return speed; }

    public int getTotal() { return hp + attack + defense + spAttack + spDefense + speed; }
}
