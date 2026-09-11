package org.example.pokemon.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 招式能力等级变化声明的解析测试。
 *
 * <p>这层解析是变化类技能（叫声/硬邦邦/高速移动…）效果的唯一来源：一旦 statChanges
 * 列解析失败，招式进入战斗后会退化成「空变化招」。</p>
 */
class StatChangeTest {

    @Test
    void parse_selfIncrease() {
        StatChange change = StatChange.parse("SELF:DEFENSE:+1");

        assertEquals(StatChange.Recipient.SELF, change.recipient());
        assertEquals(StatModifier.DEFENSE, change.stat());
        assertEquals(1, change.delta());
        assertTrue(change.isIncrease());
    }

    @Test
    void parse_opponentDecrease() {
        StatChange change = StatChange.parse("OPPONENT:ATTACK:-1");

        assertEquals(StatChange.Recipient.OPPONENT, change.recipient());
        assertEquals(StatModifier.ATTACK, change.stat());
        assertEquals(-1, change.delta());
        assertTrue(!change.isIncrease());
    }

    @Test
    void parse_isTolerantToCaseAndSeparators() {
        assertEquals(StatModifier.SP_ATTACK, StatChange.parse("self:sp-attack:2").stat());
        assertEquals(StatModifier.SP_ATTACK, StatChange.parse(" Self : sp_attack : 2 ").stat());
        assertEquals(2, StatChange.parse("self:sp-attack:2").delta());
    }

    @Test
    void parse_rejectsMalformedAndUnsupported() {
        assertNull(StatChange.parse(null));
        assertNull(StatChange.parse(""));
        assertNull(StatChange.parse("SELF:DEFENSE"), "缺少幅度应被拒绝");
        assertNull(StatChange.parse("EVERYONE:DEFENSE:+1"), "未知受方应被拒绝");
        assertNull(StatChange.parse("SELF:LUCK:+1"), "未知能力项应被拒绝");
        assertNull(StatChange.parse("SELF:HP:+1"), "HP 不参与能力等级变化");
        assertNull(StatChange.parse("SELF:DEFENSE:+1.5"), "非整数幅度应被拒绝");
        assertNull(StatChange.parse("SELF:DEFENSE:0"), "零幅度无意义应被拒绝");
    }

    @Test
    void parseAll_splitsAndSkipsInvalidEntries() {
        List<StatChange> changes = StatChange.parseAll("SELF:SPEED:+2;OPPONENT:ATTACK:-1");

        assertEquals(2, changes.size());
        assertEquals(StatModifier.SPEED, changes.get(0).stat());
        assertEquals(2, changes.get(0).delta());
        assertEquals(StatChange.Recipient.OPPONENT, changes.get(1).recipient());
        assertEquals(-1, changes.get(1).delta());
    }

    @Test
    void parseAll_handlesEmptyAndPartiallyInvalidInput() {
        assertTrue(StatChange.parseAll(null).isEmpty());
        assertTrue(StatChange.parseAll("   ").isEmpty());

        List<StatChange> mixed = StatChange.parseAll("SELF:HP:+1;SELF:DEFENSE:+1");
        assertEquals(1, mixed.size(), "非法项应被忽略，合法项保留");
        assertEquals(StatModifier.DEFENSE, mixed.get(0).stat());
    }

    @Test
    void constructor_clampsDeltaIntoLegalRange() {
        assertEquals(StatChange.MAX_DELTA, new StatChange(StatChange.Recipient.SELF, StatModifier.ATTACK, 99).delta());
        assertEquals(-StatChange.MAX_DELTA, new StatChange(StatChange.Recipient.SELF, StatModifier.ATTACK, -99).delta());
    }

    @Test
    void constructor_rejectsMissingRecipientOrStat() {
        assertThrows(IllegalArgumentException.class,
                () -> new StatChange(null, StatModifier.ATTACK, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StatChange(StatChange.Recipient.SELF, null, 1));
    }
}
