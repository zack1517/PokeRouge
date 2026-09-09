package com.bao01.model;

/**
 * 宝可梦六项基础能力，其中「速度」直接决定对局中的出手先后（先手权）。
 *
 * <p>枚举顺序即种族值 / 努力值 / 能力值的存储顺序，不得随意调换。
 */
public enum Stat {

    HP("HP", "生命"),
    ATTACK("物攻", "攻击"),
    DEFENSE("物防", "防御"),
    SP_ATTACK("特攻", "特攻"),
    SP_DEFENSE("特防", "特防"),
    SPEED("速度", "速度");

    private final String code;
    private final String label;

    Stat(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 短代号，如 HP / 物攻 / 特攻。 */
    public String getCode() {
        return code;
    }

    /** 中文含义标签，如 生命 / 攻击。 */
    public String getLabel() {
        return label;
    }

    /** 是否为生命（HP 的能力公式与其余五项不同）。 */
    public boolean isHp() {
        return this == HP;
    }
}
