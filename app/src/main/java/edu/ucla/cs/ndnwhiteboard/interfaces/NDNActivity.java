package edu.ucla.cs.ndnwhiteboard.interfaces;

import android.app.ProgressDialog;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import net.named_data.jndn.Face;

import java.util.ArrayList;

/**
 * Interface for Activity tht uses NDN
 */
public abstract class NDNActivity extends ActionBarActivity {
    public Face m_face;

    public abstract Handler getHandler();

    public abstract ProgressDialog getProgressDialog();

    public boolean activity_stop;

    public String applicationNamePrefix;

    public ArrayList<String> dataHistory;

    public abstract void handleDataReceived(String data);

    // Set the boolean flag that stops all long running loops
    public void stop() {
        activity_stop  = true;

        // Shut down face if it is not null
        if (m_face != null) {
            m_face.shutdown();
            Log.d(NDNActivity.class.getSimpleName(), "Shutting down Face");
        }
    }
}
