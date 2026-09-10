package org.example.growth;

/**
 * 个体值成长规则：把「某种族累计捕捉次数」换算为个体值加成（纯函数，无状态）。
 *
 * <p><b>口径</b>：玩家每累计捕捉同族精灵 {@value #CAPTURES_PER_STEP} 只，该族即累积 1 点
 * 个体值成长（{@link #speciesBonus(int)}）；各族成长值求和即全局加成
 * （{@link GrowthProgress#globalIvBonus()}）。全局加成作用于<b>之后生成的所有宝可梦</b>：
 * 六项个体值在随机值基础上各加同样的数值，并按 {@link #MAX_IV} 截断。</p>
 *
 * <p><b>不变项</b>：本规则只影响个体值，种族值（{@code Species#getBaseStats()}）与性格一律不变。</p>
 *
 * <p>进度本身由 {@link GrowthProgress} 持有，本类不做存储。</p>
 */
public final class IvGrowthRule {

    /** 个体值上限（含），与 {@code Stats} 的 IV 取值域一致；达到该值即「满个体」。 */
    public static final int MAX_IV = 31;

    /** 每累计捕捉该数量只同族精灵，个体值加成 +1。 */
    public static final int CAPTURES_PER_STEP = 2;

    private IvGrowthRule() {
        // 纯规则工具类，禁止实例化
    }

    /**
     * 单种族个体值加成：由该族累计捕捉次数换算，满 {@link #CAPTURES_PER_STEP} 次记 1 点，
     * 上限 {@link #MAX_IV}（图鉴中展示的即本值）。
     *
     * @param captureCount 该族累计捕捉次数，非正数按 0 处理
     * @return 该族个体值加成（0 ~ {@value #MAX_IV}）
     */
    public static int speciesBonus(int captureCount) {
        if (captureCount <= 0) {
            return 0;
        }
        return Math.min(MAX_IV, captureCount / CAPTURES_PER_STEP);
    }

    /**
     * 把加成叠加到单项个体值上并按 {@link #MAX_IV} 截断。
     *
     * @param iv    原始随机个体值（0 ~ {@value #MAX_IV}）
     * @param bonus 个体值加成，负数按 0 处理
     * @return 截断后的个体值（0 ~ {@value #MAX_IV}）
     */
    public static int apply(int iv, int bonus) {
        return Math.min(MAX_IV, Math.max(0, iv) + Math.max(0, bonus));
    }
}
