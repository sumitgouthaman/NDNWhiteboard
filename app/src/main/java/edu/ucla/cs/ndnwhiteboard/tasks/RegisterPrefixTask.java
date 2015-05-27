package edu.ucla.cs.ndnwhiteboard.tasks;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.util.Blob;

import java.io.IOException;

import edu.ucla.cs.ndnwhiteboard.helpers.Utils;
import edu.ucla.cs.ndnwhiteboard.interfaces.NDNChronoSyncActivity;

/**
 * AsyncTask to perform a registeration of this user's prefix.
 * <p/>
 * ALSO: Starts the task to register for ChronoSync.
 */
public class RegisterPrefixTask extends AsyncTask<Void, Void, String> {
    private String m_retVal = "not changed";
    private NDNChronoSyncActivity ndnActivity;

    private String TAG = RegisterPrefixTask.class.getSimpleName();  // TAG for logging

    public RegisterPrefixTask(NDNChronoSyncActivity ndnActivity) {
        this.ndnActivity = ndnActivity;
    }

    @Override
    protected String doInBackground(Void... params) {
        Log.d(TAG, "Register Prefix Task (doInBackground)");

        // Create keychain
        KeyChain keyChain;
        try {
            keyChain = Utils.buildTestKeyChain();
        } catch (net.named_data.jndn.security.SecurityException e) {
            m_retVal = "ERROR: " + e.getMessage();
            e.printStackTrace();
            return m_retVal;
        }

        // Register keychain with the face
        keyChain.setFace(ndnActivity.m_face);
        try {
            ndnActivity.m_face.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
        } catch (SecurityException e) {
            m_retVal = "ERROR: " + e.getMessage();
            e.printStackTrace();
            return m_retVal;
        }

        Name base_name = new Name(ndnActivity.applicationNamePrefix);
        try {
            // Register the prefix
            ndnActivity.m_face.registerPrefix(base_name, new OnInterestCallback() {
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
                    if (ndnActivity.dataHistory.size() > comp) {
                        blob = new Blob(ndnActivity.dataHistory.get(comp).getBytes());
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
            // Start task to register with ChronoSync
            ndnActivity.getProgressDialog().setMessage("Setting up ChronoSync");
            Log.d(TAG, "Register Prefix Task ended (onPostExecute)");
            Log.d(TAG, "About to trigger Register ChronoSync");
            new RegisterChronoSyncTask(ndnActivity).execute();
        }
    }
}