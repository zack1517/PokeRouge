package org.example.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 存档文本编解码测试。
 *
 * <p>编解码是存档系统唯一的「不可见契约」：玩家可以手工编辑存档文件，程序升级也可能写入新字段，
 * 因此这里重点覆盖<b>往返一致性</b>（写出去再读回来必须一模一样）、<b>向前兼容</b>（未知键跳过）
 * 与<b>损坏拒绝</b>（格式错就明确报错，绝不猜测解析）。</p>
 *
 * <p>纯字符串操作，不涉及磁盘。</p>
 */
class SaveCodecTest {

    private static SaveData sample() {
        return new SaveData(SaveData.FORMAT_VERSION, "小明", 1,
                List.of(
                        new SaveData.PokemonData("bulbasaur", 12,
                                new SaveData.IvData(31, 20, 15, 10, 5, 0),
                                340L, "POISON", 2, 3, 1, 27,
                                List.of(new SaveData.MoveData("tackle", 30),
                                        new SaveData.MoveData("vine-whip", 7)),
                                List.of(), "e_life_orb"),
                        new SaveData.PokemonData("charmander", 10,
                                new SaveData.IvData(12, 12, 12, 12, 12, 12),
                                0L, "NONE", 0, 0, 0, 30, List.of(), List.of(), "")),
                List.of(new SaveData.ItemData("potion", 3),
                        new SaveData.ItemData("poke-ball", 5)),
                new SaveData.RunRecord(4, 10, "EXPLORING", 0, 320, true, false),
                List.of(new SaveData.OptionData("野生遭遇", "WILD", 0, "遇到一只野生精灵", true),
                        new SaveData.OptionData("医院", "HOSPITAL", 1, "回复全队")),
                new SaveData.OptionData("道馆战", "GYM", 0, "行动点耗尽后必然触发"),
                3, "map/forest.png", 1_700_000_000_000L);
    }

    /** 完整快照写出再读回必须逐字段一致（这是存档系统的根本契约）。 */
    @Test
    void 完整快照往返一致() {
        SaveData original = sample();

        SaveData parsed = SaveCodec.parse(SaveCodec.render(original));

        assertEquals(original, parsed);
    }

    /** 携带装备 id 必须随文本往返；未携带的精灵写出空串并原样读回。 */
    @Test
    void 携带装备id随文本往返() {
        SaveData original = sample();

        String text = SaveCodec.render(original);
        SaveData parsed = SaveCodec.parse(text);

        assertEquals("e_life_orb", parsed.party().get(0).heldItemId());
        assertEquals("", parsed.party().get(1).heldItemId(), "未携带时应为空串");
    }

    /** 旧档的 PP 行没有装备字段（少一段）：按未携带解析，不能读档失败。 */
    @Test
    void 旧档缺少装备字段仍可读() {
        String legacy = SaveCodec.render(sample()).replace("|e_life_orb", "");

        SaveData parsed = SaveCodec.parse(legacy);

        assertEquals("", parsed.party().get(0).heldItemId());
        assertEquals("e_life_orb", sample().party().get(0).heldItemId(),
                "原快照仍应带装备，确保上面的替换确实生效");
    }

    /** 可选字段（地图背景、必然节点）为 null 时不应写出该行，读回仍是 null。 */
    @Test
    void 可选字段为空时往返仍为null() {
        SaveData original = new SaveData(SaveData.FORMAT_VERSION, "无名", 0,
                List.of(), List.of(), SaveData.RunRecord.notStarted(),
                List.of(), null, 0, null, 42L);

        SaveData parsed = SaveCodec.parse(SaveCodec.render(original));

        assertNull(parsed.mapBackground(), "未抽取地图背景应保持 null，而不是被占位符顶替");
        assertNull(parsed.mandatoryOption());
        assertEquals(original, parsed);
    }

