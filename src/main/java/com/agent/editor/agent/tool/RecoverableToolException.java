package com.agent.editor.agent.tool;

/**
 * 表示模型有机会通过修正下一次 tool call 来恢复的工具错误。
 * 这类异常不能直接打断 tool-loop，而应由 runtime 转成 tool result 回注给模型。
 */
public class RecoverableToolException extends RuntimeException {

    public RecoverableToolException(String message) {
        super(message);
    }

    public RecoverableToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
