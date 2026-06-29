package com.example.vex360.shared.utils;

public class LogSanitizer {

    private static final int MAX_LOG_PARAM_LENGTH = 255;

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        // Remove CRLF to prevent log injection (log forging)
        String clean = input.replace("\n", "_").replace("\r", "_");
        
        // Truncate to prevent log flooding (Denial of Service)
        if (clean.length() > MAX_LOG_PARAM_LENGTH) {
            clean = clean.substring(0, MAX_LOG_PARAM_LENGTH) + "...[truncated]";
        }
        return clean;
    }
}