    /** 中文、竖线、反斜杠与换行都必须能安全往返（否则存档一旦含分隔符就会解析错位）。 */
    @Test
    void 特殊字符被正确转义并还原() {
        String tricky = "小|明\\ 换\n行";
        SaveData original = new SaveData(SaveData.FORMAT_VERSION, tricky, 0,
                List.of(new SaveData.PokemonData("bulbasaur", 5, null, 0L, null, 0, 0, 0, 1,
                        List.of(new SaveData.MoveData("tackle", 1)), List.of(), "")),
                List.of(new SaveData.ItemData("potion", 1)),
                new SaveData.RunRecord(1, 8, "EXPLORING", 0, 0, false, false),
                List.of(new SaveData.OptionData(tricky, "WILD", 0, tricky)),
                null, 1, null, 1L);

        String text = SaveCodec.render(original);
        SaveData parsed = SaveCodec.parse(text);

        assertEquals(tricky, parsed.playerName());
        assertEquals(tricky, parsed.options().get(0).name());
        assertEquals(tricky, parsed.options().get(0).description());
        assertEquals(original, parsed);
        // 换行被转义成字面 \n，因此队伍与背包的行数不会被撑破
        assertEquals(10, recordLines(text), "一条记录一行，转义不应破坏行结构：\n" + text);
    }

    /** 统计正文记录条数（跳过格式头与格式提示）。 */
    private static long recordLines(String text) {
        return text.lines().filter(line -> !line.startsWith("#")).count();
    }

    /** 技能库（含未出战技能）必须随存档往返，且未出战的技能写入独立的 KMOVE 行。 */
    @Test
    void 技能库随存档往返() {
        SaveData original = new SaveData(SaveData.FORMAT_VERSION, "小明", 0,
                List.of(new SaveData.PokemonData("bulbasaur", 12,
                        new SaveData.IvData(1, 1, 1, 1, 1, 1),
                        0L, "NONE", 0, 0, 0, 30,
                        List.of(new SaveData.MoveData("tackle", 10)),
                        List.of("tackle", "vine-whip", "razor-leaf"), "")),
                List.of(), SaveData.RunRecord.notStarted(), List.of(), null, 0, null, 1L);

        String text = SaveCodec.render(original);
        SaveData parsed = SaveCodec.parse(text);

        assertEquals(List.of("tackle", "vine-whip", "razor-leaf"),
                parsed.party().get(0).knownMoves(), "技能库完整往返");
        assertTrue(text.contains("KMOVE|0|tackle"), "出战技能同样写入 KMOVE 行：\n" + text);
        assertTrue(text.contains("KMOVE|0|vine-whip"), "未出战的技能写入 KMOVE 行：\n" + text);
        assertTrue(text.contains("KMOVE|0|razor-leaf"), "多招未出战技能逐行写入：\n" + text);
    }

    /** 旧档（技能库机制之前）没有 KMOVE 行：解析层保持空列表（由映射层按出战技能兜底）。 */
    @Test
    void 旧档无技能库行时保持空列表() {
        String text = String.join("\n",
                "VERSION|" + SaveData.FORMAT_VERSION,
                "PLAYER|小明",
                "PP|bulbasaur|5|1|1|1|1|1|1|0|NONE|0|0|0|20",
                "MOVE|0|0|tackle|8",
                "RUN|1|8|EXPLORING|0|0|0|0");

        SaveData parsed = SaveCodec.parse(text);

        assertEquals(List.of(), parsed.party().get(0).knownMoves(),
                "旧档技能库为空，映射层按出战技能兜底");
    }

    /** 未知键必须被跳过：老程序读到新版本多写的字段不能崩。 */
    @Test
    void 未知键被跳过() {
        String text = String.join("\n",
                "VERSION|" + SaveData.FORMAT_VERSION,
                "未来的字段|一些值|另一些",
                "PLAYER|小明",
                "FUTURE_BLOCK|a|b|c");

        SaveData parsed = SaveCodec.parse(text);

        assertEquals("小明", parsed.playerName());
        assertEquals(SaveData.FORMAT_VERSION, parsed.version());
    }

