package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 招式附带的能力等级变化（如「叫声」令目标物攻 −1、「高速移动」令自身速度 +2）。
 *
 * <p>数据形态为 {@code moves.csv} 的 {@code statChanges} 列，语法：</p>
 * <pre>
 * statChanges := entry[;entry...]
 * entry       := 能力项|±变化量[|SELF]
 * 能力项       := ATTACK / DEFENSE / SP_ATTACK / SP_DEFENSE / SPEED
 * </pre>
 *
 * <p>省略 {@code SELF} 表示作用于<b>招式目标</b>；带 {@code SELF} 表示作用于<b>使用者自己</b>。
 * 变化量可写 {@code -1} 或 {@code +2}。无法识别的片段一律静默跳过，空串 / {@code null} 返回空列表，
 * 因此单条脏数据不会导致整张招式表加载失败。</p>
 *
 * <p>契约：接口文档《战斗服务》§15.1.2。</p>
 *
 * @param stat  变化的能力项（不可能是 {@link StatModifier#HP}）
 * @param delta 变化量（正为提升、负为降低，绝对值由 {@link Pokemon#changeStatStage} 按 ±6 截断）
 * @param self  为 {@code true} 时作用于使用者自身，否则作用于招式目标
 */
public record MoveStatChange(StatModifier stat, int delta, boolean self) {

    /**
     * 解析 {@code statChanges} 列原文。
     *
     * @param raw 列原文；{@code null} 或空串返回空列表
     * @return 不可变的变化列表（保持原文顺序）；全部片段均无法识别时返回空列表
     */
    public static List<MoveStatChange> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<MoveStatChange> changes = new ArrayList<>();
        for (String entry : raw.split(";", -1)) {
            MoveStatChange change = parseEntry(entry);
            if (change != null) {
                changes.add(change);
            }
        }
        return Collections.unmodifiableList(changes);
    }

    /** 解析单个 {@code 能力项|±变化量[|SELF]} 片段；无法识别时返回 {@code null}。 */
    private static MoveStatChange parseEntry(String entry) {
        if (entry == null || entry.isBlank()) {
            return null;
        }
        String[] parts = entry.split("\\|", -1);
        if (parts.length < 2) {
            return null;
        }
        StatModifier stat = parseStat(parts[0]);
        if (stat == null) {
            return null;
        }
        Integer delta = parseDelta(parts[1]);
        if (delta == null || delta == 0) {
            return null;
        }
        boolean self = parts.length > 2 && "SELF".equalsIgnoreCase(parts[2].trim());
        return new MoveStatChange(stat, delta, self);
    }

    /** 解析能力项英文名；HP 不参与等级变化，故视为无法识别。 */
    private static StatModifier parseStat(String text) {
        if (text == null) {
            return null;
        }
        try {
            StatModifier stat = StatModifier.valueOf(text.trim().toUpperCase(Locale.ROOT));
            return stat == StatModifier.HP ? null : stat;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 解析带符号的变化量（容忍 {@code +2} / {@code -1} / 前后空格）；非法返回 {@code null}。 */
    private static Integer parseDelta(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("+")) {
            trimmed = trimmed.substring(1);
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
