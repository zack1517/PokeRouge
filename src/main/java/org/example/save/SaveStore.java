package org.example.save;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.growth.GrowthProgress;
import org.example.growth.GrowthProgressStore;
import org.example.util.LogUtil;

/**
 * 存档文件读写：把每个存档位映射到磁盘上的一个独立目录。
 *
 * <p>目录结构（{@code root} 默认为 {@code <用户目录>/.pokerouge/saves}）：</p>
 * <pre>
 *   saves/slot1/save.txt              进度快照（队伍 / 背包 / 肉鸽楼层）
 *   saves/slot1/growth-progress.txt   该档位自己的图鉴成长记录（个体值加成）
 *   saves/slot2/…
 * </pre>
 *
 * <p>图鉴成长与进度快照<b>分开存放</b>：成长记录沿用成长机制既有的文件格式与读写实现
 * （{@link GrowthProgressStore}），因此每个档位的个体值加成天然互相独立，写坏一份也不会
 * 连带影响另一份。</p>
 *
 * <p>写入采用「先写临时文件再原子替换」，中途崩溃不会留下半截存档；读取时若内容不合格式会抛
 * {@link SaveFormatException}，由上层按「存档损坏」提示，而不是猜着解析。</p>
 */
public final class SaveStore {

    /** 默认根目录下的应用数据目录名。 */
    private static final String DATA_DIR = ".pokerouge";

    /** 存档位目录的父目录名。 */
    private static final String SAVES_DIR = "saves";

    /** 进度快照文件名。 */
    private static final String SAVE_FILE = "save.txt";

    /** 临时文件后缀（原子写中间态）。 */
    private static final String TEMP_SUFFIX = ".tmp";

    /** 原子替换的最大尝试次数：Windows 上杀毒/索引器短暂占用会让替换失败，重试几次再报错。 */
    private static final int MOVE_ATTEMPTS = 3;

    /** 原子替换的重试间隔（毫秒）。 */
    private static final long MOVE_RETRY_MILLIS = 20;


    private final Path root;

    /**
     * 以指定根目录创建（测试用 {@code @TempDir} 隔离，避免读到本机真实存档）。
     *
     * @param root 存档根目录（各档位为其下的子目录）
     */
    public SaveStore(Path root) {
        this.root = root.toAbsolutePath();
    }

    /** 默认存档根目录：{@code <用户目录>/.pokerouge/saves}。 */
    public static Path defaultRoot() {
        return Paths.get(System.getProperty("user.home"), DATA_DIR, SAVES_DIR);
    }

    /** 使用默认根目录的存档仓库。 */
    public static SaveStore defaultStore() {
        return new SaveStore(defaultRoot());
    }

    /** 存档根目录（各档位目录的父目录）。 */
    public Path root() {
        return root;
    }

    /** 指定档位的目录。 */
    public Path slotDir(SaveSlot slot) {
        return root.resolve(slot.id());
    }

    /** 指定档位的进度快照文件。 */
    public Path saveFile(SaveSlot slot) {
        return slotDir(slot).resolve(SAVE_FILE);
    }

    /** 指定档位的图鉴成长文件。 */
    public Path growthFile(SaveSlot slot) {
        return slotDir(slot).resolve(GrowthProgressStore.fileName());
    }

    /** 该档位是否已有进度快照。 */
    public boolean exists(SaveSlot slot) {
        return Files.isRegularFile(saveFile(slot));
    }

    /** 是否至少有一个档位存在存档（启动页据此决定「继续游戏」是否可用）。 */
    public boolean hasAnySave() {
        for (SaveSlot slot : SaveSlot.all()) {
            if (exists(slot)) {
                return true;
            }
        }
        return false;
    }

    /** 4 个档位的状态列表（供存档位选择界面渲染）。 */
    public List<SlotStatus> statuses() {
        List<SlotStatus> list = new ArrayList<>(SaveSlot.count());
        for (SaveSlot slot : SaveSlot.all()) {
            list.add(status(slot));
        }
        return list;
    }

