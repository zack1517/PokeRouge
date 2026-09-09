package com.bao01.model;

/**
 * 招式定义（攻击类与变化类）。
 *
 * <p>关键设计——出手顺序与「优先级」相关：
 * <ul>
 *   <li>普通招式的默认优先级为 {@value #DEFAULT_PRIORITY}；</li>
 *   <li>先制（优先级更高）招式独立于速度出手，优先级越高必然更快；
 *       同优先级时才比较速度决定先后；</li>
 *   <li>部分变化类招式可改变宝可梦的能力等级（如速度），进而影响后续回合的出手顺序。</li>
 * </ul>
 *
 * <p>招式属性 / 威力 / 命中率 / 优先级 / 效果均在构造后不可变。
 */
public final class Move {

    /** 普通招式默认优先级。 */
    public static final int DEFAULT_PRIORITY = 1;

    /** 能力变化的作用对象。 */
    public enum EffectTarget { SELF, OPPONENT }

    /** 能力等级变化效果：对谁、哪项能力、变化几级（正负）。 */
    public record Effect(EffectTarget target, Stat stat, int stageDelta) {
    }

    private final String name;
    private final PokeType type;
    private final MoveCategory category;
    /** 威力：&gt;0 为攻击招式；=0 为变化招式。 */
    private final int power;
    private final double accuracy;
    /** 招式优先级：默认 1，先制招式 &gt;1（数值越大越先出手）。 */
    private final int priority;
    private final Effect effect;
    /** 天气变化：非 null 表示该变化类招式会引发天气（求雨 / 大晴天 / 沙暴 / 下雪）。 */
    private final Weather weather;

    /**
     * 构造默认优先级、无附加效果、命中率 100% 的攻击招式。
     */
    public Move(String name, PokeType type, MoveCategory category, int power) {
        this(name, type, category, power, 1.0, DEFAULT_PRIORITY, null, null);
    }

    /**
     * 构造默认优先级、无附加效果的攻击招式。
     */
    public Move(String name, PokeType type, MoveCategory category, int power, double accuracy) {
        this(name, type, category, power, accuracy, DEFAULT_PRIORITY, null, null);
    }

    private Move(String name, PokeType type, MoveCategory category, int power,
                 double accuracy, int priority, Effect effect, Weather weather) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("招式名不能为空");
        }
        if (type == null || category == null) {
            throw new IllegalArgumentException("招式属性 / 分类不能为空");
        }
        if (power < 0) {
            throw new IllegalArgumentException("威力不能为负: " + power);
        }
        if (power == 0 && effect == null && weather == null) {
            throw new IllegalArgumentException("变化类招式必须带有效果或天气变化");
        }
        if (power > 0 && weather != null) {
            throw new IllegalArgumentException("攻击招式不能附带天气变化");
        }
        if (accuracy <= 0 || accuracy > 1) {
            throw new IllegalArgumentException("命中率必须在 (0,1] 区间: " + accuracy);
        }
        if (priority <= 0) {
            throw new IllegalArgumentException("优先级必须为正整数: " + priority);
        }
        this.name = name;
        this.type = type;
        this.category = category;
        this.power = power;
        this.accuracy = accuracy;
        this.priority = priority;
        this.effect = effect;
        this.weather = weather;
    }

    /** 带指定优先级的攻击招式。 */
    public static Move damaging(String name, PokeType type, MoveCategory category,
                                int power, double accuracy, int priority) {
        if (power <= 0) {
            throw new IllegalArgumentException("攻击招式威力必须为正: " + power);
        }
        return new Move(name, type, category, power, accuracy, priority, null, null);
    }

    /** 提升己方某项能力等级的招式（默认命中率 100%）。 */
    public static Move buff(String name, PokeType type, Stat stat, int stageDelta, int priority) {
        if (stageDelta <= 0) {
            throw new IllegalArgumentException("buff 必须提升能力: " + stageDelta);
        }
        return new Move(name, type, MoveCategory.STATUS, 0, 1.0, priority,
                new Effect(EffectTarget.SELF, stat, stageDelta), null);
    }

    /** 降低对方某项能力等级的招式。 */
    public static Move debuff(String name, PokeType type, Stat stat, int stageDelta, double accuracy, int priority) {
        if (stageDelta >= 0) {
            throw new IllegalArgumentException("debuff 必须降低能力: " + stageDelta);
        }
        return new Move(name, type, MoveCategory.STATUS, 0, accuracy, priority,
                new Effect(EffectTarget.OPPONENT, stat, stageDelta), null);
    }

    /**
     * 引发天气的变化类招式（求雨 / 大晴天 / 沙暴 / 下雪）。
     * 命中率 100%，优先级可自定，不可同时携带能力等级效果。
     *
     * @param weather 引发的天气，必须非 {@link Weather#NONE}
     */
    public static Move weather(String name, PokeType type, Weather weather, int priority) {
        if (weather == null || weather == Weather.NONE) {
            throw new IllegalArgumentException("天气招式必须指定具体天气: " + weather);
        }
        return new Move(name, type, MoveCategory.STATUS, 0, 1.0, priority, null, weather);
    }

    public String getName() {
        return name;
    }

    public PokeType getType() {
        return type;
    }

    public MoveCategory getCategory() {
        return category;
    }

    /** 威力；&gt;0 表示攻击招式。 */
    public int getPower() {
        return power;
    }

    /** 命中率，1.0 表示 100%。 */
    public double getAccuracy() {
        return accuracy;
    }

    /** 招式优先级：越大越先出手（默认 1，先制招式更高）。 */
    public int getPriority() {
        return priority;
    }

    public Effect getEffect() {
        return effect;
    }

    /** 天气变化；非 null 表示该变化类招式会引发天气。 */
    public Weather getWeather() {
        return weather;
    }

    /** 是否为造成伤害的攻击招式。 */
    public boolean isDamaging() {
        return power > 0;
    }

    /** 是否带能力等级变化效果。 */
    public boolean hasEffect() {
        return effect != null;
    }

    /** 是否为引发天气的变化类招式。 */
    public boolean isWeatherMove() {
        return weather != null;
    }

    /** 是否为先制招式（优先级高于普通默认值）。 */
    public boolean isPriority() {
        return priority > DEFAULT_PRIORITY;
    }

    private String describeEffect() {
        if (effect == null) {
            return "";
        }
        Stat stat = effect.stat();
        String arrow = effect.stageDelta() > 0 ? "↑" : "↓";
        int amount = Math.abs(effect.stageDelta());
        String statName = stat.isHp() ? "HP" : stat.getLabel();
        return (effect.target() == EffectTarget.SELF ? "自身" : "对方")
                + statName + arrow + amount;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name).append('(')
                .append(type.getLabel()).append('·');
        if (isDamaging()) {
            sb.append(category.getLabel()).append("·威力").append(power);
        } else if (isWeatherMove()) {
            sb.append("变化·天气[").append(weather.getLabel()).append(']');
        } else {
            sb.append(describeEffect());
        }
        if (isPriority()) {
            sb.append("·先制").append(priority);
        }
        return sb.append(')').toString();
    }
}
