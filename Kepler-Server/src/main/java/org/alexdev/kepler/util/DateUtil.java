package org.alexdev.kepler.util;

import org.alexdev.kepler.log.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {
    private static final String LONG_DATE = "dd-MM-yyyy HH:mm:ss";
    private static final String SHORT_DATE = "dd-MM-yyyy";

    /**
     * Returns the current date as "dd-MM-yyyy"
     * @return the date as string
     */
    public static String getShortDate() {
        Date date = new Date();
        return new SimpleDateFormat(SHORT_DATE).format(date);
    }

    /**
     * Returns the current date as "dd-MM-yyyy"
     * @return the date as string
     */
    public static String getShortDate(long time) {
        try {
            Date date = new Date();
            date.setTime(time * 1000L);
            return new SimpleDateFormat(SHORT_DATE).format(date);
        } catch (Exception e) {
            Log.getErrorLogger().error("Could not format short date for timestamp {}", time, e);
        }

        return getShortDate();
    }

    /**
     * Gets the current formatted date as string.
     *
     * @return the date as string
     */
    public static String getDateAsString() {
        try {
            Date date = new Date();
            return new SimpleDateFormat(LONG_DATE).format(date);
        } catch (Exception e) {
            Log.getErrorLogger().error("Could not format current date", e);
        }

        return "";
    }

    /**
     * Gets the date given by unix timestamp as string.
     *
     * @param time the unix timestamp
     * @return the date as string
     */
    public static String getDateAsString(long time) {
        try {
            Date date = new Date();
            date.setTime(time * 1000L);
            return new SimpleDateFormat(LONG_DATE).format(date);
        } catch (Exception e) {
            Log.getErrorLogger().error("Could not format date for timestamp {}", time, e);
        }

        return getDateAsString();
    }

    /**
     * Gets the readable timestamp.
     *
     * @param timestamp the timestamp
     * @return the readable timestamp
     */
    public static String getReadableTimestamp(long timestamp) {
        try {
            long different = System.currentTimeMillis() - (timestamp * 1000);

            long secondsInMilli = 1000;
            long minutesInMilli = secondsInMilli * 60;
            long hoursInMilli = minutesInMilli * 60;
            long daysInMilli = hoursInMilli * 24;

            long elapsedDays = different / daysInMilli;
            different = different % daysInMilli;

            long elapsedHours = different / hoursInMilli;
            different = different % hoursInMilli;

            long elapsedMinutes = different / minutesInMilli;
            different = different % minutesInMilli;

            long elapsedSeconds = different / secondsInMilli;
            return elapsedDays + " days, " + elapsedHours + " hours, " + elapsedMinutes + " minutes, " + elapsedSeconds + " seconds";

        } catch (Exception e){
            Log.getErrorLogger().error("Error occurred: ", e);
        }

        return "0 days, 0 hours, 0 minutes, 0 seconds";
    }

    /**
     * Convert seconds to readable English.
     *
     * @param input the seconds input
     * @return the seconds represented as words
     */
    public static String getReadableSeconds(long input) {
        // Spanish without accents: the v14 catalogue bitmap fonts cannot draw them.
        long seconds = Math.max(0, input);
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        java.util.List<String> parts = new java.util.ArrayList<>();

        if (days > 0) {
            parts.add(days + (days == 1 ? " dia" : " dias"));
        }

        if (hours > 0) {
            parts.add(hours + (hours == 1 ? " hora" : " horas"));
        }

        if (minutes > 0 || parts.isEmpty()) {
            parts.add(minutes + (minutes == 1 ? " minuto" : " minutos"));
        }

        if (parts.size() == 1) {
            return parts.get(0);
        }

        return String.join(", ", parts.subList(0, parts.size() - 1)) + " y " + parts.get(parts.size() - 1);
    }


    /**
     * Gets the current time in seconds.
     *
     * @return the current time in seconds
     */
    public static int getCurrentTimeSeconds() {
        return (int) (System.currentTimeMillis() / 1000);
    }
}
