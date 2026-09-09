package com.bao01.model;

import com.bao01.config.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 背包：存放对战道具及其数量。
 *
 * <p>每支队伍携带一个独立背包。道具在使用后从背包中扣除；
 * 数量用尽的道具项会从 {@link #items()} 中消失。
 */
public final class Bag {

    private final Map<Item, Integer> counts = new LinkedHashMap<>();

    public Bag() {
    }

    /** 开局标准背包：伤药 ×2 + 好伤药 ×1。 */
    public static Bag startingBag() {
        Bag bag = new Bag();
        bag.give(Items.POTION, 2);
        bag.give(Items.SUPER_POTION, 1);
        return bag;
    }

    /** 放入/追加一定数量道具。 */
    public void give(Item item, int amount) {
        if (item == null || amount <= 0) {
            return;
        }
        counts.merge(item, amount, Integer::sum);
    }

    /** 使用一个道具；数量不足返回 false。 */
    public boolean take(Item item) {
        Integer c = counts.get(item);
        if (item == null || c == null || c <= 0) {
            return false;
        }
        if (c == 1) {
            counts.remove(item);
        } else {
            counts.put(item, c - 1);
        }
        return true;
    }

    public int count(Item item) {
        Integer c = counts.get(item);
        return c == null ? 0 : c;
    }

    /** 当前仍有余量的道具（只读快照，迭代顺序稳定）。 */
    public Map<Item, Integer> items() {
        Map<Item, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : counts.entrySet()) {
            if (e.getValue() > 0) {
                copy.put(e.getKey(), e.getValue());
            }
        }
        return copy;
    }

    public boolean isEmpty() {
        return items().isEmpty();
    }

    /**
     * 选出对目标「当前最有效」的恢复道具（回复量最大者）；无可用则 null。
     * 供 AI 决策使用，不会修改目标或背包。
     */
    public Item bestUsableFor(Pokemon target) {
        Item best = null;
        int bestPower = -1;
        for (Item item : items().keySet()) {
            if (item.isUsableOn(target) && item.healPower() > bestPower) {
                best = item;
                bestPower = item.healPower();
            }
        }
        return best;
    }
}
