package edu.ucla.cs.ndnwhiteboard.custom_views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;

import edu.ucla.cs.ndnwhiteboard.R;
import edu.ucla.cs.ndnwhiteboard.WhiteboardActivity;

/**
 * DrawingView: Custom view that handles the drawing for the whiteboard
 */
public class DrawingView extends View {
    private boolean isEraser = false;  // Is the eraser button enabled
    private int currentColor = 0;      // Current pen color
    private int viewWidth = 0;         // Width of the screen in pixels

    // Objects to handle painting
    private Canvas drawCanvas;
    private Path drawPath = new Path();
    private Paint drawPaint = new Paint();
    private Paint canvasPaint = new Paint(Paint.DITHER_FLAG);
    private Bitmap canvasBitmap;

    // Colors for the pen
    private int[] colors = {
            Color.BLACK,
            Color.RED,
            Color.BLUE,
            0xFF458B00,  // Dark green
            0xFFED9121}; // Orange
    int num_colors = colors.length;

    // JSON Objects
    private JSONObject jsonObject = new JSONObject();
    private ArrayList<String> history = new ArrayList<>();
    private ArrayList<PointF> points = new ArrayList<>();

    // Reference to the associated WhiteboardActivity
    private WhiteboardActivity whiteboardActivity;

    private String TAG = DrawingView.class.getSimpleName(); // TAG for logging

