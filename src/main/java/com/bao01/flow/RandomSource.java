package com.bao01.flow;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机源（可注入测试，保证确定性可复现）。
 *
 * <p>对应《游戏流程接口设计》3.2。流程模块所有随机决策都经由本接口，
 * 生产环境用 {@link #system()}，需要复现时用 {@link #seeded(long)}，
 * 也可注入固定序列桩。
 */
public interface RandomSource {

    /** [0, bound) 随机整数。 */
    int nextInt(int bound);

    /** [0, 1) 随机小数。 */
    double nextDouble();

    /** 以概率 p 返回 true（p∈[0,1]）。 */
    boolean chance(double p);

    /** ThreadLocalRandom 实现。 */
    static RandomSource system() {
        return new RandomSource() {
            private final ThreadLocalRandom rnd = ThreadLocalRandom.current();

            @Override
            public int nextInt(int bound) {
                return rnd.nextInt(bound);
            }

            @Override
            public double nextDouble() {
                return rnd.nextDouble();
            }

            @Override
            public boolean chance(double p) {
                return p > 0 && (p >= 1 || rnd.nextDouble() < p);
            }
        };
    }

    /**
     * 固定种子实现：同一 seed 产生同一随机序列，用于回放/调试/冒烟测试。
     *
     * <p>与 {@link #system()} 语义一致（{@code nextInt} 为 [0,bound)、
     * {@code chance(p)} 在 p≤0 时恒 false、p≥1 时恒 true），仅随机序列可复现。
     */
    static RandomSource seeded(long seed) {
        return new RandomSource() {
            private final Random rnd = new Random(seed);

            @Override
            public int nextInt(int bound) {
                return rnd.nextInt(bound);
            }

            @Override
            public double nextDouble() {
                return rnd.nextDouble();
            }

            @Override
            public boolean chance(double p) {
                return p > 0 && (p >= 1 || rnd.nextDouble() < p);
            }
        };
    }
}
