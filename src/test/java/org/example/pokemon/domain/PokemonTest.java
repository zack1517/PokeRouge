package org.example.pokemon.domain;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Pokemon} 的单元测试。
 */
class PokemonTest {

    /** 创建测试用种族：种族值 45/49/49/65/65/45，16 级进化为 testmon-evo。 */
    private static Species createTestSpecies() {
        BaseStats baseStats = new BaseStats(45, 49, 49, 65, 65, 45);
        return new Species("testmon", "测试兽", List.of(ElementType.NORMAL), baseStats,
                16, "testmon-evo", 64, 45, "测试宝可梦", "测试用描述");
    }

    /** 创建个体值全 0、勤奋性格的测试精灵。 */
    private static Pokemon createTestPokemon(int level) {
        Stats ivs = new Stats(0, 0, 0, 0, 0, 0);
        return new Pokemon(createTestSpecies(), level, ivs, Nature.HARDY);
    }

    @Test
    void testCreatePokemon_initializesAllFieldsCorrectly() {
        Pokemon pokemon = createTestPokemon(5);

        assertNotNull(pokemon.getUuid());
        assertFalse(pokemon.getUuid().isEmpty());
        assertEquals("testmon", pokemon.getSpecies().getId());
        assertEquals("测试兽", pokemon.getName());
        assertEquals(5, pokemon.getLevel());
        assertEquals(0, pokemon.getExp());
        assertEquals(StatusCondition.NONE, pokemon.getStatus());
        assertEquals(Nature.HARDY, pokemon.getNature());
        assertTrue(pokemon.getMoveSlots().isEmpty());
        assertEquals(pokemon.getMaxHp(), pokemon.getCurrentHp());
    }

    @Test
    void testCreatePokemon_nullNatureFallsBackToHardy() {
        Pokemon pokemon = new Pokemon(createTestSpecies(), 5, new Stats(0, 0, 0, 0, 0, 0), null);

        assertEquals(Nature.HARDY, pokemon.getNature());
    }

    @Test
    void testTakeDamage_reducesHpCorrectly() {
        Pokemon pokemon = createTestPokemon(5);
        int before = pokemon.getCurrentHp();

        pokemon.takeDamage(10);

        assertEquals(before - 10, pokemon.getCurrentHp());
        assertFalse(pokemon.isFainted());
    }

    @Test
    void testTakeDamage_hpZeroMarksFainted() {
        Pokemon pokemon = createTestPokemon(5);

        pokemon.takeDamage(pokemon.getCurrentHp() + 999);

        assertEquals(0, pokemon.getCurrentHp());
        assertEquals(StatusCondition.FAINTED, pokemon.getStatus());
        assertTrue(pokemon.isFainted());
    }

    @Test
    void testHeal_restoresHpWithoutExceedingMaxHp() {
        Pokemon pokemon = createTestPokemon(5);
        pokemon.takeDamage(5);
        int damagedHp = pokemon.getCurrentHp();

        pokemon.heal(3);

        assertEquals(damagedHp + 3, pokemon.getCurrentHp());

        pokemon.heal(9999);

        assertEquals(pokemon.getMaxHp(), pokemon.getCurrentHp());
    }

    @Test
    void testHeal_faintedPokemonCannotBeHealed() {
        Pokemon pokemon = createTestPokemon(5);
        pokemon.takeDamage(pokemon.getCurrentHp() + 999);

        pokemon.heal(50);

        assertEquals(0, pokemon.getCurrentHp());
        assertTrue(pokemon.isFainted());
    }

    @Test
    void testFullHeal_restoresFullHpAndClearsStatus() {
        Pokemon pokemon = createTestPokemon(5);
        pokemon.takeDamage(pokemon.getCurrentHp() + 999);
        assertEquals(StatusCondition.FAINTED, pokemon.getStatus());

        pokemon.fullHeal();

        assertEquals(pokemon.getMaxHp(), pokemon.getCurrentHp());
        assertEquals(StatusCondition.NONE, pokemon.getStatus());
        assertFalse(pokemon.isFainted());
    }

    @Test
    void testGainExp_increasesExpAndTriggersLevelUp() {
        Pokemon pokemon = createTestPokemon(1);

        // medium-fast 曲线：升到 2 级需要 expToLevel(2) = 6 经验
        pokemon.gainExp(6);

        assertEquals(6, pokemon.getExp());
        assertEquals(2, pokemon.getLevel());
        assertEquals(pokemon.getMaxHp(), pokemon.getCurrentHp());
    }

    @Test
    void testGainExp_supportsMultipleLevelUps() {
        Pokemon pokemon = createTestPokemon(1);

        // expToLevel(10) = 800，expToLevel(11) = 1065
        pokemon.gainExp(1000);

        assertEquals(1000, pokemon.getExp());
        assertEquals(10, pokemon.getLevel());
    }

    @Test
    void testGetStats_calculatesCorrectStatValues() {
        // 5 级、个体值全 0、勤奋性格（无修正）
        // HP = floor((2*45+0)*5/100) + 5 + 10 = 19
        // 攻击/防御/速度 = floor(floor((2*49+0)*5/100)+5) = 9
        // 特攻/特防 = floor(floor((2*65+0)*5/100)+5) = 11
        Pokemon pokemon = createTestPokemon(5);

        Stats stats = pokemon.getStats();

        assertEquals(19, stats.getHp());
        assertEquals(9, stats.getAttack());
        assertEquals(9, stats.getDefense());
        assertEquals(11, stats.getSpAttack());
        assertEquals(11, stats.getSpDefense());
        assertEquals(9, stats.getSpeed());
    }

    @Test
    void testGetStats_appliesNatureModifier() {
        // 固执性格：攻击 1.1 倍、特攻 0.9 倍
        // 攻击 = floor(9 * 1.1) = 9；特攻 = floor(11 * 0.9) = 9
        Pokemon pokemon = new Pokemon(createTestSpecies(), 5,
                new Stats(0, 0, 0, 0, 0, 0), Nature.ADAMANT);

        Stats stats = pokemon.getStats();

        assertEquals(9, stats.getAttack());
        assertEquals(9, stats.getSpAttack());
    }

    @Test
    void testIsFainted_returnsTrueWhenHpIsZero() {
        Pokemon pokemon = createTestPokemon(5);

        assertFalse(pokemon.isFainted());

        pokemon.takeDamage(pokemon.getCurrentHp() + 999);

        assertTrue(pokemon.isFainted());
    }
}
