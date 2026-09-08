package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 背包：持有若干道具堆叠。仅支持一次取用/添加，简化对战期交互。
 */
public class Bag {

    private final List<ItemStack> stacks = new ArrayList<>();

    public void add(Item item, int count) {
        for (ItemStack stack : stacks) {
            if (stack.getItem().getId().equals(item.getId())) {
                stack.add(count);
                return;
            }
        }
        stacks.add(new ItemStack(item, count));
    }

    /** 取用一个道具；无该道具或数量为 0 返回 null。 */
    public ItemStack consume(Item item) {
        for (ItemStack stack : stacks) {
            if (stack.getItem().getId().equals(item.getId()) && !stack.isEmpty()) {
                stack.consume();
                return stack;
            }
        }
        return null;
    }

    /** 返回可用的堆叠（数量 &gt; 0）。 */
    public List<ItemStack> availableStacks() {
        return stacks.stream().filter(s -> !s.isEmpty()).toList();
    }

    public int countOf(Item item) {
        for (ItemStack stack : stacks) {
            if (stack.getItem().getId().equals(Objects.requireNonNull(item).getId())) {
                return stack.getCount();
            }
        }
        return 0;
    }

    public List<ItemStack> getAll() {
        return Collections.unmodifiableList(stacks);
    }
}
