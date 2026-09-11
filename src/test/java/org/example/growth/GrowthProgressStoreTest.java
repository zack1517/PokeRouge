package org.example.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 成长进度本地存档测试：个体值成长是<b>本地单机永久</b>机制，必须能落盘并在
 * 「重启」后原样恢复；同时加成必须受上限约束，且任何磁盘异常都不得中断游戏。
 *
 * <p>所有用例都在 {@link TempDir} 内进行，不触碰开发者本机的真实存档。</p>
 */
class GrowthProgressStoreTest {

    private static final String SPECIES_ID = "bulbasaur";

    private static Path fileIn(Path dir) {
        return dir.resolve("growth-progress.txt");
    }

    /** 无存档文件时按新档处理：载入为空，且不报错、不创建多余文件。 */
    @Test
    void 无存档文件时载入为空档(@TempDir Path dir) {
        Path file = fileIn(dir);

        GrowthProgress progress = GrowthProgressStore.at(file).loadProgress();

        assertTrue(progress.dexEntries().isEmpty());
        assertEquals(0, progress.globalIvBonus());
        assertFalse(Files.exists(file), "只读取不应凭空创建存档文件");
    }

    /** 捕捉与对战次数都能原样保存再读回。 */
    @Test
    void 保存后重新载入保持计数不变(@TempDir Path dir) {
        Path file = fileIn(dir);
        GrowthProgress first = GrowthProgressStore.at(file).loadProgress();
        first.recordCapture(SPECIES_ID);
        first.recordCapture(SPECIES_ID);
        first.recordCapture(SPECIES_ID);
        first.recordBattle(SPECIES_ID);
        first.recordBattle(SPECIES_ID);

        GrowthProgress reloaded = GrowthProgressStore.at(file).loadProgress();

        assertEquals(3, reloaded.captureCount(SPECIES_ID));
        assertEquals(2, reloaded.battleCount(SPECIES_ID));
        assertEquals(1, reloaded.ivBonus(SPECIES_ID));
    }

    /**
     * 核心用例：进度变化立即落盘，因此「关闭程序再打开」等于换一个 store 重新载入同一文件 ——
     * 这是「永久提升」的行为证据。
     */
    @Test
    void 进度变化立即落盘并可跨重启恢复(@TempDir Path dir) {
        Path file = fileIn(dir);
        GrowthProgress session1 = GrowthProgressStore.at(file).loadProgress();
        for (int i = 0; i < 8; i++) {
            session1.recordCapture(SPECIES_ID);
        }
        assertTrue(Files.isRegularFile(file), "捕捉后应立即生成存档文件");

        // 模拟关闭程序后重新启动：同一文件、新的 store 与新的进度表
        GrowthProgress session2 = GrowthProgressStore.at(file).loadProgress();

        assertEquals(8, session2.captureCount(SPECIES_ID));
        assertEquals(4, session2.globalIvBonus());
    }

    /** 多族进度互相独立，且全局加成为各族之和。 */
    @Test
    void 多族进度与全局加成跨重启保持(@TempDir Path dir) {
        Path file = fileIn(dir);
        GrowthProgress session1 = GrowthProgressStore.at(file).loadProgress();
        for (int i = 0; i < 6; i++) {
            session1.recordCapture("bulbasaur");
        }
        for (int i = 0; i < 4; i++) {
            session1.recordCapture("charmander");
        }

        GrowthProgress session2 = GrowthProgressStore.at(file).loadProgress();

        assertEquals(3, session2.ivBonus("bulbasaur"));
        assertEquals(2, session2.ivBonus("charmander"));
        assertEquals(5, session2.globalIvBonus());
    }

    /** 存档文本应含格式头，且按物种 id 排序（人类可读、便于调试）。 */
    @Test
    void 存档为可读文本且按物种排序(@TempDir Path dir) throws IOException {
        Path file = fileIn(dir);
        GrowthProgress progress = GrowthProgressStore.at(file).loadProgress();
        progress.recordCapture("squirtle");
        progress.recordCapture("bulbasaur");

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String body = String.join("\n", lines);

        assertTrue(body.contains("PokeRouge 成长进度"), "应写入格式头：" + body);
        assertTrue(body.contains("bulbasaur=1,0"), "应写入记录行：" + body);
        assertTrue(body.contains("squirtle=1,0"));
        assertTrue(body.indexOf("bulbasaur") < body.indexOf("squirtle"), "记录应按物种 id 排序");
    }

    /** 注释行、空行被忽略，不影响读取。 */
    @Test
    void 注释与空行被忽略(@TempDir Path dir) throws IOException {
        Path file = fileIn(dir);
        Files.writeString(file, String.join(System.lineSeparator(),
                "# 注释",
                "",
                "   ",
                "bulbasaur=4,1"), StandardCharsets.UTF_8);

        GrowthProgress progress = GrowthProgressStore.at(file).loadProgress();

        assertEquals(1, progress.dexEntries().size());
        assertEquals(4, progress.captureCount("bulbasaur"));
        assertEquals(1, progress.battleCount("bulbasaur"));
    }

