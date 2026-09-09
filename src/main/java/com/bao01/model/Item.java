package com.bao01.model;

/**
 * 道具：当前仅实现恢复类，并支持「对战期间无法使用」的类型。
 *
 * <p>对战规则：道具行动优先级为 4，先于普通招式（优先级 1）结算。
 * 部分场景道具（如进化 / 捕捉用）在 {@code battle} 中不可使用，仅能在队伍界面使用。
 */
public final class Item {

    /** 道具类别。 */
    public enum Kind {
        /** 固定回复一定 HP。 */
        HEAL("恢复"),
        /** 直接回满。 */
        FULL_HEAL("全恢复"),
        /** 对战期间无法使用的道具（场景道具）。 */
        FIELD_ONLY("场景");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String name;
    private final Kind kind;
    /** HEAL 类固定回复量；其他类别为 0。 */
    private final int healAmount;
    /** 是否可在对战中使用。 */
    private final boolean battleUsable;

    private Item(String name, Kind kind, int healAmount, boolean battleUsable) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("道具名不能为空");
        }
        if (kind == null) {
            throw new IllegalArgumentException("道具类别不能为空");
        }
        this.name = name;
        this.kind = kind;
        this.healAmount = healAmount;
        this.battleUsable = battleUsable;
    }

    /** 固定回复类（对战可用）。 */
    public static Item heal(String name, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("回复量必须为正: " + amount);
        }
        return new Item(name, Kind.HEAL, amount, true);
    }

    /** 全恢复类（对战可用）。 */
    public static Item fullHeal(String name) {
        return new Item(name, Kind.FULL_HEAL, 0, true);
    }

    /** 场景类：对战期间不可使用。 */
    public static Item fieldOnly(String name) {
        return new Item(name, Kind.FIELD_ONLY, 0, false);
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isBattleUsable() {
        return battleUsable;
    }

    /**
     * 能否对目标使用（不消耗）。
     * 濒死不可用；恢复类要求 HP 未满；对战不可用的类型直接返回 false。
     */
    public boolean isUsableOn(Pokemon target) {
        if (target == null || !battleUsable || target.isFainted()) {
            return false;
        }
        return target.currentHp() < target.maxHp();
    }

    /** 回复量描述，用于 AI 对比与界面展示（全恢复视为极大值）。 */
    public int healPower() {
        if (kind == Kind.HEAL) {
            return healAmount;
        }
        if (kind == Kind.FULL_HEAL) {
            return 1_000_000;
        }
        return 0;
    }

    /** 对目标使用并扣除回复效果，返回实际恢复的 HP。不可用时返回 0。 */
    public int apply(Pokemon target) {
        if (!isUsableOn(target)) {
            return 0;
        }
        if (kind == Kind.HEAL) {
            return target.heal(healAmount);
        }
        if (kind == Kind.FULL_HEAL) {
            int gap = target.maxHp() - target.currentHp();
            target.healFull();
            return gap;
        }
        return 0;
    }

    @Override
    public String toString() {
        return name + '(' + kind.getLabel() + ')';
    }
}
