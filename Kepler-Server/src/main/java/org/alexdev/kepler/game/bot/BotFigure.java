package org.alexdev.kepler.game.bot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns the figure stored in {@code rooms_bots.figure} into a v14 figure so every bot is sent as a
 * normal user (no {@code [bot]} marker) and the infostand draws its avatar.
 *
 * <p>Why: with {@code [bot]} the client builds the entity with Bot_Object_Class, whose getInfo() sets
 * the infostand picture to {@code image(1, 1, 8)}. Without it, Figure_System_Class.parseFigure only
 * accepts the 25 digit v14 format: five "setId(3) + colour index(2)" groups resolved through
 * figuredata for the given sex.
 *
 * <p>Legacy figures ({@code "sd=001&hr=203/103,78,59&ch=202/139,24,32&..."}, models + RGB) are
 * converted part by part (hr, hd, lg, sh, ch) using {@code bots/bot_figuredata.txt}, generated from the
 * production figuredata and the hh_people_* casts:
 * <ul>
 *     <li>the set with the same model for that sex, else the model with the closest bitmap
 *     ({@code alias} lines), else the default set of that part;</li>
 *     <li>ties broken by sleeves (ch) or eyes (hd);</li>
 *     <li>the colour is the closest palette entry of that set to the legacy RGB.</li>
 * </ul>
 * Sets flagged {@code x} are never picked: ch 200/201/202 are 32-bit bitmaps with an empty alpha in
 * the small casts (invisible shirt in the Welcome Lounge), heads with hd model 2 do not exist in small
 * size and hr 457 is 32-bit in normal size.
 */
public final class BotFigure {
    private static final String RESOURCE = "/bots/bot_figuredata.txt";
    private static final String[] MAIN_PARTS = {"hr", "hd", "lg", "sh", "ch"};
    private static final int WHITE = 0xEEEEEE;

    private static final Pattern V14_FIGURE = Pattern.compile("^\\d{25}$");
    private static final Pattern LEGACY_PART = Pattern.compile("^[a-z]{2,3}=\\d{1,3}(/[0-9, ]*)?$");
    /** Some kepler.sql rows have the start_look glued to the figure ("...ch=700/255,255,255look=2,2"). */
    private static final Pattern TRAILING_LOOK = Pattern.compile("\\s*look=\\d+,\\d+\\s*$");

    /** Bots whose legacy figure reads as the wrong sex (Mario wears a female legacy shirt). */
    private static final Map<String, String> SEX_BY_NAME = Map.of("mario", "M");

    /** Fallback looks: hr, hd, lg, sh, ch. Checked against the data by the tests. */
    public static final String DEFAULT_MALE = "8310818501275343000621022";
    public static final String DEFAULT_FEMALE = "5000460501715017301063007";

    private static final Data DATA = Data.load();

    private BotFigure() { }

    public static boolean isV14(String figure) {
        return figure != null && V14_FIGURE.matcher(figure).matches();
    }

    public static boolean isLegacy(String figure) {
        return !isV14(figure);
    }

    public static String normalise(String stored) {
        return normalise(stored, null);
    }

    /**
     * @param stored the raw value from rooms_bots.figure
     * @param botName the bot name, used for the few sex overrides
     * @return always a complete v14 figure (hair, head, legs, shoes and shirt) that draws in every room size
     */
    public static String normalise(String stored, String botName) {
        String override = botName == null ? null : SEX_BY_NAME.get(botName.trim().toLowerCase(Locale.ROOT));
        String figure = stored == null ? "" : stored.trim();

        try {
            if (isV14(figure)) {
                return normaliseV14(figure, override);
            }

            Map<String, LegacyPart> legacy = parseLegacy(figure);

            // Only the head is required; missing hair/clothes are completed with defaults.
            if (legacy.containsKey("hd")) {
                String sex = override != null ? override : guessSex(legacy);
                return encode(sex, toWanted(legacy), Map.of());
            }
        } catch (RuntimeException ignored) {
            // fall through to the default look
        }

        return "F".equals(override) ? DEFAULT_FEMALE : DEFAULT_MALE;
    }

