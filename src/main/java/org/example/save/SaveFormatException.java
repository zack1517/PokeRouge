package org.example.save;

/**
 * 存档内容不符合预期格式时抛出（文件为空、缺少版本行、版本超出支持范围、字段数值非法等）。
 *
 * <p>继承 {@link RuntimeException}：读档链路上层只需捕获它并提示「存档已损坏」，
 * 不必为每一层方法声明受检异常。抛出它意味着<b>不可继续解析</b>，因此绝不吞掉。</p>
 */
public class SaveFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SaveFormatException(String message) {
        super(message);
    }

    public SaveFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
