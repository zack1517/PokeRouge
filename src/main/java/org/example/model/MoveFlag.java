package org.example.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 招式标记（正作的 move flag）。
 *
 * <p>标记描述招式的「形式特征」，与属性、类别正交：属性决定克制关系，类别决定物理/特殊，
 * 标记决定哪些携带装备与特性可以挂钩。当前引擎落地与携带装备相关的四种标记，
 * 对应 equipment.csv 中拳击手套（{@link #PUNCH}）、凸凸头盔（{@link #CONTACT}）、
 * 防尘护目镜（{@link #POWDER}）、爽喉喷雾（{@link #SOUND}）四件装备。</p>
 *
 * <ul>
 *     <li>{@link #CONTACT}：接触类招式 —— 攻击方以身体接触目标（撞击、拳类、啃咬、踢击等）</li>
 *     <li>{@link #PUNCH}：拳类招式（火焰拳、冰冻拳、暗影拳等），拳类招式同时具备 {@link #CONTACT}</li>
 *     <li>{@link #POWDER}：粉末类招式（催眠粉、毒粉、蘑菇孢子等）</li>
 *     <li>{@link #SOUND}：声音类招式（叫声、战吼、轮唱等）</li>
 * </ul>
 *
 * <p>招式数据来自 moves.csv 第 12 列 flags（{@code |} 分隔）：留空表示无标记。
 * 标记是增量的 —— 未标注的招式没有任何标记，因此在不标注时新系统对旧数据完全无副作用。</p>
 */
public enum MoveFlag {

    /** 接触类招式：会被凸凸头盔反伤、被拳击手套的「取消接触」修正。 */
    CONTACT,

    /** 拳类招式：被拳击手套强化。 */
    PUNCH,

    /** 粉末类招式：对携带防尘护目镜的目标无效。 */
    POWDER,

    /** 声音类招式：触发爽喉喷雾。 */
    SOUND;

    /**
     * 解析 {@code |} 分隔的标记串（如 {@code CONTACT|PUNCH}）。
     *
     * <p>无法识别的片段被忽略，空输入返回空集合 —— 与 {@link HeldItem#textPart(int)} 一致，
     * 使数据表写错标记名时退化为「无标记」而不是抛异常。</p>
     *
     * @param raw 标记串；{@code null} 或空串返回空集合
     * @return 不可变标记集合
     */
    public static Set<MoveFlag> parseFlags(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        EnumSet<MoveFlag> flags = EnumSet.noneOf(MoveFlag.class);
        for (String part : raw.split("\\|")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            for (MoveFlag flag : values()) {
                if (flag.name().equalsIgnoreCase(name)) {
                    flags.add(flag);
                }
            }
        }
        return flags.isEmpty() ? Set.of() : Collections.unmodifiableSet(flags);
    }
}
