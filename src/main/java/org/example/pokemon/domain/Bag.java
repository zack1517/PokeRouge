package org.example.pokemon.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    public ItemStack consume(Item item) {
        for (ItemStack stack : stacks) {
            if (stack.getItem().getId().equals(item.getId()) && !stack.isEmpty()) {
                stack.consume();
                return stack;
            }
        }
        return null;
    }

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
