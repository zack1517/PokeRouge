package org.example.model;

import java.util.Objects;

/**
 * 背包中的一个道具堆叠（道具 + 数量）。
 */
public class ItemStack {

    private final Item item;
    private int count;

    public ItemStack(Item item, int count) {
        this.item = Objects.requireNonNull(item);
        this.count = count;
    }

    public Item getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public boolean isEmpty() {
        return count <= 0;
    }

    /** 取用一次，数量不足返回 false。 */
    public boolean consume() {
        if (count <= 0) {
            return false;
        }
        count--;
        return true;
    }

    public void add(int amount) {
        count += amount;
    }
}
