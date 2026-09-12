package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link StatChange} 解析测试：{@code moves.csv} 的 {@code statChanges} 列语法。 */
class StatChangeTest {

    @Test
    void 解析单项声明() {
        List<StatChange> changes = StatChange.parseAll("OPPONENT:DEFENSE:-1");
        assertEquals(1, changes.size());
        StatChange change = changes.get(0);
        assertEquals(StatChange.Recipient.OPPONENT, change.recipient());
        assertEquals(Stat.DEFENSE, change.stat());
        assertEquals(-1, change.delta());
        assertFalse(change.isIncrease());
    }

    @Test
    void 解析自身提升与大小写容错() {
        StatChange change = StatChange.parse(" self : sp_attack : +3 ");
        assertEquals(StatChange.Recipient.SELF, change.recipient());
        assertEquals(Stat.SP_ATTACK, change.stat());
        assertEquals(3, change.delta());
        assertTrue(change.isIncrease());
    }

    @Test
    void 解析多项声明() {
        List<StatChange> changes = StatChange.parseAll("SELF:ATTACK:+1;OPPONENT:DEFENSE:-1");
        assertEquals(2, changes.size());
        assertEquals(Stat.ATTACK, changes.get(0).stat());
        assertEquals(1, changes.get(0).delta());
        assertEquals(Stat.DEFENSE, changes.get(1).stat());
        assertEquals(-1, changes.get(1).delta());
    }

    @Test
    void 空值返回空列表() {
        assertTrue(StatChange.parseAll(null).isEmpty(), "null 应返回空列表");
        assertTrue(StatChange.parseAll("").isEmpty(), "空串应返回空列表");
        assertTrue(StatChange.parseAll("   ").isEmpty(), "空白串应返回空列表");
    }

    @Test
    void 非法项被跳过() {
        assertNull(StatChange.parse("oops"), "缺分隔符应返回 null");
        assertNull(StatChange.parse("SELF:LUCK:+1"), "未知能力项应返回 null");
        assertNull(StatChange.parse("LUCK:ATTACK:+1"), "未知受方应返回 null");
        assertNull(StatChange.parse("SELF:ATTACK:oops"), "非法幅度应返回 null");
        assertNull(StatChange.parse("SELF:ATTACK:0"), "幅度为 0 应返回 null");
        assertNull(StatChange.parse("SELF:ATTACK:+1:-2"), "分段数不为 3 应返回 null");
    }

    @Test
    void 合法项与非法项混在一列时只保留合法项() {
        List<StatChange> changes = StatChange.parseAll("SELF:ATTACK:+1;oops;OPPONENT:DEFENSE:-2;;SELF:SPEED:+1");
        assertEquals(3, changes.size());
        assertEquals(Stat.DEFENSE, changes.get(1).stat());
        assertEquals(-2, changes.get(1).delta());
        assertEquals(Stat.SPEED, changes.get(2).stat());
    }

    @Test
    void 幅度按正负六裁剪() {
        assertEquals(6, StatChange.parse("SELF:ATTACK:+99").delta(), "超量提升裁剪到 +6");
        assertEquals(-6, StatChange.parse("SELF:ATTACK:-99").delta(), "超量降低裁剪到 -6");
    }

    @Test
    void 结果列表不可变且构造校验必填项() {
        List<StatChange> changes = StatChange.parseAll("SELF:ATTACK:+1");
        assertThrows(UnsupportedOperationException.class,
                () -> changes.add(new StatChange(StatChange.Recipient.SELF, Stat.SPEED, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new StatChange(null, Stat.SPEED, 1), "受方不可为空");
        assertThrows(IllegalArgumentException.class,
                () -> new StatChange(StatChange.Recipient.SELF, null, 1), "能力项不可为空");
    }
}
