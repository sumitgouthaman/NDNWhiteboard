package edu.ucla.cs.ndnwhiteboard;

import android.content.Intent;
import android.graphics.Color;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;


public class IntroActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_intro, menu);
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

    public void onStart(View view) {
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
        Intent WhiteboardActivityIntent = new Intent(this, WhiteboardActivity.class);
        WhiteboardActivityIntent
                .putExtra("name", ((EditText) findViewById(R.id.userName)).getText().toString())
                .putExtra("whiteboard", ((EditText) findViewById(R.id.whiteboardName)).getText().toString())
                .putExtra("prefix", ((EditText) findViewById(R.id.prefixName)).getText().toString());
        startActivity(WhiteboardActivityIntent);
    }
}
