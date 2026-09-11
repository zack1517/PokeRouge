package org.example.pokemon.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 招式附带的<b>能力等级变化</b>声明（数据驱动，由 moves.csv 的 {@code statChanges} 列指定）。
 *
 * <p>形如 {@code SELF:DEFENSE:+1}（自己物防 +1）或 {@code OPPONENT:ATTACK:-1}（对方物攻 -1）；
 * 一条招式可声明多项，用 {@code ;} 连接，如 {@code SELF:SPEED:+2;OPPONENT:SPEED:-1}。
 * 本类只负责<b>声明</b>，实际施加与数值裁剪由战斗引擎完成。</p>
 *
 * <p>HP 不参与能力等级变化，因此声明为 {@code HP} 的条目在解析时被忽略。</p>
 */
public record StatChange(Recipient recipient, StatModifier stat, int delta) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 能力等级变化的受方。 */
    public enum Recipient {

        /** 招式使用者自己（如硬邦邦、高速移动）。 */
        SELF,
        /** 招式目标（如叫声、摇尾巴）。 */
        OPPONENT;

        /** 解析受方名（大小写不敏感），未知返回 {@code null}。 */
        public static Recipient parse(String name) {
            if (name == null || name.trim().isEmpty()) {
                return null;
            }
            String normalized = name.trim().toUpperCase(Locale.ROOT);
            for (Recipient recipient : values()) {
                if (recipient.name().equals(normalized)) {
                    return recipient;
                }
            }
            return null;
        }
    }

    /** 单项变化的最大幅度（等级上限与下限）。 */
    public static final int MAX_DELTA = 6;

    /** 规范化：受方与能力项不可为空，变化幅度裁剪到 {@code [-6, 6]}。 */
    public StatChange {
        if (recipient == null || stat == null) {
            throw new IllegalArgumentException("能力等级变化必须指定受方与能力项");
        }
        delta = Math.max(-MAX_DELTA, Math.min(MAX_DELTA, delta));
    }

    /** 是否为提升（供播报选择文案）。 */
    public boolean isIncrease() {
        return delta > 0;
    }

    /**
     * 解析单项声明，如 {@code SELF:DEFENSE:+1}。
     *
     * @return 解析结果；格式非法、能力项未知（含 HP）或幅度为 0 时返回 {@code null}
     */
    public static StatChange parse(String token) {
        if (token == null) {
            return null;
        }
        String[] parts = token.trim().split(":");
        if (parts.length != 3) {
            return null;
        }
        Recipient recipient = Recipient.parse(parts[0]);
        StatModifier stat = parseStat(parts[1]);
        if (recipient == null || stat == null) {
            return null;
        }
        int delta;
        try {
            delta = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (delta == 0) {
            return null;
        }
        return new StatChange(recipient, stat, delta);
    }

    /**
     * 解析整列声明，用 {@code ;} 分隔；空值返回空列表，非法项被忽略。
     *
     * @param value 如 {@code SELF:SPEED:+2} 或 {@code SELF:SPEED:+2;OPPONENT:ATTACK:-1}
     */
    public static List<StatChange> parseAll(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        List<StatChange> changes = new ArrayList<>();
        for (String token : value.split(";")) {
            StatChange change = parse(token);
            if (change != null) {
                changes.add(change);
            }
        }
        return List.copyOf(changes);
    }

    /** 解析能力项名（大小写不敏感，{@code -} 与 {@code _} 等价）；HP 与未知值返回 {@code null}。 */
    private static StatModifier parseStat(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (StatModifier stat : StatModifier.values()) {
            if (stat != StatModifier.HP && stat.name().equals(normalized)) {
                return stat;
            }
        }
        return null;
    }
}
