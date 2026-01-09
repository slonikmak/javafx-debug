package com.github.mcpjavafx.util;

/**
 * Common string utilities.
 */
public final class StringUtils {

    private StringUtils() {
        // Utility class
    }

    /**
     * Truncates a string to the specified maximum length, adding ellipsis if
     * truncated.
     *
     * @param s      the string to truncate
     * @param maxLen the maximum length (must be at least 4 for ellipsis to work)
     * @return the truncated string, or the original if shorter than maxLen
     */
    public static String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        if (maxLen < 4) {
            return s.length() <= maxLen ? s : s.substring(0, maxLen);
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen - 3) + "...";
    }

    /**
     * Checks if a string is null or blank.
     *
     * @param s the string to check
     * @return true if null or blank
     */
    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Checks if a string is not null and not blank.
     *
     * @param s the string to check
     * @return true if not null and not blank
     */
    public static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
