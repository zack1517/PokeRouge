package com.bao01.save;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把 {@link SaveDemo} 的 73 条断言接入 JUnit，使 {@code mvn test} 即可覆盖存档系统，
 * 无需手工执行 {@code main}。
 *
 * <p>断言内容与命令行方式完全一致（共用 {@link SaveDemo#runSuite()}），
 * 两种入口不会出现覆盖范围漂移。</p>
 */
class SaveSystemTest {

    @Test
    @DisplayName("存档系统：全部 73 条用例通过")
    void saveSuitePasses() {
        List<String> failures = SaveDemo.runSuite();

        assertTrue(failures.isEmpty(), () -> "存档系统自测失败 " + failures.size()
                + " / " + SaveDemo.checks() + " 项：\n  - " + String.join("\n  - ", failures));
    }

    @Test
    @DisplayName("存档系统：用例数不少于 73 条（防止用例被误删）")
    void suiteSizeIsStable() {
        SaveDemo.runSuite();

        assertTrue(SaveDemo.checks() >= 73,
                () -> "断言数从 73 掉到 " + SaveDemo.checks() + "，疑似用例被误删");
    }
}
