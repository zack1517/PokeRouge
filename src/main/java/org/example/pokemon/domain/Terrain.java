package org.example.pokemon.domain;

public enum Terrain {
    NONE("无", "", ""),
    ELECTRIC("电气场地", "地面被电气覆盖了！", "电气场地的效果消失了。"),
    GRASSY("青草场地", "地面长满了青草！", "青草场地的效果消失了。"),
    MISTY("薄雾场地", "地面被薄雾笼罩了！", "薄雾场地的效果消失了。"),
    PSYCHIC("精神场地", "地面变得神秘莫测！", "精神场地的效果消失了。");

    public static final int DURATION_TURNS = 5;
    private final String displayName;
    private final String startMessage;
    private final String endMessage;

    Terrain(String displayName, String startMessage, String endMessage) {
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
            case ELECTRIC -> attackType == ElementType.ELECTRIC ? 1.3 : 1.0;
            case GRASSY -> attackType == ElementType.GRASS ? 1.3 : 1.0;
            case MISTY -> attackType == ElementType.DRAGON ? 0.5 : 1.0;
            case PSYCHIC -> attackType == ElementType.PSYCHIC ? 1.3 : 1.0;
            default -> 1.0;
        };
    }
    public double healRatio() { return this == GRASSY ? 1.0 / 16.0 : 0.0; }
}
