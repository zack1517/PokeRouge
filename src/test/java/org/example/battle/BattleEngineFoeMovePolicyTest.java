package org.example.battle;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import org.example.model.ElementType;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.MoveSlot;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.Trainer;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * 敌方选招 AI 倾向测试：
 * <ul>
 *   <li>野生对战：对手使用一次变化类招式后，伤害类招式的选择概率提升；</li>
 *   <li>训练家 / 火箭队 / 道馆主对战：使用一次变化类招式后，下一次<b>必定</b>使用伤害类招式，
 *       此后变化类招式的选择权重持续降低。</li>
 * </ul>
 *
 * <p>玩家用必中且无效果的「原地不动」陪跑、不攻击敌方，战斗可无限持续；随机源用
 * {@link ScriptedRandom} 精确控制敌方每一次选招，从而断言 AI 倾向本身而非运气。</p>
 */
class BattleEngineFoeMovePolicyTest {

    /** 变化类招式：叫声。 */
    private static final Move GROWL = new Move("m_growl", "叫声", ElementType.NORMAL,
            MoveCategory.STATUS, 0, 100, 999);
    /** 伤害类招式：撞击。 */
    private static final Move TACKLE = new Move("m_tackle", "撞击", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 40, 100, 999);
    /** 玩家陪跑招式：必中、无效果、不消耗随机数。 */
    private static final Move IDLE = new Move("m_idle", "原地不动", ElementType.NORMAL,
            MoveCategory.STATUS, 0, -1, 999);

    /** 脚本化随机源：{@code nextInt}/{@code nextDouble} 按入队顺序消费预设值，耗尽后恒返回 0。 */
    private static final class ScriptedRandom extends Random {

        private final Queue<Integer> ints = new ArrayDeque<>();
        private final Queue<Double> doubles = new ArrayDeque<>();

        void enqueueInt(int value) {
            ints.add(value);
        }

        void enqueueDouble(double value) {
            doubles.add(value);
        }

        @Override
        public int nextInt(int bound) {
            return ints.isEmpty() ? 0 : Math.min(ints.remove(), bound - 1);
        }

        @Override
        public double nextDouble() {
            return doubles.isEmpty() ? 0 : doubles.remove();
        }
    }

    private static Species species(String id, int hp, int attack, int defense, int speed) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(hp, attack, defense, attack, defense, speed), 51,
                List.of(), null, 0, Map.of());
    }

    /** 玩家：高速高耐久，陪跑招式不造成伤害、不会被敌方击倒。 */
    private static Pokemon idlePlayer() {
        return Pokemon.create(species("mine_sp", 100000, 10, 40, 200), 20, List.of(IDLE));
    }

    /** 敌方：低速高耐久，携带 1 个变化类（叫声）+ 1 个伤害类（撞击）。 */
    private static Pokemon foeWithGrowlAndTackle() {
        return Pokemon.create(species("foe_sp", 300, 20, 40, 10), 20, List.of(GROWL, TACKLE));
    }

    private static Player playerWith(Pokemon pokemon) {
        Player player = new Player("玩家");
        player.addPokemon(pokemon);
        return player;
    }

    private static String joinLog(BattleService battle) {
        return String.join("\n", battle.getLog());
    }

    /**
     * 野生对战：第 1 回合敌方等概率选招选中叫声（变化类），第 2 回合应提升伤害类概率。
     * 若第 2 回合仍走等概率（{@code nextInt(2)} 回退 0），会选中叫声而断言失败。
     */
    @Test
    void 野生对战使用变化类招式后提升伤害类招式概率() {
        Player player = playerWith(idlePlayer());
        Pokemon wild = foeWithGrowlAndTackle();
        ScriptedRandom random = new ScriptedRandom();
        random.enqueueInt(0);    // 第 1 回合等概率选招：index 0 → 叫声（变化类）
        random.enqueueDouble(0.5); // 第 2 回合加权：0.5*3 < 2 → 伤害类
        random.enqueueDouble(0.5); // 撞击的伤害浮动
        BattleService battle = BattleServices.newBattle(player, wild, random);
        MoveSlot idle = player.getActive().getMoveSlots().get(0);

        battle.useMove(idle);
        assertTrue(joinLog(battle).contains("使用了【叫声】"),
                "第 1 回合敌方应使用变化类招式：" + joinLog(battle));

        battle.useMove(idle);
        String log2 = joinLog(battle);
        assertTrue(log2.contains("使用了【撞击】"),
                "使用变化类后应提升伤害类概率（本回合应选伤害类）：" + log2);
    }

    /**
     * 训练师对战：使用变化类后下一次<b>必定</b>伤害类（不掷权重骰），
     * 再下一次（上次为伤害类）变化类按低权重仍可被选中。
     *
     * <p>若第 2 回合错走加权路径，{@code nextDouble(0.9)} 会选中叫声；若第 3 回合错走
     * 「永远必定伤害类」，会选中撞击——两种退化都会被断言拦下。</p>
     */
    @Test
    void 训练师对战变化类后下一次必定伤害类且之后变化类权重降低() {
        Player player = playerWith(idlePlayer());
        Trainer trainer = new Trainer("道馆馆主");
        trainer.addPokemon(foeWithGrowlAndTackle());
        ScriptedRandom random = new ScriptedRandom();
        random.enqueueInt(0);    // 第 1 回合等概率选招：index 0 → 叫声
        random.enqueueDouble(0.9); // 第 2 回合撞击伤害浮动（「必定伤害类」路径不消耗选招随机数）
        random.enqueueDouble(0.9); // 第 3 回合加权：0.9*1.5 >= 1 → 变化类
        BattleService battle = BattleServices.newTrainerBattle(player, trainer, random);
        MoveSlot idle = player.getActive().getMoveSlots().get(0);

        battle.useMove(idle);
        assertTrue(joinLog(battle).contains("使用了【叫声】"),
                "第 1 回合敌方应使用变化类招式：" + joinLog(battle));

        battle.useMove(idle);
        String log2 = joinLog(battle);
        assertTrue(log2.contains("使用了【撞击】"),
                "变化类后下一次应必定使用伤害类招式：" + log2);

        battle.useMove(idle);
        String log3 = joinLog(battle);
        assertTrue(log3.contains("使用了【叫声】"),
                "上次是伤害类后不再必定伤害类，变化类按低权重仍可被选中：" + log3);
    }

    /**
     * 训练师对战趋势统计：100 回合中变化类招式出现次数应显著低于等概率的 50 次，
     * 证明「使用过变化类后其权重持续降低」确实生效。
     */
    @Test
    void 训练师对战中变化类招式使用频率显著低于等概率() {
        Player player = playerWith(idlePlayer());
        Trainer trainer = new Trainer("火箭队队员");
        trainer.addPokemon(foeWithGrowlAndTackle());
        BattleService battle = BattleServices.newTrainerBattle(player, trainer, new Random(42));
        MoveSlot idle = player.getActive().getMoveSlots().get(0);

        for (int i = 0; i < 100; i++) {
            battle.useMove(idle);
        }

        long growlUses = battle.getLog().stream()
                .filter(line -> line.contains("使用了【叫声】"))
                .count();
        assertTrue(growlUses > 0, "敌方应至少使用过一次变化类招式");
        assertTrue(growlUses < 40,
                "使用过变化类后其权重应显著降低（100 回合中叫声出现 " + growlUses + " 次，应远低于等概率的 50 次）");
    }
}
