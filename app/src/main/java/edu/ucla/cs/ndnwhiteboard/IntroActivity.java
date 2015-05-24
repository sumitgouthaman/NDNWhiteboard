package edu.ucla.cs.ndnwhiteboard;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * IntroActivity: The first activity to appear when the app starts.
 *
 * The activity displays the generated username and whiteboard name along with the prefix to be
 * used by the application.
 */
public class IntroActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        // Display the generated whiteboard name
        ((EditText) findViewById(R.id.whiteboardName)).setText(Utils.genWhiteboardName());

        // Display the randomly generated username
        ((EditText) findViewById(R.id.userName)).setText(Utils.generateRandomName());

        Button startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start the whiteboard
                onStartClicked();
            }
        });
    }

    /**
     * Function to start the Whiteboard activity
     */
    public void onStartClicked() {
        // Check for errors
        boolean error = false;
        if (((EditText) findViewById(R.id.userName)).getText().toString().trim().length() == 0) {
            ((EditText) findViewById(R.id.userName)).setHintTextColor(Color.RED);
            error = true;
        }
        if (((EditText) findViewById(R.id.whiteboardName)).getText().toString().trim().length() == 0) {
            ((EditText) findViewById(R.id.whiteboardName)).setHintTextColor(Color.RED);
            error = true;
        }
        if (error) {
            return;
        }

        // Create whiteboard activity intent
        Intent WhiteboardActivityIntent = new Intent(this, WhiteboardActivity.class);
        // Send necessary parameters
        WhiteboardActivityIntent
                .putExtra("name", ((EditText) findViewById(R.id.userName)).getText().toString())
                .putExtra("whiteboard", ((EditText) findViewById(R.id.whiteboardName)).getText().toString())
                .putExtra("prefix", ((EditText) findViewById(R.id.prefixName)).getText().toString());
        // Start whiteboard activity
        startActivity(WhiteboardActivityIntent);
    }
}