    /**
     * The sex sent in USERS: figuredata keeps male and female set ids apart, so it is read from the sets.
     */
    public static String sexOf(String figure) {
        if (!isV14(figure)) {
            return "M";
        }

        int female = 0;
        int male = 0;

        for (int i = 0; i < 25; i += 5) {
            FigureSet set = DATA.sets.get(Integer.parseInt(figure.substring(i, i + 3)));

            if (set != null) {
                if (set.sex.equals("F")) {
                    female++;
                } else {
                    male++;
                }
            }
        }

        return female > male ? "F" : "M";
    }

    /** Whether every group is a usable set of the given sex, with all five parts present. */
    static boolean isComplete(String figure, String sex) {
        if (!isV14(figure)) {
            return false;
        }

        Map<String, Boolean> seen = new HashMap<>();

        for (int i = 0; i < 25; i += 5) {
            FigureSet set = DATA.sets.get(Integer.parseInt(figure.substring(i, i + 3)));
            int colour = Integer.parseInt(figure.substring(i + 3, i + 5));

            if (set == null || !set.usable || !set.sex.equals(sex) || colour < 1 || colour > set.colours.length) {
                return false;
            }

            seen.put(set.part, true);
        }

        return seen.size() == MAIN_PARTS.length;
    }

    private static String normaliseV14(String figure, String override) {
        String sex = override != null ? override : sexOf(figure);
        Map<String, String> keep = new HashMap<>();
        Map<String, Wanted> wanted = new HashMap<>();

        for (int i = 0; i < 25; i += 5) {
            FigureSet set = DATA.sets.get(Integer.parseInt(figure.substring(i, i + 3)));

            if (set == null) {
                continue;
            }

            int colour = Math.max(1, Math.min(set.colours.length, Integer.parseInt(figure.substring(i + 3, i + 5))));

            if (set.usable && set.sex.equals(sex)) {
                keep.put(set.part, group(set.id, colour));
            } else {
                wanted.put(set.part, new Wanted(set.model, set.extra, set.colours[colour - 1]));
            }
        }

        return encode(sex, wanted, keep);
    }

    private static String encode(String sex, Map<String, Wanted> wanted, Map<String, String> keep) {
        StringBuilder figure = new StringBuilder(25);

        for (String part : MAIN_PARTS) {
            if (keep.containsKey(part)) {
                figure.append(keep.get(part));
                continue;
            }

            figure.append(pick(sex, part, wanted.get(part)));
        }

        return figure.toString();
    }

    private static String pick(String sex, String part, Wanted wanted) {
        List<FigureSet> usable = DATA.usable(sex, part);
        List<FigureSet> candidates = new ArrayList<>();

        if (wanted == null) {
            return defaultFor(sex).substring(index(part), index(part) + 5);
        }

        if (!part.equals("hd")) {
            candidates = withModel(usable, wanted.model);

            if (candidates.isEmpty()) {
                Integer alias = DATA.aliases.get(sex + " " + part + " " + wanted.model);

                if (alias != null) {
                    candidates = withModel(usable, alias);
                }
            }
        } else {
            candidates = new ArrayList<>(usable);
        }

        if (candidates.isEmpty()) {
            String fallback = defaultFor(sex).substring(index(part), index(part) + 5);

            // Unknown model (costume parts such as hr=802/995 or ch=995 that only exist in other casts).
            // A costume "hair" is a hat, so its colour is not a hair colour: keep the default hair.
            // Clothes keep their colour on the default set (white chef/uniform stays white).
            if (part.equals("hr")) {
                return fallback;
            }

            candidates = List.of(DATA.sets.get(Integer.parseInt(fallback.substring(0, 3))));
        }

        if (wanted.extra > 0) {
            List<FigureSet> sameExtra = new ArrayList<>();

            for (FigureSet set : candidates) {
                if (set.extra == wanted.extra) {
                    sameExtra.add(set);
                }
            }

            if (!sameExtra.isEmpty()) {
                candidates = sameExtra;
            }
        }

        if (wanted.rgb == null) {
            return group(candidates.get(0).id, 1);
        }

        FigureSet best = null;
        int bestColour = 1;
        long bestDistance = Long.MAX_VALUE;

        for (FigureSet set : candidates) {
            for (int i = 0; i < set.colours.length; i++) {
                long distance = distance(set.colours[i], wanted.rgb);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = set;
                    bestColour = i + 1;
                }
            }
        }

