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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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


public class WhiteboardActivity extends ActionBarActivity {

    private DrawingView drawingView_canvas;
    private ImageButton button_pencil;
    private ImageButton button_eraser;
    private ImageButton button_color;
    private ImageButton button_save;
    private ImageButton button_undo;
    private ImageButton button_clear;
    public String username;
    private String whiteboard;
    private String prefix;

    boolean activity_stop = false;
    ArrayList<String> dataHist = new ArrayList<String>();
    private Face m_face;
    private ChronoSync2013 sync;

    Handler mHandler = new Handler();

    Map<String, Long> highestRequested = new HashMap<String, Long>();

    private String TAG = WhiteboardActivity.class.getSimpleName();

    ProgressDialog progressDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whiteboard);

        Intent introIntent = getIntent();
        this.username = introIntent.getExtras().getString("name");
        this.whiteboard = introIntent.getExtras().getString("whiteboard").replaceAll("\\s", "");
        this.prefix = introIntent.getExtras().getString("prefix");
        Log.d(TAG, "username: " + this.username);
        Log.d(TAG, "whiteboard: " + this.whiteboard);
        Log.d(TAG, "prefix: " + this.prefix);
        Toast.makeText(getApplicationContext(), "Welcome " + this.username, Toast.LENGTH_SHORT).show();

        drawingView_canvas = (DrawingView) findViewById(R.id.drawingview_canvas);
        button_pencil = (ImageButton) findViewById(R.id.button_pencil);
        button_eraser = (ImageButton) findViewById(R.id.button_eraser);
        button_color = (ImageButton) findViewById(R.id.button_color);
        button_save = (ImageButton) findViewById(R.id.button_save);
        button_undo = (ImageButton) findViewById(R.id.button_undo);
        button_clear = (ImageButton) findViewById(R.id.button_clear);
        drawingView_canvas.setActivity(this);

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

        activity_stop = false;
        new PingTask().execute();

        progressDialog = ProgressDialog.show(this, "Initializing", "Performing Ping", true);

        Log.d(TAG, "Finished On Create");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activity_stop = true;
        if (m_face != null) {
            m_face.shutdown();
            Log.d(TAG, "Shutting down Face");
        }
        Log.d(TAG, "Finished On Destroy");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_whiteboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void callback(String jsonData) {
        //TODO: implement callback
        dataHist.add(jsonData);
        int seq = dataHist.size() - 1;
        String dataName = prefix + "/" + whiteboard + "/" + username + "/" + seq;
        final Data data = new Data();
        data.setName(new Name(dataName));
        Blob blob = null;
        blob = new Blob(dataHist.get(seq).getBytes());
        Log.d(TAG, "About to send data, Size: " + dataHist.get(seq).length());
        data.setContent(blob);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sync != null) {
                        while (sync.getSequenceNo() < dataHist.size() && sync.getSequenceNo() != -1) {
                            Log.d(TAG, "Seq is now: " + sync.getSequenceNo());
                            sync.publishNextSequenceNo();
                            Log.d(TAG, "Plubished next seq number. Seq is now: " + sync.getSequenceNo());
                        }
                    }
                } catch (IOException | SecurityException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        Log.d(TAG, "Stroke generated: " + jsonData);
    }

    public void setButtonColor(int color) {
        button_color.setBackgroundColor(color);
    }

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
                        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
                            File sdCard = Environment.getExternalStorageDirectory();
                            File dir = new File(sdCard.getAbsolutePath() + "/NDN_Whiteboard");
                            dir.mkdirs();
                            File file = new File(dir, fileName);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            drawingView_canvas.getDrawingCache().compress(Bitmap.CompressFormat.PNG, 100, baos);
                            FileOutputStream f = null;
                            try {
                                f = new FileOutputStream(file);
                                f.write(baos.toByteArray());
                                f.flush();
                                f.close();
                                Toast.makeText(getApplicationContext(), "Saved", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(), "Save Failed!", Toast.LENGTH_SHORT).show();
                            }
                        }
                        drawingView_canvas.destroyDrawingCache();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private class RegisterPrefixTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "Register Prefix Task (doInBackground)");
            KeyChain keyChain = null;
            try {
                keyChain = buildTestKeyChain();
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            keyChain.setFace(m_face);
            try {
                m_face.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            final String nameStr = prefix + "/" + whiteboard + "/" + username;
            Name base_name = new Name(nameStr);
            try {
                m_face.registerPrefix(base_name, new OnInterestCallback() {
                    @Override
                    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
                        Name interestName = interest.getName();
                        String lastComp = interestName.get(interestName.size() - 1).toEscapedString();
                        Log.i("NDN", "Interest received: " + lastComp);
                        int comp = Integer.parseInt(lastComp) - 1;

                        Data data = new Data();
                        data.setName(new Name(interestName));
                        Blob blob = null;
                        if (dataHist.size() > comp) {
                            blob = new Blob(dataHist.get(comp).getBytes());
                        } else {
                            return;
                        }
                        data.setContent(blob);
                        try {
                            face.putData(data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, new OnRegisterFailed() {
                    @Override
                    public void onRegisterFailed(Name prefix) {
                        Log.d(TAG, "Register Prefix Task: Registeration Failed");
                    }
                });
            } catch (IOException | SecurityException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressDialog.setMessage("Setting up ChronoSync");
            Log.d(TAG, "Register Prefix Task ended (onPostEvecute)");
            Log.d(TAG, "About to trigger Register ChronoSync");
            new RegisterChronoSyncTask().execute();
        }
    }

    private class FetchChangesTask extends AsyncTask<Void, Void, Void> {
        String namePrefixStr;
        boolean m_shouldStop = false;

        public FetchChangesTask(String namePrefixStr) {
            this.namePrefixStr = namePrefixStr;
        }

        String m_retVal;

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "Fetch Task (doInBackground) for prefix: " + namePrefixStr);
            String nameStr = namePrefixStr;

            try {
                m_face.expressInterest(new Name(nameStr),
                        new OnData() {
                            @Override
                            public void
                            onData(Interest interest, Data data) {
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
                        },
                        new OnTimeout() {
                            @Override
                            public void onTimeout(Interest interest) {
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

    private class PingTask extends AsyncTask<Void, Void, String> {
        private String m_retVal = "not changed";
        private boolean m_shouldStop = false;

        @Override
        protected String doInBackground(Void... voids) {
            Log.d(TAG, "Ping Task (doInBackground)");
            try {

                m_face = new Face("localhost");
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

                while (!m_shouldStop && !activity_stop) {
                    m_face.processEvents();
                    //Log.i("Main", "loop");
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
                progressDialog.setMessage("Registering Prefix");
                Log.d(TAG, "Ping Task Suceeded: " + m_retVal);
                Log.d(TAG, "ABout to trigger Registering prefix task");
                new RegisterPrefixTask().execute();
            }
        }

    }

    private class RegisterChronoSyncTask extends AsyncTask<Void, Void, Void> {
        int attempt = 1;

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
                sync = new ChronoSync2013(
                        new ChronoSync2013.OnReceivedSyncState() {
                            @Override
                            public void onReceivedSyncState(List syncStates, boolean isRecovery) {
                                for (Object syncStateOb : syncStates) {
                                    ChronoSync2013.SyncState syncState = (ChronoSync2013.SyncState) syncStateOb;
                                    String syncPrefix = syncState.getDataPrefix();
                                    long syncSeq = syncState.getSequenceNo();
                                    if (syncSeq == 0 || syncPrefix.contains(username)) {
                                        Log.d(TAG, "SYNC: prefix: " + syncPrefix + " seq: " + syncSeq + " ignored. (is Recovery: " + isRecovery + ")");
                                        continue;
                                    }
                                    if (highestRequested.keySet().contains(syncPrefix)) {
                                        long highestSeq = highestRequested.get(syncPrefix);
                                        if (syncSeq == highestSeq + 1) {
                                            highestRequested.put(syncPrefix, syncSeq);
                                        } else if (syncSeq <= highestSeq) {
                                            Log.d(TAG, "Avoiding starting new task for: " + syncPrefix + "/" + syncSeq);
                                            continue;
                                        } else if (syncSeq - highestSeq > 1) {
                                            Log.d(TAG, "Gaps in SYNC found. Sending Interest for missing pieces.");
                                            highestSeq++;
                                            while(highestSeq <= syncSeq) {
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
                        },
                        new ChronoSync2013.OnInitialized() {
                            @Override
                            public void onInitialized() {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressDialog.dismiss();
                                    }
                                });
                                Log.d(TAG, "ChronoSync On Initialized");
                            }
                        },
                        new Name(prefix + "/" + whiteboard + "/" + username), // App data prefix
                        new Name("/ndn/broadcast/whiteboard/" + whiteboard), // Broadcast prefix
                        0l,
                        m_face,
                        testKeyChain,
                        testKeyChain.getDefaultCertificateName(),
                        5000.0,
                        new OnRegisterFailed() {
                            @Override
                            public void onRegisterFailed(Name prefix) {
                                Log.d(TAG, "Chronosync registeration failed, Attempt: " + attempt);
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
     * Setup an in-memory KeyChain with a default identity.
     *
     * @return
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
