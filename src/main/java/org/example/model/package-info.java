/**
 * 兼容层/历史模型层。
 * <p>本包作为历史工程代码的保留实现，保留对现有界面与战斗逻辑的兼容性；
 * 新增逻辑统一应优先由 {@code org.example.pokemon} 体系承载，避免再次出现两套模型互相漂移。</p>
 * <p>在合并后，旧包的稳定性以兼容为主，所有新功能一律优先落到 {@code org.example.pokemon.*}。</p>
 */
package org.example.model;
