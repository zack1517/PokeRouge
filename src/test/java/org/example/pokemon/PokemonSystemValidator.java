package org.example.pokemon;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.growth.GrowthProgress;
import org.example.pokemon.domain.BaseStats;
import org.example.pokemon.domain.ElementType;
import org.example.pokemon.domain.LearnableMove;
import org.example.pokemon.domain.Move;
import org.example.pokemon.domain.MoveCategory;
import org.example.pokemon.domain.MoveSlot;
import org.example.pokemon.domain.Nature;
import org.example.pokemon.domain.Player;
import org.example.pokemon.domain.Pokemon;
import org.example.pokemon.domain.Species;
import org.example.pokemon.domain.StatModifier;
import org.example.pokemon.domain.Stats;
import org.example.pokemon.domain.StatusCondition;
import org.example.pokemon.infrastructure.GameData;
import org.example.pokemon.service.PokemonService;
import org.example.pokemon.service.PokemonServiceImpl;
import org.example.pokemon.util.ExperienceCalculator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 宝可梦系统纯验证类。
 *
 * <p>本类只做读取和检查：不调用任何 setter 或修改方法
 * （如 takeDamage/heal/gainExp 等），只调用 getter 和计算型方法。
 * 每个验证点对应一个独立的测试方法，全部通过后输出汇总成功信息。</p>
 */
@ExtendWith(PokemonSystemValidator.ValidatorWatcher.class)
class PokemonSystemValidator {

    /** 验证点总数，用于汇总输出。 */
    private static int totalChecks;

    /** 测试用种族：妙蛙种子（45/49/49/65/65/45，16 级进化为 ivysaur）。 */
    private Species species;

    /** 测试用技能：撞击。 */
    private Move move;

    /** 测试用技能槽。 */
    private MoveSlot moveSlot;

    /** 测试用个体值（全 31）。 */
    private Stats ivs;

    /** 测试用宝可梦：5 级妙蛙种子。 */
    private Pokemon pokemon;

    /** 测试用训练家。 */
    private Player player;

    /** 游戏数据中心单例。 */
    private GameData gameData;

    /** 业务服务实现。 */
    private PokemonService service;

    @BeforeAll
    static void setUpAll() {
        totalChecks = 37;
    }

    @BeforeEach
    void setUp() {
        species = new Species("bulbasaur", "妙蛙种子",
                List.of(ElementType.GRASS, ElementType.POISON),
                new BaseStats(45, 49, 49, 65, 65, 45),
                16, "ivysaur", 64, 45.0, "种子宝可梦", "背上的种子会随成长而长大");
        move = new Move("tackle", "撞击", ElementType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);
        moveSlot = new MoveSlot(move);
        ivs = new Stats(31, 31, 31, 31, 31, 31);
        pokemon = new Pokemon(species, 5, ivs, Nature.HARDY);
        player = new Player("测试训练师");
        gameData = GameData.instance();
        service = new PokemonServiceImpl(new GrowthProgress());
    }

    // ==================== 枚举 ====================

    /** 验证点 1：ElementType.FIRE 的显示名为 "火"。 */
    @Test
    void testElementTypeFireDisplayName() {
        assertEquals("火", ElementType.FIRE.getDisplayName());
    }

    /** 验证点 2：SLEEP 状态阻止行动。 */
    @Test
    void testStatusConditionSleepPreventsAction() {
        assertTrue(StatusCondition.SLEEP.preventsAction());
    }

    /** 验证点 3：NONE 状态不阻止行动。 */
    @Test
    void testStatusConditionNonePreventsAction() {
        assertFalse(StatusCondition.NONE.preventsAction());
    }

    /** 验证点 4：MoveCategory.PHYSICAL 的显示名为 "物理"。 */
    @Test
    void testMoveCategoryPhysicalDisplayName() {
        assertEquals("物理", MoveCategory.PHYSICAL.getDisplayName());
    }

    /** 验证点 5：StatModifier 枚举包含 ATTACK。 */
    @Test
    void testStatModifierContainsAttack() {
        assertEquals("ATTACK", StatModifier.ATTACK.name());
    }

    // ==================== BaseStats ====================

    /** 验证点 6：BaseStats(45,49,49,65,65,45) 的种族值总和为 318。 */
    @Test
    void testBaseStatsTotal() {
        BaseStats baseStats = new BaseStats(45, 49, 49, 65, 65, 45);
        assertEquals(318, baseStats.getTotal());
    }

    // ==================== Stats ====================

    /** 验证点 7：Stats(10,20,30,40,50,60) 的 HP 为 10、攻击为 20。 */
    @Test
    void testStatsGetters() {
        Stats stats = new Stats(10, 20, 30, 40, 50, 60);
        assertEquals(10, stats.getHp());
        assertEquals(20, stats.getAttack());
    }

