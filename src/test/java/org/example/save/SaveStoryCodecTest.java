package org.example.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 存档中剧情线状态（{@code STORY} 行，格式 v2 的可选扩展键）的编解码测试。
 *
 * <p>{@code STORY} 记录《需求文档》§5 的五个标记：是否开启火箭队剧情线、是否已击败首领、
 * 是否已触发神兽偶遇、是否有待触发的 0 点神兽、是否已触发首领侵略战。这里重点验证
 * <b>往返一致</b>与<b>向后兼容</b>——旧文件没有这一行（或字段不全）时必须能正常读回。</p>
 */
class SaveStoryCodecTest {

    private static SaveData withStory(SaveData.StoryRecord story) {
        return new SaveData(SaveData.FORMAT_VERSION, "小明", 0,
                List.of(), List.of(),
                new SaveData.RunRecord(4, 10, "EXPLORING", 0, 320, false, false),
                List.of(), null, 3, null, 1_700_000_000_000L, story);
    }

    /** 五个标记全为真时，写出再读回必须逐字段一致。 */
    @Test
    void 剧情线状态往返一致() {
        SaveData original = withStory(new SaveData.StoryRecord(true, true, true, false, true));

        SaveData parsed = SaveCodec.parse(SaveCodec.render(original));

        assertEquals(original, parsed);
        assertTrue(parsed.story().rocketLineUnlocked());
        assertTrue(parsed.story().rocketBossDefeated());
        assertTrue(parsed.story().legendaryMet());
        assertFalse(parsed.story().pendingLegendary());
        assertTrue(parsed.story().aggressionTriggered());
    }

    /** 剧情线一次也没碰过时不应写出 {@code STORY} 行（避免污染未涉及剧情线的旧档）。 */
    @Test
    void 剧情线全未开启时不写出STORY行() {
        SaveData original = withStory(SaveData.StoryRecord.none());

        String text = SaveCodec.render(original);

        assertFalse(text.contains("STORY"), "全 false 的剧情线状态不应写入存档：\n" + text);
        assertEquals(SaveData.StoryRecord.none(), SaveCodec.parse(text).story());
    }

    /** 缺少 {@code STORY} 行的旧文件读回时按「剧情线未开启」处理。 */
    @Test
    void 旧文件缺少STORY行时按未开启读回() {
        SaveData original = withStory(new SaveData.StoryRecord(true, false, true, false, false));
        String text = SaveCodec.render(original);
        String withoutStory = text.lines()
                .filter(line -> !line.startsWith("STORY|"))
                .reduce("", (a, b) -> a + b + "\n");

        SaveData parsed = SaveCodec.parse(withoutStory);

        assertEquals(SaveData.StoryRecord.none(), parsed.story(), "缺行即视为未开启剧情线");
    }

    /** 字段不全的 {@code STORY} 行按缺失字段为 false 处理，不报错。 */
    @Test
    void STORY行字段缺失时按false处理() {
        SaveData original = withStory(new SaveData.StoryRecord(true, true, true, true, true));
        String text = SaveCodec.render(original);
        String shortened = text.lines()
                .map(line -> line.startsWith("STORY|") ? "STORY|1|0" : line)
                .reduce("", (a, b) -> a + b + "\n");

        SaveData parsed = SaveCodec.parse(shortened);

        assertTrue(parsed.story().rocketLineUnlocked(), "已写出的标记按值读回");
        assertFalse(parsed.story().rocketBossDefeated());
        assertFalse(parsed.story().legendaryMet(), "缺失字段按 false 处理而不是解析失败");
        assertFalse(parsed.story().pendingLegendary());
        assertFalse(parsed.story().aggressionTriggered());
    }

    /** 不含剧情线参数的兼容构造器等价于「剧情线未开启」，保证旧调用点行为不变。 */
    @Test
    void 兼容构造器默认剧情线未开启() {
        SaveData data = new SaveData(SaveData.FORMAT_VERSION, "无名", 0,
                List.of(), List.of(), SaveData.RunRecord.notStarted(),
                List.of(), null, 0, null, 7L);

        assertEquals(SaveData.StoryRecord.none(), data.story());
        assertEquals(data, SaveCodec.parse(SaveCodec.render(data)));
    }
}
