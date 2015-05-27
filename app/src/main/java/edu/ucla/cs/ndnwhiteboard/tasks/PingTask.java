package edu.ucla.cs.ndnwhiteboard.tasks;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnTimeout;

import edu.ucla.cs.ndnwhiteboard.interfaces.NDNChronoSyncActivity;

/**
 * AsyncTask that performs the ping sequence.
 * <p/>
 * It is necessary to perform a Ping before doing a register prefix. Or else, the register
 * prefix request will be ignored by the hub.
 * <p/>
 * ALSO: Initiates task to register application prefix once Ping is successful
 */
public class PingTask extends AsyncTask<Void, Void, String> {
    private String m_retVal = "not changed";
    private boolean m_shouldStop = false;
    private NDNChronoSyncActivity ndnActivity;

    private String TAG = PingTask.class.getSimpleName();  // TAG for logging

    public PingTask(NDNChronoSyncActivity ndnActivity) {
        this.ndnActivity = ndnActivity;
    }

    @Override
    protected String doInBackground(Void... voids) {
        Log.d(TAG, "Ping Task (doInBackground)");
        try {
            // Initialize the Face
            ndnActivity.m_face = new Face("localhost");

            // Express the ping Interest
            ndnActivity.m_face.expressInterest(new Name("/ndn/edu/ucla/remap/ping"),
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
            while (!m_shouldStop && !ndnActivity.activity_stop) {
                ndnActivity.m_face.processEvents();
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
            ndnActivity.getProgressDialog().dismiss();
            new AlertDialog.Builder(ndnActivity)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Error received")
                    .setMessage(m_retVal)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ndnActivity.finish();
                        }
                    })
                    .show();
        } else {
            // Successful ping, trigger register prefix task
            ndnActivity.getProgressDialog().setMessage("Registering prefix");
            Log.d(TAG, "Ping Task succeeded: " + m_retVal);
            Log.d(TAG, "About to trigger Register Prefix Task");
            new RegisterPrefixTask(ndnActivity).execute();
        }
    }

}