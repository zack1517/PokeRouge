package org.example.pokemon.domain;

import java.util.Objects;

public class Item {
    private final String id;
    private final String name;
    private final ItemCategory category;
    private final double effect;
    private final boolean alwaysCatch;

    public Item(String id, String name, ItemCategory category, double effect) {
        this(id, name, category, effect, false);
    }

    public Item(String id, String name, ItemCategory category, double effect, boolean alwaysCatch) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.category = Objects.requireNonNull(category);
        this.effect = effect;
        this.alwaysCatch = alwaysCatch;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public ItemCategory getCategory() { return category; }
    public double getEffect() { return effect; }
    public boolean isAlwaysCatch() { return alwaysCatch; }
    @Override public String toString() { return name; }
}
