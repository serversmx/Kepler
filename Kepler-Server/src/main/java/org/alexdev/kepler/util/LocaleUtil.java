package org.alexdev.kepler.util;

import java.util.Locale;

/**
 * Normalises the language stored in users.user_language to one of the
 * supported catalogue locales: "es" (default), "en" or "pt".
 */
public class LocaleUtil {
    public static final String DEFAULT_LOCALE = "es";

    public static String normalize(String raw) {
        if (raw == null) {
            return DEFAULT_LOCALE;
        }

        String value = raw.trim().toLowerCase(Locale.ROOT);

        if (value.length() >= 2) {
            value = value.substring(0, 2);
        }

        return value.equals("en") || value.equals("pt") ? value : DEFAULT_LOCALE;
    }

    /**
     * Returns the translated text, or the fallback when it is null or blank.
     */
    public static String pick(String translated, String fallback) {
        return translated == null || translated.trim().isEmpty() ? fallback : translated;
    }
}
