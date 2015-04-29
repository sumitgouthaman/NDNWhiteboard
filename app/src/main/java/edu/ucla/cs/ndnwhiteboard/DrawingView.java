package edu.ucla.cs.ndnwhiteboard;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * TODO: document your custom view class.
 */
public class DrawingView extends View {

    private boolean isEraser = false;
    private int currentColor = 0;

    //drawing path
    private Path drawPath;
    //drawing and canvas paint
    private Paint drawPaint, canvasPaint;
    //initial color
    private int defaultPaintColor = Color.BLACK;
    private int eraserPaintColor = Color.WHITE;
    private int[] colors = {Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
    //canvas
    private Canvas drawCanvas;
    //canvas bitmap
    private Bitmap canvasBitmap;

    private JSONObject jsonObject = new JSONObject();
    private ArrayList<PointF> points = new ArrayList<>();
    private WhiteboardActivity activity;

    public void setActivity(WhiteboardActivity activity) {
        this.activity = activity;
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupDrawing();
    }

    private void setupDrawing() {
        drawPath = new Path();
        drawPaint = new Paint();
        drawPaint.setColor(defaultPaintColor);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(5);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
        canvasPaint = new Paint(Paint.DITHER_FLAG);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(canvasBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        canvas.drawPath(drawPath, drawPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float touchX = event.getX();
        float touchY = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                drawPath.moveTo(touchX, touchY);
                try {
                    jsonObject = new JSONObject();
                    jsonObject.put("type", (isEraser) ? "eraser" : "pen");
                    if (!isEraser) {
                        jsonObject.put("color", currentColor);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                points.add(new PointF(touchX, touchY));
                break;
            case MotionEvent.ACTION_MOVE:
                drawPath.lineTo(touchX, touchY);
                points.add(new PointF(touchX, touchY));
                break;
            case MotionEvent.ACTION_UP:
                drawCanvas.drawPath(drawPath, drawPaint);
                drawPath.reset();
                try {
                    JSONArray coordinates = new JSONArray();
                    for (PointF p : points) {
                        JSONArray ja = new JSONArray();
                        ja.put((int) p.x);
                        ja.put((int) p.y);
                        coordinates.put(ja);
                    }
                    jsonObject.put("coordinates", coordinates);
                    points.clear();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.i("DrawingView", String.valueOf(jsonObject));
                activity.callback();
                break;
            default:
                return false;
        }
        invalidate();
        return true;
    }

    public void setEraser() {
        drawPaint.setColor(eraserPaintColor);
        drawPaint.setStrokeWidth(40);
        isEraser = true;
    }

    public void setPencil() {
        drawPaint.setColor(colors[currentColor]);
        drawPaint.setStrokeWidth(5);
        isEraser = false;
    }

    public void setColor(int c) {
        currentColor = c;
        drawPaint.setColor(colors[currentColor]);
        drawPaint.setStrokeWidth(5);
        isEraser = false;
    }

    public void clear() {
        drawCanvas.drawColor(eraserPaintColor);
        invalidate();
    }

}
