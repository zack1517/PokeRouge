package org.example.model;

import java.util.List;
import java.util.Locale;

/**
 * 精灵异常状态（参照原版宝可梦）。
 *
 * <p>分为两类：</p>
 * <ul>
 *     <li><b>主要异常</b>（{@link #isMajor()}）：中毒/剧毒/麻痹/灼伤/睡眠/冰冻。同一时刻只能拥有一种，
 *     战斗结束后仍然保留（换宠不清除），需用解除道具或 {@link Pokemon#cureStatus()} 治疗。</li>
 *     <li><b>挥发性异常</b>（{@link #isVolatile()}）：混乱。可与其他状态共存，换下精灵即清除。</li>
 * </ul>
 *
 * <p>规则（速度/攻击倍率、回合末扣血比例、属性免疫）集中在枚举内，供战斗引擎与界面共用；
 * 契约见接口文档「战斗服务」异常状态章节。</p>
 */
public enum StatusCondition {

    /** 无异常。 */
    NONE("无"),
    /** 中毒：回合末损失最大 HP 的 1/8。 */
    POISON("中毒"),
    /** 剧毒：回合末损失最大 HP 的 n/16，n 逐回合递增（第 1 回合 1/16）。 */
    BADLY_POISON("剧毒"),
    /** 麻痹：速度减半，每回合有 25% 概率完全无法行动。 */
    PARALYSIS("麻痹"),
    /** 灼伤：物理攻击减半，回合末损失最大 HP 的 1/16。 */
    BURN("灼伤"),
    /** 睡眠：连续数回合无法行动，回合数耗尽后自动醒来。 */
    SLEEP("睡眠"),
    /** 冰冻：无法行动，每回合有 20% 概率自行解冻。 */
    FREEZE("冰冻"),
    /** 混乱（挥发性）：每回合有 33% 概率攻击自己，持续数回合后自动解除，换宠立即清除。 */
    CONFUSION("混乱"),
    /** 濒死：HP 归零。 */
    FAINTED("濒死");

    /** 剧毒首次结算的计数（扣 1/16）。 */
    public static final int BADLY_POISON_START = 1;
    /** 睡眠持续回合数范围（含两端）。 */
    public static final int SLEEP_MIN_TURNS = 2;
    public static final int SLEEP_MAX_TURNS = 5;
    /** 混乱持续回合数范围（含两端）。 */
    public static final int CONFUSION_MIN_TURNS = 2;
    public static final int CONFUSION_MAX_TURNS = 5;
    /** 麻痹导致本回合无法行动的概率。 */
    public static final double PARALYSIS_SKIP_CHANCE = 0.25;
    /** 混乱时攻击自己的概率。 */
    public static final double CONFUSION_SELF_HIT_CHANCE = 0.33;
    /** 冰冻状态每回合自行解冻的概率。 */
    public static final double FREEZE_THAW_CHANCE = 0.2;
    /** 混乱自伤按威力 40 的无属性物理招式结算。 */
    public static final int CONFUSION_SELF_HIT_POWER = 40;

    private static final List<StatusCondition> MAJOR = List.of(
            POISON, BADLY_POISON, PARALYSIS, BURN, SLEEP, FREEZE);

    private final String displayName;

    StatusCondition(String displayName) {
        this.displayName = displayName;
    }

    /** 界面对外展示名称（中文）。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 全部主要异常状态（中毒/剧毒/麻痹/灼伤/睡眠/冰冻）。 */
    public static List<StatusCondition> majorStatuses() {
        return MAJOR;
    }

    /** 是否为主要异常（互斥，换宠不清除，战斗结束后保留）。 */
    public boolean isMajor() {
        return MAJOR.contains(this);
    }

    /** 是否为挥发性异常（混乱：可与主要异常共存，换宠清除）。 */
    public boolean isVolatile() {
        return this == CONFUSION;
    }

    /** 该异常是否彻底阻止精灵行动（睡眠/冰冻/濒死；麻痹与混乱按概率判定，见概率常量）。 */
    public boolean preventsAction() {
        return this == SLEEP || this == FREEZE || this == FAINTED;
    }

    /** 速度倍率（麻痹 1/2，其余 1）。 */
    public double speedMultiplier() {
        return this == PARALYSIS ? 0.5 : 1.0;
    }

    /** 物理攻击倍率（灼伤 1/2，其余 1）。 */
    public double attackMultiplier() {
        return this == BURN ? 0.5 : 1.0;
    }

    /**
     * 回合结束时的持续伤害比例（相对最大 HP）。
     *
     * @param badlyPoisonCounter 剧毒计数（{@link #BADLY_POISON} 专用，自 1 起逐回合递增）
     * @return 扣血比例；无持续伤害返回 0
     */
    public double residualDamageRatio(int badlyPoisonCounter) {
        return switch (this) {
            case POISON -> 1.0 / 8.0;
            case BURN -> 1.0 / 16.0;
            case BADLY_POISON -> Math.max(BADLY_POISON_START, badlyPoisonCounter) / 16.0;
            default -> 0.0;
        };
    }

    /** 回合末持续伤害的提示文案（如「中毒」）；无持续伤害返回空串。 */
    public String residualMessage(int badlyPoisonCounter) {
        return switch (this) {
            case POISON -> "中毒";
            case BURN -> "灼伤";
            case BADLY_POISON -> "剧毒";
            default -> "";
        };
    }

    /**
     * 该异常能否施加到指定精灵：属性免疫（电→麻痹、毒→中毒/剧毒、火→灼伤、冰→冰冻）、
     * 已有主要异常不可覆盖、已混乱不可叠加、已倒下的精灵无效。
     */
    public boolean canApply(Pokemon target) {
        if (target == null || target.isFainted() || this == NONE || this == FAINTED) {
            return false;
        }
        if (this == CONFUSION) {
            return !target.isConfused();
        }
        if (target.getStatus() != NONE) {
            return false;
        }
        ElementType immune = immunityType();
        return immune == null || !target.hasType(immune);
    }

    /** 免疫该异常的属性；无属性免疫返回 {@code null}。 */
    public ElementType immunityType() {
        return switch (this) {
            case PARALYSIS -> ElementType.ELECTRIC;
            case POISON, BADLY_POISON -> ElementType.POISON;
            case BURN -> ElementType.FIRE;
            case FREEZE -> ElementType.ICE;
            default -> null;
        };
    }

    /** 按英文枚举名或中文名解析（大小写不敏感）；无法识别返回 {@link #NONE}。 */
    public static StatusCondition parse(String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        String normalized = text.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        for (StatusCondition condition : values()) {
            if (condition.name().equals(upper) || condition.displayName.equals(normalized)) {
                return condition;
            }
        }
        return NONE;
    }
}
