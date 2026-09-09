package org.example.pokemon.domain;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Player} 的单元测试。
 */
class PlayerTest {

    /** 创建测试用种族。 */
    private static Species createTestSpecies() {
        BaseStats baseStats = new BaseStats(45, 49, 49, 65, 65, 45);
        return new Species("testmon", "测试兽", List.of(ElementType.NORMAL), baseStats,
                16, "testmon-evo", 64, 45, "测试宝可梦", "测试用描述");
    }

    /** 创建个体值全 0、勤奋性格的测试精灵。 */
    private static Pokemon createTestPokemon() {
        Stats ivs = new Stats(0, 0, 0, 0, 0, 0);
        return new Pokemon(createTestSpecies(), 5, ivs, Nature.HARDY);
    }

    @Test
    void testAddPokemon_addsToPartyAndSetsActiveAutomatically() {
        Player player = new Player("tester");
        Pokemon pokemon = createTestPokemon();

        boolean added = player.addPokemon(pokemon);

        assertTrue(added);
        assertEquals(1, player.getPartySize());
        assertEquals(0, player.getActiveIndex());
        assertEquals(pokemon, player.getActive().orElse(null));
    }

    @Test
    void testSwitchActive_switchesToHealthyPokemon() {
        Player player = new Player("tester");
        Pokemon first = createTestPokemon();
        Pokemon second = createTestPokemon();
        player.addPokemon(first);
        player.addPokemon(second);

        boolean switched = player.switchActive(1);

        assertTrue(switched);
        assertEquals(1, player.getActiveIndex());
        assertEquals(second, player.getActive().orElse(null));
    }

    @Test
    void testSwitchActive_failsForFaintedPokemon() {
        Player player = new Player("tester");
        Pokemon first = createTestPokemon();
        Pokemon fainted = createTestPokemon();
        fainted.takeDamage(fainted.getCurrentHp() + 999);
        player.addPokemon(first);
        player.addPokemon(fainted);

        boolean switched = player.switchActive(1);

        assertFalse(switched);
        assertEquals(0, player.getActiveIndex());
        assertEquals(first, player.getActive().orElse(null));
    }

    @Test
    void testSwitchActive_failsForInvalidIndex() {
        Player player = new Player("tester");
        player.addPokemon(createTestPokemon());

        assertFalse(player.switchActive(-1));
        assertFalse(player.switchActive(5));
        assertEquals(0, player.getActiveIndex());
    }

    @Test
    void testGetActive_returnsCorrectActivePokemon() {
        Player player = new Player("tester");
        Pokemon first = createTestPokemon();
        Pokemon second = createTestPokemon();
        player.addPokemon(first);
        player.addPokemon(second);

        assertEquals(first, player.getActive().orElse(null));

        player.switchActive(1);

        assertEquals(second, player.getActive().orElse(null));
    }

    @Test
    void testGetActive_returnsEmptyWhenActiveIsFainted() {
        Player player = new Player("tester");
        Pokemon pokemon = createTestPokemon();
        player.addPokemon(pokemon);
        pokemon.takeDamage(pokemon.getCurrentHp() + 999);

        assertEquals(Optional.empty(), player.getActive());
    }

    @Test
    void testIsPartyFull_returnsTrueWhenSixPokemon() {
        Player player = new Player("tester");

        assertFalse(player.isPartyFull());

        for (int i = 0; i < 6; i++) {
            assertTrue(player.addPokemon(createTestPokemon()));
        }

        assertTrue(player.isPartyFull());
        assertEquals(6, player.getPartySize());
        assertFalse(player.addPokemon(createTestPokemon()));
    }

    @Test
    void testHealParty_healsAllPokemon() {
        Player player = new Player("tester");
        Pokemon fainted = createTestPokemon();
        Pokemon injured = createTestPokemon();
        fainted.takeDamage(fainted.getCurrentHp() + 999);
        injured.takeDamage(10);
        player.addPokemon(fainted);
        player.addPokemon(injured);

        player.healParty();

        assertFalse(fainted.isFainted());
        assertEquals(fainted.getMaxHp(), fainted.getCurrentHp());
        assertEquals(injured.getMaxHp(), injured.getCurrentHp());
        assertEquals(StatusCondition.NONE, fainted.getStatus());
    }
}
