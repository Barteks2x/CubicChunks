package io.github.opencubicchunks.cubicchunks.utils;

import net.minecraft.util.StringRepresentable;

/**
 * Simple enum representing a failure.
 */
public enum ExceptionHandler implements StringRepresentable {
    EXCEPTION_THROWN(true, true, "When this fails, the exception will be thrown causing a crash."),
    EXCEPTION_LOGGED(true, false, "When this fails, the exception is caught and logged."),
    EXCEPTION_SILENCED(false, false, "When this fails, the exception is caught and not logged.");

    private final boolean logException;
    private final boolean crash;
    private final String description;

    ExceptionHandler(boolean logException, boolean crash, String description) {
        this.logException = logException;
        this.crash = crash;
        this.description = description;
    }

    public boolean isLogException() {
        return logException;
    }

    public boolean isCrash() {
        return crash;
    }

    public <T extends Throwable> void wrapException(T throwable) throws T {
        if (crash) {
            throw throwable;
        } else if (logException) {
            throwable.printStackTrace();
        }
    }

    @Override public String getSerializedName() {
        return this.name() + ": " + description;
    }
}
