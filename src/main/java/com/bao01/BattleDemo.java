package com.bao01;

import com.bao01.config.SamplePokemon;
import com.bao01.model.Bag;
import com.bao01.model.Battle;
import com.bao01.model.BattleAction;
import com.bao01.model.Item;
import com.bao01.model.Move;
import com.bao01.model.Pokemon;
import com.bao01.model.Team;
import com.bao01.model.Weather;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令行演示：验证 1v1 对战引擎在两种模式下都能正常完结，
 * 并验证逃跑规则（野生可逃、训练家必败）。
 */
public final class BattleDemo {

    private BattleDemo() {
    }

    public static void main(String[] args) {
        System.out.println("===== 自动对战演示：野生宝可梦 =====");
        Battle wild = autoBattle(buildPlayerTeam(), buildFoeTeam(1, 55), Battle.Opponent.WILD);
        System.out.println("结果: " + wild.outcome() + "（共 " + wild.roundNo() + " 回合）");

        System.out.println();
        System.out.println("===== 自动对战演示：训练家 =====");
        Battle trainer = autoBattle(buildPlayerTeam(), buildFoeTeam(4, 55), Battle.Opponent.TRAINER);
        System.out.println("结果: " + trainer.outcome() + "（共 " + trainer.roundNo() + " 回合）");

        System.out.println();
        System.out.println("===== 逃跑规则验证 =====");
        fleeRuleCheck();

        System.out.println();
        weatherRuleCheck();
        System.out.println("全部演示完成。");
    }

    /** 让双方 AI 自动打到分出胜负。 */
    private static Battle autoBattle(Team player, Team foe, Battle.Opponent opp) {
        Battle battle = new Battle(player, foe, opp);
        while (!battle.isOver()) {
            battle.resolveAuto();
        }
        List<String> hist = battle.history();
        int from = Math.max(0, hist.size() - 6);
        for (int i = from; i < hist.size(); i++) {
            System.out.println("  " + hist.get(i));
        }
        return battle;
    }

    private static void fleeRuleCheck() {
        // 训练家对战：逃跑应被拒绝
        Battle vsTrainer = new Battle(buildPlayerTeam(), buildFoeTeam(1, 55), Battle.Opponent.TRAINER);
        Battle.RoundResult rr = vsTrainer.resolve(new BattleAction.Flee());
        System.out.println("对训练家逃跑 -> " + rr.logs().get(0));
        System.out.println("  对局是否结束: " + vsTrainer.isOver() + "（应为 false）");

        // 野生对战：一直尝试逃跑直至成功或对局结束
        Battle vsWild = new Battle(buildPlayerTeam(), buildFoeTeam(1, 55), Battle.Opponent.WILD);
        int tries = 0;
        while (!vsWild.isOver() && vsWild.canFlee() && tries < 20) {
            Battle.RoundResult r = vsWild.resolve(new BattleAction.Flee());
            tries++;
        }
        System.out.println("对野生尝试逃跑 " + tries + " 次 -> 结果: " + vsWild.outcome());
    }

    private static Team buildPlayerTeam() {
        List<Pokemon> mons = new ArrayList<>();
        mons.add(SamplePokemon.flameFox(50));
        mons.add(SamplePokemon.waveFrog(50));
        mons.add(SamplePokemon.thunderBird(50));
        mons.add(SamplePokemon.vineLizard(50));
        mons.add(SamplePokemon.rockTurtle(50));
        return new Team(mons, Bag.startingBag());
    }

    private static Team buildFoeTeam(int count, int level) {
        List<Pokemon> mons = new ArrayList<>();
        mons.add(SamplePokemon.vineLizard(level));
        if (count > 1) {
            mons.add(SamplePokemon.thunderBird(level));
        }
        if (count > 2) {
            mons.add(SamplePokemon.waveFrog(level));
        }
        if (count > 3) {
            mons.add(SamplePokemon.rockTurtle(level));
        }
        return new Team(mons, new Bag());
    }

    // ---------- 天气系统验证 ----------

    private static void weatherRuleCheck() {
        sandstormCheck();
        System.out.println();
        sunRainDamageCheck();
        System.out.println();
        snowCheck();
    }

