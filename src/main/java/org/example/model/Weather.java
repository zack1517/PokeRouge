package org.example.model;

/**
 * 天气。
 * <p>由天气类变化技能开启，持续 {@link #DURATION_TURNS} 回合（含开启当回合）后自然消退；
 * 战斗场地共享同一份天气。天气会调节对应属性招式的威力，沙暴/冰雹还会对非免疫精灵造成
 * 每回合固定比例的扣血。</p>
 */
public enum Weather {

    /** 无天气。 */
    NONE("无", "", ""),
    /** 大晴天：火系威力 ×1.5、水系威力 ×0.5。 */
    SUNNY("大晴天", "阳光变得强烈了！", "阳光恢复了。"),
    /** 下雨：水系威力 ×1.5、火系威力 ×0.5。 */
    RAIN("下雨", "开始下雨了！", "雨停了。"),
    /** 沙暴：每回合对岩石/地面以外精灵造成最大 HP 的 1/16 伤害。 */
    SANDSTORM("沙暴", "刮起了沙暴！", "沙暴平息了。"),
    /** 冰雹：每回合对冰系以外精灵造成最大 HP 的 1/16 伤害。 */
    HAIL("冰雹", "开始下冰雹了！", "冰雹停了。");

    /** 天气/场地持续回合数（含开启当回合）。 */
    public static final int DURATION_TURNS = 5;

    private final String displayName;
    private final String startMessage;
    private final String endMessage;

    Weather(String displayName, String startMessage, String endMessage) {
        this.displayName = displayName;
        this.startMessage = startMessage;
        this.endMessage = endMessage;
    }

    /** 天气是否处于生效状态（非 NONE）。 */
    public boolean isActive() {
        return this != NONE;
    }

    /** 界面对外展示名称（中文）。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 开启该天气时的广播消息（原版风味台词）。 */
    public String getStartMessage() {
        return startMessage;
    }

    /** 天气消退时的广播消息。 */
    public String getEndMessage() {
        return endMessage;
    }

    /**
     * 天气对「指定属性招式」的威力倍率（未涉及则 1.0）。
     *
     * @param attackType 招式属性
     */
    public double moveTypeMultiplier(ElementType attackType) {
        if (attackType == null) {
            return 1.0;
        }
        return switch (this) {
            case SUNNY -> attackType == ElementType.FIRE ? 1.5
                    : attackType == ElementType.WATER ? 0.5 : 1.0;
            case RAIN -> attackType == ElementType.WATER ? 1.5
                    : attackType == ElementType.FIRE ? 0.5 : 1.0;
            default -> 1.0;
        };
    }

    /** 沙暴每回合固定扣血比例（占最大 HP）。 */
    public double chipRatio() {
        return this == SANDSTORM || this == HAIL ? 1.0 / 16.0 : 0.0;
    }
}
