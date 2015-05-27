package edu.ucla.cs.ndnwhiteboard;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import net.named_data.jndn.security.SecurityException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import edu.ucla.cs.ndnwhiteboard.custom_views.DrawingView;
import edu.ucla.cs.ndnwhiteboard.helpers.TextToSpeechHelper;
import edu.ucla.cs.ndnwhiteboard.interfaces.NDNChronoSyncActivity;
import edu.ucla.cs.ndnwhiteboard.tasks.PingTask;

/**
 * WhiteboardActivity: Main activity that displays the whiteboard for drawing
 */
public class WhiteboardActivity extends NDNChronoSyncActivity { // ActionBarActivity
    private DrawingView drawingView_canvas; // Reference to the associated DrawingView

    // View references
    private ImageButton button_color;

    // Parameters passed from IntroActivity
    private String whiteboard;
    private String prefix;

    // NDN related references
    //public Face m_face;          // References to the Face being used
    //public ChronoSync2013 sync;  // References to the ChronoSync object

    Handler mHandler = new Handler();      // To handle view access from other threads
    ProgressDialog progressDialog = null;  // Progress dialog for initial setup
    TextToSpeechHelper ttsHelper; // Helper for TTS

    private String TAG = WhiteboardActivity.class.getSimpleName();  // TAG for logging
    protected static final int RESULT_SPEECH = 1;

    /**
     * Overriding the onCreate from Activity class
     *
     * @param savedInstanceState previous saved state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whiteboard);

        // Get parameters passed from IntroActivity
        Intent introIntent = getIntent();
        this.username = introIntent.getExtras().getString("name");  // from NDNChronoSyncActivity
        this.whiteboard = introIntent.getExtras().getString("whiteboard").replaceAll("\\s", "");
        this.prefix = introIntent.getExtras().getString("prefix");
        Log.d(TAG, "username: " + this.username);
        Log.d(TAG, "whiteboard: " + this.whiteboard);
        Log.d(TAG, "prefix: " + this.prefix);
        Toast.makeText(getApplicationContext(), "Welcome " + this.username, Toast.LENGTH_SHORT)
                .show();
        applicationNamePrefix = prefix + "/" + whiteboard + "/" + username;
        applicationBroadcastPrefix = "/ndn/broadcast/whiteboard/" + whiteboard;

        // Get relevant View references
        drawingView_canvas = (DrawingView) findViewById(R.id.drawingview_canvas);
        ImageButton button_pencil = (ImageButton) findViewById(R.id.button_pencil);
        ImageButton button_eraser = (ImageButton) findViewById(R.id.button_eraser);
        button_color = (ImageButton) findViewById(R.id.button_color);
        ImageButton button_save = (ImageButton) findViewById(R.id.button_save);
        ImageButton button_undo = (ImageButton) findViewById(R.id.button_undo);
        ImageButton button_clear = (ImageButton) findViewById(R.id.button_clear);

        // Set link to this activity in the DrawingView class
        drawingView_canvas.setWhiteboardActivity(this);

        // Set click listeners for the buttons
        button_pencil.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.setPencil();
            }
        });
        button_eraser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.setEraser();
            }
        });
        button_color.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.incrementColor();
            }
        });
        button_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmSave();
            }
        });
        button_undo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.undo();
            }
        });
        button_clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmErase();
            }
        });

        // Initialize TTS Helper
        ttsHelper = new TextToSpeechHelper(this);

        // Start Ping sequence
        activity_stop = false;
        dataHistory = new ArrayList<>();  // History of packets generated
        // Keeping track of what seq nos are requested from each user
        highestRequested = new HashMap<>();
        new PingTask(this).execute();

        // Show progress dialog for setup
        progressDialog = ProgressDialog.show(this, "Initializing", "Performing ping", true);
        Log.d(TAG, "Finished onCreate");
    }

    /**
     * Overriding onDestroy of the Activity class
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Set the boolean flag that stops all long running loops
        activity_stop = true;

        // Shut down face if it is not null
        if (m_face != null) {
            m_face.shutdown();
            Log.d(TAG, "Shutting down Face");
        }
        Log.d(TAG, "Finished onDestroy");
    }

    /**
     * Show menu items
     *
     * @param menu menu for this activity
     * @return whether menu was inflated successfully
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_whiteboard, menu);
        return true;
    }

    /**
     * Handle menu item click
     *
     * @param item the selected item from the menu
     * @return whether item click was handled
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_text:
                textMessage();
                return true;
            case R.id.action_speech:
                speechMessage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Listen to user, convert to text and send.
     */
    private void speechMessage() {
        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");
        try {
            startActivityForResult(intent, RESULT_SPEECH);
        } catch (ActivityNotFoundException a) {
            Toast t = Toast.makeText(getApplicationContext(),
                    "Opps! Your device doesn't support Speech to Text",
                    Toast.LENGTH_SHORT);
            t.show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RESULT_SPEECH: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> text = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                    String speechStr = text.get(0);
                    final JSONObject jObject = new JSONObject();
                    try {
                        jObject.put("data", speechStr);
                        jObject.put("type", "speech");
                        jObject.put("user", username);
                        callback(jObject.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(getApplicationContext(), "Message sent", Toast.LENGTH_SHORT).show();
                }
                break;
            }

        }
    }

