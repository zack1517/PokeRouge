package org.example.pokemon.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Nature} 的单元测试。
 */
class NatureTest {

    @Test
    void testGetModifier_increasedStatReturnsOnePointOne() {
        assertEquals(1.1, Nature.ADAMANT.getModifier(StatModifier.ATTACK), 0.0001);
        assertEquals(1.1, Nature.TIMID.getModifier(StatModifier.SPEED), 0.0001);
    }

    @Test
    void testGetModifier_decreasedStatReturnsZeroPointNine() {
        assertEquals(0.9, Nature.ADAMANT.getModifier(StatModifier.SP_ATTACK), 0.0001);
        assertEquals(0.9, Nature.TIMID.getModifier(StatModifier.ATTACK), 0.0001);
    }

    @Test
    void testGetModifier_neutralStatReturnsOnePointZero() {
        assertEquals(1.0, Nature.ADAMANT.getModifier(StatModifier.SPEED), 0.0001);
        assertEquals(1.0, Nature.ADAMANT.getModifier(StatModifier.DEFENSE), 0.0001);
        assertEquals(1.0, Nature.HARDY.getModifier(StatModifier.ATTACK), 0.0001);
        assertEquals(1.0, Nature.HARDY.getModifier(StatModifier.SP_DEFENSE), 0.0001);
    }
}