    /** 单行损坏只丢该行，不能整档报废（手改存档容错）。 */
    @Test
    void 损坏行被跳过而不影响其它记录(@TempDir Path dir) throws IOException {
        Path file = fileIn(dir);
        Files.writeString(file, String.join(System.lineSeparator(),
                "bulbasaur=4,1",
                "这不是合法行",
                "=,2",
                "charmander=abc,1",
                "squirtle=2",
                "pikachu=6,0"), StandardCharsets.UTF_8);

        GrowthProgress progress = GrowthProgressStore.at(file).loadProgress();

        assertEquals(4, progress.captureCount("bulbasaur"));
        assertEquals(6, progress.captureCount("pikachu"));
        assertEquals(2, progress.dexEntries().size(), "仅两条合法记录："
                + progress.dexEntries());
    }

    /** 手工把次数改到远超上限，游戏内加成仍封顶 —— 上限由规则层强制，不依赖存档内容。 */
    @Test
    void 存档中的超量次数仍受加成上限约束(@TempDir Path dir) throws IOException {
        Path file = fileIn(dir);
        Files.writeString(file, "bulbasaur=9999,0", StandardCharsets.UTF_8);

        GrowthProgress progress = GrowthProgressStore.at(file).loadProgress();

        assertEquals(9999, progress.captureCount("bulbasaur"));
        assertEquals(IvGrowthRule.MAX_IV, progress.ivBonus("bulbasaur"));
        assertEquals(IvGrowthRule.MAX_IV, progress.globalIvBonus());
    }

    /** 全零记录不写入存档，避免文件里堆满无用行。 */
    @Test
    void 空记录不写入存档(@TempDir Path dir) throws IOException {
        Path file = fileIn(dir);
        GrowthProgressStore store = GrowthProgressStore.at(file);
        store.write(List.of(
                SpeciesGrowthRecord.empty("bulbasaur"),
                new SpeciesGrowthRecord("charmander", 0, 2)));

        String body = Files.readString(file, StandardCharsets.UTF_8);

        assertFalse(body.contains("bulbasaur="), "全零记录不应写入：" + body);
        assertTrue(body.contains("charmander=0,2"), "对战-only 记录应保留：" + body);
    }

    /** 目标目录不存在时自动创建（首次运行即可用）。 */
    @Test
    void 目标目录不存在时自动创建(@TempDir Path dir) {
        Path nested = dir.resolve("a").resolve("b").resolve("growth-progress.txt");

        GrowthProgress progress = GrowthProgressStore.at(nested).loadProgress();
        progress.recordCapture(SPECIES_ID);

        assertTrue(Files.isRegularFile(nested), "应自动创建父目录并写入存档");
        assertEquals(1, GrowthProgressStore.at(nested).loadProgress().captureCount(SPECIES_ID));
    }

    /** 重开存档：clear() 既清空内存也清空本地文件，重启后仍是空的。 */
    @Test
    void 清空进度同时清空存档文件(@TempDir Path dir) throws IOException {
        Path file = fileIn(dir);
        GrowthProgress progress = GrowthProgressStore.at(file).loadProgress();
        progress.recordCapture(SPECIES_ID);
        progress.recordBattle(SPECIES_ID);

        progress.clear();

        GrowthProgress reloaded = GrowthProgressStore.at(file).loadProgress();
        assertTrue(reloaded.dexEntries().isEmpty());
        assertEquals(0, reloaded.globalIvBonus());
        assertFalse(Files.readString(file, StandardCharsets.UTF_8).contains("bulbasaur="),
                "存档文件不应残留记录");
    }

    /** 存档写入失败（父路径是文件而非目录）时不得抛出异常，降级为仅内存。 */
    @Test
    void 写入失败不抛出异常(@TempDir Path dir) throws IOException {
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "占位文件", StandardCharsets.UTF_8);

        GrowthProgress progress = GrowthProgressStore.at(blocker.resolve("growth-progress.txt"))
                .loadProgress();

        int count = progress.recordCapture(SPECIES_ID);

        assertEquals(1, count, "落盘失败时内存进度仍应正常累加");
        assertEquals(1, progress.captureCount(SPECIES_ID));
        assertEquals(0, progress.globalIvBonus(), "1 次捕捉尚不给加成");
    }

    /** 默认存档位置必须在用户主目录下，而不是工程目录（重新构建不应丢档）。 */
    @Test
    void 默认存档路径位于用户主目录() {
        Path expectedRoot = Path.of(System.getProperty("user.home", "."));

        Path file = GrowthProgressStore.defaultFile();

        assertTrue(file.startsWith(expectedRoot), "默认存档应在用户主目录下：" + file);
        assertEquals("growth-progress.txt", file.getFileName().toString());
        assertEquals(expectedRoot.resolve(".pokerouge"), file.getParent(),
                "默认存档目录应为 .pokerouge");
    }

    /** 未绑定落盘回调的实例纯内存运行，不产生任何文件（测试与临时实例的默认状态）。 */
    @Test
    void 未绑定落盘回调时不触碰磁盘(@TempDir Path dir) {
        GrowthProgress progress = new GrowthProgress();

        progress.recordCapture(SPECIES_ID);
        progress.recordBattle(SPECIES_ID);

        assertEquals(1, progress.captureCount(SPECIES_ID));
        assertTrue(hasNoEntries(dir), "不应产生任何文件");
    }

    private static boolean hasNoEntries(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }
}
