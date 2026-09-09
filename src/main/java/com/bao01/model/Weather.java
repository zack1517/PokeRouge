package com.bao01.model;

/**
 * 对战天气：雨天 / 晴天 / 沙暴 / 雪（空天气为 {@link #NONE}）。
 *
 * <p>天气由对应变化类招式引发（求雨 / 大晴天 / 沙暴 / 下雪），持续
 * {@value com.bao01.config.BattleConfig#WEATHER_TURNS} 回合（含施展回合），
 * 回合结束时倒计时递减；后释放的不同天气会覆盖当前天气并重新计时。
 *
 * <ul>
 *   <li>雨天：水系招式威力 ×1.5，火系 ×0.5；</li>
 *   <li>晴天：火系招式威力 ×1.5，水系 ×0.5；</li>
 *   <li>沙暴：岩石属性宝可梦特防 ×1.5；除岩石 / 地面 / 钢外的宝可梦
 *       每回合末损失 1/16 最大 HP（不会击倒，最低保留 1 HP）；</li>
 *   <li>雪：冰属性宝可梦物防 ×1.5（第 9 世代规则）。</li>
 * </ul>
 *
 * <p>招式修正（本作未收录对应招式故不生效）：雨天雷电/暴风、雪天暴风雪必定命中；
 * 晴天 / 沙暴 / 雪天均会使 日光束/日光刃 等招式变化——本作未收录相关招式。
 */
public enum Weather {

    /** 无天气。 */
    NONE("", "", ""),
    /** 雨天。 */
    RAIN("雨天", "下起了大雨！", "雨渐渐停了……"),
    /** 晴天（大晴天）。 */
    SUN("晴天", "阳光变得强烈了！", "阳光逐渐恢复了平常。"),
    /** 沙暴。 */
    SAND("沙暴", "扬起了沙暴！", "沙暴平息了。"),
    /** 雪（第 9 世代起，游戏内仍按 5 回合统一计时）。 */
    SNOW("雪", "开始下雪了！", "雪停了。");

    private final String label;
    private final String startMessage;
    private final String endMessage;

    Weather(String label, String startMessage, String endMessage) {
        this.label = label;
        this.startMessage = startMessage;
        this.endMessage = endMessage;
    }

    public String getLabel() {
        return label;
    }

    /** 天气刚开始时的提示文案（NONE 为空字符串）。 */
    public String getStartMessage() {
        return startMessage;
    }

    /** 天气结束（自然消退）时的提示文案（NONE 为空字符串）。 */
    public String getEndMessage() {
        return endMessage;
    }

    /**
     * 天气修正火 / 水属性招式的威力倍率：雨天水 ×1.5、火 ×0.5；
     * 晴天火 ×1.5、水 ×0.5；其余天气或属性倍率为 1.0。
     *
     * @param attackType 招式属性
     * @return 天气威力倍率
     */
    public double movePowerModifier(PokeType attackType) {
        if (this == RAIN) {
            if (attackType == PokeType.WATER) {
                return 1.5;
            }
            if (attackType == PokeType.FIRE) {
                return 0.5;
            }
        }
        if (this == SUN) {
            if (attackType == PokeType.FIRE) {
                return 1.5;
            }
            if (attackType == PokeType.WATER) {
                return 0.5;
            }
        }
        return 1.0;
    }

    /**
     * 天气对「防御方能力值」的倍率（只在防御结算生效）：
     * 沙暴下岩石属性特防 ×1.5；雪天下冰属性物防 ×1.5。
     *
     * @param defenderType 防御方属性
     * @param stat         被取用的防御能力项（物防或特防）
     * @return 天气防御倍率
     */
    public double defenseModifier(PokeType defenderType, Stat stat) {
        if (this == SAND && defenderType == PokeType.ROCK && stat == Stat.SP_DEFENSE) {
            return 1.5;
        }
        if (this == SNOW && defenderType == PokeType.ICE && stat == Stat.DEFENSE) {
            return 1.5;
        }
        return 1.0;
    }

    /** 是否需要在回合末对非免疫方造成固定环境伤害（仅沙暴）。 */
    public boolean hasEndOfTurnDamage() {
        return this == SAND;
    }

    /** 该天气下，属性为 {@code type} 的宝可梦是否免疫回合末环境伤害。 */
    public boolean isEndOfTurnDamageImmune(PokeType type) {
        if (type == null) {
            return false;
        }
        // 沙暴：岩石、地面、钢免疫环境伤害。
        return type == PokeType.ROCK || type == PokeType.GROUND || type == PokeType.STEEL;
    }
}
