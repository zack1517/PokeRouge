package com.bao01.app;

import javafx.application.Application;

/**
 * 存档 + 游戏流程独立窗口的启动壳。
 *
 * <p>与 {@code org.example.Launcher}（{@code dev} 的主界面）互不影响：本类只启动
 * {@link RougeApp}，用于验收「存档系统 + 流程推进」这一条链路。</p>
 *
 * <p>JavaFX 的 {@code Application} 子类不能直接作为 mainClass 启动（模块系统要求），
 * 因此需要本壳类。运行方式见 {@code docs/存档接口设计.md} §6.3。</p>
 */
public final class RougeLauncher {

    private RougeLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(RougeApp.class, args);
    }
}
