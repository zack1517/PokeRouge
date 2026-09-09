package com.bao01.flow;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 抓捕记账（对应《游戏流程接口设计》2.5）。
 *
 * <p>本类只负责记账：按物种累计成功捕获次数。
 * 「次数 → 努力值成长」「同类合并」「性格选择」为养成模块逻辑，不在本模块实现。
 */
public record CaptureLedger(Map<String, Integer> catchesBySpecies) {

    /** 空记账。 */
    public static CaptureLedger empty() {
        return new CaptureLedger(new LinkedHashMap<>());
    }

    public CaptureLedger {
        if (catchesBySpecies == null) {
            throw new IllegalArgumentException("抓捕计数表不能为 null");
        }
    }

    /** 记录一次成功捕获：该物种 +1。 */
    public CaptureLedger record(String species) {
        if (species == null || species.isBlank()) {
            throw new IllegalArgumentException("物种名不能为空");
        }
        Map<String, Integer> copy = new LinkedHashMap<>(catchesBySpecies);
        copy.put(species, copy.getOrDefault(species, 0) + 1);
        return new CaptureLedger(copy);
    }

    /** 该物种累计捕获次数（未捕获过为 0）。 */
    public int countOf(String species) {
        return species == null ? 0 : catchesBySpecies.getOrDefault(species, 0);
    }

    /** 捕获过的物种总数。 */
    public int totalSpecies() {
        return catchesBySpecies.size();
    }

    /** 累计捕获次数。 */
    public int totalCatches() {
        int sum = 0;
        for (int v : catchesBySpecies.values()) {
            sum += v;
        }
        return sum;
    }

    /** 防外部修改的快照（存档/展示用）。 */
    public Map<String, Integer> snapshot() {
        return new HashMap<>(catchesBySpecies);
    }
}