    /**
     * Function to handle user wanting to send a text message
     */
    public void textMessage() {
        final JSONObject jObject = new JSONObject();  // JSON object to store message

        // Show dialog to enter the message
        final AlertDialog.Builder textBox = new AlertDialog.Builder(this);
        textBox.setTitle("NDN Text");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textBox.setView(input);
        textBox.setPositiveButton("Send", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Store message in JSON object
                try {
                    jObject.put("data", input.getText().toString());
                    jObject.put("type", "text");
                    jObject.put("user", username);
                    callback(jObject.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Toast.makeText(getApplicationContext(), "Message sent", Toast.LENGTH_SHORT).show();
            }
        });
        textBox.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
                dialog.cancel();
            }
        });
        textBox.show();
    }

    /**
     * Function to handle situation where user of this device draws a new stroke.
     *
     * @param jsonData the json representation of the user's action
     */
    public void callback(String jsonData) {
        dataHistory.add(jsonData);  // Add action to history

        // Create a new thread to publish new sequence numbers
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sync != null) {
                        while (sync.getSequenceNo() < dataHistory.size()
                                && sync.getSequenceNo() != -1) {
                            Log.d(TAG, "Seq is now: " + sync.getSequenceNo());
                            sync.publishNextSequenceNo();
                            Log.d(TAG, "Published next seq number. Seq is now: "
                                    + sync.getSequenceNo());
                        }
                    }
                } catch (IOException | SecurityException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        Log.d(TAG, "Stroke generated: " + jsonData);
    }

    /**
     * Change color of the color button based the currently active color
     *
     * @param color the color to paint the button
     */
    public void setButtonColor(int color) {
        button_color.setBackgroundColor(color);
    }

    /**
     * Function to confirm that user want's the canves to be erased
     */
    private void confirmErase() {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Confirm erase")
                .setMessage("Are you sure you want to erase the canvas?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        drawingView_canvas.clear();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    /**
     * Function to confirm that the user want's to save the current whiteboard state as a image in
     * phone gallery
     */
    private void confirmSave() {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Confirm canvas save")
                .setMessage("Do you want to save the canvas?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        drawingView_canvas.setDrawingCacheEnabled(true);
                        Date date = new Date();
                        Format formatter = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss", Locale.US);
                        String fileName = formatter.format(date) + ".png";
                        if (android.os.Environment.getExternalStorageState()
                                .equals(android.os.Environment.MEDIA_MOUNTED)) {
                            File sdCard = Environment.getExternalStorageDirectory();
                            File dir = new File(sdCard.getAbsolutePath() + "/NDN_Whiteboard");
                            boolean directoryCreated = dir.mkdirs();
                            if (!directoryCreated) {
                                Toast.makeText(getApplicationContext(), "Save Failed!",
                                        Toast.LENGTH_SHORT).show();
                            }
                            File file = new File(dir, fileName);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            drawingView_canvas.getDrawingCache()
                                    .compress(Bitmap.CompressFormat.PNG, 100, baos);
                            FileOutputStream f;
                            try {
                                f = new FileOutputStream(file);
                                f.write(baos.toByteArray());
                                f.flush();
                                f.close();
                                Toast.makeText(getApplicationContext(), "Saved", Toast.LENGTH_SHORT)
                                        .show();
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(), "Save Failed!",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                        drawingView_canvas.destroyDrawingCache();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    /**
     * Speak out the message
     *
     * @param ttsStr the String to be spoken out
     */
    public void speakOut(String ttsStr) {
        ttsHelper.speakOut(ttsStr);
    }

    /**
     * @return the Handler for this activity
     */
    @Override
    public Handler getHandler() {
        return mHandler;
    }

    /**
     * @return the progress dialog for initial NDN setup
     */
    @Override
    public ProgressDialog getProgressDialog() {
        return progressDialog;
    }

    @Override
    public void handleDataReceived(String data) {
        drawingView_canvas.callback(data);
    }
}
