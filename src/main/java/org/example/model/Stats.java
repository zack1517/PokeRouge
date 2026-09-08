package org.example.model;

import java.util.Random;

/**
 * 六项基础属性数值。
 * <p>种族值/个体实际属性共用同一结构：生命(HP)、物攻、物防、特攻、特防、速度。
 * 个体值场景（0~31）时各字段语义等同 IV，见契约别名 getter（文档 §2.5）。</p>
 */
public final class Stats {

    private static final Random RANDOM = new Random();

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

    /** 生成一组随机个体值（各项 0~31）。 */
    public static Stats randomIv() {
        return new Stats(
                RANDOM.nextInt(32), RANDOM.nextInt(32), RANDOM.nextInt(32),
                RANDOM.nextInt(32), RANDOM.nextInt(32), RANDOM.nextInt(32));
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

    // ===== 个体值语义别名（契约 §2.5）：当本结构承载个体值(0~31)时使用 =====
    public int getHpIv() { return hp; }
    public int getAttackIv() { return attack; }
    public int getDefenseIv() { return defense; }
    public int getSpAttackIv() { return spAttack; }
    public int getSpDefenseIv() { return spDefense; }
    public int getSpeedIv() { return speed; }

    @Override
    public String toString() {
        return "Stats{HP=" + hp + ", 物攻=" + attack + ", 物防=" + defense
                + ", 特攻=" + spAttack + ", 特防=" + spDefense + ", 速度=" + speed + '}';
    }
}
