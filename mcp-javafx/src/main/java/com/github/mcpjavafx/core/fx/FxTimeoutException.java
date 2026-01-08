package com.github.mcpjavafx.core.fx;

/**
 * Exception thrown when FX thread operation times out.
 */
public class FxTimeoutException extends RuntimeException {

    public FxTimeoutException(String message) {
        super(message);
    }

    public FxTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
