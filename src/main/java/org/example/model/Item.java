package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 道具定义。
 * <p>HEAL：{@link #effect} 为回复 HP 量；POKE_BALL：{@link #effect} 为捕捉倍率系数，
 * {@link #alwaysCatch} 为 true 时必定捕捉成功（如大师球）；CURE：{@link #curesSpec} 描述可治愈的
 * 异常状态（状态英文名或中文名，如 {@code POISON}、{@code 麻痹}；多项用 {@code |}/{@code ,}/空白
 * 分隔，如解毒药 {@code POISON|BADLY_POISON}；{@value #CURE_ALL} 表示全部主要异常，
 * 见 {@link #canCure(StatusCondition)}）。</p>
 */
public class Item {

    /** 解除范围标记：治愈全部主要异常（万灵药）。 */
    public static final String CURE_ALL = "ALL";

    private final String id;
    private final String name;
    private final ItemCategory category;
    /** 见类别说明。 */
    private final double effect;
    private final boolean alwaysCatch;
    /** CURE 类别的解除范围（状态名，多项用 {@code |}/{@code ,}/空白 分隔，或 {@link #CURE_ALL}）；其他类别为空串。 */
    private final String curesSpec;

    public Item(String id, String name, ItemCategory category, double effect) {
        this(id, name, category, effect, false, "");
    }

    public Item(String id, String name, ItemCategory category, double effect, boolean alwaysCatch) {
        this(id, name, category, effect, alwaysCatch, "");
    }

    /**
     * 完整构造。
     *
     * @param curesSpec CURE 类别的解除范围：状态英文名或中文名，{@value #CURE_ALL} 表示全部主要异常；
     *                  其他类别传空串或 {@code null}
     */
    public Item(String id, String name, ItemCategory category, double effect,
                boolean alwaysCatch, String curesSpec) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.category = Objects.requireNonNull(category);
        this.effect = effect;
        this.alwaysCatch = alwaysCatch;
        this.curesSpec = curesSpec == null ? "" : curesSpec.trim();
    }

    /** 解除道具工厂：治愈单一异常状态（解毒药/麻痹药等）。 */
    public static Item cureItem(String id, String name, String statusSpec) {
        return new Item(id, name, ItemCategory.CURE, 0, false, statusSpec);
    }

    /** 解除道具工厂：治愈全部主要异常（万灵药）。 */
    public static Item cureAllItem(String id, String name) {
        return new Item(id, name, ItemCategory.CURE, 0, false, CURE_ALL);
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

    /** CURE 类别的解除范围原文（状态名或 {@value #CURE_ALL}）；其他类别为空串。 */
    public String getCuresSpec() {
        return curesSpec;
    }

    /**
     * 是否为解除指定异常状态的道具（{@value #CURE_ALL} 对全部主要异常生效，不含混乱）。
     *
     * <p>{@link #curesSpec} 可填单个状态名，也可用 {@code |}/{@code ,}/空白 分隔多项
     * （如解毒药 {@code POISON|BADLY_POISON}），命中其中任意一项即可解除。</p>
     */
    public boolean canCure(StatusCondition condition) {
        if (category != ItemCategory.CURE || condition == null || condition == StatusCondition.NONE) {
            return false;
        }
        if (CURE_ALL.equalsIgnoreCase(curesSpec)) {
            return condition.isMajor();
        }
        for (String token : curesSpec.split("[|,\\s]+")) {
            if (!token.isBlank() && StatusCondition.parse(token) == condition) {
                return true;
            }
        }
        return false;
    }

    /** {@link #curesSpec} 中列出的各项解除范围（{@value #CURE_ALL} 时返回空列表）。 */
    public List<StatusCondition> curedStatuses() {
        if (category != ItemCategory.CURE || curesSpec.isBlank()
                || CURE_ALL.equalsIgnoreCase(curesSpec)) {
            return List.of();
        }
        List<StatusCondition> result = new ArrayList<>();
        for (String token : curesSpec.split("[|,\\s]+")) {
            StatusCondition condition = StatusCondition.parse(token);
            if (!token.isBlank() && condition != StatusCondition.NONE && !result.contains(condition)) {
                result.add(condition);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 是否解除全部主要异常（{@value #CURE_ALL}）。 */
    public boolean curesAll() {
        return category == ItemCategory.CURE && CURE_ALL.equalsIgnoreCase(curesSpec);
    }

    @Override
    public String toString() {
        return name;
    }
}
