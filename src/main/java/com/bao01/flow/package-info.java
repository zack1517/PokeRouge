/**
 * 流程与经济（Roguelike Run）包：分段路线、行动点（AP）、节点生成与结算、经济、剧情线状态机。
 *
 * <p>对应《docs/游戏流程接口设计.md》的「开发 C：流程与经济」。
 * 已实现：
 * <ul>
 *   <li>行动点第一版：{@link com.bao01.flow.NodeType}（节点种类）、
 *       {@link com.bao01.flow.RouteOption}（带实例级 AP 成本/强制标记的可选节点）、
 *       {@link com.bao01.flow.FlowConfig}（AP 上限/消耗/随机事件数值集中）与
 *       {@link com.bao01.flow.ActionPoints}（每段 AP 记账：重置到上限、扣减、耗尽判定）；</li>
 *   <li>节点池生成：{@link com.bao01.flow.NodePool}（每次战后按「常驻 + 随机事件概率」重抽一组，
 *       覆盖商店/火箭队/神兽/抓捕剧情线，见 README 节点表）；</li>
 *   <li>剧情线与随机：{@link com.bao01.flow.StoryFlags}（神兽/大师球/抓捕首领/侵略等每 Run 状态）、
 *       {@link com.bao01.flow.RandomSource}（可注入随机源，生产用 {@code system()}，测试用桩复现）。</li>
 * </ul>
 * 战斗结算、经济、必然节点（道馆/四天王/冠军/侵略）与控制器等按《接口文档》后续轮次并入本包。
 */
package com.bao01.flow;
