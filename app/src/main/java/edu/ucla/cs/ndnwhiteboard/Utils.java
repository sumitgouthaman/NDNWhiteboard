package edu.ucla.cs.ndnwhiteboard;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by sumit on 5/15/15.
 */
public class Utils {
    public static String genWhiteboardName() {
        String timeStamp = new SimpleDateFormat("dd_HH_").format(Calendar.getInstance().getTime());
        int minute = Calendar.getInstance().getTime().getMinutes() / 10;
        return "board_" + timeStamp + "" + minute;
    }

    public static String generateRandomName() {
        String seed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int length = 6;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int random = (int) (Math.random() * seed.length());
            sb.append(seed.charAt(random) + "");
        }
        return sb.toString();
    }
}
