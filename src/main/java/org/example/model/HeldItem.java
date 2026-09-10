package org.example.model;

import java.util.Objects;

/**
 * 可携带装备（持有道具）定义。
 * <p>装备不是消耗品：每件装备在同一时间只能被一只精灵穿戴，穿戴由 UI/奖励流程
 * 从外部设置到 {@link Pokemon#setHeldItem}，战斗引擎只读取并应用效果。</p>
 *
 * <p>效果类型见 {@link HeldItemEffect}：属性招式威力提升（木炭等）、效果拔群增伤（达人带）、
 * 回合末回血（剩饭）、攻击吸血（贝壳之铃）、概率先手（先制之爪）、未进化双防提升（进化辉石）。</p>
 */
public class HeldItem {

    private final String id;
    private final String name;
    private final HeldItemEffect effectType;
    /** 效果参数原文（如 {@code FIRE|1.2}、{@code 0.0625}、{@code 20}）。 */
    private final String param;
    private final String description;

    public HeldItem(String id, String name, HeldItemEffect effectType, String param, String description) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.effectType = Objects.requireNonNull(effectType);
        this.param = param == null ? "" : param.trim();
        this.description = description == null ? "" : description.trim();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public HeldItemEffect getEffectType() {
        return effectType;
    }

    public String getParam() {
        return param;
    }

    public String getDescription() {
        return description;
    }

    /**
     * DAMAGE_TYPE 装备的参数解析：{@code FIRE|1.2} → 属性 FIRE + 倍率 1.2。
     *
     * @return 属性部分；非 DAMAGE_TYPE 或参数格式异常时返回 {@code null}
     */
    public String typeParam() {
        if (effectType != HeldItemEffect.DAMAGE_TYPE || param.isEmpty()) {
            return null;
        }
        int sep = param.indexOf('|');
        String type = sep < 0 ? param : param.substring(0, sep);
        return type.isBlank() ? null : type.trim();
    }

    /** 倍率参数（DAMAGE_TYPE 取 {@code |} 之后部分；其余取参数整体）；解析失败返回 1.0。 */
    public double doubleParam() {
        String number = param;
        if (effectType == HeldItemEffect.DAMAGE_TYPE) {
            int sep = param.indexOf('|');
            if (sep >= 0 && sep + 1 < param.length()) {
                number = param.substring(sep + 1);
            }
        }
        try {
            return Double.parseDouble(number.trim());
        } catch (NumberFormatException ex) {
            return 1.0;
        }
    }

    /** 概率参数（FIRST_STRIKE 的触发百分比 0~100）；解析失败返回 0。 */
    public double chanceParam() {
        try {
            return Double.parseDouble(param);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
