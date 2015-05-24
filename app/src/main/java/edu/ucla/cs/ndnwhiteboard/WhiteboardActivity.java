package edu.ucla.cs.ndnwhiteboard;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.sync.ChronoSync2013;
import net.named_data.jndn.util.Blob;

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
import java.util.List;
import java.util.Map;

/**
 * WhiteboardActivity: Main activity that displays the whiteboard for drawing
 */
public class WhiteboardActivity extends ActionBarActivity {
    private DrawingView drawingView_canvas; // Reference to the associated DrawingView

    // View references
    private ImageButton button_pencil;
    private ImageButton button_eraser;
    private ImageButton button_color;
    private ImageButton button_save;
    private ImageButton button_undo;
    private ImageButton button_clear;

    // Parameters passed from IntroActivity
    public String username;
    private String whiteboard;
    private String prefix;

    boolean activity_stop = false; // To know when to stop long-running loops
    ArrayList<String> dataHist = new ArrayList<String>();  // History of packets generated

    // Keeping track of what seq nos are requested from each user
    Map<String, Long> highestRequested = new HashMap<String, Long>();

    // NDN related references
    private Face m_face;          // References to the Face being used
    private ChronoSync2013 sync;  // References to the ChronoSync object

    Handler mHandler = new Handler();      // To handle view access from other threads
    ProgressDialog progressDialog = null;  // Progress dialog for initial setup

    private String TAG = WhiteboardActivity.class.getSimpleName();  // TAG for logging

    /**
     * Overriding the onCreate from Activity class
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whiteboard);

        // Get parameters passed from IntroActivity
        Intent introIntent = getIntent();
        this.username = introIntent.getExtras().getString("name");
        this.whiteboard = introIntent.getExtras().getString("whiteboard").replaceAll("\\s", "");
        this.prefix = introIntent.getExtras().getString("prefix");
        Log.d(TAG, "username: " + this.username);
        Log.d(TAG, "whiteboard: " + this.whiteboard);
        Log.d(TAG, "prefix: " + this.prefix);
        Toast.makeText(getApplicationContext(), "Welcome " + this.username, Toast.LENGTH_SHORT)
                .show();

        // Get relevant View references
        drawingView_canvas = (DrawingView) findViewById(R.id.drawingview_canvas);
        button_pencil = (ImageButton) findViewById(R.id.button_pencil);
        button_eraser = (ImageButton) findViewById(R.id.button_eraser);
        button_color = (ImageButton) findViewById(R.id.button_color);
        button_save = (ImageButton) findViewById(R.id.button_save);
        button_undo = (ImageButton) findViewById(R.id.button_undo);
        button_clear = (ImageButton) findViewById(R.id.button_clear);

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

        // Start Ping sequence
        activity_stop = false;
        new PingTask().execute();

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
     * @param menu
     * @return
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
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_text:
                textMessage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
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
        dataHist.add(jsonData);  // Add action to history

        // Create a new thread to publish new sequence numbers
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sync != null) {
                        while (sync.getSequenceNo() < dataHist.size()
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
     * @param color
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
                        Format formatter = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss");
                        String fileName = formatter.format(date) + ".png";
                        if (android.os.Environment.getExternalStorageState()
                                .equals(android.os.Environment.MEDIA_MOUNTED)) {
                            File sdCard = Environment.getExternalStorageDirectory();
                            File dir = new File(sdCard.getAbsolutePath() + "/NDN_Whiteboard");
                            dir.mkdirs();
                            File file = new File(dir, fileName);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            drawingView_canvas.getDrawingCache()
                                    .compress(Bitmap.CompressFormat.PNG, 100, baos);
                            FileOutputStream f = null;
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
     * AsyncTask that performs the ping sequence.
     * <p/>
     * It is necessary to perform a Ping before doing a register prefix. Or else, the register
     * prefix request will be ignored by the hub.
     * <p/>
     * ALSO: Initiates task to register application prefix once Ping is successful
     */
    private class PingTask extends AsyncTask<Void, Void, String> {
        private String m_retVal = "not changed";
        private boolean m_shouldStop = false;

        @Override
        protected String doInBackground(Void... voids) {
            Log.d(TAG, "Ping Task (doInBackground)");
            try {
                // Initialize the Face
                m_face = new Face("localhost");

                // Express the ping Interest
                m_face.expressInterest(new Name("/ndn/edu/ucla/remap/ping"),
                        new OnData() {
                            @Override
                            public void
                            onData(Interest interest, Data data) {
                                m_retVal = data.getContent().toString();
                                Log.i("NDN", data.getContent().toHex());
                                m_shouldStop = true;
                            }
                        },
                        new OnTimeout() {
                            @Override
                            public void onTimeout(Interest interest) {
                                m_retVal = "ERROR: Timeout trying";
                                m_shouldStop = true;
                            }
                        });

                // Keep precessing events on the face (necessary)
                while (!m_shouldStop && !activity_stop) {
                    m_face.processEvents();
                    Thread.sleep(100);
                }

                return m_retVal;
            } catch (Exception e) {
                m_retVal = "ERROR: " + e.getMessage();
                return m_retVal;
            }
        }

