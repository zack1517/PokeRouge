package com.bao01.config;

import com.bao01.model.Move;
import com.bao01.model.Stat;
import com.bao01.model.Weather;

import static com.bao01.model.MoveCategory.PHYSICAL;
import static com.bao01.model.MoveCategory.SPECIAL;
import static com.bao01.model.PokeType.ELECTRIC;
import static com.bao01.model.PokeType.FIRE;
import static com.bao01.model.PokeType.GRASS;
import static com.bao01.model.PokeType.ICE;
import static com.bao01.model.PokeType.NORMAL;
import static com.bao01.model.PokeType.ROCK;
import static com.bao01.model.PokeType.WATER;

/**
 * 招式常量库：全部为低威力基础招式，覆盖属性克制、优先度与能力变化三类机制。
 *
 * <p>对战中的出手顺序取决于「行动类别优先级」+「招式优先级」+「速度」：
 * <ul>
 *   <li>道具 / 轮换 / 逃跑 = 行动优先级 4，永远先于普通攻击招式结算；</li>
 *   <li>普通招式优先级默认 1；先制招式（如 电光一闪）优先级 2，必然快于普通招式；</li>
 *   <li>同优先级才比较当前实际速度（含速度等级变化）。</li>
 * </ul>
 */
public final class Moves {

    private Moves() {
    }

    // ---------- 普通系（物理） ----------
    /** 撞击：威力 40 普通物理，命中 100%。 */
    public static final Move TACKLE = new Move("撞击", NORMAL, PHYSICAL, 40);
    /** 迅击：威力 45 普通物理。 */
    public static final Move QUICK_HIT = new Move("迅击", NORMAL, PHYSICAL, 45);

    // ---------- 属性招式（威力 40，配合 STAB 与克制表） ----------
    public static final Move EMBER = new Move("火花", FIRE, SPECIAL, 40);
    public static final Move WATER_SHOT = new Move("水枪", WATER, SPECIAL, 40);
    public static final Move VINE_WHIP = new Move("藤鞭", GRASS, PHYSICAL, 45);
    public static final Move THUNDER_SHOCK = new Move("电击", ELECTRIC, SPECIAL, 40);
    /** 落石：威力 55，命中 90%。 */
    public static final Move ROCK_THROW = new Move("落石", ROCK, PHYSICAL, 55, 0.9);
    /** 冰冻之风：威力 40 冰系特殊。 */
    public static final Move ICY_WIND = new Move("冰冻之风", ICE, SPECIAL, 40);

    // ---------- 先制招式（优先级 2，无视速度差先出手） ----------
    /** 电光一闪：普通物理 40，优先级 2——比所有普通招式先出手。 */
    public static final Move QUICK_ATTACK = Move.damaging("电光一闪", NORMAL, PHYSICAL, 40, 1.0, 2);

    // ---------- 能力变化招式（影响出手顺序 / 伤害项） ----------
    /** 高速移动：提升自身速度 2 级，后续回合可能反超对手获得先手权。 */
    public static final Move AGILITY = Move.buff("高速移动", NORMAL, Stat.SPEED, 2, 1);
    /** 叫声：降低对方物攻 1 级。 */
    public static final Move GROWL = Move.debuff("叫声", NORMAL, Stat.ATTACK, -1, 1.0, 1);
    /** 甩尾：降低对方物防 1 级。 */
    public static final Move TAIL_WHIP = Move.debuff("甩尾", NORMAL, Stat.DEFENSE, -1, 1.0, 1);

    // ---------- 天气招式（变化类，命中 100%，切换全局天气） ----------
    /** 求雨：引发雨天（水 ×1.5 / 火 ×0.5）。 */
    public static final Move RAIN_DANCE = Move.weather("求雨", WATER, Weather.RAIN, 1);
    /** 大晴天：引发晴天（火 ×1.5 / 水 ×0.5）。 */
    public static final Move SUNNY_DAY = Move.weather("大晴天", FIRE, Weather.SUN, 1);
    /** 沙暴：引发沙暴（岩石特防 ×1.5 + 回合末环境伤害）。 */
    public static final Move SANDSTORM = Move.weather("沙暴", ROCK, Weather.SAND, 1);
    /** 下雪：引发雪天（冰物防 ×1.5）。 */
    public static final Move SNOWSCAPE = Move.weather("下雪", ICE, Weather.SNOW, 1);

    /** 六属性招式示例，便于给 AI / 示例宝可梦补齐覆盖度。 */
    public static final Move[] BASIC_ATTACKS = {TACKLE, EMBER, WATER_SHOT, VINE_WHIP, THUNDER_SHOCK, ROCK_THROW};

    /** 天气招式示例。 */
    public static final Move[] WEATHER_MOVES = {RAIN_DANCE, SUNNY_DAY, SANDSTORM, SNOWSCAPE};
}
