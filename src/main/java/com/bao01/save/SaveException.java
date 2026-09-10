package com.bao01.save;

/** 存档读写 / 解析 / 恢复失败时抛出的运行时异常。 */
public class SaveException extends RuntimeException {

    public SaveException(String message) {
        super(message);
    }

    public SaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
