package com.jarvis.tools;

/** An expected failure inside a tool (bad input, missing file, network down...). */
public class ToolException extends Exception {

    public ToolException(String message) {
        super(message);
    }
}
