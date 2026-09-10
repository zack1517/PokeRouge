package org.example.model;

/**
 * 场地。
 * <p>由场地类变化技能开启，持续 {@link #DURATION_TURNS} 回合（含开启当回合）后自然消退；
 * 战斗场地共享同一份场地。不同场地会调节对应属性招式的威力；青草场地还会让场上的精灵
 * （飞行系除外）每回合回复最大 HP 的 1/16。</p>
 *
 * <p><b>可扩展性</b>：精神/薄雾场地当前对已有属性效果有限（超能力/妖精招式暂缺），
 * 日后新增对应属性招式/精灵后即可自动生效。</p>
 */
public enum Terrain {

    /** 无场地。 */
    NONE("无", "", ""),
    /** 电气场地：电系招式威力 ×1.3。 */
    ELECTRIC("电气场地", "地面被电气覆盖了！", "电气场地的效果消失了。"),
    /** 青草场地：草系招式威力 ×1.3；场上精灵每回合回复 1/16 最大 HP（飞行系除外）。 */
    GRASSY("青草场地", "地面长满了青草！", "青草场地的效果消失了。"),
    /** 薄雾场地：龙系招式威力 ×0.5。 */
    MISTY("薄雾场地", "地面被薄雾笼罩了！", "薄雾场地的效果消失了。"),
    /** 精神场地：超能力系招式威力 ×1.3。 */
    PSYCHIC("精神场地", "地面变得神秘莫测！", "精神场地的效果消失了。");

    /** 天气/场地持续回合数（含开启当回合）。 */
    public static final int DURATION_TURNS = 5;

    private final String displayName;
    private final String startMessage;
    private final String endMessage;

    Terrain(String displayName, String startMessage, String endMessage) {
        this.displayName = displayName;
        this.startMessage = startMessage;
        this.endMessage = endMessage;
    }

    /** 场地是否处于生效状态（非 NONE）。 */
    public boolean isActive() {
        return this != NONE;
    }

    /** 界面对外展示名称（中文）。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 开启该场地时的广播消息（原版风味台词）。 */
    public String getStartMessage() {
        return startMessage;
    }

    /** 场地消退时的广播消息。 */
    public String getEndMessage() {
        return endMessage;
    }

    /**
     * 场地对「指定属性招式」的威力倍率（未涉及则 1.0）。
     *
     * @param attackType 招式属性
     */
    public double moveTypeMultiplier(ElementType attackType) {
        if (attackType == null) {
            return 1.0;
        }
        return switch (this) {
            case ELECTRIC -> attackType == ElementType.ELECTRIC ? 1.3 : 1.0;
            case GRASSY -> attackType == ElementType.GRASS ? 1.3 : 1.0;
            case MISTY -> attackType == ElementType.DRAGON ? 0.5 : 1.0;
            case PSYCHIC -> attackType == ElementType.PSYCHIC ? 1.3 : 1.0;
            default -> 1.0;
        };
    }

    /** 青草场地回合回复比例（占最大 HP）。 */
    public double healRatio() {
        return this == GRASSY ? 1.0 / 16.0 : 0.0;
    }
}
