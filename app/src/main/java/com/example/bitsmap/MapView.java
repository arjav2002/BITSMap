package com.example.bitsmap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

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
    private Paint showCoordbgPaint;
    private Paint showCoordTextPaint;
    private MapNode showCoordNode;

    private static final float nodeRadius = 0.2f;
    private static final float pathThickness = 0.2f;
    private static final float MAX_DRAG_SPEED = 500;
    private static final float TEXT_DP = 0.5f;
    private static final float SHOW_COORD_DIST = 1;
    private static final float SHOW_COORD_CHAR_WIDTH = 0.6f;
    private static final float SHOW_COORD_HEIGHT = 2.5f;

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
        nodePaint.setTextSize(pxFromDp(context, TEXT_DP));

        linePaint = new Paint();
        linePaint.setColor(getResources().getColor(R.color.teal_200));
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setStrokeWidth(pathThickness);

        showCoordbgPaint = new Paint();
        showCoordbgPaint.setColor(Color.WHITE);
        showCoordbgPaint.setStyle(Paint.Style.FILL);

        this.startNode = startNode;

        visited = new boolean[graph.keySet().size()];

        textPaint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(pxFromDp(context, TEXT_DP));

        showCoordTextPaint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        showCoordTextPaint.setColor(Color.BLUE);
        showCoordTextPaint.setTextSize(pxFromDp(context, TEXT_DP));

        bgPaint = new Paint();
        bgPaint.setColor(Color.LTGRAY);
        bgPaint.setStyle(Paint.Style.FILL);

        showCoordNode = null;
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

                float[] sc = {startFingerX, startFingerY, 1};
                getWorldCoord(sc);

                double minDist = Double.POSITIVE_INFINITY;
                MapNode x = null;
                for(MapNode n : graph.keySet()) {
                    if(n.getPosition().getZ() != startNode.getPosition().getZ()) continue;
                    double d = Math.sqrt(Math.pow(n.getPosition().getX()-sc[0], 2) + Math.pow(n.getPosition().getY()-sc[1], 2));
                    if(d < minDist) {
                        minDist = d;
                        x = n;
                    }
                }

                MapNode oldNode = showCoordNode;

                if (minDist < SHOW_COORD_DIST) {
                    showCoordNode = x;
                }
                else {
                    showCoordNode = null;
                }

                if(showCoordNode != oldNode) invalidate();

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

        canvas.setMatrix(matrix);

        drawStuff(canvas);

        canvas.restore();
    }

    private void drawConnections(Canvas canvas, boolean[] visited, MapNode node) {
        if(visited[node.getId()]) return;

        visited[node.getId()] = true;

        List<Integer> infraIdList = nodeToInfra.get(node);

        if(infraIdList != null) {
            for(int id : infraIdList) {
                Infra infra = infraList.get(id);
//                if(infra.getInfratype() != Infratype.Room) continue;

                float rotateAngle = 0;
                float dx = 0, dy = 0;
                if(infra.getOrientation() == Orientation.Down) {
                    rotateAngle = 90;
                    dx = -4;
                }
                else if(infra.getOrientation() == Orientation.Up) {
                    rotateAngle = 90;
                    dx = nodeRadius+1;
                }
                else if(infra.getOrientation() == Orientation.Left) {
                    rotateAngle = 0;
                    dx = -4;
                }
                else if(infra.getOrientation() == Orientation.Right) {
                    rotateAngle =  0;
                    dx = nodeRadius+1;
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

            drawConnections(canvas, visited, n);
        }
    }

    private void drawStuff(Canvas canvas) {
        canvas.drawPaint(bgPaint);

        Arrays.fill(visited, false);
        drawConnections(canvas, visited, startNode);

        for(MapNode node : graph.keySet()) {
            if(node.getPosition().getZ() != startNode.getPosition().getZ()) continue;
            canvas.drawCircle((float)node.getPosition().getX(), (float)node.getPosition().getY(), nodeRadius, nodePaint);
        }

        if(showCoordNode != null) {
            String toDrawString = ((int)showCoordNode.getPosition().getX() + ", " + (int)showCoordNode.getPosition().getY() + " (" + showCoordNode.getId() + ")");
            int ssize = toDrawString.length();

            canvas.drawRect(new RectF((float)(showCoordNode.getPosition().getX() - 1 - ssize * SHOW_COORD_CHAR_WIDTH),
                                      (float)(showCoordNode.getPosition().getY() + SHOW_COORD_HEIGHT/2),
                                      (float)(showCoordNode.getPosition().getX() - 1),
                                      (float)(showCoordNode.getPosition().getY() - SHOW_COORD_HEIGHT/2)),
                    showCoordbgPaint);

            canvas.translate((float)(showCoordNode.getPosition().getX() - 1 - ssize * SHOW_COORD_CHAR_WIDTH),
                    (float)(showCoordNode.getPosition().getY()));
            canvas.scale(1, -1);
            canvas.drawText(toDrawString,
                    0,
                    0,
                    showCoordTextPaint);
            canvas.scale(1, -1);
            canvas.translate((float)-(showCoordNode.getPosition().getX() - 1 - ssize * SHOW_COORD_CHAR_WIDTH),
                    (float)-(showCoordNode.getPosition().getY()));
        }
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

            matrix.postTranslate(-lastSpanX, -lastSpanY);
            matrix.postScale(scaleFactor, scaleFactor);
            matrix.postTranslate(lastSpanX, lastSpanY);

            invalidate();

            return true;
        }
    }

    void getWorldCoord(float[] sc) {
        Matrix inverse = new Matrix();
        assert(matrix.invert(inverse));
        inverse.getValues(values);

        float wc[] = {0, 0, 0};
        for(int i = 0; i < 3; i++) {
            for(int j = 0; j < 3; j++) {
                wc[i] += values[3*i + j] * sc[j];
            }
        }

        sc[0] = wc[0]/wc[2];
        sc[1] = wc[1]/wc[2];
    }

    public void setStartNode(MapNode startNode) {
        this.startNode = startNode;
        showCoordNode = null;
        invalidate();
    }
}
