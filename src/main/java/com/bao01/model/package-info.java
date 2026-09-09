/**
 * 数据模型包：领域对象与游戏状态（MVC 的 Model 层）。
 *
 * <p>已实现单打对战核心：{@link com.bao01.model.Pokemon}（属性 / 等级 /
 * 种族值 / 努力值 / 招式 / 当前 HP / 能力等级变化）、{@link com.bao01.model.Move}
 * （招式，含威力 / 命中 / 优先度 / 能力变化效果）、{@link com.bao01.model.Item}
 * （对战道具，含「对战期间不可用」类别）、{@link com.bao01.model.Bag} 背包、
 * {@link com.bao01.model.Team}（最多 6 只，1 只在场）、{@link com.bao01.model.PokeType}
 * （属性克制）、{@link com.bao01.model.Stat} 与 {@link com.bao01.model.EffortValues}，
 * 以及 {@link com.bao01.model.Battle} 1v1 回合制引擎：
 * 每回合四类行动（对战 / 道具 / 轮换 / 逃跑），道具与轮换行动优先级 4 先于攻击，
 * 攻击招式按「优先度 → 实际速度（含等级变化）→ 随机」决定出手顺序。
 */
package com.bao01.model;