        @Override
        protected void onPostExecute(final String result) {
            Log.d(TAG, "Ping Task (onPostExecute)");
            if (m_retVal.contains("ERROR:")) {
                //If error, stop sequence and end activity
                progressDialog.dismiss();
                new AlertDialog.Builder(WhiteboardActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Error received")
                        .setMessage(m_retVal)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .show();
            } else {
                // Successful ping, trigger register prefix task
                progressDialog.setMessage("Registering prefix");
                Log.d(TAG, "Ping Task succeeded: " + m_retVal);
                Log.d(TAG, "About to trigger Register Prefix Task");
                new RegisterPrefixTask().execute();
            }
        }

    }

    /**
     * AsyncTask to perform a registeration of this user's prefix.
     * <p/>
     * ALSO: Starts the task to register for ChronoSync.
     */
    private class RegisterPrefixTask extends AsyncTask<Void, Void, String> {
        private String m_retVal = "not changed";

        @Override
        protected String doInBackground(Void... params) {
            Log.d(TAG, "Register Prefix Task (doInBackground)");

            // Create keychain
            KeyChain keyChain = null;
            try {
                keyChain = buildTestKeyChain();
            } catch (SecurityException e) {
                m_retVal = "ERROR: " + e.getMessage();
                e.printStackTrace();
                return m_retVal;
            }

            // Register keychain with the face
            keyChain.setFace(m_face);
            try {
                m_face.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
            } catch (SecurityException e) {
                m_retVal = "ERROR: " + e.getMessage();
                e.printStackTrace();
                return m_retVal;
            }

            final String nameStr = prefix + "/" + whiteboard + "/" + username; // The user's prefix

            Name base_name = new Name(nameStr);
            try {
                // Register the prefix
                m_face.registerPrefix(base_name, new OnInterestCallback() {
                    @Override
                    public void onInterest(Name prefix,
                                           Interest interest,
                                           Face face,
                                           long interestFilterId,
                                           InterestFilter filter) {
                        Name interestName = interest.getName();
                        String lastComp = interestName.get(interestName.size() - 1).toEscapedString();
                        Log.i("NDN", "Interest received: " + lastComp);
                        int comp = Integer.parseInt(lastComp) - 1;

                        Data data = new Data();
                        data.setName(new Name(interestName));
                        Blob blob;
                        if (dataHist.size() > comp) {
                            blob = new Blob(dataHist.get(comp).getBytes());
                            data.setContent(blob);
                        } else {
                            return;
                        }
                        try {
                            face.putData(data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, new OnRegisterFailed() {
                    @Override
                    public void onRegisterFailed(Name prefix) {
                        Log.d(TAG, "Register Prefix Task: Registration failed");
                    }
                });
            } catch (IOException | SecurityException e) {
                m_retVal = "ERROR: " + e.getMessage();
                e.printStackTrace();
                return m_retVal;
            }
            return m_retVal;
        }

        @Override
        protected void onPostExecute(final String result) {
            if (m_retVal.contains("ERROR:")) {
                // If error, end the activity
                progressDialog.dismiss();
                new AlertDialog.Builder(WhiteboardActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Error received")
                        .setMessage(m_retVal)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .show();
            } else {
                // Start task to register with ChronoSync
                progressDialog.setMessage("Setting up ChronoSync");
                Log.d(TAG, "Register Prefix Task ended (onPostExecute)");
                Log.d(TAG, "About to trigger Register ChronoSync");
                new RegisterChronoSyncTask().execute();
            }
        }
    }

    /**
     * AsyncTask to perform registration for ChronoSync.
     * <p/>
     * ALSO: Starts the long running thread that keeps preocessing the events on the Face.
     */
    private class RegisterChronoSyncTask extends AsyncTask<Void, Void, Void> {
        int attempt = 1;  // Keep track on current attempt. Try for max 3 attempts.

        // Constructors
        public RegisterChronoSyncTask() {
            this(1);
        }

        public RegisterChronoSyncTask(int attempt) {
            this.attempt = attempt;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                Log.d(TAG, "ChronoSync Task (doInBackground): Attempt: " + attempt);
                KeyChain testKeyChain = buildTestKeyChain();
                sync = new ChronoSync2013(new ChronoSync2013.OnReceivedSyncState() {
                    @Override
                    public void onReceivedSyncState(List syncStates, boolean isRecovery) {
                        for (Object syncStateOb : syncStates) {
                            ChronoSync2013.SyncState syncState = (ChronoSync2013.SyncState) syncStateOb;
                            String syncPrefix = syncState.getDataPrefix();
                            long syncSeq = syncState.getSequenceNo();
                            // Ignore the initial sync state and sync updates of this user
                            if (syncSeq == 0 || syncPrefix.contains(username)) {
                                Log.d(TAG, "SYNC: prefix: " + syncPrefix + " seq: "
                                        + syncSeq + " ignored. (is Recovery: " + isRecovery + ")");
                                continue;
                            }
                            if (highestRequested.keySet().contains(syncPrefix)) {
                                long highestSeq = highestRequested.get(syncPrefix);

                                if (syncSeq == highestSeq + 1) {
                                    // New request
                                    highestRequested.put(syncPrefix, syncSeq);
                                } else if (syncSeq <= highestSeq) {
                                    // Duplicate request, ignore
                                    Log.d(TAG, "Avoiding starting new task for: "
                                            + syncPrefix + "/" + syncSeq);
                                    continue;
                                } else if (syncSeq - highestSeq > 1) {
                                    // Gaps found. Recover missing pieces
                                    Log.d(TAG, "Gaps in SYNC found. Sending Interest for missing pieces.");
                                    highestSeq++;
                                    while (highestSeq <= syncSeq) {
                                        new FetchChangesTask(syncPrefix + "/" + highestSeq).execute();
                                        highestSeq++;
                                    }
                                    highestRequested.put(syncPrefix, syncSeq);
                                }
                            } else {
                                highestRequested.put(syncPrefix, syncSeq);
                            }
                            String syncNameStr = syncPrefix + "/" + syncSeq;
                            Log.d(TAG, "SYNC: " + syncNameStr + " (is Recovery: " + isRecovery + ")");
                            new FetchChangesTask(syncNameStr).execute();
                        }

                    }
                }, new ChronoSync2013.OnInitialized() {
                    @Override
                    public void onInitialized() {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // Done registering ChronoSync
                                progressDialog.dismiss();
                            }
                        });
                        Log.d(TAG, "ChronoSync onInitialized");
                    }
                }, new Name(prefix + "/" + whiteboard + "/" + username), // App data prefix
                        new Name("/ndn/broadcast/whiteboard/" + whiteboard), // Broadcast prefix
                        0l,
                        m_face,
                        testKeyChain,
                        testKeyChain.getDefaultCertificateName(),
                        5000.0, new OnRegisterFailed() {
                    @Override
                    public void onRegisterFailed(Name prefix) {
                        // Handle failure of this register attempt. Try again.
                        Log.d(TAG, "ChronoSync registration failed, Attempt: " + attempt);
                        Log.d(TAG, "Starting next attempt");
                        new RegisterChronoSyncTask(attempt + 1).execute();
                    }
                }
                );
            } catch (IOException | SecurityException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            // Start the long running thread that keeps processing the events on the face every
            // few milliseconds
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!activity_stop) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                        try {
                            m_face.processEvents();
                        } catch (IOException | EncodingException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }
    }

    /**
     * AsyncTask to fetch new packets from other user's when ChronoSync tells that a new packet may
     * be available.
     */
    private class FetchChangesTask extends AsyncTask<Void, Void, Void> {
        String namePrefixStr;
        boolean m_shouldStop = false;

        // Constructors
        public FetchChangesTask(String namePrefixStr) {
            this.namePrefixStr = namePrefixStr;
        }

        String m_retVal;

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "Fetch Task (doInBackground) for prefix: " + namePrefixStr);
            String nameStr = namePrefixStr;

            try {
                m_face.expressInterest(new Name(nameStr), new OnData() {
                    @Override
                    public void
                    onData(Interest interest, Data data) {
                        // Success, send data to be drawn by the Drawing view
                        m_retVal = data.getContent().toString();
                        m_shouldStop = true;
                        Log.d(TAG, "Got content: " + m_retVal);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                drawingView_canvas.callback(m_retVal);
                            }
                        });

                    }
                }, new OnTimeout() {
                    @Override
                    public void onTimeout(Interest interest) {
                        // Failure, try again
                        m_retVal = null;
                        m_shouldStop = true;
                        Log.d(TAG, "Got Timeout " + namePrefixStr);
                        if (!activity_stop) {
                            new FetchChangesTask(namePrefixStr).execute();
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void data) {
            Log.d(TAG, "Fetch Task (onPostExecute)");
        }
    }

    /**
     * Setup an in-memory KeyChain with a default identity.
     *
     * @return keyChain object
     * @throws net.named_data.jndn.security.SecurityException
     */
    public static KeyChain buildTestKeyChain() throws SecurityException {
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
