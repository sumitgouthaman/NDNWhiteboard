package edu.ucla.cs.ndnwhiteboard;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by sumit on 5/15/15.
 */
public class Utils {
    public static String genWhiteboardName() {
        String timeStamp = new SimpleDateFormat("dd_HH_").format(Calendar.getInstance().getTime());
        int minute = Calendar.getInstance().getTime().getMinutes()/10;
        return "board_" + timeStamp + "" + minute;
    }
}