    /**
     * 沙暴：岩甲龟放沙暴，验证持续 5 回合（含施展回合）与环境伤害——
     * 草系对手每回合受 1/16 固定伤害，岩石系施术者自身免疫。
     */
    private static void sandstormCheck() {
        Pokemon turtle = SamplePokemon.rockTurtle(55);
        Pokemon lizard = SamplePokemon.vineLizard(50);
        Battle b = new Battle(new Team(List.of(turtle), Bag.startingBag()),
                new Team(List.of(lizard), new Bag()), Battle.Opponent.WILD);
        int sand = indexOf(turtle, "沙暴");
        boolean foeChip = false;
        boolean selfChip = false;

        // 第 1 回合：施展沙暴（含本回合共持续 5 回合）
        Battle.RoundResult cast = b.resolve(new BattleAction.Attack(sand));
        boolean activeAfterCast = b.weather() == Weather.SAND;
        for (String line : cast.logs()) {
            foeChip |= line.startsWith("对方") && line.contains("沙暴的伤害");
            selfChip |= line.startsWith("我方") && line.contains("沙暴的伤害");
        }

        // 之后继续结算直至沙暴消散，观测剩余在场回合
        int extra = 0;
        while (extra < 6 && !b.isOver() && b.weather() == Weather.SAND) {
            Battle.RoundResult rr = b.resolve(new BattleAction.Attack(sand));
            extra++;
            for (String line : rr.logs()) {
                foeChip |= line.startsWith("对方") && line.contains("沙暴的伤害");
                selfChip |= line.startsWith("我方") && line.contains("沙暴的伤害");
            }
        }
        int total = activeAfterCast ? extra + 1 : 0;
        System.out.println("沙暴：施展后合计持续 " + total + " 回合（期望 5）");
        System.out.println("  对方(青藤蜥·草) 受环境伤害: " + foeChip + "（应 true）");
        System.out.println("  我方(岩甲龟·岩) 受环境伤害: " + selfChip + "（应 false）");
    }

    /** 晴雨威力倍率：用同一招式对比无天气 / 有天气时的理论伤害。 */
    private static void sunRainDamageCheck() {
        Pokemon fox = SamplePokemon.flameFox(55);
        Pokemon lizard = SamplePokemon.vineLizard(50);
        Battle sun = new Battle(new Team(List.of(fox), Bag.startingBag()),
                new Team(List.of(lizard), new Bag()), Battle.Opponent.WILD);
        int emberIdx = indexOf(fox, "火花");
        int sunnyIdx = indexOf(fox, "大晴天");
        Move ember = fox.move(emberIdx);
        double fireNone = sun.rawDamage(ember, fox, lizard);
        sun.resolve(new BattleAction.Attack(sunnyIdx));
        double fireSun = sun.rawDamage(ember, fox, lizard);
        System.out.printf("晴天：火花(火) 理论伤害 %.0f → %.0f（倍率 %.2f，期望 ~1.5）%n",
                fireNone, fireSun, fireSun / fireNone);

        Pokemon frog = SamplePokemon.waveFrog(55);
        Battle rain = new Battle(new Team(List.of(frog), Bag.startingBag()),
                new Team(List.of(fox), new Bag()), Battle.Opponent.WILD);
        int shotIdx = indexOf(frog, "水枪");
        int rainIdx = indexOf(frog, "求雨");
        Move shot = frog.move(shotIdx);
        double waterNone = rain.rawDamage(shot, frog, fox);
        double emberNone = rain.rawDamage(ember, fox, frog);
        rain.resolve(new BattleAction.Attack(rainIdx));
        double waterRain = rain.rawDamage(shot, frog, fox);
        double emberRain = rain.rawDamage(ember, fox, frog);
        System.out.printf("雨天：水枪(水) 理论伤害 %.0f → %.0f（倍率 %.2f，期望 ~1.5）%n",
                waterNone, waterRain, waterRain / waterNone);
        System.out.printf("雨天：火花(火) 理论伤害 %.0f → %.0f（倍率 %.2f，期望 ~0.5）%n",
                emberNone, emberRain, emberRain / emberNone);
    }

    /** 雪天：冰系雪绒狐物防 ×1.5，受物理招式伤害应约为无雪时的 2/3。 */
    private static void snowCheck() {
        Pokemon fox = SamplePokemon.frostFox(55);
        Pokemon lizard = SamplePokemon.vineLizard(50);
        Battle b = new Battle(new Team(List.of(fox), Bag.startingBag()),
                new Team(List.of(lizard), new Bag()), Battle.Opponent.WILD);
        int vineIdx = indexOf(lizard, "藤鞭");
        int snowIdx = indexOf(fox, "下雪");
        Move vine = lizard.move(vineIdx);
        double none = b.rawDamage(vine, lizard, fox);
        b.resolve(new BattleAction.Attack(snowIdx));
        double snow = b.rawDamage(vine, lizard, fox);
        System.out.printf("雪天：雪绒狐受藤鞭(物理) %.0f → %.0f（倍率 %.2f，期望 ~0.67）%n",
                none, snow, snow / none);
    }

    private static int indexOf(Pokemon p, String moveName) {
        for (int i = 0; i < p.moveCount(); i++) {
            if (p.move(i).getName().equals(moveName)) {
                return i;
            }
        }
        return 0;
    }
}
