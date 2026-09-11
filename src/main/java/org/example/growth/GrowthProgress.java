package org.example.growth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成长进度表：按种族累计「捕捉次数 / 对战次数」，并据此派生出个体值加成。
 *
 * <p>本类同时是<b>图鉴数据接口</b>：局外（非战斗界面）可查询任意种族的捕捉次数、对战次数
 * 与个体值加成（{@link #record(String)} / {@link #dexEntries()}），不依赖战斗引擎。</p>
 *
 * <p><b>加成口径</b>：单族加成为 {@link IvGrowthRule#speciesBonus(int)}；
 * {@link #globalIvBonus()} 为各族加成之和（上限 {@link IvGrowthRule#MAX_IV}），
 * 由创建流程叠加到之后生成的<b>所有</b>宝可梦的个体值上（见
 * {@code org.example.pokemon.domain.Stats#randomIv(int)}）。种族值不受影响。</p>
 *
 * <p><b>生命周期</b>：进度属于「局外」数据，须跨单轮远征、且跨进程重启存活。因此默认实例为
 * {@link #instance()} —— 由 {@link GrowthProgressStore} 从<b>本地文件</b>载入并接管自动保存
 * （本地单机永久成长，关掉程序再打开仍保留）；测试可自行 {@code new GrowthProgress()} 隔离状态，
 * 此路径不触碰磁盘。</p>
 *
 * @see GrowthProgressStore
 */
public final class GrowthProgress {

    /**
     * 落盘回调：每次进度变化后由 {@link GrowthProgressStore} 接管写文件。
     * 未绑定时（默认）进度只存于内存。
     */
    @FunctionalInterface
    public interface Saver {

        /** 保存全量记录。实现方必须自行吞掉异常，不得向调用方抛出。 */
        void save(List<SpeciesGrowthRecord> entries);
    }

    /** 进程级共享进度（跨 GameSession / 单轮远征 / 进程重启存活）。 */
    private static final class Holder {
        private static final GrowthProgress INSTANCE = GrowthProgressStore.at(GrowthProgressStore.defaultFile())
                .loadProgress();
    }

    private final Map<String, SpeciesGrowthRecord> records = new ConcurrentHashMap<>();

    /** 落盘回调；{@code null} 表示不落盘（测试与临时实例）。 */
    private volatile Saver saver;

    /** 返回进程级共享进度实例：首次访问时从本地存档载入，并开启自动保存。 */
    public static GrowthProgress instance() {
        return Holder.INSTANCE;
    }

    /**
     * 查询某种族的图鉴记录；无记录时返回各项为 0 的空记录（不会写入）。
     *
     * @param speciesId 物种 id
     * @return 该族的成长记录，永不为 {@code null}
     */
    public SpeciesGrowthRecord record(String speciesId) {
        Objects.requireNonNull(speciesId, "speciesId");
        SpeciesGrowthRecord found = records.get(speciesId);
        return found == null ? SpeciesGrowthRecord.empty(speciesId) : found;
    }

    /**
     * 图鉴列表：按物种 id 排序的全部记录，含只有对战记录、从未捕捉过的种族。
     *
     * @return 只读记录列表
     */
    public List<SpeciesGrowthRecord> dexEntries() {
        List<SpeciesGrowthRecord> all = new ArrayList<>(records.values());
        all.sort(Comparator.comparing(SpeciesGrowthRecord::getSpeciesId));
        return List.copyOf(all);
    }

    /** 该族累计捕捉次数（图鉴）。 */
    public int captureCount(String speciesId) {
        return record(speciesId).getCaptureCount();
    }

    /** 该族累计对战次数（图鉴）。 */
    public int battleCount(String speciesId) {
        return record(speciesId).getBattleCount();
    }

    /** 该族的个体值加成（图鉴），上限 {@link IvGrowthRule#MAX_IV}。 */
    public int ivBonus(String speciesId) {
        return record(speciesId).getIvBonus();
    }

    /**
     * 全局个体值加成：全部种族加成之和，上限 {@link IvGrowthRule#MAX_IV}。
     * 新生成的宝可梦按本值提升六项个体值。
     *
     * @return 全局加成（0 ~ {@value IvGrowthRule#MAX_IV}）
     */
    public int globalIvBonus() {
        int sum = 0;
        for (SpeciesGrowthRecord r : records.values()) {
            sum += r.getIvBonus();
            if (sum >= IvGrowthRule.MAX_IV) {
                return IvGrowthRule.MAX_IV;
            }
        }
        return Math.min(IvGrowthRule.MAX_IV, sum);
    }

    /**
     * 记录一次成功捕捉（成长机制的驱动事件）。
     *
     * @param speciesId 被捕捉物种 id
     * @return 该族累计捕捉次数（含本次）
     */
    public int recordCapture(String speciesId) {
        Objects.requireNonNull(speciesId, "speciesId");
        int count = records.compute(speciesId, (id, old) ->
                (old == null ? SpeciesGrowthRecord.empty(id) : old).plusCapture()).getCaptureCount();
        persist();
        return count;
    }

    /**
     * 记录一次参战（战斗获胜时按参战精灵逐只申报）。
     *
     * @param speciesId 参战物种 id
     * @return 该族累计对战次数（含本次）
     */
    public int recordBattle(String speciesId) {
        Objects.requireNonNull(speciesId, "speciesId");
        int count = records.compute(speciesId, (id, old) ->
                (old == null ? SpeciesGrowthRecord.empty(id) : old).plusBattle()).getBattleCount();
        persist();
        return count;
    }

    /**
     * 用给定记录替换全部进度（存档载入 / 外部恢复用）。
     *
     * <p>空记录与空 id 被跳过；重复 id 以后者为准。本方法<b>不</b>触发落盘，
     * 以免载入过程反过来重写文件。</p>
     *
     * @param entries 待载入记录，{@code null} 视为空（即清空）
     */
    public void restore(List<SpeciesGrowthRecord> entries) {
        records.clear();
        if (entries == null) {
            return;
        }
        for (SpeciesGrowthRecord record : entries) {
            if (record == null || record.isBlank() || record.getSpeciesId().isBlank()) {
                continue;
            }
            records.put(record.getSpeciesId(), record);
        }
    }

    /**
     * 绑定落盘回调：此后每次进度变化都会把全量记录交给 {@code saver}。
     * 传 {@code null} 可解除绑定（恢复为纯内存）。
     *
     * @param saver 落盘实现，见 {@link Saver}
     */
    public void attachSaver(Saver saver) {
        this.saver = saver;
    }

    /**
     * 清空全部进度（仅测试与「重开存档」使用）：同时清空本地存档文件。
     */
    public void clear() {
        records.clear();
        persist();
    }

    /**
     * 把当前全量记录交给落盘回调。落盘失败绝不能影响游戏流程，故此处吞掉一切异常。
     */
    private void persist() {
        Saver current = saver;
        if (current == null) {
            return;
        }
        try {
            current.save(dexEntries());
        } catch (RuntimeException e) {
            // 磁盘问题不应让战斗流程崩溃；GrowthProgressStore 内部已打日志。
        }
    }

    @Override
    public String toString() {
        return "GrowthProgress{种族数=" + records.size() + ", 全局个体值加成=" + globalIvBonus() + '}';
    }
}
