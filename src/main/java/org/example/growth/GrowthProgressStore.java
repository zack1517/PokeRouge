package org.example.growth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.example.util.LogUtil;

/**
 * 成长进度的本地文件存储：把 {@link GrowthProgress} 落到本地单机文件，使其成为
 * <b>永久成长</b>（关闭程序、重开游戏、开启新一轮远征后仍然保留）。
 *
 * <p><b>文件格式</b>（UTF-8 文本，人类可读，便于调试与手工清档）：</p>
 * <pre>
 * # PokeRouge 成长进度 v1
 * # 格式：物种id=捕捉次数,对战次数
 * bulbasaur=3,5
 * charmander=0,2
 * </pre>
 *
 * <p><b>健壮性约定</b>：读写任何异常（磁盘只读、文件被占用、内容损坏等）都<b>不抛出</b>，
 * 只打日志并降级 —— 记录不写入时按新档处理，保存失败时本局游戏照常进行。成长进度不是
 * 关键路径，绝不能因为磁盘问题让游戏崩溃。</p>
 *
 * <p><b>上限</b>：本类只负责计数存取，个体值上限由 {@link IvGrowthRule#MAX_IV} 在演算时统一约束；
 * 因此即便手工把文件改成远超上限的次数，游戏内加成仍不超过上限。</p>
 */
public final class GrowthProgressStore {

    /** 存档头：含格式版本号，便于后续迁移。 */
    private static final String HEADER = "# PokeRouge 成长进度 v1";

    /** 格式说明：随文件一起写，方便玩家/助教直接看懂。 */
    private static final String FORMAT_HINT = "# 格式：物种id=捕捉次数,对战次数";

    /** 原子写入用的临时文件后缀。 */
    private static final String TEMP_SUFFIX = ".tmp";

    /** 替换目标文件的最大尝试次数：Windows 上杀毒/索引器短暂占用会让原子替换失败。 */
    private static final int MOVE_ATTEMPTS = 3;

    /** 替换重试间隔（毫秒）。 */
    private static final long MOVE_RETRY_MILLIS = 20;

    /** 默认存档目录名（位于用户主目录下）。 */
    private static final String DATA_DIR = ".pokerouge";

    /** 默认存档文件名。 */
    private static final String FILE_NAME = "growth-progress.txt";

    private final Path file;

    private GrowthProgressStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /** 绑定指定存档文件。 */
    public static GrowthProgressStore at(Path file) {
        return new GrowthProgressStore(file);
    }

    /**
     * 默认存档位置：{@code <用户主目录>/.pokerouge/growth-progress.txt}。
     *
     * <p>放在用户主目录而非工程目录，是为了「本地单机」语义：重新构建/换分支/清空
     * target 都不会丢掉玩家的成长进度。</p>
     */
    public static Path defaultFile() {
        return Paths.get(System.getProperty("user.home", "."), DATA_DIR, FILE_NAME);
    }

    /** 本存储绑定的存档文件。 */
    public Path file() {
        return file;
    }

    /**
     * 默认存档文件名（{@code growth-progress.txt}）。
     * <p>供存档系统为每个存档位拼出各自的图鉴文件名，避免常量在两处各写一遍。</p>
     */
    public static String fileName() {
        return FILE_NAME;
    }

    /**
     * 读取全部记录。文件不存在、内容损坏或读取失败时返回空列表（按新档处理）。
     *
     * @return 成长记录列表，永不为 {@code null}
     */
    public List<SpeciesGrowthRecord> load() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<SpeciesGrowthRecord> entries = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                SpeciesGrowthRecord record = parse(line);
                if (record != null) {
                    entries.add(record);
                }
            }
        } catch (IOException | RuntimeException e) {
            LogUtil.info("[GrowthProgress] 读取成长进度失败，按新档处理：" + file + "（" + e.getMessage() + "）");
            return List.of();
        }
        return entries;
    }

    /**
     * 载入磁盘进度、接管自动保存，返回可直接使用的进度表。
     *
     * <p>此后每次 {@code recordCapture} / {@code recordBattle} / {@code clear} 都会立即落盘，
     * 无需调用方关心保存时机。</p>
     */
    public GrowthProgress loadProgress() {
        GrowthProgress progress = new GrowthProgress();
        progress.restore(load());
        progress.attachSaver(this::write);
        return progress;
    }

    /**
     * 覆盖写入全部记录（原子写：先写临时文件再替换，避免中途崩溃损坏存档）。
     *
     * <p>空记录（捕捉与对战均为 0）不写入；写入失败只打日志，不影响调用方流程。</p>
     *
     * @param entries 待写入记录，{@code null} 视为空
     */
    public void write(List<SpeciesGrowthRecord> entries) {
        Path target = file.toAbsolutePath();
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temp, render(entries), StandardCharsets.UTF_8);
            moveIntoPlace(temp, target);
        } catch (IOException | RuntimeException e) {
            LogUtil.info("[GrowthProgress] 写入成长进度失败（本次进度仅存于内存）："
                    + target + "（" + e.getMessage() + "）");
            deleteQuietly(temp);
        }
    }

    /**
     * 把临时文件原子替换到目标位置。
     *
     * <p>Windows 上目标文件被索引器/杀毒软件短暂占用时 {@code Files.move} 会以共享冲突失败，
     * 而本类的约定是「进度变化必须落盘」，故此处带短间隔重试；仍失败才交给调用方按降级处理。</p>
     */
    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < MOVE_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(MOVE_RETRY_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw last;
                }
            }
            try {
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    /** 清理临时文件；失败不关心（下次写入会覆盖同名临时文件）。 */
    private static void deleteQuietly(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // 清理失败无影响：临时文件不参与读取，且会被下次写入覆盖
        }
    }

    /** 渲染文件正文：头部 + 按物种 id 排序的记录行。 */
    private static String render(List<SpeciesGrowthRecord> entries) {
        List<SpeciesGrowthRecord> sorted = new ArrayList<>();
        if (entries != null) {
            for (SpeciesGrowthRecord record : entries) {
                if (record != null && !record.isBlank()) {
                    sorted.add(record);
                }
            }
        }
        sorted.sort(Comparator.comparing(SpeciesGrowthRecord::getSpeciesId));

        String newline = System.lineSeparator();
        StringBuilder text = new StringBuilder();
        text.append(HEADER).append(newline).append(FORMAT_HINT).append(newline);
        for (SpeciesGrowthRecord record : sorted) {
            text.append(record.getSpeciesId()).append('=')
                    .append(record.getCaptureCount()).append(',')
                    .append(record.getBattleCount()).append(newline);
        }
        return text.toString();
    }

    /**
     * 解析单行记录。空行、注释行、格式错误行一律返回 {@code null}（跳过而不中断读取），
     * 这样手改文件出错时只丢一行，不会整档报废。
     */
    private static SpeciesGrowthRecord parse(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty() || text.startsWith("#")) {
            return null;
        }
        int separator = text.indexOf('=');
        if (separator <= 0) {
            return null;
        }
        String speciesId = text.substring(0, separator).trim();
        String[] counts = text.substring(separator + 1).trim().split("\\s*,\\s*");
        if (speciesId.isEmpty() || counts.length < 2) {
            return null;
        }
        try {
            int captures = Integer.parseInt(counts[0].trim());
            int battles = Integer.parseInt(counts[1].trim());
            SpeciesGrowthRecord record = new SpeciesGrowthRecord(speciesId, captures, battles);
            return record.isBlank() ? null : record;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
