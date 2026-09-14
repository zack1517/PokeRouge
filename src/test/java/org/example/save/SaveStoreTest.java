package org.example.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.example.growth.GrowthProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 存档仓库测试：覆盖 4 个存档位的文件布局、读写、状态与删除。
 *
 * <p>核心不变量有两条：<b>档位之间互不影响</b>（换档位等于换一份进度，图鉴成长也不共享），
 * 以及<b>写盘必须原子</b>（不留半截文件、不留临时文件）。所有用例都在 {@link TempDir}
 * 内进行，不触碰开发者本机的真实存档。</p>
 */
class SaveStoreTest {

    private static SaveData data(String playerName, int segment, int ap) {
        return data(playerName, segment, ap, 150, false, false);
    }

    private static SaveData data(String playerName, int segment, int ap, int gold,
                                boolean gameOver, boolean cleared) {
        return new SaveData(SaveData.FORMAT_VERSION, playerName, 0,
                List.of(new SaveData.PokemonData("bulbasaur", 5,
                        new SaveData.IvData(1, 2, 3, 4, 5, 6), 100L, "NONE", 0, 0, 0, 20,
                        List.of(new SaveData.MoveData("tackle", 30)), List.of(), "")),
                List.of(new SaveData.ItemData("potion", 1)),
                new SaveData.RunRecord(ap, 8, "EXPLORING", 0, gold, gameOver, cleared),
                List.of(), null, segment, "map.png", 1_000L);
    }

    /** 4 个档位固定存在，目录名即档位 id（改名会让玩家丢档，因此要锁住）。 */
    @Test
    void 四个档位与目录命名稳定(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);

