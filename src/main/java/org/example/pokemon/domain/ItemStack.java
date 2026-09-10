package org.example.pokemon.domain;

import java.util.Objects;

public class ItemStack {
    private final Item item;
    private int count;

    public ItemStack(Item item, int count) {
        this.item = Objects.requireNonNull(item);
        this.count = count;
    }

    public Item getItem() { return item; }
    public int getCount() { return count; }
    public boolean isEmpty() { return count <= 0; }
    public boolean consume() {
        if (count <= 0) return false;
        count--;
        return true;
    }
    public void add(int amount) { count += amount; }
}