    /**
     * Contructor
     *
     * @param context the current Activity context
     * @param attrs   attribute set
     */
    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupDrawing();
    }

    /**
     * Initialize objects necessary for drawing
     */
    private void setupDrawing() {
        int dp = 3; // Initial pen width

        // Make pixel width density independent
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float pixelWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm);

        // Set initial parameters
        drawPaint.setColor(Color.BLACK);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(pixelWidth);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Overriding the onSizeChanged method of the View class
     *
     * @param w    new width
     * @param h    new height
     * @param oldw old width
     * @param oldh old height
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Store view width for later
        viewWidth = w;

        // Make sure the height is 6/5 times the width
        this.getLayoutParams().height = (int) (6 / 5f * viewWidth);

        // Create bitmap for the drawing
        canvasBitmap = Bitmap.createBitmap(w, (int) (6 / 5f * w), Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(canvasBitmap);
    }

    /**
     * Overrriding the onDraw method of the View class
     *
     * @param canvas the canvas to draw the view
     */
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        canvas.drawPath(drawPath, drawPaint);
    }

    /**
     * Create link back to the WhiteboardActivity
     *
     * @param whiteboardActivity reference to the associated WhiteboardActivity
     */
    public void setWhiteboardActivity(WhiteboardActivity whiteboardActivity) {
        this.whiteboardActivity = whiteboardActivity;
    }

    /**
     * Overriding onTouchEvent method of the View class
     *
     * @param event the current touch event
     * @return true because event was handled
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Sending coordinates only to 3 decimal values
        DecimalFormat df = new DecimalFormat("#.###");

        // Get the coordinates of the touch
        float touchX = event.getX();
        float touchY = event.getY();

        // Handle the type of event
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // First point in the stroke being drawn

                // Position start of curve to position
                drawPath.moveTo(touchX, touchY);

                // Create the JSON object to be sent and fill relevant fields
                try {
                    jsonObject = new JSONObject();
                    jsonObject.put("user", whiteboardActivity.username);
                    jsonObject.put("type", (isEraser) ? "eraser" : "pen");
                    if (!isEraser) {
                        jsonObject.put("color", currentColor);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                // Add point to the arraylist of points in this curve
                points.add(new PointF(touchX, touchY));
                break;

            case MotionEvent.ACTION_MOVE:
                // Handle point somewhere in middle of the stroke
                drawPath.lineTo(touchX, touchY);

                // Add point to the arraylist of points in current curve
                points.add(new PointF(touchX, touchY));

                // maxCoordSize determines if the number of points in the current curve being drawn
                // is too much to send in one NDN packet.
                // If so, chop up the data and send what is already accumulated.
                int maxCoordSize = (8000 - whiteboardActivity.username.length()) / 18;
                if (points.size() == maxCoordSize) {
                    // Handle number of points exceeding capacity of one NDN packet
                    drawCanvas.drawPath(drawPath, drawPaint);
                    drawPath.reset();
                    drawPath.moveTo(touchX, touchY);
                    try {
                        JSONArray coordinates = new JSONArray();
                        for (PointF p : points) {
                            JSONArray ja = new JSONArray();
                            ja.put(df.format(p.x / viewWidth));
                            ja.put(df.format(p.y / viewWidth));
                            coordinates.put(ja);
                        }
                        jsonObject.put("coordinates", coordinates);
                        points.clear();
                        points.add(new PointF(touchX, touchY));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    String jsonString = jsonObject.toString();
                    history.add(jsonString);
                    whiteboardActivity.callback(jsonString);
                }
                break;

            case MotionEvent.ACTION_UP:
                // Handle last point in the current stroke
                drawCanvas.drawPath(drawPath, drawPaint);
                drawPath.reset();
                try {
                    JSONArray coordinates = new JSONArray();
                    for (PointF p : points) {
                        JSONArray ja = new JSONArray();
                        ja.put(df.format(p.x / viewWidth));
                        ja.put(df.format(p.y / viewWidth));
                        coordinates.put(ja);
                    }
                    jsonObject.put("coordinates", coordinates);
                    points.clear();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                String jsonString = jsonObject.toString();
                history.add(jsonString);
                whiteboardActivity.callback(jsonString);
                break;

            default:
                return false;
        }
        invalidate(); // Trigger a redraw of the view
        return true;  // Because event has been handled
    }

    /**
     * Enable pencil button
     */
    public void setPencil() {
        setColor(currentColor);
    }

    // Enable eraser button
    public void setEraser() {
        int dp = 20; // Default size of eraser

        // Make eraser width density independent
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float pixelWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm);

        // Set values to enable erasing
        whiteboardActivity.setButtonColor(Color.WHITE);
        drawPaint.setColor(Color.WHITE);
        drawPaint.setStrokeWidth(pixelWidth);
        isEraser = true;
    }

    // Change color of the pen
    private void setColor(int c) {
        int dp = 3; // Default width of the pen

        // Make width density independent
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float pixelWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm);

        currentColor = c;
        whiteboardActivity.setButtonColor(colors[currentColor]);
        drawPaint.setColor(colors[currentColor]);
        drawPaint.setStrokeWidth(pixelWidth);
        isEraser = false;
    }

    /**
     * Increment color of the pen
     */
    public void incrementColor() {
        if (!isEraser && ++currentColor > num_colors - 1) {
            currentColor = 0;
        }
        setColor(currentColor);
    }

    /**
     * Function to handle undo
     */
    public void undo() {
        // If history is empty, ignore
        if (history.isEmpty()) {
            Toast.makeText(whiteboardActivity, "No more history", Toast.LENGTH_SHORT).show();
            return;
        }

        // If history is not empty, go through it and delete last action by this user
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).contains("\"user\":\"" + whiteboardActivity.username + "\"")) {
                history.remove(i);
                try {
                    jsonObject = new JSONObject();
                    jsonObject.put("user", whiteboardActivity.username);
                    jsonObject.put("type", "undo");
                    whiteboardActivity.callback(jsonObject.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;
            }
        }

        // Erase canvas
        drawCanvas.drawColor(Color.WHITE);

        invalidate(); // Trigger redraw of the view

        for (String string : history) {
            parseJSON(string, false); //Reperform all the actions in history
        }
    }

    /**
     * Function to clear the whiteboard
     */
    public void clear() {
        history.clear();
        drawCanvas.drawColor(Color.WHITE);
        invalidate();
        try {
            jsonObject = new JSONObject();
            jsonObject.put("user", whiteboardActivity.username);
            jsonObject.put("type", "clear");
            whiteboardActivity.callback(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper Function to parse and draw the action mentioned in the passed JSON string
     *
     * @param string the json representation of the action to be performed
     */
    public void callback(String string) {
        parseJSON(string, true);
    }

    /**
     * Function to parse and draw the action mentioned in the passed JSON string
     *
     * @param string       the json representation of the action to be performed
     * @param addToHistory if true, add the action to history
     */
    public void parseJSON(String string, boolean addToHistory) {
        try {
            JSONObject jsonObject = new JSONObject(string);
            try {
                int colorBefore = currentColor;
                boolean isEraserBefore = isEraser;
                String type = jsonObject.get("type").toString();

                switch (type) {
                    case "pen":
                        // If type pen, extract color
                        int color = jsonObject.getInt("color");
                        setColor(color);
                        break;
                    case "eraser":
                        setEraser();
                        break;
                    case "undo":
                        if (history.isEmpty()) {
                            return;
                        }
                        String userStr = jsonObject.getString("user");
                        for (int i = history.size() - 1; i >= 0; i--) {
                            if (history.get(i).contains("\"user\":\"" + userStr + "\"")) {
                                history.remove(i);
                                break;
                            }
                        }
                        drawCanvas.drawColor(Color.WHITE);
                        invalidate();
                        for (String str : history) {
                            parseJSON(str, false);
                        }
                        break;
                    case "clear":
                        history.clear();
                        drawCanvas.drawColor(Color.WHITE);
                        invalidate();
                        break;
                    case "text": {
                        // Create a toast of the message text
                        String message = jsonObject.getString("data");
                        LayoutInflater inflater = (LayoutInflater) whiteboardActivity.getSystemService(
                                Context.LAYOUT_INFLATER_SERVICE);
                        View layout = inflater.inflate(R.layout.activity_text,
                                (ViewGroup) findViewById(R.id.toast_layout_root));

                        TextView text = (TextView) layout.findViewById(R.id.text_message);
                        text.setText(message);

                        String username = jsonObject.getString("user");
                        TextView text_username = (TextView) layout.findViewById(R.id.text_username);
                        text_username.setText(username + ": ");

                        Toast toast = new Toast(whiteboardActivity);
                        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        toast.setDuration(Toast.LENGTH_LONG);
                        toast.setView(layout);
                        toast.show();

                        break;
                    }
                    case "speech": {
                        String message = jsonObject.getString("data");
                        String username = jsonObject.getString("user");
                        String ttsStr = username + " says, " + message;
                        whiteboardActivity.speakOut(ttsStr);
                        break;
                    }
                    default:
                        throw new JSONException("Unrecognized string: " + string);
                }

                if (type.equals("pen") || type.equals("eraser")) {
                    JSONArray coordinates = jsonObject.getJSONArray("coordinates");
                    JSONArray startPoint = coordinates.getJSONArray(0);
                    Path drawPath = new Path();
                    float touchX = (float) startPoint.getDouble(0) * viewWidth;
                    float touchY = (float) startPoint.getDouble(1) * viewWidth;
                    drawPath.moveTo(touchX, touchY);
                    for (int i = 1; i < coordinates.length(); i++) {
                        JSONArray point = coordinates.getJSONArray(i);
                        float x = (float) point.getDouble(0) * viewWidth;
                        float y = (float) point.getDouble(1) * viewWidth;
                        drawPath.lineTo(x, y);
                    }
                    drawCanvas.drawPath(drawPath, drawPaint);
                    invalidate();
                    drawPath.reset();
                    if (addToHistory) {
                        history.add(string);
                    }
                    if (isEraserBefore) {
                        setEraser();
                    } else {
                        setColor(colorBefore);
                    }
                }
            } catch (JSONException e) {
                Log.d(TAG, "JSON string error: " + string);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
