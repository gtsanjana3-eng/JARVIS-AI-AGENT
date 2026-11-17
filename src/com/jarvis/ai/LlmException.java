package com.jarvis.ai;

/** A failure talking to the reasoning engine, with a message that is safe to show the user. */
public class LlmException extends Exception {

    public enum Kind { NOT_RUNNING, MODEL_MISSING, TIMEOUT, CANCELLED, HTTP_ERROR, BAD_RESPONSE }

    private final Kind kind;

    public LlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