        assertEquals(4, SaveSlot.count());
        assertEquals(List.of("slot1", "slot2", "slot3", "slot4"),
                SaveSlot.all().stream().map(SaveSlot::id).toList());
        assertEquals(List.of("存档 1", "存档 2", "存档 3", "存档 4"),
                SaveSlot.all().stream().map(SaveSlot::displayName).toList());
        assertEquals(dir.resolve("slot3"), store.slotDir(SaveSlot.SLOT_3));
        assertEquals("save.txt", store.saveFile(SaveSlot.SLOT_3).getFileName().toString());
        assertEquals("save.prev.txt", store.previousFile(SaveSlot.SLOT_3).getFileName().toString(),
                "「上一个存档点」文件名是回退机制的接口，改名会让旧备份失效");
        assertEquals("growth-progress.txt", store.growthFile(SaveSlot.SLOT_3).getFileName().toString());
        assertEquals(SaveSlot.SLOT_2, SaveSlot.ofId("slot2").orElseThrow());
        assertTrue(SaveSlot.ofId("slot9").isEmpty());
        assertTrue(SaveSlot.ofId(null).isEmpty());
    }

    /** 写出再读回必须逐字段一致。 */
    @Test
    void 写出后可原样读回(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        SaveData original = data("小明", 2, 5);

        store.write(SaveSlot.SLOT_1, original);

        assertTrue(store.exists(SaveSlot.SLOT_1));
        assertEquals(original, store.read(SaveSlot.SLOT_1).orElseThrow());
    }

    /** 空档位读取返回空而不是抛错。 */
    @Test
    void 空档位读取返回空(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);

        assertFalse(store.exists(SaveSlot.SLOT_4));
        assertTrue(store.read(SaveSlot.SLOT_4).isEmpty());
        assertFalse(store.hasAnySave());
    }

    /** 只要有任意一档存在，启动页的「继续游戏」就应可用。 */
    @Test
    void 任一档位有存档即可继续(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        store.write(SaveSlot.SLOT_3, data("小明", 1, 1));

        assertTrue(store.hasAnySave());
    }

    /** 一次写入不得残留临时文件；第二次写入只应多出「上一个存档点」，不能留下 *.tmp。 */
    @Test
    void 写入不残留临时文件(@TempDir Path dir) throws IOException {
        SaveStore store = new SaveStore(dir);

        store.write(SaveSlot.SLOT_1, data("小明", 1, 1));
        assertEquals(List.of("save.txt"), slotFileNames(store),
                "第一次写入没有旧档可备份，目录里只应有快照本身");

        store.write(SaveSlot.SLOT_1, data("小明", 2, 3));
        assertEquals(List.of("save.prev.txt", "save.txt"), slotFileNames(store));
    }

    private static List<String> slotFileNames(SaveStore store) throws IOException {
        try (var files = Files.list(store.slotDir(SaveSlot.SLOT_1))) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    /** 覆盖保存应替换旧内容，而不是追加。 */
    @Test
    void 覆盖保存替换旧内容(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        store.write(SaveSlot.SLOT_1, data("小明", 1, 1));
        store.write(SaveSlot.SLOT_1, data("小红", 4, 9));

        SaveData loaded = store.read(SaveSlot.SLOT_1).orElseThrow();

        assertEquals("小红", loaded.playerName());
        assertEquals(4, loaded.segment());
        assertEquals(9, loaded.run().ap());
    }

    /** 自动创建不存在的父目录（首次运行、手工删过目录都要能用）。 */
    @Test
    void 目录不存在时自动创建(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir.resolve("deep").resolve("nested"));

        store.write(SaveSlot.SLOT_2, data("小明", 1, 1));

        assertTrue(Files.isRegularFile(store.saveFile(SaveSlot.SLOT_2)));
    }

    /** 档位之间完全独立：写一个不影响另一个。 */
    @Test
    void 档位之间互不影响(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        store.write(SaveSlot.SLOT_1, data("小明", 1, 1));

        assertTrue(store.exists(SaveSlot.SLOT_1));
        assertFalse(store.exists(SaveSlot.SLOT_2));
        assertTrue(store.read(SaveSlot.SLOT_2).isEmpty());
    }

    /** 档位状态列表一次给全 4 档，供界面渲染。 */
    @Test
    void 状态列表覆盖全部档位(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        store.write(SaveSlot.SLOT_2, data("小明", 3, 4));

        List<SaveStore.SlotStatus> statuses = store.statuses();

        assertEquals(4, statuses.size());
        SaveStore.SlotStatus first = statuses.get(0);
        assertEquals(SaveSlot.SLOT_1, first.slot());
        assertTrue(first.empty());
        assertFalse(first.exists());
        assertNull(first.summary(), "空档位没有摘要可展示");
        SaveStore.SlotStatus second = statuses.get(1);
        assertEquals(SaveSlot.SLOT_2, second.slot());
        assertTrue(second.exists());
        assertTrue(second.usable());
        assertFalse(second.empty());
        assertEquals("小明", second.summary().playerName());
        assertEquals(4, second.summary().ap());
    }

    /** 损坏的存档在列表里标为「不可用」而不是抛异常 —— 4 个档位必须都能渲染出来。 */
    @Test
    void 损坏存档标为不可读(@TempDir Path dir) throws IOException {
        SaveStore store = new SaveStore(dir);
        Files.createDirectories(store.slotDir(SaveSlot.SLOT_1));
        Files.writeString(store.saveFile(SaveSlot.SLOT_1), "这不是存档", StandardCharsets.UTF_8);

        SaveStore.SlotStatus status = store.status(SaveSlot.SLOT_1);

        assertTrue(status.exists());
        assertFalse(status.empty());
        assertTrue(status.usable(), "摘要解析容错，仍应可在列表里显示并允许覆盖");
        assertEquals("", status.summary().playerName());
        assertThrows(SaveFormatException.class, () -> store.read(SaveSlot.SLOT_1),
                "正式读档必须严格校验，与列表展示的宽松不同");
    }

    /** 删除档位应同时清掉进度、上一个存档点与图鉴成长三份文件（不留能「复活」旧进度的备份）。 */
    @Test
    void 删除档位清空全部文件(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        store.write(SaveSlot.SLOT_1, data("小明", 1, 1));
        store.write(SaveSlot.SLOT_1, data("小明", 2, 2));
        GrowthProgress growth = store.createGrowth(SaveSlot.SLOT_1);
        growth.recordCapture("bulbasaur");
        assertTrue(Files.isRegularFile(store.growthFile(SaveSlot.SLOT_1)));
        assertTrue(store.hasPrevious(SaveSlot.SLOT_1));

        assertTrue(store.delete(SaveSlot.SLOT_1));

        assertFalse(store.exists(SaveSlot.SLOT_1));
        assertFalse(Files.exists(store.growthFile(SaveSlot.SLOT_1)));
        assertFalse(store.hasPrevious(SaveSlot.SLOT_1), "删除档位后不应再留着可回退的备份");
        assertFalse(store.delete(SaveSlot.SLOT_1), "已是空档，再删应返回 false");
    }

    /** 只清进度不清图鉴：同一档位开新一轮时用它（成长要跨轮继承）。 */
    @Test
    void 清除进度保留图鉴成长(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        store.write(SaveSlot.SLOT_1, data("小明", 1, 1));
        store.write(SaveSlot.SLOT_1, data("小明", 2, 2));
        GrowthProgress growth = store.createGrowth(SaveSlot.SLOT_1);
        growth.recordCapture("bulbasaur");

        assertTrue(store.deleteProgress(SaveSlot.SLOT_1));

        assertFalse(store.exists(SaveSlot.SLOT_1));
        assertFalse(store.hasPrevious(SaveSlot.SLOT_1));
        assertEquals(1, store.loadGrowth(SaveSlot.SLOT_1).dexEntries().size(),
                "图鉴成长必须原样保留，这是「同档位反复游玩」的基础");
        assertFalse(store.deleteProgress(SaveSlot.SLOT_1), "没有进度可清时应返回 false");
    }

    /** 每次落盘都把上一份快照留成「上一个存档点」，供读档页「回退一步」。 */
    @Test
    void 保留上一个存档点(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        assertFalse(store.hasPrevious(SaveSlot.SLOT_1), "空档位没有上一个存档点");
        assertTrue(store.readPrevious(SaveSlot.SLOT_1).isEmpty());

        SaveData first = data("小明", 1, 1);
        SaveData second = data("小明", 3, 5);
        store.write(SaveSlot.SLOT_1, first);
        assertFalse(store.hasPrevious(SaveSlot.SLOT_1), "首次写入没有可备份的旧档");

        store.write(SaveSlot.SLOT_1, second);

        assertTrue(store.hasPrevious(SaveSlot.SLOT_1));
        assertEquals(first, store.readPrevious(SaveSlot.SLOT_1).orElseThrow());
        assertEquals(second, store.read(SaveSlot.SLOT_1).orElseThrow(), "当前快照仍是最新那份");
    }

    /** 内容没变的重复落盘不推进上一个存档点，否则回退一步会退到同一份进度。 */
    @Test
    void 重复写入相同进度不推进上一个存档点(@TempDir Path dir) throws IOException {
        SaveStore store = new SaveStore(dir);
        store.write(SaveSlot.SLOT_1, data("小明", 2, 4));
        Files.writeString(store.previousFile(SaveSlot.SLOT_1), "哨兵", StandardCharsets.UTF_8);

        store.write(SaveSlot.SLOT_1, data("小明", 2, 4)); // 只有 SAVED_AT 不同，视为同一份进度

        assertEquals("哨兵", Files.readString(store.previousFile(SaveSlot.SLOT_1), StandardCharsets.UTF_8),
                "无变化的写入不应把当前快照备份成上一个存档点");
    }

    /** 回退：用备份覆盖当前快照并消耗掉备份（同一份备份不能反复用）。 */
    @Test
    void 回退到上一个存档点(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        SaveData first = data("小明", 1, 1);
        store.write(SaveSlot.SLOT_1, first);
        store.write(SaveSlot.SLOT_1, data("小明", 3, 5));

        assertTrue(store.rollbackToPrevious(SaveSlot.SLOT_1));

        assertEquals(first, store.read(SaveSlot.SLOT_1).orElseThrow());
        assertFalse(store.hasPrevious(SaveSlot.SLOT_1), "备份被消耗后不能再退一次");
        assertFalse(store.rollbackToPrevious(SaveSlot.SLOT_1), "没有备份时回退应返回 false");
    }

    /** 备份损坏时回退必须失败且不动当前进度（否则等于把玩家的存档删了）。 */
    @Test
    void 上一个存档点损坏时回退不动当前进度(@TempDir Path dir) throws IOException {
        SaveStore store = new SaveStore(dir);
        store.write(SaveSlot.SLOT_1, data("小明", 1, 1));
        SaveData latest = data("小明", 3, 5);
        store.write(SaveSlot.SLOT_1, latest);
        Files.writeString(store.previousFile(SaveSlot.SLOT_1), "这不是存档", StandardCharsets.UTF_8);

        assertThrows(SaveFormatException.class, () -> store.rollbackToPrevious(SaveSlot.SLOT_1));
        assertEquals(latest, store.read(SaveSlot.SLOT_1).orElseThrow(), "当前进度必须原封不动");
    }

    /** 档位状态要能回答「有没有上一个存档点」，读档页据此决定是否显示回退按钮。 */
    @Test
    void 状态标注是否可回退(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        store.write(SaveSlot.SLOT_2, data("小明", 1, 1));
        assertFalse(store.status(SaveSlot.SLOT_2).canRollback());
        store.write(SaveSlot.SLOT_2, data("小明", 2, 2));

        assertTrue(store.status(SaveSlot.SLOT_2).canRollback());
        assertTrue(store.statuses().get(1).canRollback());
        assertFalse(store.status(SaveSlot.SLOT_1).canRollback(), "空档位当然不可回退");
    }

    /** 摘要要带上「这一轮是否已结束」，档位列表据此把按钮改成「开始新一轮」。 */
    @Test
    void 摘要标注本轮是否已结束(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        store.write(SaveSlot.SLOT_1, data("小明", 1, 1, 150, false, false));
        store.write(SaveSlot.SLOT_2, data("小明", 1, 1, 150, true, false));
        store.write(SaveSlot.SLOT_3, data("小明", 1, 1, 150, false, true));

        assertFalse(store.status(SaveSlot.SLOT_1).finished());
        assertEquals("", store.status(SaveSlot.SLOT_1).summary().finishedText());

        assertTrue(store.status(SaveSlot.SLOT_2).finished());
        assertEquals("本轮已战败", store.status(SaveSlot.SLOT_2).summary().finishedText());

        assertTrue(store.status(SaveSlot.SLOT_3).finished());
        assertEquals("本轮已通关", store.status(SaveSlot.SLOT_3).summary().finishedText());
    }

    /** 每个档位持有自己的图鉴成长：这是「4 个存档各自独立」的直接证据。 */
    @Test
    void 图鉴成长按档位独立(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);
        GrowthProgress slot1 = store.createGrowth(SaveSlot.SLOT_1);
        for (int i = 0; i < 4; i++) {
            slot1.recordCapture("bulbasaur");
        }

        GrowthProgress reloaded1 = store.loadGrowth(SaveSlot.SLOT_1);
        GrowthProgress reloaded2 = store.loadGrowth(SaveSlot.SLOT_2);

        assertEquals(2, reloaded1.ivBonus("bulbasaur"), "档位 1 应有 2 点加成");
        assertEquals(0, reloaded2.ivBonus("bulbasaur"), "档位 2 必须从零开始");
        assertEquals(0, reloaded2.globalIvBonus());
        assertFalse(Files.exists(store.growthFile(SaveSlot.SLOT_2)),
                "只读取不应凭空创建档位 2 的成长文件");
    }

    /** 无成长文件时载入为空档，且文件格式与成长机制既有实现保持一致。 */
    @Test
    void 无成长文件时载入为空档(@TempDir Path dir) {
        SaveStore store = new SaveStore(dir);

        GrowthProgress growth = store.loadGrowth(SaveSlot.SLOT_3);

        assertTrue(growth.dexEntries().isEmpty());
        assertEquals(0, growth.globalIvBonus());
        assertEquals("growth-progress.txt", org.example.growth.GrowthProgressStore.fileName());
    }

    /** 默认根目录必须在用户主目录下的 .pokerouge/saves（重新构建不应丢档）。 */
    @Test
    void 默认根目录位于用户主目录() {
        Path home = Path.of(System.getProperty("user.home", "."));

        Path root = SaveStore.defaultRoot();

        assertEquals(home.resolve(".pokerouge").resolve("saves"), root);
        assertEquals(root, SaveStore.defaultStore().root());
    }
}
