package edu.ucla.cs.ndnwhiteboard.tasks;

import android.os.AsyncTask;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnTimeout;

import java.io.IOException;

import edu.ucla.cs.ndnwhiteboard.interfaces.NDNActivity;

/**
 * AsyncTask to fetch new packets from other user's when ChronoSync tells that a new packet may
 * be available.
 */
public class FetchChangesTask extends AsyncTask<Void, Void, Void> {
    String namePrefixStr;
    boolean m_shouldStop = false;
    private NDNActivity ndnActivity;

    private String TAG = FetchChangesTask.class.getSimpleName();  // TAG for logging

    // Constructors
    public FetchChangesTask(NDNActivity ndnActivity, String namePrefixStr) {
        this.ndnActivity = ndnActivity;
        this.namePrefixStr = namePrefixStr;
    }

    String m_retVal;

    @Override
    protected Void doInBackground(Void... params) {
        Log.d(TAG, "Fetch Task (doInBackground) for prefix: " + namePrefixStr);
        String nameStr = namePrefixStr;

        try {
            ndnActivity.m_face.expressInterest(new Name(nameStr), new OnData() {
                @Override
                public void
                onData(Interest interest, Data data) {
                    // Success, send data to be drawn by the Drawing view
                    m_retVal = data.getContent().toString();
                    m_shouldStop = true;
                    Log.d(TAG, "Got content: " + m_retVal);
                    ndnActivity.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            ndnActivity.handleDataReceived(m_retVal);
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
                    if (!ndnActivity.activity_stop) {
                        new FetchChangesTask(ndnActivity, namePrefixStr).execute();
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