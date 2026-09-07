package org.example;

import javafx.application.Application;

/**
 * 程序入口。
 * <p>JavaFX 自 JDK 11 起已从 JDK 中剥离，必须通过插件或模块化方式运行，
 * 因此这里用一个独立入口类启动 JavaFX 应用。</p>
 */
public class Launcher {

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
