package edu.ucla.cs.ndnwhiteboard;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;


public class WhiteboardActivity extends ActionBarActivity {

    private DrawingView drawingView_canvas;
    private ImageButton button_eraser;
    private ImageButton button_pencil;
    private ImageButton button_save;
    private ImageButton button_clear;
    private ImageButton button_color1, button_color2, button_color3, button_color4, button_color5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whiteboard);
        drawingView_canvas = (DrawingView) findViewById(R.id.drawingview_canvas);
        button_eraser = (ImageButton) findViewById(R.id.button_eraser);
        button_pencil = (ImageButton) findViewById(R.id.button_pencil);
        button_save = (ImageButton) findViewById(R.id.button_save);
        button_clear = (ImageButton) findViewById(R.id.button_clear);
        button_color1 = (ImageButton) findViewById(R.id.button_color1);
        button_color2 = (ImageButton) findViewById(R.id.button_color2);
        button_color3 = (ImageButton) findViewById(R.id.button_color3);
        button_color4 = (ImageButton) findViewById(R.id.button_color4);
//        button_color5 = (ImageButton) findViewById(R.id.button_color5);

        drawingView_canvas.setActivity(this);

        button_eraser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.setEraser();
            }
        });

        button_pencil.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.setPencil();
            }
        });

        button_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                        if (f != null) {
                           f.write(baos.toByteArray());
                           f.flush();
                           f.close();
                           Toast.makeText(getApplicationContext(), "Saved", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                       e.printStackTrace();
                       Toast.makeText(getApplicationContext(), "Save Failed!", Toast.LENGTH_SHORT).show();
                    }
                }

                drawingView_canvas.destroyDrawingCache();
            }
        });

        button_color1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.setColor(0);
            }
        });
        button_color2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.setColor(1);
            }
        });
        button_color3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.setColor(2);
            }
        });
        button_color4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.setColor(3);
            }
        });
//        button_color5.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                drawingView_canvas.setColor(4);
//            }
//        });

        button_clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView_canvas.clear();
            }
        });

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

    public void callback() {
        Log.i("WhiteboardActivity", "callback");
    }
}