    /**
     * 单个档位的状态：不存在 / 存在且可读（附摘要）/ 存在但损坏。
     *
     * <p>损坏不会抛异常 —— 列表需要把 4 个档位都渲染出来，损坏的档位以「损坏」标记提示玩家。</p>
     */
    public SlotStatus status(SaveSlot slot) {
        Path file = saveFile(slot);
        if (!Files.isRegularFile(file)) {
            return new SlotStatus(slot, false, true, null);
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return new SlotStatus(slot, true, true, SaveCodec.parseSummary(slot, text));
        } catch (IOException | RuntimeException e) {
            LogUtil.info("[SaveStore] 读取存档摘要失败：" + file + "（" + e.getMessage() + "）");
            return new SlotStatus(slot, true, false, null);
        }
    }

    /**
     * 写入指定档位的进度快照（原子写：先写 {@code save.txt.tmp} 再替换）。
     *
     * @throws UncheckedIOException 目录创建或写入失败（调用方应提示「保存失败」）
     */
    public void write(SaveSlot slot, SaveData data) {
        Path target = saveFile(slot);
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temp, SaveCodec.render(data), StandardCharsets.UTF_8);
            move(temp, target);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new UncheckedIOException("写入存档失败：" + target, e);
        }
    }

    /** 清理写入失败的临时文件；清理失败不影响报错（临时文件不参与读取）。 */
    private static void deleteQuietly(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // 忽略：临时文件不参与读取，且会被下次写入覆盖
        }
    }

    /**
     * 读取指定档位的进度快照。
     *
     * @return 该档位无存档时返回空；文件存在但内容不合格式时抛 {@link SaveFormatException}
     */
    public Optional<SaveData> read(SaveSlot slot) {
        Path file = saveFile(slot);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(SaveCodec.parse(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("读取存档失败：" + file, e);
        }
    }

    /**
     * 载入指定档位的图鉴成长记录，并接管其自动保存（此后每次记录捕捉/对战都会立即落盘）。
     *
     * @return 该档位的成长进度；无文件时为空进度，永不为 {@code null}
     */
    public GrowthProgress loadGrowth(SaveSlot slot) {
        return GrowthProgressStore.at(growthFile(slot)).loadProgress();
    }

    /**
     * 为指定档位创建一份全新的空白成长记录（新游戏用），并接管自动保存。
     *
     * <p>调用方应先用 {@link #delete(SaveSlot)} 清掉该档位旧存档，否则旧图鉴数据会残留。</p>
     */
    public GrowthProgress createGrowth(SaveSlot slot) {
        GrowthProgress progress = new GrowthProgress();
        progress.attachSaver(GrowthProgressStore.at(growthFile(slot))::write);
        return progress;
    }

    /**
     * 删除指定档位的全部存档文件（进度快照 + 图鉴成长）。
     *
     * @return 是否有文件被删除
     */
    public boolean delete(SaveSlot slot) {
        boolean removed = false;
        for (Path path : List.of(saveFile(slot), growthFile(slot))) {
            try {
                removed |= Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new UncheckedIOException("删除存档失败：" + path, e);
            }
        }
        return removed;
    }

    /** 原子替换；平台不支持原子移动时退化为普通替换。 */
    private static void move(Path temp, Path target) throws IOException {
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

    /**
     * 单个存档位的状态快照（供档位列表渲染）。
     *
     * @param slot     档位
     * @param exists   是否已有存档文件
     * @param readable 存档是否可被解析（{@code exists && !readable} 即「已损坏」）
     * @param summary  可读时的摘要；不存在或损坏时为 {@code null}
     */
    public record SlotStatus(SaveSlot slot, boolean exists, boolean readable, SaveSummary summary) {

        /** 该档位可以「继续游戏」/「保存到此」吗。 */
        public boolean usable() {
            return readable;
        }

        /** 该档位是空的吗（可用于新游戏，无需覆盖确认）。 */
        public boolean empty() {
            return !exists;
        }

        /** 该档位里队伍是否已无可用成员（有成员但全部倒下）；档位列表据此变灰标注。 */
        public boolean teamWiped() {
            return summary != null && summary.teamWiped();
        }
    }
}