    /** 注释与空行被忽略，手工加注释不影响读取。 */
    @Test
    void 注释与空行被忽略() {
        String text = String.join("\n",
                "# PokeRouge 存档 v1",
                "",
                "   ",
                "VERSION|1",
                "# 这是注释",
                "PLAYER|小明");

        SaveData parsed = SaveCodec.parse(text);

        assertEquals("小明", parsed.playerName());
    }

    /** 缺少版本行说明这不是本作的存档，必须明确拒绝而不是按默认版本硬解。 */
    @Test
    void 缺少版本行时抛出格式异常() {
        String text = "PLAYER|小明\nRUN|3|0|0";

        SaveFormatException e = assertThrows(SaveFormatException.class,
                () -> SaveCodec.parse(text));

        assertTrue(e.getMessage().contains("VERSION"), e.getMessage());
    }

    /** 版本高于本程序支持范围时应提示升级游戏，而不是猜着读。 */
    @Test
    void 版本过高时抛出格式异常() {
        String text = "VERSION|" + (SaveData.FORMAT_VERSION + 1) + "\nPLAYER|小明";

        SaveFormatException e = assertThrows(SaveFormatException.class,
                () -> SaveCodec.parse(text));

        assertTrue(e.getMessage().contains("升级游戏"), e.getMessage());
    }

    /** 内容为空或全是注释时视为损坏。 */
    @Test
    void 空内容抛出格式异常() {
        assertThrows(SaveFormatException.class, () -> SaveCodec.parse(""));
        assertThrows(SaveFormatException.class, () -> SaveCodec.parse("   \n  "));
        assertThrows(SaveFormatException.class, () -> SaveCodec.parse(null));
        assertThrows(SaveFormatException.class, () -> SaveCodec.parse("# 只有注释"));
    }

    /** 数值字段非法时明确指出行号，便于玩家定位手改错的地方。 */
    @Test
    void 数值非法时抛出的异常带行号() {
        String text = String.join("\n",
                "VERSION|1",
                "SEGMENT|不是数字");

        SaveFormatException e = assertThrows(SaveFormatException.class,
                () -> SaveCodec.parse(text));

        assertTrue(e.getMessage().contains("第 2 行"), e.getMessage());
    }

    /** 技能按队伍下标归并到各自精灵，顺序保持写入顺序。 */
    @Test
    void 技能按队伍下标归并() {
        SaveData parsed = SaveCodec.parse(SaveCodec.render(sample()));

        assertEquals(List.of("tackle", "vine-whip"),
                parsed.party().get(0).moves().stream().map(SaveData.MoveData::moveId).toList());
        assertEquals(7, parsed.party().get(0).moves().get(1).pp());
        assertTrue(parsed.party().get(1).moves().isEmpty(), "第二只没有技能行，应为空列表");
    }

    /** MOVE 行的下标越界（指向不存在的精灵）时该行被忽略，不能让整档报废。 */
    @Test
    void 越界的技能行被忽略() {
        String text = String.join("\n",
                "VERSION|1",
                "PP|bulbasaur|5|1|1|1|1|1|1|0|NONE|0|0|0|20",
                "MOVE|9|0|tackle|30");

        SaveData parsed = SaveCodec.parse(text);

        assertEquals(1, parsed.party().size());
        assertTrue(parsed.party().get(0).moves().isEmpty());
    }

