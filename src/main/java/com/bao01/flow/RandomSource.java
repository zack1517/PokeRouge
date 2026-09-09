package com.bao01.flow;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机源（可注入测试，保证确定性可复现）。
 *
 * <p>对应《游戏流程接口设计》3.2。流程模块所有随机决策都经由本接口，
 * 生产环境用 {@link #system()}，测试可用固定序列桩替换。
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
}
