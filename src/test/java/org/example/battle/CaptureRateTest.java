package org.example.battle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.example.model.ElementType;
import org.example.model.Item;
import org.example.model.ItemCategory;
import org.example.model.Move;
import org.example.model.MoveCategory;
import org.example.model.Player;
import org.example.model.Pokemon;
import org.example.model.Species;
import org.example.model.Stats;
import org.example.model.StatusCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * 捕捉判定测试：捕捉率公式为
 * {@code (3×最大HP − 2×当前HP) ÷ (3×最大HP) × 种族捕获率 × 球倍率 × 状态加成 ÷ 255}。
 *
 * <p>测试数据手工构造并配合固定随机源：种族捕获率取 51，野生精灵满血（HP 系数恰好 1/3），
 * 于是「精灵球(×3)」的成功率为 <b>0.2</b>、「超级球(×5)」为 <b>1/3</b>、
 * 「高级球(×8)」为 <b>0.5333</b>，睡眠/麻痹再整体 ×2。用落在阈值两侧的固定随机数即可
 * 精确断言成败，无需统计采样。</p>
 */
class CaptureRateTest {

    /** 低威力普通系物理技能：让野生精灵能行动但不至于击倒玩家。 */
    private static final Move TAP = new Move("m_tap", "轻拍", ElementType.NORMAL,
            MoveCategory.PHYSICAL, 1, 100, 40);

    /** 高耐久玩家精灵。 */
    private static Pokemon playerPokemon() {
        return Pokemon.create(species("mine_sp", 300), 20, List.of(TAP));
    }

    /** 高耐久野生精灵，避免被捕捉失败后的反击与回合末结算干扰。 */
    private static Pokemon wildPokemon() {
        return Pokemon.create(species("wild_sp", 300), 20, List.of(TAP));
    }

    /** 种族：捕获率固定 51（配合球倍率 3 与满血 HP 系数得到 0.2 的基准成功率）。 */
    private static Species species(String id, int hp) {
        return new Species(id, id, ElementType.NORMAL, null,
                new Stats(hp, 20, 300, 20, 300, 10), 51,
                List.of(), null, 0, Map.of());
    }

    /** 固定随机源：{@code nextDouble} 恒返回同一值，{@code nextInt}/{@code nextBoolean} 取 0。 */
    private static final class FixedRandom extends Random {

        private final double doubleValue;

        FixedRandom(double doubleValue) {
            this.doubleValue = doubleValue;
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public double nextDouble() {
            return doubleValue;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }
    }

    private static Item ball(String id, double effect) {
        return new Item(id, id, ItemCategory.POKE_BALL, effect);
    }

    /** 组织一场野生战斗（敌方为满血野生精灵），玩家背包中已有 1 个待测精灵球。 */
    private static BattleService battleWith(Item ball, double randomValue) {
        return battleWith(ball, randomValue, wildPokemon());
    }

    private static BattleService battleWith(Item ball, double randomValue, Pokemon wild) {
        Player player = new Player("玩家");
        player.addPokemon(playerPokemon());
        player.getBag().add(ball, 1);
        return BattleServices.newBattle(player, wild, new FixedRandom(randomValue));
    }

    @Test
    void 满血野生精灵按血量系数与球倍率判定成败() {
        Item pokeBall = ball("i_poke_ball", 3);
        // 成功率 0.2：随机数 0.1 命中、0.3 落空
        BattleService hit = battleWith(pokeBall, 0.1);
        hit.useItem(pokeBall);
        assertEquals(BattleService.Status.CAUGHT, hit.getStatus());

        BattleService miss = battleWith(pokeBall, 0.3);
        miss.useItem(pokeBall);
        assertEquals(BattleService.Status.ONGOING, miss.getStatus());
        assertTrue(String.join("\n", miss.getLog()).contains("挣脱"), "应提示挣脱");
        assertEquals(0, miss.getBag().countOf(pokeBall), "投球无论成败都消耗");
    }

