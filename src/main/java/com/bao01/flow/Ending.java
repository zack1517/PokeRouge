package com.bao01.flow;

/** 结局类型（对应《游戏流程接口设计》2.7）。 */
public enum Ending {

    /** Run 失败结束（不可失败战斗战败、道馆/四天王二败）。 */
    RUN_OVER,

    /** 普通通关：未开启火箭队线 / 首领已被提前击败（击败冠军即通关）。 */
    NORMAL_CLEAR,

    /** 真结局：开线且未提前击败首领 → 冠军后过首领侵略战并获胜。 */
    TRUE_CLEAR
}
