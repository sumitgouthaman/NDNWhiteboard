package edu.ucla.cs.ndnwhiteboard.interfaces;

import android.app.ProgressDialog;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;

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
}
