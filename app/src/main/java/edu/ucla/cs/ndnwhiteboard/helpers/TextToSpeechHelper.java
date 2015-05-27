package edu.ucla.cs.ndnwhiteboard.helpers;

import android.app.Activity;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;

/**
 * Helper class to make it easy to use TextToSpeech
 */
public class TextToSpeechHelper implements TextToSpeech.OnInitListener {
    private Activity associatedActivity;  // The activity where TTS is used
    private TextToSpeech tts;              // Text-To-Speech feature
    private boolean ttsSuccessful = true;  // Was TTS initialization successful

    private String TAG = TextToSpeechHelper.class.getSimpleName();  // TAG for logging

    public TextToSpeechHelper(Activity activity) {
        associatedActivity = activity;
        tts = new TextToSpeech(associatedActivity, this);
    }

    /**
     * Handle initialization of Text-To-Speech
     *
     * @param status the initialization status of the TTS service
     */
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "This Language is not supported");
                ttsSuccessful = false;
            }
        } else {
            Log.e(TAG, "Initilization Failed!");
            ttsSuccessful = false;
        }
    }

    /**
     * Speak out the message
     *
     * @param ttsStr the String to be spoken out
     */
    public void speakOut(String ttsStr) {
        if (!ttsSuccessful) {
            // If Text-To-Speech is not available for some reason
            Toast.makeText(associatedActivity, ttsStr, Toast.LENGTH_LONG).show();
        } else {
            //noinspection deprecation
            tts.speak(ttsStr, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    /**
     * Function to stop TTS service when done
     */
    public void stopTTS() {
        // Shutdown tts!
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
