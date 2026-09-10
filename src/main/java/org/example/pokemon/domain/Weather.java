package org.example.pokemon.domain;

public enum Weather {
    NONE("无", "", ""),
    SUNNY("大晴天", "阳光变得强烈了！", "阳光恢复了。"),
    RAIN("下雨", "开始下雨了！", "雨停了。"),
    SANDSTORM("沙暴", "刮起了沙暴！", "沙暴平息了。"),
    HAIL("冰雹", "开始下冰雹了！", "冰雹停了。");

    public static final int DURATION_TURNS = 5;
    private final String displayName;
    private final String startMessage;
    private final String endMessage;

    Weather(String displayName, String startMessage, String endMessage) {
        this.displayName = displayName;
        this.startMessage = startMessage;
        this.endMessage = endMessage;
    }

    public boolean isActive() { return this != NONE; }
    public String getDisplayName() { return displayName; }
    public String getStartMessage() { return startMessage; }
    public String getEndMessage() { return endMessage; }
    public double moveTypeMultiplier(ElementType attackType) {
        if (attackType == null) return 1.0;
        return switch (this) {
            case SUNNY -> attackType == ElementType.FIRE ? 1.5 : attackType == ElementType.WATER ? 0.5 : 1.0;
            case RAIN -> attackType == ElementType.WATER ? 1.5 : attackType == ElementType.FIRE ? 0.5 : 1.0;
            default -> 1.0;
        };
    }
    public double chipRatio() { return this == SANDSTORM || this == HAIL ? 1.0 / 16.0 : 0.0; }
}
