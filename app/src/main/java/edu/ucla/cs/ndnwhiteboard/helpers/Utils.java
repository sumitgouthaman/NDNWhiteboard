package edu.ucla.cs.ndnwhiteboard.helpers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import net.named_data.jndn.Name;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import edu.ucla.cs.ndnwhiteboard.R;

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

    /**
     * Function to return a random english name from a predefined set.
     *
     * @param context the current context
     * @return the random name
     */
    public static String generateEnglishNames(Context context) {
        String[] englishNames = context.getResources().getStringArray(R.array.english_names);
        int length = englishNames.length;
        int randomIndex = (int) (Math.random() * (float) length);
        String randomName = englishNames[randomIndex];
        return randomName;
    }

    /**
     * Function to confirm that the user want's to save the current whiteboard state as a image in
     * phone gallery
     *
     * @param context the current activity context
     * @param baos    the image represented as a Byte Array Output Stream
     */
    public static void saveWhiteboardImage(final Context context, final ByteArrayOutputStream baos) {
        new AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Confirm canvas save")
                .setMessage("Do you want to save the canvas?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Date date = new Date();
                        Format formatter = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss", Locale.US);
                        String fileName = formatter.format(date) + ".png";
                        if (android.os.Environment.getExternalStorageState()
                                .equals(android.os.Environment.MEDIA_MOUNTED)) {
                            File sdCard = Environment.getExternalStorageDirectory();
                            File dir = new File(sdCard.getAbsolutePath() + "/NDN_Whiteboard");
                            boolean directoryCreated = dir.mkdirs();

                            File file = new File(dir, fileName);

                            FileOutputStream f;
                            try {
                                f = new FileOutputStream(file);
                                f.write(baos.toByteArray());
                                f.flush();
                                f.close();
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT)
                                        .show();
                                // Trigger gallery refresh on photo save
                                MediaScannerConnection.scanFile(
                                        context,
                                        new String[]{file.toString()},
                                        null,
                                        new MediaScannerConnection.OnScanCompletedListener() {
                                            public void onScanCompleted(String path, Uri uri) {
                                            }
                                        });
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(context, "Save Failed!",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    /**
     * Setup an in-memory KeyChain with a default identity.
     *
     * @return keyChain object
     * @throws net.named_data.jndn.security.SecurityException
     */
    public static KeyChain buildTestKeyChain() throws net.named_data.jndn.security.SecurityException {
        MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        IdentityManager identityManager = new IdentityManager(identityStorage, privateKeyStorage);
        KeyChain keyChain = new KeyChain(identityManager);
        try {
            keyChain.getDefaultCertificateName();
        } catch (net.named_data.jndn.security.SecurityException e) {
            keyChain.createIdentity(new Name("/test/identity"));
            keyChain.getIdentityManager().setDefaultIdentity(new Name("/test/identity"));
        }
        return keyChain;
    }
}