    @Test
    void 睡眠与麻痹的目标捕捉率加倍() {
        Item pokeBall = ball("i_poke_ball", 3);
        // 基准成功率 0.2，加成后 0.4：随机数 0.3 由「落空」变为「命中」
        Pokemon sleeping = wildPokemon();
        assertTrue(sleeping.tryApplyStatus(StatusCondition.SLEEP, 3));
        BattleService onSleep = battleWith(pokeBall, 0.3, sleeping);
        onSleep.useItem(pokeBall);
        assertEquals(BattleService.Status.CAUGHT, onSleep.getStatus());

        Pokemon paralyzed = wildPokemon();
        assertTrue(paralyzed.tryApplyStatus(StatusCondition.PARALYSIS, 0));
        BattleService onParalysis = battleWith(pokeBall, 0.3, paralyzed);
        onParalysis.useItem(pokeBall);
        assertEquals(BattleService.Status.CAUGHT, onParalysis.getStatus());
    }

    @Test
    void 其他异常状态不提供捕捉加成() {
        Item pokeBall = ball("i_poke_ball", 3);
        Pokemon burned = wildPokemon();
        assertTrue(burned.tryApplyStatus(StatusCondition.BURN, 0));
        BattleService engine = battleWith(pokeBall, 0.3, burned);

        engine.useItem(pokeBall);

        assertEquals(BattleService.Status.ONGOING, engine.getStatus(), "灼伤不提供捕捉加成");
    }

    @Test
    void 球倍率更高者更容易捕捉() {
        Item greatBall = ball("i_great_ball", 5);   // 成功率 1/3
        Item ultraBall = ball("i_ultra_ball", 8);   // 成功率 0.5333

        BattleService great = battleWith(greatBall, 0.3);
        great.useItem(greatBall);
        assertEquals(BattleService.Status.CAUGHT, great.getStatus());

        BattleService ultra = battleWith(ultraBall, 0.5);
        ultra.useItem(ultraBall);
        assertEquals(BattleService.Status.CAUGHT, ultra.getStatus());
    }

    @Test
    void 必定捕捉球无视血量直接成功() {
        Item masterBall = new Item("i_master_ball", "大师球", ItemCategory.POKE_BALL, 255, true);
        BattleService engine = battleWith(masterBall, 0.99);

        engine.useItem(masterBall);

        assertEquals(BattleService.Status.CAUGHT, engine.getStatus());
        assertTrue(engine.getBag().countOf(masterBall) == 0);
    }

    @Test
    void 捕捉成功后野生精灵加入玩家队伍() {
        Item pokeBall = ball("i_poke_ball", 3);
        BattleService engine = battleWith(pokeBall, 0.1);

        engine.useItem(pokeBall);

        assertFalse(engine.getPlayer().getParty().isEmpty());
        assertTrue(engine.getPlayer().getParty().stream()
                        .anyMatch(p -> p.getSpecies().getId().equals("wild_sp")),
                "被捕捉的精灵应入队");
    }

    /** 捕捉成功时向成长端口申报<b>捕捉结算</b>（经验奖励入口），而不是击倒结算。 */
    @Test
    void 捕捉成功时申报捕捉结算() {
        Item masterBall = new Item("i_master_ball", "大师球", ItemCategory.POKE_BALL, 255, true);
        Player player = new Player("玩家");
        player.addPokemon(playerPokemon());
        player.getBag().add(masterBall, 1);
        Pokemon wild = wildPokemon();
        RecordingGrowth growth = new RecordingGrowth();
        BattleService engine = BattleServices.newBattle(player, wild, new FixedRandom(0.99),
                BattleDataPorts.none(), growth);

        engine.useItem(masterBall);

        assertEquals(BattleService.Status.CAUGHT, engine.getStatus());
        assertEquals(List.of(wild), growth.captured, "捕捉成功应申报捕捉结算（含 1.5 倍经验奖励）");
        assertTrue(growth.defeated.isEmpty(), "捕捉成功不应走击倒结算");
    }

    /** 记录成长申报轨迹的端口桩：区分捕捉结算与击倒结算。 */
    private static final class RecordingGrowth implements BattleGrowthPort {

        final List<Pokemon> captured = new ArrayList<>();
        final List<Pokemon> defeated = new ArrayList<>();

        @Override
        public Settlement settle(List<Pokemon> survivors, List<Pokemon> defeated) {
            this.defeated.addAll(defeated);
            return new Settlement(List.of(), List.of());
        }

        @Override
        public Settlement settleCapture(List<Pokemon> survivors, Pokemon caught) {
            captured.add(caught);
            return new Settlement(List.of(), List.of());
        }

        @Override
        public List<String> resolveLearn(BattleService.LearnChoice choice, int forgetSlotIndex) {
            return List.of();
        }
    }
}