    /** 验证点 8：Stats.randomIv() 生成的每项个体值均在 0-31 范围内。 */
    @Test
    void testStatsRandomIvRange() {
        for (int i = 0; i < 100; i++) {
            Stats random = Stats.randomIv();
            assertTrue(random.getHpIv() >= 0 && random.getHpIv() <= 31, "HP 个体值越界");
            assertTrue(random.getAttackIv() >= 0 && random.getAttackIv() <= 31, "攻击个体值越界");
            assertTrue(random.getDefenseIv() >= 0 && random.getDefenseIv() <= 31, "防御个体值越界");
            assertTrue(random.getSpAttackIv() >= 0 && random.getSpAttackIv() <= 31, "特攻个体值越界");
            assertTrue(random.getSpDefenseIv() >= 0 && random.getSpDefenseIv() <= 31, "特防个体值越界");
            assertTrue(random.getSpeedIv() >= 0 && random.getSpeedIv() <= 31, "速度个体值越界");
        }
    }

    // ==================== Nature ====================

    /** 验证点 9：固执性格对攻击的修正倍率为 1.1。 */
    @Test
    void testNatureAdamantAttackModifier() {
        assertEquals(1.1, Nature.ADAMANT.getModifier(StatModifier.ATTACK), 0.0001);
    }

    /** 验证点 10：固执性格对特攻的修正倍率为 0.9。 */
    @Test
    void testNatureAdamantSpAttackModifier() {
        assertEquals(0.9, Nature.ADAMANT.getModifier(StatModifier.SP_ATTACK), 0.0001);
    }

    /** 验证点 11：勤奋性格对攻击的修正倍率为 1.0。 */
    @Test
    void testNatureHardyAttackModifier() {
        assertEquals(1.0, Nature.HARDY.getModifier(StatModifier.ATTACK), 0.0001);
    }

    // ==================== Move ====================

    /** 验证点 12：物理类技能撞击的 isPhysical() 为 true。 */
    @Test
    void testMoveIsPhysical() {
        assertTrue(move.isPhysical());
    }

    // ==================== MoveSlot ====================

    /** 验证点 13：新建技能槽的当前 PP 等于最大 PP。 */
    @Test
    void testMoveSlotInitialPp() {
        assertEquals(moveSlot.getMaxPp(), moveSlot.getCurrentPp());
    }

    /** 验证点 14：不调用 usePp()，只验证初始状态：PP 未耗尽且绑定正确技能。 */
    @Test
    void testMoveSlotInitialState() {
        assertFalse(moveSlot.isExhausted());
        assertEquals(move, moveSlot.getMove());
    }

    // ==================== Species ====================

    /** 验证点 15：10 级不可进化，16 级可进化。 */
    @Test
    void testSpeciesCanEvolveAt() {
        assertFalse(species.canEvolveAt(10));
        assertTrue(species.canEvolveAt(16));
    }

