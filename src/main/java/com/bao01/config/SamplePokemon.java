package com.bao01.config;

import com.bao01.model.EffortValues;
import com.bao01.model.Move;
import com.bao01.model.Pokemon;
import com.bao01.model.Stat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.bao01.config.Moves.AGILITY;
import static com.bao01.config.Moves.EMBER;
import static com.bao01.config.Moves.GROWL;
import static com.bao01.config.Moves.ICY_WIND;
import static com.bao01.config.Moves.QUICK_ATTACK;
import static com.bao01.config.Moves.QUICK_HIT;
import static com.bao01.config.Moves.RAIN_DANCE;
import static com.bao01.config.Moves.ROCK_THROW;
import static com.bao01.config.Moves.SANDSTORM;
import static com.bao01.config.Moves.SNOWSCAPE;
import static com.bao01.config.Moves.SUNNY_DAY;
import static com.bao01.config.Moves.TACKLE;
import static com.bao01.config.Moves.TAIL_WHIP;
import static com.bao01.config.Moves.THUNDER_SHOCK;
import static com.bao01.config.Moves.VINE_WHIP;
import static com.bao01.config.Moves.WATER_SHOT;
import static com.bao01.model.PokeType.ELECTRIC;
import static com.bao01.model.PokeType.FIRE;
import static com.bao01.model.PokeType.GRASS;
import static com.bao01.model.PokeType.ICE;
import static com.bao01.model.PokeType.ROCK;
import static com.bao01.model.PokeType.WATER;

/**
 * 示例宝可梦图鉴：六种初始系（火 / 水 / 草 / 电 / 岩 / 冰）。
 *
 * <p>只提供按等级生成的方法，努力值侧重体现速度与主攻项的差异——
 * 速度慢（岩甲龟）与速度快（雷鸣雀）的对局最能体现先手权机制。
 *
 * <p>焰尾狐 / 碧波蛙 / 岩甲龟 / 雪绒狐 各自携带天气招式
 * （大晴天 / 求雨 / 沙暴 / 下雪），用于演示天气系统。
 */
public final class SamplePokemon {

    private SamplePokemon() {
    }

    /** 构造仅设置三项努力值（其余为 0）的便捷工具。 */
    private static EffortValues ev(Stat a, int va, Stat b, int vb, Stat c, int vc) {
        EffortValues evs = new EffortValues();
        evs.set(a, va);
        evs.set(b, vb);
        evs.set(c, vc);
        return evs;
    }

    /** 焰尾狐：火系 特攻型高速（种族值 105 速度），携带 大晴天 强化本系火招。 */
    public static Pokemon flameFox(int level) {
        return new Pokemon("焰尾狐", FIRE, level,
                new int[]{75, 65, 70, 110, 85, 105},
                ev(Stat.SP_ATTACK, 252, Stat.SPEED, 252, Stat.SP_DEFENSE, 6),
                moves(EMBER, QUICK_ATTACK, AGILITY, SUNNY_DAY));
    }

    /** 碧波蛙：水系 特攻耐久（高 HP 高物防），携带 求雨 强化本系水枪。 */
    public static Pokemon waveFrog(int level) {
        return new Pokemon("碧波蛙", WATER, level,
                new int[]{100, 75, 95, 85, 85, 60},
                ev(Stat.HP, 252, Stat.DEFENSE, 252, Stat.SP_DEFENSE, 6),
                moves(WATER_SHOT, VINE_WHIP, AGILITY, RAIN_DANCE));
    }

    /** 青藤蜥：草系 物攻型（105 物攻 / 95 速度），物理压制路线。 */
    public static Pokemon vineLizard(int level) {
        return new Pokemon("青藤蜥", GRASS, level,
                new int[]{80, 105, 70, 70, 75, 95},
                ev(Stat.ATTACK, 252, Stat.SPEED, 252, Stat.SP_DEFENSE, 6),
                moves(VINE_WHIP, QUICK_HIT, QUICK_ATTACK, GROWL));
    }

    /** 雷鸣雀：电系 特攻超高速（120 速度全队最快），靠先手压制。 */
    public static Pokemon thunderBird(int level) {
        return new Pokemon("雷鸣雀", ELECTRIC, level,
                new int[]{70, 70, 60, 110, 70, 120},
                ev(Stat.SP_ATTACK, 252, Stat.SPEED, 252, Stat.HP, 6),
                moves(THUNDER_SHOCK, QUICK_ATTACK, AGILITY, TACKLE));
    }

    /** 岩甲龟：岩石系 物防重装慢速（35 速度最慢），携带 沙暴 提升特防并刮伤对手。 */
    public static Pokemon rockTurtle(int level) {
        return new Pokemon("岩甲龟", ROCK, level,
                new int[]{105, 95, 120, 55, 70, 35},
                ev(Stat.HP, 252, Stat.DEFENSE, 252, Stat.ATTACK, 6),
                moves(ROCK_THROW, QUICK_HIT, TAIL_WHIP, SANDSTORM));
    }

    /** 雪绒狐：冰系 特攻高速，携带 下雪 强化自身物防（冰系物防 ×1.5）。 */
    public static Pokemon frostFox(int level) {
        return new Pokemon("雪绒狐", ICE, level,
                new int[]{75, 70, 65, 110, 85, 110},
                ev(Stat.SP_ATTACK, 252, Stat.SPEED, 252, Stat.HP, 6),
                moves(ICY_WIND, QUICK_ATTACK, AGILITY, SNOWSCAPE));
    }

    private static List<Move> moves(Move... ms) {
        return List.of(ms);
    }

    /**
     * 物种注册表：名称 → 按等级生成函数。
     * 供存档系统按物种名重建宝可梦（同一物种构造方式固定）。
     */
    private static final Map<String, Function<Integer, Pokemon>> REGISTRY = Map.of(
            "焰尾狐", SamplePokemon::flameFox,
            "碧波蛙", SamplePokemon::waveFrog,
            "青藤蜥", SamplePokemon::vineLizard,
            "雷鸣雀", SamplePokemon::thunderBird,
            "岩甲龟", SamplePokemon::rockTurtle,
            "雪绒狐", SamplePokemon::frostFox
    );

    /** 按物种名生成宝可梦；未知物种返回 null。 */
    public static Pokemon create(String speciesName, int level) {
        Function<Integer, Pokemon> factory = REGISTRY.get(speciesName);
        return factory == null ? null : factory.apply(level);
    }

    /** 当前图鉴收录的全部物种名。 */
    public static Set<String> speciesNames() {
        return REGISTRY.keySet();
    }
}
