package org.alexdev.kepler.game.pets;

import org.alexdev.kepler.game.moderation.WordfilterManager;

import java.text.Normalizer;

public final class PetNameValidation {
    private PetNameValidation() { }

    /**
     * Composed (NFC) form: input normalisation may decompose accents (n + U+0303),
     * which would otherwise fail the Latin-1 check after the nest was paid for.
     */
    public static String normalise(String name) {
        return name == null ? null : Normalizer.normalize(name, Normalizer.Form.NFC);
    }

    public static int getNameCheckCode(String name) {
        name = normalise(name);
        if (name == null || name.trim().isEmpty()) {
            return 2;
        }
        if (name.length() > 15) {
            return 1;
        }
        // The v14 text field has no username whitelist. Allow printable Latin-1
        // without spaces (pets obey commands by their first word), and reject
        // protocol delimiters and control characters.
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch <= 32 || (ch >= 127 && ch <= 160) || ch > 255) {
                return 3;
            }
        }
        return WordfilterManager.filterSentence(name).equals(name) ? 0 : 3;
    }
}
