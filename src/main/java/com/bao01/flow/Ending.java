package com.bao01.flow;

/** 结局类型（对应《游戏流程接口设计》2.7）。 */
public enum Ending {

    /** Run 失败结束（不可失败战斗战败、道馆/四天王二败）。 */
    RUN_OVER,

    /** 普通通关：未开启火箭队线 / 首领已被提前击败（击败冠军即通关）。 */
    NORMAL_CLEAR,

    /** 真结局：开线且未提前击败首领 → 冠军后过首领侵略战并获胜。 */
    TRUE_CLEAR;

    /** 存档文本标记：Run 仍在进行中（{@code ending == null}）。 */
    public static final String PLAYING = "PLAYING";

    /** 存档文本标记：{@code null} → {@value #PLAYING}，否则为枚举名。 */
    public static String textOf(Ending ending) {
        return ending == null ? PLAYING : ending.name();
    }

    /**
     * 解析存档文本标记。
     *
     * @return {@value #PLAYING} / {@code null} / 空串 → {@code null}（进行中）；否则为对应结局
     * @throws IllegalArgumentException 文本不是已知结局名（存档损坏）
     */
    public static Ending parse(String text) {
        if (text == null || text.isBlank() || PLAYING.equalsIgnoreCase(text.trim())) {
            return null;
        }
        try {
            return valueOf(text.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知结局标记: " + text);
        }
    }
}
