package edu.ucla.cs.ndnwhiteboard;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * A simple class with utility functions.
 */
public class Utils {
    /**
     * Generates name for whiteboard.
     * <p/>
     * Ensures that whiteboard names generated within 5 minutes of each other are same. The format
     * of the name is:
     * board_[day_of_month]_[hour_of_day]_[minute_divided_by_5]
     *
     * @return The generated whiteboard name
     */
    public static String genWhiteboardName() {
        String timeStamp = new SimpleDateFormat("dd_HH_", Locale.US).format(Calendar.getInstance().getTime());
        @SuppressWarnings("deprecation")
        int minute = Calendar.getInstance().getTime().getMinutes() / 10;
        return "board_" + timeStamp + "" + minute;
    }

    /**
     * Function to generate random name for the user.
     * <p/>
     * THe name will include lower and upper case alphabets.
     *
     * @return the generated username
     */
    public static String generateRandomName() {
        String seed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int length = 6;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int random = (int) (Math.random() * seed.length());
            sb.append(seed.charAt(random));
        }
        return sb.toString();
    }
}
