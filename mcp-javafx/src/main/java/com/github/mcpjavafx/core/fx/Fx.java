package com.github.mcpjavafx.core.fx;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class for executing code on the JavaFX Application Thread.
 * Provides thread-safe execution with timeout support.
 */
public final class Fx {

    private Fx() {
        // Utility class
    }

    /**
     * Executes a callable on the FX Application Thread and returns the result.
     * If already on FX thread, executes immediately.
     *
     * @param action    the callable to execute
     * @param timeoutMs timeout in milliseconds
     * @param <T>       return type
     * @return the result of the callable
     * @throws FxTimeoutException if the operation times out
     */
    public static <T> T exec(Callable<T> action, int timeoutMs) throws FxTimeoutException {
        if (Platform.isFxApplicationThread()) {
            try {
                return action.call();
            } catch (Exception e) {
                throw new RuntimeException("Error executing on FX thread", e);
            }
        }

        var future = new CompletableFuture<T>();

        Platform.runLater(() -> {
            try {
                future.complete(action.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new FxTimeoutException("FX thread operation timed out after " + timeoutMs + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FxTimeoutException("FX thread operation interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error executing on FX thread", e.getCause());
        }
    }

    /**
     * Executes a runnable on the FX Application Thread.
     * If already on FX thread, executes immediately.
     *
     * @param action    the runnable to execute
     * @param timeoutMs timeout in milliseconds
     * @throws FxTimeoutException if the operation times out
     */
    public static void run(Runnable action, int timeoutMs) throws FxTimeoutException {
        exec(() -> {
            action.run();
            return null;
        }, timeoutMs);
    }

    /**
     * Waits for the UI to be idle (all pending events processed).
     * Uses double runLater + one AnimationTimer pulse for reliable idle detection.
     *
     * @param timeoutMs timeout in milliseconds
     * @throws FxTimeoutException if the operation times out
     */
    public static void awaitUiIdle(int timeoutMs) throws FxTimeoutException {
        var future = new CompletableFuture<Void>();

        // First runLater to ensure we're in the queue
        Platform.runLater(() -> {
            // Second runLater to process any events from the first
            Platform.runLater(() -> {
                // Wait for one animation pulse
                var done = new AtomicBoolean(false);
                new AnimationTimer() {
                    @Override
                    public void handle(long now) {
                        if (!done.getAndSet(true)) {
                            stop();
                            future.complete(null);
                        }
                    }
                }.start();
            });
        });

        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new FxTimeoutException("Timed out waiting for UI idle after " + timeoutMs + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FxTimeoutException("Interrupted waiting for UI idle", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error waiting for UI idle", e.getCause());
        }
    }

    /**
     * Checks if the FX toolkit is initialized.
     */
    public static boolean isFxToolkitInitialized() {
        try {
            Platform.runLater(() -> {
            });
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
