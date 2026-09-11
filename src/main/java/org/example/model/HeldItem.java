package org.example.model;

import java.util.Objects;

/**
 * 可携带装备（持有道具）定义。
 * <p>装备不是消耗品：每件装备在同一时间只能被一只精灵穿戴，穿戴由 UI/奖励流程
 * 从外部设置到 {@link Pokemon#setHeldItem}，战斗引擎只读取并应用效果。</p>
 *
 * <p>效果类型见 {@link HeldItemEffect}：属性招式威力提升（木炭等）、效果拔群增伤（达人带）、
 * 回合末回血（剩饭）、攻击吸血（贝壳之铃）、概率先手（先制之爪）、未进化双防提升（进化辉石）
 * 等。多段参数统一用 {@code |} 分隔，按段位读取见 {@link #textPart(int)} /
 * {@link #doublePart(int, double)}。</p>
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

    /**
     * 倍率参数。DAMAGE_TYPE 取 {@code |} 之后部分（{@code FIRE|1.2} → 1.2）；
     * 其余效果取参数整体。若参数整体是多段形式（如 SPEED_MULTIPLIER 的
     * {@code 0.5|GROUND}），退化为取首段数值。
     *
     * @return 倍率；解析失败返回 1.0
     */
    public double doubleParam() {
        String number = effectType == HeldItemEffect.DAMAGE_TYPE ? textPart(1) : param;
        try {
            return Double.parseDouble(number.trim());
        } catch (NumberFormatException ex) {
            return doublePart(0, 1.0);
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

    /**
     * 按 {@code |} 切分参数并取第 {@code index} 段原文（0 起）。
     *
     * @return 该段原文（已 trim）；越界或为空串时返回 {@code ""}
     */
    public String textPart(int index) {
        if (param.isEmpty() || index < 0) {
            return "";
        }
        String[] parts = param.split("\\|", -1);
        return index < parts.length ? parts[index].trim() : "";
    }

    /**
     * 按 {@code |} 切分参数并取第 {@code index} 段解析为 double（0 起）。
     *
     * @return 解析结果；越界或解析失败时返回 {@code fallback}
     */
    public double doublePart(int index, double fallback) {
        String part = textPart(index);
        if (part.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(part);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** POISON_HEAL 的毒属性回血比例（param 第 1 段）；解析失败返回 0。 */
    public double healRatio() {
        return doublePart(0, 0);
    }

    /** POISON_HEAL 的非毒属性扣血比例（param 第 2 段）；解析失败返回 0。 */
    public double damageRatio() {
        return doublePart(1, 0);
    }

    /** LIFE_ORB 的招式伤害倍率（param 第 1 段）；解析失败返回 1.0。 */
    public double damageMultiplier() {
        return doublePart(0, 1.0);
    }

    /** LIFE_ORB 的每次命中反伤比例（param 第 2 段）；解析失败返回 0。 */
    public double recoilRatio() {
        return doublePart(1, 0);
    }

    /** CHOICE 的修正项（param 第 1 段，{@code SPECIAL} 或 {@code SPEED}）；缺失返回 {@code ""}。 */
    public String choiceKind() {
        return textPart(0);
    }

    /** CHOICE 的修正倍率（param 第 2 段）；解析失败返回 1.0。 */
    public double choiceMultiplier() {
        return doublePart(1, 1.0);
    }

    /** WEATHER_DURATION 的天气英文名（param 第 1 段）；缺失返回 {@code ""}。 */
    public String weatherParam() {
        return textPart(0);
    }

    /** WEATHER_DURATION 的延长后回合数（param 第 2 段）；解析失败返回 0。 */
    public int durationTurns() {
        return (int) doublePart(1, 0);
    }

    /** END_TURN_STATUS 要施加的异常状态英文名（param 第 1 段）；缺失返回 {@code ""}。 */
    public String statusParam() {
        return textPart(0);
    }

    @Override
    public String toString() {
        return name;
    }
}
