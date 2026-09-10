package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 能力值容器，也用于表示宝可梦的个体值（IV，范围 0-31）。
 */
public class Stats implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 个体值范围上限（含）。 */
    private static final int MAX_IV = 31;

    private final int hp;
    private final int attack;
    private final int defense;
    private final int spAttack;
    private final int spDefense;
    private final int speed;

    /**
     * 以实际能力值构造 Stats。
     */
    public Stats(int hp, int attack, int defense, int spAttack, int spDefense, int speed) {
        this.hp = hp;
        this.attack = attack;
        this.defense = defense;
        this.spAttack = spAttack;
        this.spDefense = spDefense;
        this.speed = speed;
    }

    /**
     * 生成六项个体值均为 0-31 随机数的 Stats 实例。
     *
     * @return 随机个体值实例
     */
    public static Stats randomIv() {
        return randomIv(0);
    }

    /**
     * 生成六项个体值随机、并叠加成长加成的 Stats 实例（局外成长机制的落地入口）。
     *
     * <p>每项按 {@code min(31, 随机值 + bonus)} 生成，因此加成满 31 时必为「满个体」。
     * 本方法只影响个体值，种族值与性格不在此处参与演算。</p>
     *
     * @param bonus 个体值加成，负数按 0 处理
     * @return 叠加加成后的随机个体值实例
     */
    public static Stats randomIv(int bonus) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int extra = Math.max(0, bonus);
        return new Stats(
                roll(random, extra),
                roll(random, extra),
                roll(random, extra),
                roll(random, extra),
                roll(random, extra),
                roll(random, extra));
    }

    /** 单项个体值：随机 0-31 后叠加加成，并按 {@link #MAX_IV} 截断。 */
    private static int roll(ThreadLocalRandom random, int bonus) {
        return Math.min(MAX_IV, random.nextInt(MAX_IV + 1) + bonus);
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

    public int getHpIv() {
        return hp;
    }

    public int getAttackIv() {
        return attack;
    }

    public int getDefenseIv() {
        return defense;
    }

    public int getSpAttackIv() {
        return spAttack;
    }

    public int getSpDefenseIv() {
        return spDefense;
    }

    public int getSpeedIv() {
        return speed;
    }
}