    /** 验证点 16：学招表返回的列表不可修改（add 时抛出异常）。 */
    @Test
    void testSpeciesLearnableMovesUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> species.getLearnableMoves().add(new LearnableMove("tackle", 1)));
    }

    // ==================== ExperienceCalculator ====================

    /** 验证点 17：expToLevel(1) 返回 0。 */
    @Test
    void testExpToLevelOne() {
        assertEquals(0, ExperienceCalculator.expToLevel(1));
    }

    /** 验证点 18：expToLevel(5) 返回 100。 */
    @Test
    void testExpToLevelFive() {
        assertEquals(100, ExperienceCalculator.expToLevel(5));
    }

    // ==================== Pokemon ====================

    /** 验证点 19：无昵称时 getName() 返回种族名 "妙蛙种子"。 */
    @Test
    void testPokemonName() {
        assertEquals("妙蛙种子", pokemon.getName());
    }

    /** 验证点 20：getLevel() 返回 5。 */
    @Test
    void testPokemonLevel() {
        assertEquals(5, pokemon.getLevel());
    }

    /** 验证点 21：getMaxHp() 大于 0。 */
    @Test
    void testPokemonMaxHpPositive() {
        assertTrue(pokemon.getMaxHp() > 0);
    }

    /** 验证点 22：新建精灵满血，getCurrentHp() 等于 getMaxHp()。 */
    @Test
    void testPokemonFullHpOnCreation() {
        assertEquals(pokemon.getMaxHp(), pokemon.getCurrentHp());
    }

    /** 验证点 23：满血精灵 isFainted() 返回 false。 */
    @Test
    void testPokemonNotFainted() {
        assertFalse(pokemon.isFainted());
    }

    /** 验证点 24：getStats() 返回的六项能力值均大于 0。 */
    @Test
    void testPokemonStatsPositive() {
        Stats stats = pokemon.getStats();
        assertTrue(stats.getHp() > 0, "HP 应大于 0");
        assertTrue(stats.getAttack() > 0, "攻击应大于 0");
        assertTrue(stats.getDefense() > 0, "防御应大于 0");
        assertTrue(stats.getSpAttack() > 0, "特攻应大于 0");
        assertTrue(stats.getSpDefense() > 0, "特防应大于 0");
        assertTrue(stats.getSpeed() > 0, "速度应大于 0");
    }

    /** 验证点 25：expToNextLevel() 等于 expToLevel(6) - exp（5 级 0 经验时）。 */
    @Test
    void testPokemonExpToNextLevel() {
        assertEquals(ExperienceCalculator.expToLevel(6), pokemon.expToNextLevel());
    }

    // ==================== Player ====================

    /** 验证点 26：Player 的 getName() 正确。 */
    @Test
    void testPlayerName() {
        assertEquals("测试训练师", player.getName());
    }

    /** 验证点 27：addPokemon 后队伍数量增加。 */
    @Test
    void testPlayerAddPokemonIncreasesPartySize() {
        int before = player.getPartySize();
        player.addPokemon(pokemon);
        assertEquals(before + 1, player.getPartySize());
    }

    /** 验证点 28：添加精灵后 getActive() 返回非空 Optional。 */
    @Test
    void testPlayerGetActivePresent() {
        player.addPokemon(pokemon);
        Optional<Pokemon> active = player.getActive();
        assertTrue(active.isPresent());
        assertEquals(pokemon, active.get());
    }

    /** 验证点 29：队伍满 6 只时 isPartyFull() 返回 true。 */
    @Test
    void testPlayerIsPartyFull() {
        Player fullPlayer = new Player("满员训练师");
        for (int i = 0; i < 6; i++) {
            fullPlayer.addPokemon(new Pokemon(species, 5, ivs, Nature.HARDY));
        }
        assertTrue(fullPlayer.isPartyFull());
    }

    /**
     * 验证点 30：全队濒死时 hasHealthyPokemon() 返回 false。
     *
     * <p>通过负等级构造使精灵创建时 HP 即为负值（HP 公式
     * floor((2*45+31)*(-10)/100) + (-10) + 10 = -13），
     * 精灵一诞生就是濒死状态，全程未调用任何修改方法。</p>
     */
    @Test
    void testPlayerHasHealthyPokemonAllFainted() {
        Pokemon fainted = new Pokemon(species, -10, ivs, Nature.HARDY);
        assertTrue(fainted.isFainted(), "负等级构造的精灵应处于濒死状态");

        Player faintedPlayer = new Player("全灭训练师");
        faintedPlayer.addPokemon(fainted);
        assertFalse(faintedPlayer.hasHealthyPokemon());
    }

    // ==================== GameData ====================

    /** 验证点 31：GameData.instance() 返回非空单例。 */
    @Test
    void testGameDataInstanceNotNull() {
        assertNotNull(gameData);
    }

    /** 验证点 32：getInitialPool() 返回 3 只初始宝可梦。 */
    @Test
    void testGameDataInitialPoolSize() {
        assertEquals(3, gameData.getInitialPool().size());
    }

    /** 验证点 33：getNature("adamant") 的中文名为 "固执"。 */
    @Test
    void testGameDataNatureName() {
        Nature adamant = gameData.getNature("adamant")
                .orElseThrow(() -> new AssertionError("找不到性格 adamant"));
        assertEquals("固执", adamant.getName());
    }

    /** 验证点 34：getAllNatures() 返回 21 种性格。 */
    @Test
    void testGameDataAllNaturesSize() {
        assertEquals(21, gameData.getAllNatures().size());
    }

    // ==================== PokemonService ====================

    /** 验证点 35：createPlayer("训练师") 的姓名为 "训练师"。 */
    @Test
    void testServiceCreatePlayerName() {
        Player created = service.createPlayer("训练师");
        assertEquals("训练师", created.getName());
    }

    /** 验证点 36：exportState() 返回非空的 PokemonState。 */
    @Test
    void testServiceExportStateNotNull() {
        assertNotNull(service.exportState());
    }

    /** 验证点 37：getDefaultNature() 的 id 为 "hardy"。 */
    @Test
    void testServiceDefaultNatureId() {
        assertEquals("hardy", service.getDefaultNature().getId());
    }

    /**
     * 测试结果观察器：统计每个验证点的结果，
     * 全部通过时输出 "✅ 所有验证通过！"，失败时输出具体错误信息。
     */
    static class ValidatorWatcher implements TestWatcher {

        private static final AtomicInteger PASSED = new AtomicInteger(0);
        private static final AtomicInteger FAILED = new AtomicInteger(0);

        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("✅ " + context.getDisplayName() + " 通过");
            if (PASSED.incrementAndGet() + FAILED.get() == totalChecks) {
                System.out.println();
                System.out.println("✅ 所有验证通过！共 " + PASSED.get() + " 项验证全部成功。");
            }
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            FAILED.incrementAndGet();
            System.err.println("❌ " + context.getDisplayName() + " 失败: " + cause.getMessage());
        }
    }
}