    /** 摘要只挑需要的字段，因此损坏得只剩几行的存档也能给出可读信息。 */
    @Test
    void 摘要可在不完整内容上工作() {
        String text = String.join("\n",
                "PLAYER|小明",
                "SEGMENT|2",
                "RUN|5|10|EXPLORING|0|320|0|0",
                "SAVED_AT|1700000000000",
                "PP|bulbasaur|5|1|1|1|1|1|1|0|NONE|0|0|0|20",
                "PP|charmander|5|1|1|1|1|1|1|0|NONE|0|0|0|20");

        SaveSummary summary = SaveCodec.parseSummary(SaveSlot.SLOT_2, text);

        assertEquals(SaveSlot.SLOT_2, summary.slot());
        assertEquals("小明", summary.playerName());
        assertEquals(2, summary.segment());
        assertEquals(5, summary.ap());
        assertEquals(320, summary.gold());
        assertEquals(2, summary.partySize());
        assertEquals(1_700_000_000_000L, summary.savedAtMillis());
        assertTrue(summary.describe().contains("小明"), summary.describe());
        assertTrue(summary.describe().contains("行动点 5"), summary.describe());
    }

    /** v1 旧档（RUN 行只有楼层 / 点数 / 是否结束三个字段）仍要能读出摘要。 */
    @Test
    void 摘要兼容v1旧档的run行() {
        SaveSummary summary = SaveCodec.parseSummary(SaveSlot.SLOT_3,
                "PLAYER|旧档\nSEGMENT|3\nRUN|7|11|0");

        assertEquals(3, summary.segment());
        assertEquals(11, summary.ap(), "旧档的「点数」即行动点");
        assertEquals(0, summary.gold(), "旧档没有金币概念，按 0 显示");
    }

    /** 摘要对非法数值采取兜底而非抛错（列表必须能把 4 个档位都渲染出来）。 */
    @Test
    void 摘要对非法数值使用兜底值() {
        SaveSummary summary = SaveCodec.parseSummary(SaveSlot.SLOT_1,
                "SEGMENT|abc\nRUN|xyz|0|0\nSAVED_AT|nope");

        assertEquals(0, summary.segment(), "无法识别段号时按尚未开始处理");
        assertEquals(0, summary.ap());
        assertEquals(0, summary.gold());
        assertEquals(0L, summary.savedAtMillis());
        assertEquals("时间未知", summary.savedAtText());
        assertTrue(summary.describe().contains("未开始远征"), summary.describe());
    }

    /** 渲染结果含格式头，便于人工辨认文件用途。 */
    @Test
    void 渲染结果含文件头() {
        String text = SaveCodec.render(sample());

        assertTrue(text.startsWith(SaveCodec.HEADER), text.substring(0, Math.min(40, text.length())));
        assertTrue(text.contains("VERSION|" + SaveData.FORMAT_VERSION));
    }

    /** v1 旧档（三字段 RUN、五字段 OPTION、无 consumed 标记）必须仍可读，缺失字段取默认值。 */
    @Test
    void v1旧档仍可读取并迁移动到行动点() {
        String text = String.join("\n",
                "VERSION|1",
                "SEGMENT|2",
                "RUN|6|9|1",
                "OPTION|野生遭遇|WILD|1|遇到一只野生精灵");

        SaveData parsed = SaveCodec.parse(text);

        assertEquals(1, parsed.version(), "旧档版本号原样保留，不做假升级");
        assertEquals(9, parsed.run().ap(), "旧档的点数迁移为行动点");
        assertEquals(9, parsed.run().apMax(), "旧档没有上限概念，按当前行动点补齐上限");
        assertTrue(parsed.run().gameOver(), "旧档的结束标记保留");
        assertEquals("", parsed.run().phase(), "旧档没有阶段字段");
        assertEquals(0, parsed.run().gold());
        assertFalse(parsed.options().get(0).consumed(), "缺少 consumed 字段时按未走过处理");
    }

    /** 定界符切分与转义还原（包私有工具，直接验证边界行为）。 */
    @Test
    void 定界符切分与转义还原() {
        assertEquals(List.of("a", "b", "c"), SaveCodec.splitFields("a|b|c"));
        assertEquals(List.of("a|b", "\\", "\n"), SaveCodec.splitFields("a\\|b|\\\\|\\n"));
        assertEquals("", SaveCodec.escape(null));
        assertEquals("a\\|b", SaveCodec.escape("a|b"));
        assertFalse(SaveCodec.splitFields("a|b").isEmpty());
    }
}
