package com.example.bitsmap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.renderscript.Matrix3f;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.core.view.ScaleGestureDetectorCompat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MapView extends View implements RotationGestureDetector.OnRotationGestureListener {

    //These two constants specify the minimum and maximum zoom
    private static float MIN_ZOOM = 1f;
    private static float MAX_ZOOM = 5f;

    private float scaleFactor = 20.f;
    private ScaleGestureDetector scaleGestureDetector;

    private static int NONE = 0;
    private static int DRAG = 1;
    private static int ZOOM = 2;

    private int mode;

    private float startFingerX = 0f;
    private float startFingerY = 0f;

    private float translateX = 0f;
    private float translateY = 0f;

    private float previousTranslateX = 0f;
    private float previousTranslateY = 0f;

    private boolean dragged = true;
    private final int displayWidth;
    private final int displayHeight;
    private DisplayMetrics displayMetrics;

    private float lastSpanX;
    private float lastSpanY;

    private Matrix matrix;

    private RotationGestureDetector mRotationDetector;
    private float lastAngle;

    private Map<MapNode, List<Integer>> nodeToInfra;
    private Map<MapNode, List<MapNode>> graph;
    private List<Infra> infraList;
    private MapNode startNode;
    private Paint nodePaint;
    private Paint linePaint;
    private Paint textPaint;
    private Paint bgPaint;
    private static final float nodeRadius = 1;
    private static final int pathThickness = 1;
    private static final float MAX_DRAG_SPEED = 500;
    private static final float TEXT_DP = 0.5f;

    private boolean[] visited;

    public MapView(Context context, Map<MapNode, List<MapNode>> graph, Map<MapNode, List<Integer>> nodeToInfra, MapNode startNode, List<Infra> infraList) {
        super(context);
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        mRotationDetector = new RotationGestureDetector(this);
        displayMetrics = new DisplayMetrics();
        ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        displayHeight = displayMetrics.heightPixels;
        displayWidth = displayMetrics.widthPixels;
        matrix = new Matrix();
        matrix.postScale(scaleFactor, -scaleFactor);
        matrix.postTranslate(displayWidth/2, 3*displayHeight/4);
        lastAngle = 0;

        this.graph = graph;
        this.nodeToInfra = nodeToInfra;
        this.infraList = infraList;

        nodePaint = new Paint();
        nodePaint.setColor(Color.BLUE);
        nodePaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint();
        linePaint.setColor(getResources().getColor(R.color.teal_200));
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setStrokeWidth(pathThickness);

        this.startNode = startNode;

        visited = new boolean[graph.keySet().size()];

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(pxFromDp(context, TEXT_DP));

        bgPaint = new Paint();
        bgPaint.setColor(Color.LTGRAY);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    // below method is use to generate px from DP.
    public static float pxFromDp(final Context context, final float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mode = DRAG;
                startFingerX = event.getX();
                startFingerY = event.getY();

                break;

            case MotionEvent.ACTION_MOVE:
                translateX = event.getX() - startFingerX;
                translateY = event.getY() - startFingerY;
                startFingerX = event.getX();
                startFingerY = event.getY();

                if(Math.sqrt(translateX*translateX + translateY*translateY) < MAX_DRAG_SPEED) {
                    matrix.postTranslate(translateX, translateY);
                }
                invalidate();
                break;
        }

        scaleGestureDetector.onTouchEvent(event);

        if ((mode == DRAG && scaleFactor != 1f && dragged) || mode == ZOOM) {
            invalidate();
        }

        mRotationDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();

        //System.out.println("tx: " + translateX + "\ty: " + translateY + "\ts: " + scaleFactor);

        //canvas.scale(scaleFactor, scaleFactor, lastSpanX, lastSpanY);

//        canvas.translate(lastSpanX, lastSpanY);
        //canvas.scale(scaleFactor, scaleFactor);
        //canvas.translate(lastSpanX * (scaleFactor-1), lastSpanY*(scaleFactor-1));

        /*//If translateX times -1 is lesser than zero, let's set it to zero. This takes care of the left bound
        if ((translateX * -1) < 0) {
            translateX = 0;
        }
        //This is where we take care of the right bound. We compare translateX times -1 to (scaleFactor - 1) * displayWidth.
        //If translateX is greater than that value, then we know that we've gone over the bound. So we set the value of
        //translateX to (1 - scaleFactor) times the display width. Notice that the terms are interchanged; it's the same
        //as doing -1 * (scaleFactor - 1) * displayWidth
        else if ((translateX * -1) > (scaleFactor - 1) * displayWidth) {
            translateX = (1 - scaleFactor) * displayWidth;
        }

        if (translateY * -1 < 0) {
            translateY = 0;
        }
        //We do the exact same thing for the bottom bound, except in this case we use the height of the display
        else if ((translateY * -1) > (scaleFactor - 1) * displayHeight) {
            translateY = (1 - scaleFactor) * displayHeight;
        }*/

        //We need to divide by the scale factor here, otherwise we end up with excessive panning based on our zoom level
        //because the translation amount also gets scaled according to how much we've zoomed into the canvas.
        //canvas.translate(translateX / scaleFactor, translateY / scaleFactor);

        /* The rest of your canvas-drawing code */

        canvas.setMatrix(matrix);

        drawStuff(canvas);

        canvas.restore();
    }

    // below we are creating variables for our paint
    Paint otherPaint, outerPaint;

    // and a floating variable for our left arc.
    float arcLeft;

    private void drawNode(Canvas canvas, boolean[] visited, MapNode node) {
        if(visited[node.getId()]) return;

        visited[node.getId()] = true;

        canvas.drawCircle((float)node.getPosition().getX(), (float)node.getPosition().getY(), nodeRadius, nodePaint);
        List<Integer> infraIdList = nodeToInfra.get(node);

        if(infraIdList != null) {
            for(int id : infraIdList) {
                Infra infra = infraList.get(id);

                float rotateAngle = 0;
                float dx = 0, dy = 0;
                if(infra.getOrientation() == Orientation.Down) {
                    rotateAngle = 90;
                    dx = 7;
                }
                else if(infra.getOrientation() == Orientation.Up) {
                    rotateAngle = 90;
                    dx = 3;
                }
                else if(infra.getOrientation() == Orientation.Left) {
                    rotateAngle = 0;
                    dx = -7;
                }
                else if(infra.getOrientation() == Orientation.Right) {
                    rotateAngle =  0;
                    dx = 7;
                }

                canvas.translate((float) node.getPosition().getX(), (float) node.getPosition().getY());
                canvas.rotate(rotateAngle);
                canvas.scale(1, -1);
                canvas.drawText(infra.getName(), dx, 0, textPaint);
                canvas.scale(1, -1);
                canvas.rotate(-rotateAngle);
                canvas.translate((float) -node.getPosition().getX(), (float) -node.getPosition().getY());
            }
        }

        for(MapNode n : graph.get(node)) {
            if(n.getPosition().getZ() != node.getPosition().getZ()) continue;
            canvas.drawLine((float)node.getPosition().getX(), (float)node.getPosition().getY(),
                            (float)n.getPosition().getX(), (float)n.getPosition().getY(), linePaint);

            drawNode(canvas, visited, n);
        }
    }

    private void drawStuff(Canvas canvas) {
        canvas.drawPaint(bgPaint);

        Arrays.fill(visited, false);
        drawNode(canvas, visited, startNode);
    }

    float[] values = new float[9];

    @Override
    public void OnRotation(RotationGestureDetector rotationDetector) {
        float angle = rotationDetector.getAngle();
        float focusX = rotationDetector.getFocusX();
        float focusY = rotationDetector.getFocusY();


        float sc[] = {focusX, focusY, 1};
        getWorldCoord(sc);
        focusX = sc[0];
        focusY = sc[1];

//        focusX = 500;
//        focusY = 1000;



//        matrix.postTranslate(-focusX, -focusY);
//        matrix.postRotate(lastAngle - angle);
//        matrix.postTranslate(focusX, focusY);

        lastAngle = angle;
        invalidate();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mode = ZOOM;
            lastSpanX = detector.getFocusX();
            lastSpanY = detector.getFocusY();

            scaleFactor = detector.getScaleFactor();

            matrix.getValues(values);

            float sc[] = {lastSpanX, lastSpanY, 1};
            getWorldCoord(sc);
            lastSpanX = sc[0];
            lastSpanY = sc[1];

            matrix.postTranslate(-lastSpanX, -lastSpanY);
            matrix.postScale(scaleFactor, scaleFactor);
            matrix.postTranslate(lastSpanX, lastSpanY);

            invalidate();

            return true;
        }
    }

    void getWorldCoord(float[] sc) {
        matrix.getValues(values);

        float wc[] = {0, 0};
        for(int i = 0; i < 3; i++) {
            wc[0] += values[i] * sc[i];
            wc[1] += values[3 + i] * sc[i];
        }
    }
}
