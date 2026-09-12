package org.example.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 招式标记测试：{@link MoveFlag#parseFlags(String)} 的容错解析（{@code |} 分隔、大小写不敏感、
 * 无法识别的片段忽略、空输入返回空集）与 {@link Move} 的标记查询 API。
 */
class MoveFlagTest {

    /** flags 列解析：正常多标记。 */
    @Test
    void 解析分隔的多标记() {
        Set<MoveFlag> flags = MoveFlag.parseFlags("CONTACT|PUNCH");
        assertEquals(Set.of(MoveFlag.CONTACT, MoveFlag.PUNCH), flags);
    }

    /** 声音标记（批次⑤新增）：SOUND 与其它标记可共存，大小写不敏感。 */
    @Test
    void 解析声音标记() {
        assertEquals(Set.of(MoveFlag.SOUND), MoveFlag.parseFlags("SOUND"));
        assertEquals(Set.of(MoveFlag.SOUND), MoveFlag.parseFlags(" sound "));
        assertEquals(Set.of(MoveFlag.CONTACT, MoveFlag.SOUND), MoveFlag.parseFlags("CONTACT|SOUND"));
    }

    /** 单标记与前后空白都应被容忍（CSV 手写容易多打空格）。 */
    @Test
    void 解析忽略空白与大小写() {
        assertEquals(Set.of(MoveFlag.POWDER), MoveFlag.parseFlags("  POWDER  "));
        assertEquals(Set.of(MoveFlag.POWDER), MoveFlag.parseFlags("powder"));
        assertEquals(Set.of(MoveFlag.CONTACT, MoveFlag.PUNCH), MoveFlag.parseFlags("Contact | punch"));
    }

    /** 空输入返回空集而不是 null，也不抛异常。 */
    @Test
    void 空输入返回空集() {
        assertTrue(MoveFlag.parseFlags(null).isEmpty(), "null 应返回空集");
        assertTrue(MoveFlag.parseFlags("").isEmpty(), "空串应返回空集");
        assertTrue(MoveFlag.parseFlags("   ").isEmpty(), "空白串应返回空集");
        assertTrue(MoveFlag.parseFlags("||").isEmpty(), "只有分隔符应返回空集");
    }

    /** 无法识别的片段被忽略，不抛异常 —— 数据表写错标记名只退化为「无标记」。 */
    @Test
    void 无法识别的标记被忽略() {
        assertEquals(Set.of(MoveFlag.CONTACT), MoveFlag.parseFlags("CONTACT|NOT_A_FLAG"));
        assertTrue(MoveFlag.parseFlags("CONTACTX|PUNCHY").isEmpty(), "前缀相同的错误名称不应被匹配");
    }

    /** 重复标记只保留一份。 */
    @Test
    void 重复标记去重() {
        assertEquals(Set.of(MoveFlag.CONTACT), MoveFlag.parseFlags("CONTACT|CONTACT|contact"));
    }

    /** 返回集合不可变，防止调用方改坏共享实例。 */
    @Test
    void 返回的标记集合不可变() {
        Set<MoveFlag> flags = MoveFlag.parseFlags("CONTACT|PUNCH");
        assertThrows(UnsupportedOperationException.class, () -> flags.add(MoveFlag.POWDER));
    }

    /** 旧 11 参构造器等价于「无标记」，保证既有调用方不受新字段影响。 */
    @Test
    void 旧构造器视为无标记() {
        Move legacy = new Move("tackle", "撞击", ElementType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35);
        assertTrue(legacy.getFlags().isEmpty());
        assertFalse(legacy.isContact());
        assertFalse(legacy.isPunch());
        assertFalse(legacy.isPowder());
    }

    /** 12 参构造器写入标记，三个语义化查询与 hasFlag 保持一致。 */
    @Test
    void 新构造器写入标记() {
        Move punch = new Move("fire-punch", "火焰拳", ElementType.FIRE, MoveCategory.PHYSICAL, 75, 100, 15,
                0, MoveEffect.NONE, StatusCondition.NONE, 0,
                Set.of(MoveFlag.CONTACT, MoveFlag.PUNCH));
        assertTrue(punch.isContact(), "拳类招式同时是接触类");
        assertTrue(punch.isPunch());
        assertFalse(punch.isPowder());
        assertTrue(punch.hasFlag(MoveFlag.CONTACT));
        assertFalse(punch.hasFlag(MoveFlag.POWDER));
        assertEquals(2, punch.getFlags().size());

        Move powder = new Move("sleep-powder", "催眠粉", ElementType.GRASS, MoveCategory.STATUS, 0, 75, 15,
                0, MoveEffect.NONE, StatusCondition.SLEEP, 100, Set.of(MoveFlag.POWDER));
        assertTrue(powder.isPowder());
        assertFalse(powder.isContact());
    }

    /** 构造器对 null 与空集做归一，getFlags() 永不返回 null。 */
    @Test
    void 构造器归一空标记() {
        Move nullFlags = new Move("growl", "叫声", ElementType.NORMAL, MoveCategory.STATUS, 0, 100, 40,
                0, MoveEffect.NONE, StatusCondition.NONE, 0, null);
        assertTrue(nullFlags.getFlags().isEmpty(), "null 应归一为空集");
        assertFalse(nullFlags.isContact());

        Move emptyFlags = new Move("growl", "叫声", ElementType.NORMAL, MoveCategory.STATUS, 0, 100, 40,
                0, MoveEffect.NONE, StatusCondition.NONE, 0, Set.of());
        assertTrue(emptyFlags.getFlags().isEmpty(), "空集应归一为空集");
    }

    /** 调用方传可变集合也应被防御性拷贝，事后修改不影响招式。 */
    @Test
    void 构造器防御性拷贝标记集合() {
        Set<MoveFlag> mutable = new HashSet<>();
        mutable.add(MoveFlag.PUNCH);
        Move move = new Move("ice-punch", "冰冻拳", ElementType.ICE, MoveCategory.PHYSICAL, 75, 100, 15,
                0, MoveEffect.NONE, StatusCondition.NONE, 0, mutable);
        assertTrue(move.isPunch());

        mutable.add(MoveFlag.POWDER);
        assertFalse(move.isPowder(), "外部改动不应影响招式");
        assertThrows(UnsupportedOperationException.class, () -> move.getFlags().add(MoveFlag.CONTACT));
    }
}