        return group(best.id, bestColour);
    }

    private static List<FigureSet> withModel(List<FigureSet> sets, int model) {
        List<FigureSet> result = new ArrayList<>();

        for (FigureSet set : sets) {
            if (set.model == model) {
                result.add(set);
            }
        }

        return result;
    }

    private static int index(String part) {
        for (int i = 0; i < MAIN_PARTS.length; i++) {
            if (MAIN_PARTS[i].equals(part)) {
                return i * 5;
            }
        }

        throw new IllegalArgumentException(part);
    }

    private static String defaultFor(String sex) {
        return sex.equals("F") ? DEFAULT_FEMALE : DEFAULT_MALE;
    }

    private static String group(int setId, int colour) {
        return String.format(Locale.ROOT, "%03d%02d", setId, colour);
    }

    private static long distance(int a, int b) {
        long dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        long dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        long db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    // ---- legacy format ----

    private static Map<String, LegacyPart> parseLegacy(String figure) {
        Map<String, LegacyPart> parts = new LinkedHashMap<>();

        for (String part : TRAILING_LOOK.matcher(figure).replaceAll("").split("&")) {
            part = part.trim();

            if (!LEGACY_PART.matcher(part).matches()) {
                continue;
            }

            String key = part.substring(0, part.indexOf('='));
            String value = part.substring(part.indexOf('=') + 1);
            int slash = value.indexOf('/');
            int model = Integer.parseInt(slash < 0 ? value : value.substring(0, slash));
            String colour = slash < 0 ? "" : value.substring(slash + 1);

            parts.put(key, new LegacyPart(model, legacyColour(colour)));
        }

        return parts;
    }

    /** Same rules as the "bot" branch of Figure_System_Class.parseFigure. */
    static int legacyColour(String colour) {
        String[] items = colour.isEmpty() ? new String[0] : colour.split(",", -1);

        try {
            if (items.length == 1) {
                int index = Integer.parseInt(items[0].trim());
                return index == 0 ? WHITE : macSystemPalette(index);
            }

            if (items.length == 3) {
                int r = clamp(Integer.parseInt(items[0].trim()));
                int g = clamp(Integer.parseInt(items[1].trim()));
                int b = clamp(Integer.parseInt(items[2].trim()));

                if (r + g + b > 238 * 3) {
                    return WHITE;
                }

                return (r << 16) | (g << 8) | b;
            }
        } catch (NumberFormatException ignored) {
            // invalid colour, same as the client: EEEEEE
        }

        return WHITE;
    }

    /** paletteIndex() of Director's default #systemMac palette, used by legacy colours like "163". */
    static int macSystemPalette(int index) {
        if (index < 0 || index > 255) {
            return WHITE;
        }

        if (index < 215) {
            int r = 255 - 51 * (index / 36);
            int g = 255 - 51 * ((index / 6) % 6);
            int b = 255 - 51 * (index % 6);
            return (r << 16) | (g << 8) | b;
        }

        if (index == 255) {
            return 0;
        }

        int[] ramp = {238, 221, 187, 170, 136, 119, 85, 68, 34, 17};
        int step = ramp[(index - 215) % 10];

        switch ((index - 215) / 10) {
            case 0: return step << 16;
            case 1: return step << 8;
            case 2: return step;
            default: return (step << 16) | (step << 8) | step;
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static Map<String, Wanted> toWanted(Map<String, LegacyPart> legacy) {
        Map<String, Wanted> wanted = new HashMap<>();

        for (String part : new String[]{"hr", "lg", "sh", "ch"}) {
            LegacyPart value = legacy.get(part);

            if (value != null) {
                int extra = part.equals("ch") && legacy.containsKey("ls") ? legacy.get("ls").model : 0;
                wanted.put(part, new Wanted(value.model, extra, value.rgb));
            }
        }

        LegacyPart head = legacy.get("hd");
        int eyes = legacy.containsKey("ey") ? legacy.get("ey").model : 0;
        wanted.put("hd", new Wanted(head.model, eyes, head.rgb));
        return wanted;
    }

    /**
     * Legacy figures carry no sex. Count the hr/ch/lg/sh models that only one sex has in figuredata;
     * legacy eyes 002 (with lashes) count double for female.
     */
    private static String guessSex(Map<String, LegacyPart> legacy) {
        int male = 0;
        int female = 0;

        for (String part : new String[]{"hr", "ch", "lg", "sh"}) {
            LegacyPart value = legacy.get(part);

            if (value == null) {
                continue;
            }

            boolean inMale = DATA.hasModel("M", part, value.model);
            boolean inFemale = DATA.hasModel("F", part, value.model);

            if (inMale && !inFemale) {
                male++;
            } else if (inFemale && !inMale) {
                female++;
            }
        }

        if (legacy.containsKey("ey") && legacy.get("ey").model == 2) {
            female += 2;
        }

        return female > male ? "F" : "M";
    }

    // ---- data ----

    private record LegacyPart(int model, Integer rgb) { }

    private record Wanted(int model, int extra, Integer rgb) { }

    static final class FigureSet {
        final String sex;
        final String part;
        final int id;
        final int model;
        final int extra;
        final boolean usable;
        final int[] colours;

        FigureSet(String sex, String part, int id, int model, int extra, boolean usable, int[] colours) {
            this.sex = sex;
            this.part = part;
            this.id = id;
            this.model = model;
            this.extra = extra;
            this.usable = usable;
            this.colours = colours;
        }
    }

    private static final class Data {
        final Map<Integer, FigureSet> sets = new LinkedHashMap<>();
        final Map<String, Integer> aliases = new HashMap<>();

        static Data load() {
            Data data = new Data();

            try (InputStream stream = BotFigure.class.getResourceAsStream(RESOURCE)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing resource " + RESOURCE);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
                String line;

                while ((line = reader.readLine()) != null) {
                    String[] t = line.trim().split(" ");

                    if (t[0].equals("set")) {
                        String[] hex = t[7].split(",");
                        int[] colours = new int[hex.length];

                        for (int i = 0; i < hex.length; i++) {
                            colours[i] = Integer.parseInt(hex[i], 16);
                        }

                        int id = Integer.parseInt(t[3]);
                        data.sets.put(id, new FigureSet(t[1], t[2], id, Integer.parseInt(t[4]),
                                t[5].equals("-") ? 0 : Integer.parseInt(t[5]), t[6].equals("ok"), colours));
                    } else if (t[0].equals("alias")) {
                        data.aliases.put(t[1] + " " + t[2] + " " + t[3], Integer.parseInt(t[4]));
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + RESOURCE, e);
            }

            return data;
        }

        List<FigureSet> usable(String sex, String part) {
            List<FigureSet> result = new ArrayList<>();

            for (FigureSet set : this.sets.values()) {
                if (set.usable && set.sex.equals(sex) && set.part.equals(part)) {
                    result.add(set);
                }
            }

            return result;
        }

        boolean hasModel(String sex, String part, int model) {
            for (FigureSet set : this.sets.values()) {
                if (set.sex.equals(sex) && set.part.equals(part) && set.model == model) {
                    return true;
                }
            }

            return false;
        }
    }
}
