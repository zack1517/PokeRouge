package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MoveStatChange} 解析测试：{@code moves.csv} 的 {@code statChanges} 列语法
 * {@code 能力项|±变化量[|SELF]}（多段用 {@code ;} 分隔）。
 */
class MoveStatChangeTest {

    @Test
    void 解析单段作用于目标() {
        List<MoveStatChange> changes = MoveStatChange.parse("DEFENSE|-1");
        assertEquals(1, changes.size());
        MoveStatChange change = changes.get(0);
        assertEquals(StatModifier.DEFENSE, change.stat());
        assertEquals(-1, change.delta());
        assertFalse(change.self(), "省略 SELF 应作用于招式目标");
    }

    @Test
    void 解析单段作用于自身() {
        MoveStatChange change = MoveStatChange.parse("SPEED|2|SELF").get(0);
        assertEquals(StatModifier.SPEED, change.stat());
        assertEquals(2, change.delta());
        assertTrue(change.self());
    }

    @Test
    void 解析多段保持原文顺序() {
        List<MoveStatChange> changes = MoveStatChange.parse("ATTACK|1|SELF;DEFENSE|-1");
        assertEquals(2, changes.size());
        assertEquals(StatModifier.ATTACK, changes.get(0).stat());
        assertTrue(changes.get(0).self());
        assertEquals(StatModifier.DEFENSE, changes.get(1).stat());
        assertFalse(changes.get(1).self());
    }

    @Test
    void 解析容忍大小写与空白并接受正号() {
        MoveStatChange change = MoveStatChange.parse(" sp_attack |+3| self ").get(0);
        assertEquals(StatModifier.SP_ATTACK, change.stat());
        assertEquals(3, change.delta(), "应接受显式 + 号");
        assertTrue(change.self());
    }

    @Test
    void 空输入返回空列表() {
        assertTrue(MoveStatChange.parse(null).isEmpty(), "null 应返回空列表");
        assertTrue(MoveStatChange.parse("").isEmpty(), "空串应返回空列表");
        assertTrue(MoveStatChange.parse("   ").isEmpty(), "空白串应返回空列表");
    }

    @Test
    void 无法识别的片段被静默跳过() {
        assertTrue(MoveStatChange.parse("oops").isEmpty(), "缺分隔符应跳过");
        assertTrue(MoveStatChange.parse("LUCK|1").isEmpty(), "未知能力项应跳过");
        assertTrue(MoveStatChange.parse("ATTACK|oops").isEmpty(), "非法算术值应跳过");
        assertTrue(MoveStatChange.parse("ATTACK|0").isEmpty(), "变化量为 0 应跳过");
        assertTrue(MoveStatChange.parse("HP|1").isEmpty(), "HP 没有能力等级，应跳过");
    }

    @Test
    void 部分脏数据只丢弃脏片段() {
        List<MoveStatChange> changes = MoveStatChange.parse("ATTACK|1;oops;DEFENSE|-2;;SPEED|1|SELF");
        assertEquals(3, changes.size());
        assertEquals(StatModifier.ATTACK, changes.get(0).stat());
        assertEquals(StatModifier.DEFENSE, changes.get(1).stat());
        assertEquals(StatModifier.SPEED, changes.get(2).stat());
    }

    @Test
    void 返回列表不可变() {
        List<MoveStatChange> changes = MoveStatChange.parse("ATTACK|1");
        assertThrows(UnsupportedOperationException.class,
                () -> changes.add(new MoveStatChange(StatModifier.SPEED, 1, false)));
    }
}
