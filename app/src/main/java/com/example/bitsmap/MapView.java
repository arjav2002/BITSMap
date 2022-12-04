package com.example.bitsmap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class MapView extends View implements RotationGestureDetector.OnRotationGestureListener {

    private static float MIN_ZOOM = 5f;

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

    private Matrix worldToScreen;

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
    private Paint pathPaint;
    private Paint doorPaint;
    private Paint showCoordbgPaint;
    private Paint showCoordTextPaint;
    private Paint highlightNodePaint;
    private Paint middleNodePaint;
    private MapNode showCoordNode;
    private MapNode middleNode;
    private MapNode highlightNode;

    private ReentrantLock mutex;

    private static final float nodeRadius = 0.3f;
    private static final float highlightedNodeRadius = 0.5f;
    private static final float pathWidth = 3f;
    private static final float pathThickness = 0.8f;
    private static final float MAX_DRAG_SPEED = 500;
    private static final float TEXT_DP = 0.5f;
    private static final float SHOW_COORD_DIST = 1;
    private static final float SHOW_COORD_CHAR_WIDTH = 0.6f;
    private static final float SHOW_COORD_HEIGHT = 2.5f;
    private static final float CHAR_FACTOR = 0.6f;
    private static final float DOOR_THICKNESS = 0.2f;
    private static final float DOOR_LENGTH = 1.5f;
    private static final float NODE_CENTER_SCALE_FACTOR = 30f;
    private static final float SCALE_SPEED = 1.00005f;
    private static final float LOC_PIN_WIDTH = 160f;
    private static final float LOC_PIN_HEIGHT = 160f;

    private final float textSize;

    private boolean[] visited;

    private ArrayList<MapNode> path;
    private int startIndex, endIndex;
    private boolean pathChanged;
    private boolean nodeHighlighted;
    private boolean isShowCoordNullable;

    private Context context;

    private Drawable personPin;
    private Drawable locationPin;
    private Drawable startPin;
    private Drawable middlePin;

    private MainActivity mainActivity;

    private float minX, maxX, minY, maxY;
    private final float initTx, initTy;

    public MapView(MainActivity mainActivity, Map<MapNode, List<MapNode>> graph, Map<MapNode, List<Integer>> nodeToInfra, MapNode startNode, List<Infra> infraList) {
        super(mainActivity);
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        mRotationDetector = new RotationGestureDetector(this);
        displayMetrics = new DisplayMetrics();
        ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        displayHeight = displayMetrics.heightPixels;
        displayWidth = displayMetrics.widthPixels;
        initTx = displayWidth/2;
        initTy = 3*displayHeight/4;
        worldToScreen = new Matrix();
        worldToScreen.postScale(scaleFactor, -scaleFactor);
        worldToScreen.postTranslate(initTx, initTy);
        lastAngle = 0;
        pathChanged = false;

        personPin = getResources().getDrawable(R.drawable.ic_baseline_person_pin_circle_24);
        locationPin = getResources().getDrawable(R.drawable.ic_baseline_location_on_24);
        startPin = getResources().getDrawable(R.drawable.ic_baseline_location_start_24);
        middlePin = getResources().getDrawable(R.drawable.ic_baseline_location_middle_24);

        this.graph = graph;
        this.nodeToInfra = nodeToInfra;
        this.infraList = infraList;
        this.context = mainActivity;

        nodePaint = new Paint();
        nodePaint.setColor(Color.BLUE);
        nodePaint.setStyle(Paint.Style.FILL);
        nodePaint.setTextSize(pxFromDp(context, TEXT_DP));

        pathPaint = new Paint();
        pathPaint.setColor(getResources().getColor(R.color.path_color));
        pathPaint.setStyle(Paint.Style.FILL);

        doorPaint = new Paint();
        doorPaint.setColor(getResources().getColor(R.color.black));
        doorPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint();
        linePaint.setColor(getResources().getColor(R.color.teal_200));
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setStrokeWidth(pathThickness);

        showCoordbgPaint = new Paint();
        showCoordbgPaint.setColor(Color.WHITE);
        showCoordbgPaint.setStyle(Paint.Style.FILL);

        this.startNode = startNode;

        visited = new boolean[graph.keySet().size()];

        textSize = pxFromDp(context, TEXT_DP);

        textPaint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(textSize);
        textPaint.setTextAlign(Paint.Align.LEFT);

        showCoordTextPaint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        showCoordTextPaint.setColor(Color.BLUE);
        showCoordTextPaint.setTextSize(textSize);

        highlightNodePaint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        highlightNodePaint.setColor(Color.RED);
        highlightNodePaint.setTextSize(textSize);

        middleNodePaint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        middleNodePaint.setColor(getResources().getColor(R.color.middle_pin_color));
        middleNodePaint.setTextSize(textSize);

        bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        bgPaint.setStyle(Paint.Style.FILL);

        showCoordNode = null;
        highlightNode = null;

        mutex = new ReentrantLock();
        isShowCoordNullable = false;

        minX = minY = Float.POSITIVE_INFINITY;
        maxX = maxY = Float.NEGATIVE_INFINITY;
        initTranslateBounds();
    }

    // below method is use to generate px from DP.
    public static float pxFromDp(final Context context, final float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    float[] oldValues = new float[9];

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                startFingerX = event.getX();
                startFingerY = event.getY();

                isShowCoordNullable = true;

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

                if (minDist < SHOW_COORD_DIST) {
                    setShowCoordNode(x);
                    isShowCoordNullable = false;
                }

                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                isShowCoordNullable = false;
                break;
            case MotionEvent.ACTION_MOVE:
                isShowCoordNullable = false;
                translateX = event.getX() - startFingerX;
                translateY = event.getY() - startFingerY;
                startFingerX = event.getX();
                startFingerY = event.getY();

                if(Math.sqrt(translateX*translateX + translateY*translateY) < MAX_DRAG_SPEED) {
                    mutex.lock();
                    worldToScreen.getValues(values);

                    translateX = -translateX;
                    translateY = -translateY;

                    float[] worldCoordsAtCenter = {displayWidth/2, displayHeight/2, 1};
                    getWorldCoord(worldCoordsAtCenter);
                    System.out.println("W@C: " + worldCoordsAtCenter[0] + ", " + worldCoordsAtCenter[1]);

                    float newWorldAtCenterX = worldCoordsAtCenter[0] + values[0] * translateX;
                    float newWorldAtCenterY = worldCoordsAtCenter[1] + values[4] * translateY;

                    System.out.println("NEW_W@C: " + newWorldAtCenterX + ", " + newWorldAtCenterY);

                    newWorldAtCenterX = Math.max(minX, Math.min(maxX, newWorldAtCenterX));
                    newWorldAtCenterY = Math.max(minY, Math.min(maxY, newWorldAtCenterY));

                    System.out.println("CLAMPED_W@C: " + newWorldAtCenterX + ", " + newWorldAtCenterY);

                    float[] newScreenCoordinates = {newWorldAtCenterX, newWorldAtCenterY, 1};
                    getScreenCoords(newScreenCoordinates);

                    translateX = displayWidth/2 - newScreenCoordinates[0];
                    translateY = displayHeight/2 - newScreenCoordinates[1];

                    System.out.println(translateX + ", " + translateY);

                    worldToScreen.postTranslate(translateX, translateY);

                }
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                if(isShowCoordNullable) {
                    showCoordNode = null;
                    invalidate();
                }
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

        canvas.setMatrix(worldToScreen);

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

                int textLength = infra.getName().length();
                float rotateAngle = 0;
                float dx = 0, dy = 0;
                float dr = 0, dt = 0, dl = 0, db = 0;
                if(infra.getOrientation() == Orientation.Down) {
                    rotateAngle = 90;
                    dx = -pathWidth/2 - textLength*textSize*CHAR_FACTOR - 0.3f;
                    dy = 0.35f*textSize;

                    dl = -pathWidth/2 - DOOR_THICKNESS;
                    dr = -pathWidth/2;
                    dt = DOOR_LENGTH/2;
                    db = -DOOR_LENGTH/2;
                }
                else if(infra.getOrientation() == Orientation.Up) {
                    rotateAngle = 90;
                    dx = pathWidth/2 + 0.6f;
                    dy = 0.35f*textSize;

                    dl = pathWidth/2 + DOOR_THICKNESS;
                    dr = pathWidth/2;
                    dt = DOOR_LENGTH/2;
                    db = -DOOR_LENGTH/2;
                }
                else if(infra.getOrientation() == Orientation.Left) {
                    rotateAngle = 0;
                    dx = -pathWidth/2 - textLength*textSize*CHAR_FACTOR;
                    dy = 0.35f*textSize;

                    dl = -pathWidth/2 - DOOR_THICKNESS;
                    dr = -pathWidth/2;
                    dt = DOOR_LENGTH/2;
                    db = -DOOR_LENGTH/2;
                }
                else if(infra.getOrientation() == Orientation.Right) {
                    rotateAngle =  0;
                    dx = pathWidth/2 + 0.5f;
                    dy = 0.35f*textSize;

                    dl = pathWidth/2;
                    dr = pathWidth/2 + DOOR_THICKNESS;
                    dt = DOOR_LENGTH/2;
                    db = -DOOR_LENGTH/2;
                }

                canvas.translate((float) node.getPosition().getX(), (float) node.getPosition().getY());
                canvas.rotate(rotateAngle);
                canvas.scale(1, -1);

                canvas.drawText(infra.getName(), dx, dy, textPaint);

                canvas.drawRect(dl, dt, dr, db, doorPaint);
                canvas.scale(1, -1);
                canvas.rotate(-rotateAngle);
                canvas.translate((float) -node.getPosition().getX(), (float) -node.getPosition().getY());
            }
        }

        for(MapNode n : graph.get(node)) {
            if(n.getPosition().getZ() != node.getPosition().getZ()) continue;

            float left = (float)Math.min(n.getPosition().getX(), node.getPosition().getX()) - pathWidth / 2;
            float right = (float)Math.max(n.getPosition().getX(), node.getPosition().getX()) + pathWidth / 2;
            float top = (float)Math.max(n.getPosition().getY(), node.getPosition().getY()) + pathWidth / 2;
            float bottom = (float)Math.min(n.getPosition().getY(), node.getPosition().getY()) - pathWidth / 2;
            canvas.drawRect(left, top, right, bottom, pathPaint);

            drawConnections(canvas, visited, n);
        }
    }

    public void setPath(ArrayList<MapNode> path) {
        this.path = path;
        for(MapNode n : path) {
            System.out.println(n.getId() + ", " + n.getPosition());
        }
        pathChanged = true;
        invalidate();
    }

    private void drawPath(Canvas canvas) {
        int floor = (int)startNode.getPosition().getZ();
        MapNode n1 = null;
        int i = 0;
        do {
            if(i >= path.size()) break;
            n1 = path.get(i);
            i++;
        } while((int)n1.getPosition().getZ() != floor);

        startIndex = i-1;

        int oldI = i;
        MapNode oldN1 = n1;
        while(i < path.size()) {
            MapNode n2 = path.get(i);
            if((int)n2.getPosition().getZ() != floor) break;

            canvas.drawLine(
                    (float)n1.getPosition().getX(),
                    (float)n1.getPosition().getY(),
                    (float)n2.getPosition().getX(),
                    (float)n2.getPosition().getY(), linePaint);

            n1 = n2;
            i++;
        }

        i = oldI;
        n1 = oldN1;
        while(i < path.size()) {
            MapNode n2 = path.get(i);
            if((int)n2.getPosition().getZ() != floor) break;

            canvas.drawCircle((float) n1.getPosition().getX(), (float) n1.getPosition().getY(), highlightedNodeRadius, nodePaint);
            canvas.drawCircle((float) n2.getPosition().getX(), (float) n2.getPosition().getY(), highlightedNodeRadius, nodePaint);

            n1 = n2;
            i++;
        }

        endIndex = i-1;
    }

    private void drawLines(Canvas canvas, boolean[] visited, MapNode node) {
        if(visited[node.getId()]) return;

        visited[node.getId()] = true;

        for(MapNode n : graph.get(node)) {
            if(n.getPosition().getZ() != node.getPosition().getZ()) continue;

            canvas.drawLine((float)node.getPosition().getX(), (float)node.getPosition().getY(),
                    (float)n.getPosition().getX(), (float)n.getPosition().getY(), linePaint);

            drawLines(canvas, visited, n);
        }
    }

    private void drawStuff(Canvas canvas) {
        canvas.drawPaint(bgPaint);

        Arrays.fill(visited, false);
        drawConnections(canvas, visited, startNode);

//        Arrays.fill(visited, false);
//        drawLines(canvas, visited, startNode);

        for(MapNode node : graph.keySet()) {
            if(node.getPosition().getZ() != startNode.getPosition().getZ()) continue;
            canvas.drawCircle((float)node.getPosition().getX(), (float)node.getPosition().getY(), nodeRadius, nodePaint);
        }

        if(showCoordNode != null) {
            drawPinAtNode(locationPin, showCoordNode, canvas);
        }

        if(path != null && path.size() >= 2) {
            drawPath(canvas);
            if(pathChanged) {
                centerWorldCoords(path.get(startIndex).getPosition().add(path.get(endIndex).getPosition()).divide(2));
                zoomOutTillBothPinsVisible();
                pathChanged = false;
            }

            drawPinAtNode(locationPin, path.get(path.size()-1), canvas);
            drawPinAtNode(startPin, path.get(0), canvas);
            if(middleNode != null && middleNode.getPosition().getZ() == startNode.getPosition().getZ()) {
                canvas.drawCircle((float)middleNode.getPosition().getX(), (float)middleNode.getPosition().getY(), nodeRadius, middleNodePaint);
                drawPinAtNode(middlePin, middleNode, canvas);
            }
        }

        if(highlightNode != null && highlightNode.getPosition().getZ() == startNode.getPosition().getZ()) {
            canvas.drawCircle((float)highlightNode.getPosition().getX(), (float)highlightNode.getPosition().getY(), nodeRadius, highlightNodePaint);

            drawPinAtNode(personPin, highlightNode, canvas);

            if(!nodeHighlighted) {
                nodeHighlighted = true;
                mutex.lock();
                float[] sc = {(float)highlightNode.getPosition().getX(), (float)highlightNode.getPosition().getY(), 1};
                getScreenCoords(sc);
                worldToScreen.postTranslate(-sc[0] + displayWidth/2, -sc[1] + displayHeight/2 + pxFromDp(this.context, 100));
                mutex.unlock();
            }
        }
    }

    private void drawPinAtNode(Drawable drawable, MapNode node, Canvas canvas) {
        if(node.getPosition().getZ() == startNode.getPosition().getZ()) {
            drawPin(drawable,
                    node.getPosition().getX() - LOC_PIN_WIDTH / 2,
                    -(node.getPosition().getY() + LOC_PIN_HEIGHT),
                    node.getPosition().getX() + LOC_PIN_WIDTH / 2,
                    -(node.getPosition().getY()), canvas);
        }
    }

    private void drawPin(Drawable drawable, double left, double top, double right, double bottom, Canvas canvas) {
        mutex.lock();
        worldToScreen.getValues(values);

        float cx = (float) ((left+right)/2);
        int cy = (int) ((top+bottom)/2);
        float width = (float) (right-left);
        float height = (float) (bottom-top);

        mutex.lock();
        worldToScreen.getValues(values);

        cx *= values[0];
        bottom *= -values[4];

        canvas.scale(1/values[0], 1/values[4]);
        drawable.setBounds((int) ((int)cx-width/2), (int) ((int)bottom-height), (int) ((int)cx+width/2), (int) bottom);
        drawable.draw(canvas);
        canvas.scale(values[0], values[4]);

        mutex.unlock();
    }

    float[] values = new float[9];

    private boolean isWorldCoordinateOnScreen(Vec3D v, int padding) {
        if(v.getZ() != startNode.getPosition().getZ()) return false;

        float[] sc = {(float)v.getX(), (float)v.getY(), 1};
        getScreenCoords(sc);

        return sc[0] >= padding && sc[0] <= displayWidth-padding && sc[1] >= padding && sc[1] <= displayHeight-padding;
    }

    private void zoomOutTillBothPinsVisible() {
        MapNode n1 = path.get(startIndex);
        MapNode n2 = path.get(endIndex);

        do {
            mutex.lock();
            worldToScreen.postScale(1/SCALE_SPEED, 1/SCALE_SPEED, displayWidth/2, displayHeight/2);
            mutex.unlock();
        } while(!isWorldCoordinateOnScreen(n1.getPosition(), 400) || !isWorldCoordinateOnScreen(n2.getPosition(), 400));
    }

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

            mutex.lock();
            worldToScreen.getValues(values);

            float newScale = Math.max(MIN_ZOOM, scaleFactor * values[0]);
            scaleFactor = newScale / values[0];

            worldToScreen.postTranslate(-lastSpanX, -lastSpanY);
            worldToScreen.postScale(scaleFactor, scaleFactor);
            worldToScreen.postTranslate(lastSpanX, lastSpanY);

            invalidate();
            mutex.unlock();

            return true;
        }
    }

    void getWorldCoord(float[] sc) {
        Matrix inverse = new Matrix();
        assert(worldToScreen.invert(inverse));

        mutex.lock();
        inverse.getValues(values);

        float wc[] = {0, 0, 0};
        for(int i = 0; i < 3; i++) {
            for(int j = 0; j < 3; j++) {
                wc[i] += values[3*i + j] * sc[j];
            }
        }

        sc[0] = wc[0]/wc[2];
        sc[1] = wc[1]/wc[2];
        mutex.unlock();
    }

    void getScreenCoords(float[] wc) {
        mutex.lock();
        worldToScreen.getValues(values);

        float sc[] = {0, 0, 0};
        for(int i = 0; i < 3; i++) {
            for(int j = 0; j < 3; j++) {
                sc[i] += values[3*i + j] * wc[j];
            }
        }

        wc[0] = sc[0]/sc[2];
        wc[1] = sc[1]/sc[2];
        mutex.unlock();
    }

    public void setStartNode(MapNode startNode) {
        this.startNode = startNode;
        showCoordNode = null;
        invalidate();
    }

    public void setShowCoordNode(MapNode showCoordNode) {
        if(showCoordNode != this.showCoordNode) {
            this.showCoordNode = showCoordNode;

            invalidate();
        }
    }

    public MapNode getShowCoordNode() { return showCoordNode; }

    public void setMiddleNode(MapNode middleNode) {
        this.middleNode = middleNode;
    }

    public void setHighlightNode(MapNode highlightNode) {
        if(highlightNode != this.highlightNode) {
            this.highlightNode = highlightNode;
            nodeHighlighted = false;
            invalidate();
        }
    }

    public void centerWorldCoords(Vec3D worldCoord) {
        mutex.lock();

        float[] screenCoords = {(float)worldCoord.getX(), (float)worldCoord.getY(), 1};
        getScreenCoords(screenCoords);

        float dx = -screenCoords[0] + (float)displayWidth/2;
        float dy = -screenCoords[1] + (float)displayHeight/2;

        worldToScreen.postTranslate(dx, dy);

        do {
            worldToScreen.postScale(SCALE_SPEED, SCALE_SPEED, (float)displayWidth/2, (float)displayHeight/2);
            worldToScreen.getValues(values);
        } while(values[0] < NODE_CENTER_SCALE_FACTOR);

        invalidate();
        mutex.unlock();
    }

    private void setTranslateBounds(boolean[] visited, MapNode node) {
        if(visited[node.getId()]) return;
        visited[node.getId()] = true;

        minX = (float) Math.min(minX, node.getPosition().getX());
        minY = (float) Math.min(minY, node.getPosition().getY());
        maxX = (float) Math.max(maxX, node.getPosition().getX());
        maxY = (float) Math.max(maxY, node.getPosition().getY());

        for(MapNode n : graph.get(node)) {
            setTranslateBounds(visited, n);
        }
    }

    private void initTranslateBounds() {
        Arrays.fill(visited, false);
        setTranslateBounds(visited, startNode);
        System.out.println("TRANS: " + minX + ", " + minY + ", " + maxX + ", " + maxY);
    }
}
