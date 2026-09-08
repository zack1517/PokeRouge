package org.example.model;

import java.util.Objects;

/**
 * 道具定义。
 * <p>HEAL：{@link #effect} 为回复 HP 量；POKE_BALL：{@link #effect} 为捕捉倍率系数，
 * {@link #alwaysCatch} 为 true 时必定捕捉成功（如大师球）。</p>
 */
public class Item {

    private final String id;
    private final String name;
    private final ItemCategory category;
    /** 见类别说明。 */
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

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ItemCategory getCategory() {
        return category;
    }

    public double getEffect() {
        return effect;
    }

    public boolean isAlwaysCatch() {
        return alwaysCatch;
    }

    @Override
    public String toString() {
        return name;
    }
}
