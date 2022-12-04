package com.example.bitsmap;

import android.app.Activity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

public class MainActivity extends Activity {

    private static final double RESOLUTION_M = 0.52;
    private static final double FLOOR_DIFF = 3;

    private Map<String, MapNode> referencePoints;
    private List<MapNode> nodeList;
    private Map<Vec3D, MapNode> nodeMap;
    private List<Infra> infraList;
    private Map<Infratype, Map<Integer, Map<Double, FloorChanger>>> floorChangerMap;
    private Map<MapNode, List<Integer>> nodeToInfra;
    private Map<MapNode, List<MapNode>> graph;
    private MapNode lastNode;
    private RelativeLayout relativeLayout;
    private MapView mapView;

    private ArrayList<SearchResult> searchResults;

    private SearchView searchView;
    private RecyclerView searchResultsView;
    private Infra searchViewInfra;

    private LinearLayout floorButtonsLayout;
    private RelativeLayout navigationLayout;
    private Set<Integer> floorSet;

    private SearchResultViewHolder searchResultViewHolder;

    private LinearLayout directionsLayout;
    private Button directionsButton;
    private TextView navigationTextView;

    private LinearLayout transportLayout;
    private Button walkButton;
    private Button wheelchairButton;

    private boolean mapViewOn;
    private boolean searchFocus;
    private boolean lookingForDirections;
    private boolean selectingSourceLocation;
    private boolean selectingDestinationLocation;
    private boolean usingWheelChair;

    public Infra startInfra;
    public Infra destinationInfra;
    private ArrayList<MapNode> path;

    private int currentPathIndex;

    private int currentFloor;
    private int maxFloor;
    private int minFloor;

    // For floor changer nodes, make sure the other Delta corresponds to the actual node.
    // Each floorchanger node connects to only its directly upper and lower neighbours

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        referencePoints = new HashMap<>();
        nodeList = new ArrayList<>();
        nodeMap = new TreeMap<>();
        infraList = new ArrayList<>();
        nodeToInfra = new HashMap<>();
        floorChangerMap = new HashMap<>();
        graph = new HashMap<>();
        floorSet = new HashSet<>();
        path = new ArrayList<>();
        searchViewInfra = startInfra = destinationInfra = null;
        usingWheelChair = false;

        try {
            Scanner sc = new Scanner(getAssets().open("nodes.txt"));

            Vec3D curPos = new Vec3D(0, 0, 0);
            addNodeToList(curPos);

            while(sc.hasNextLine()) {
                String line = sc.nextLine();

                if(line.endsWith(":")) {
                    referencePoints.put(line.substring(0, line.length()-1), lastNode);
                }
                else if(line.startsWith("U ") || line.startsWith("R ") || line.startsWith("L ") || line.startsWith("D ")) {
                    char direction = line.charAt(0);
                    line = line.substring(2);

                    String[] params = line.split(" ");

                    if(Character.isDigit(line.charAt(0))) {
                        double magnitude = Double.parseDouble(params[0]);
                        curPos = moveVector(direction, curPos, magnitude);

                        MapNode oldNode = lastNode;
                        addNodeToList(curPos);
                        MapNode newNode = lastNode;

                        connectNodes(oldNode, newNode);
                    }
                    else {
                        String infraType = params[0];
                        Infra infra = null;
                        Orientation orientation = getOrientation(direction);

                        if(infraType.equals("Room")) {
                            String roomName = getRoomName(params);
                            infra = new Infra(roomName, infraList.size(), curPos, Infratype.Room, orientation, lastNode);
                            infraList.add(infra);
                        }
                        else if(infraType.equals("Stairs") || infraType.equals("Lift")) {
                            infra = handleFloorChanger(params, orientation, curPos);
                        }
                        else if(infraType.equals("Washroom")) {
                            String washroomType = params[1];
                            infra = new Infra("Washroom" + washroomType, infraList.size(), curPos, Infratype.Washroom, orientation, lastNode);
                            infraList.add(infra);
                        }
                        else {
                            infra = new Infra(infraType, infraList.size(), curPos, Infratype.DrinkingWater, orientation, lastNode);
                            infraList.add(infra);
                        }


                        if(!nodeToInfra.containsKey(lastNode)) {
                            nodeToInfra.put(lastNode, new ArrayList<>());
                        }

                        nodeToInfra.get(lastNode).add(infra.getId());
                    }
                }
                else if(line.startsWith("CLIMB")) {
                    line = line.substring(6);
                    String[] params = line.split(" ");
                    curPos = climbFloorChanger(params, curPos.getZ());
                    addNodeToList(curPos);
                }
                else if(line.startsWith("Connect")) {
                    MapNode refPoint = referencePoints.get(line.split(" ")[1]);

                    connectNodes(lastNode, refPoint);
                }
                else if(line.startsWith("Goto")) {
                    MapNode refPoint = referencePoints.get(line.split(" ")[1]);
                    curPos = refPoint.getPosition();
                    lastNode = refPoint;
                }
            }

            connectFloorChangers();

            for(MapNode n : nodeList) {
                System.out.println("id: " + n.getId() + "\tX: " + n.getPosition().getX() + "\tY: " + n.getPosition().getY() + "\tZ: " + n.getPosition().getZ());
                System.out.print("Connected to: ");
                for(MapNode n1 : graph.get(n)) System.out.print(n1.getId() + ", ");
                System.out.println();

                List<Integer> infraIndexList = nodeToInfra.get(n);
                if(infraIndexList == null) continue;
                for(int i : infraIndexList) System.out.print(infraList.get(i).getName() + ", ");
                System.out.println();
            }

            for(Map<Integer, Map<Double, FloorChanger>> m : floorChangerMap.values()) {
                for(Map<Double, FloorChanger> m2 : m.values()) {
                    for(FloorChanger fc : m2.values()) {
                        System.out.println(fc);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Integer[] floors = new Integer[floorSet.size()];
        floorSet.toArray(floors);
        minFloor = floors[0];
        maxFloor = floors[floors.length-1];

        // initializing our view.
        relativeLayout = findViewById(R.id.idRLView);
        mapView = new MapView(this, graph, nodeToInfra, nodeList.get(0), infraList);
        relativeLayout.addView(mapView);
        mapViewOn = true;

        searchView = findViewById(R.id.searchView);
        searchResultsView = findViewById(R.id.searchResultsRecycler);
        relativeLayout.removeView(searchResultsView);

        searchView.clearFocus();
        searchFocus = false;

        initializeFloorButtons();

        searchResults = new ArrayList<>();
        searchResultsView.setLayoutManager(new LinearLayoutManager(this));
        searchResultViewHolder = new SearchResultViewHolder(this, searchResults, searchResultsView);

        searchView.setOnQueryTextFocusChangeListener ((View v, boolean hasFocus) -> {
            searchFocus = hasFocus;

            if(hasFocus && mapViewOn) {
                relativeLayout.removeView(mapView);
                relativeLayout.removeView(floorButtonsLayout);
                relativeLayout.removeView(directionsLayout);
                relativeLayout.addView(searchResultsView);
                mapViewOn = false;

                filterSearchResults(searchView.getQuery().toString());
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterSearchResults(newText);
                searchResultViewHolder.notifyDataSetChanged();
                return false;
            }
        });

        lookingForDirections = false;
        directionsButton = findViewById(R.id.directionsButton);
        directionsButton.setOnClickListener((View view) -> {
            moveToDirections();
        });

        directionsLayout = findViewById(R.id.directionsLayout);

        bringHudToFront();
    }

    public boolean isMapViewOn() { return isMapViewOn(); }

    private void moveToDirections() {
        mapView.setHighlightNode(null);
        mapView.setMiddleNode(null);
        lookingForDirections = true;
        selectingSourceLocation = selectingDestinationLocation = false;
        relativeLayout.removeAllViews();
        relativeLayout.addView(mapView);

        RelativeLayout sourceSearchLayout = (RelativeLayout) LayoutInflater.from(this).inflate(R.layout.source_search_bar, relativeLayout, false);
        RelativeLayout destinationSearchLayout = (RelativeLayout) LayoutInflater.from(this).inflate(R.layout.destination_search_bar, relativeLayout, false);

        SearchView sourceSearchView = (SearchView) sourceSearchLayout.findViewById(R.id.sourceSearchBar);
        SearchView destinationSearchView = (SearchView) destinationSearchLayout.findViewById(R.id.destinationSearchBar);
        relativeLayout.addView(sourceSearchLayout, sourceSearchLayout.getLayoutParams());

        if(startInfra != null) {
            sourceSearchView.setQuery(startInfra.getName() + ", Floor: " + (int)startInfra.getPosition().getZ(), false);
        }

        if(destinationInfra != null) {
            destinationSearchView.setQuery(destinationInfra.getName() + ", Floor: " + (int)destinationInfra.getPosition().getZ(), false);
        }

        ViewGroup.LayoutParams params = destinationSearchLayout.getLayoutParams();
        RelativeLayout.LayoutParams destinationParams = new RelativeLayout.LayoutParams(params);

        sourceSearchLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        sourceSearchLayout.layout(0, 0, sourceSearchLayout.getMeasuredWidth(), sourceSearchLayout.getMeasuredHeight());
        destinationParams.setMargins(0,
                (int) (sourceSearchLayout.getHeight()),
                0,
                0);
        relativeLayout.addView(destinationSearchLayout, destinationParams);

        sourceSearchView.setOnQueryTextFocusChangeListener ((View v, boolean hasFocus) -> {
            searchFocus = hasFocus;

            if(hasFocus && lookingForDirections) {
                relativeLayout.removeView(mapView);
                relativeLayout.removeView(floorButtonsLayout);
                relativeLayout.removeView(destinationSearchLayout);
                relativeLayout.removeView(transportLayout);

                RelativeLayout.LayoutParams searchResultsViewParams = new RelativeLayout.LayoutParams(searchResultsView.getLayoutParams());
                searchResultsViewParams.setMargins((int)MapView.pxFromDp(this, 8),
                        (int) (sourceSearchLayout.getHeight() + MapView.pxFromDp(this,16)),
                        (int)MapView.pxFromDp(this, 8),
                        (int)MapView.pxFromDp(this, 8));
                relativeLayout.addView(searchResultsView, searchResultsViewParams);
                mapViewOn = false;
                selectingSourceLocation = true;
                selectingDestinationLocation = false;

                filterSearchResults(sourceSearchView.getQuery().toString());
            }
        });
        sourceSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterSearchResults(newText);
                searchResultViewHolder.notifyDataSetChanged();
                return false;
            }
        });

        destinationSearchView.setOnQueryTextFocusChangeListener ((View v, boolean hasFocus) -> {
            searchFocus = hasFocus;

            if(hasFocus && lookingForDirections) {
                relativeLayout.removeView(mapView);
                relativeLayout.removeView(floorButtonsLayout);
                relativeLayout.removeView(sourceSearchLayout);
                relativeLayout.removeView(transportLayout);

                destinationParams.topMargin = (int) MapView.pxFromDp(this, 8);
                destinationSearchLayout.setLayoutParams(destinationParams);

                RelativeLayout.LayoutParams searchResultsViewParams = new RelativeLayout.LayoutParams(searchResultsView.getLayoutParams());
                searchResultsViewParams.setMargins((int)MapView.pxFromDp(this, 8),
                        destinationSearchLayout.getHeight() + (int)MapView.pxFromDp(this, 16),
                        (int)MapView.pxFromDp(this, 8),
                        (int)MapView.pxFromDp(this, 8));
                relativeLayout.addView(searchResultsView, searchResultsViewParams);
                mapViewOn = false;
                selectingSourceLocation = false;
                selectingDestinationLocation = true;

                filterSearchResults(destinationSearchView.getQuery().toString());
            }
        });
        destinationSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterSearchResults(newText);
                searchResultViewHolder.notifyDataSetChanged();
                return false;
            }
        });

        if(startInfra != null && destinationInfra != null) {
            updatePath();
            mapView.setShowCoordNode(null);
            relativeLayout.addView(directionsLayout, directionsLayout.getLayoutParams());
            directionsButton.setOnClickListener((view) -> {
                relativeLayout.removeView(sourceSearchLayout);
                relativeLayout.removeView(destinationSearchLayout);
                relativeLayout.removeView(directionsLayout);
                relativeLayout.removeView(transportLayout);

                navigationLayout = (RelativeLayout) LayoutInflater.from(this).inflate(R.layout.navigation_prompt, relativeLayout);

                navigationTextView = navigationLayout.findViewById(R.id.promptView);
                currentPathIndex = 1;

                if(path.size() >= 2) {
                    currentPathIndex++;
                    setCurrentAction(currentPathIndex-1);
                    mapView.setHighlightNode(path.get(currentPathIndex-1));
                    navigationLayout.findViewById(R.id.prevBtn).setBackgroundColor(getResources().getColor(R.color.search_background_gray));
                }

                relativeLayout.findViewById(R.id.nextBtn).setOnClickListener((v) -> {
                    if(currentPathIndex <= path.size()-2) {
                        currentPathIndex++;
                        setCurrentAction(currentPathIndex-1);
                        mapView.setHighlightNode(path.get(currentPathIndex-1));
                        moveToFloor((int)path.get(currentPathIndex-1).getPosition().getZ());
                    }

                    if(currentPathIndex > path.size()-2) {
                        navigationLayout.findViewById(R.id.nextBtn).setBackgroundColor(getResources().getColor(R.color.search_background_gray));
                    }

                    if(currentPathIndex >= 2) {
                        navigationLayout.findViewById(R.id.prevBtn).setBackgroundColor(getResources().getColor(R.color.green));
                    }
                });

                relativeLayout.findViewById(R.id.prevBtn).setOnClickListener((v) -> {
                    if(currentPathIndex >= 2) {
                        currentPathIndex--;
                        setCurrentAction(currentPathIndex-1);
                        mapView.setHighlightNode(path.get(currentPathIndex-1));
                        moveToFloor((int)path.get(currentPathIndex-1).getPosition().getZ());
                    }

                    if(currentPathIndex < 2) {
                        navigationLayout.findViewById(R.id.prevBtn).setBackgroundColor(getResources().getColor(R.color.search_background_gray));
                    }

                    if(currentPathIndex <= path.size()-2) {
                        navigationLayout.findViewById(R.id.nextBtn).setBackgroundColor(getResources().getColor(R.color.green));
                    }
                });

                moveToFloor((int)path.get(0).getPosition().getZ());
            });
        }

        relativeLayout.addView(floorButtonsLayout, floorButtonsLayout.getLayoutParams());

        transportLayout = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.transport_selection, relativeLayout, false);
        RelativeLayout.LayoutParams transportParams = new RelativeLayout.LayoutParams(transportLayout.getLayoutParams());
        transportParams.addRule(RelativeLayout.BELOW, R.id.destinationSearchLayout);
        transportParams.setMargins(150, 0, 150, 0);
        relativeLayout.addView(transportLayout, transportParams);
        walkButton = transportLayout.findViewById(R.id.walkButton);
        wheelchairButton = transportLayout.findViewById(R.id.wheelChairButton);
        walkButton.setOnClickListener((v) -> {
            usingWheelChair = false;
            updateTransportButtons();
            updatePath();
        });
        wheelchairButton.setOnClickListener((v) -> {
            usingWheelChair = true;
            updateTransportButtons();
            updatePath();
        });
        updateTransportButtons();
    }

    private void setCurrentAction(int i) {
        String Goto="Goto ";
        if(i == path.size()-1) {
            setTextAndHighlightWithColor(Goto + destinationInfra.getName(), getResources().getColor(R.color.finish_red), Goto.length(), Goto.length()+destinationInfra.getName().length());
            mapView.setMiddleNode(null);
            return;
        }

        MapNode n1 = path.get(i);
        i++;
        MapNode n2 = path.get(i);

        String turnString = "";
        if(i > 1) {
            Vec3D dir1 = n1.getPosition().subtract(path.get(i-2).getPosition());
            Vec3D dir2 = n2.getPosition().subtract(n1.getPosition());

            double angle = Math.toDegrees(dir1.angle(dir2));
            if(Math.abs(angle) > 1) {
                turnString = "Turn " + (angle < 0? "Right by " : "Left by ") + (int)Math.abs(angle) + "° ";
            }
        }

        if(n1.getPosition().getZ() != n2.getPosition().getZ()) {
            FloorChanger fc = getFloorChangerInfra(n1, n2);

            String stairs = "Stairs (" + fc.getIndex() + ")";
            if(fc.getInfratype() == Infratype.StairsDown) {
                String cd = "Climb Down ";
                setTextAndHighlightWithColor(cd+stairs, getResources().getColor(R.color.middle_pin_color), 0, 0);
            }
            if(fc.getInfratype() == Infratype.StairsUp) {
                String cu = "Climb Up ";
                setTextAndHighlightWithColor(cu+stairs, getResources().getColor(R.color.middle_pin_color), 0, 0);
            }

            navigationTextView.setText("Take Lift (" + fc.getIndex() + ") to Floor: " + (int)fc.getOtherEnd().getZ());
            mapView.setMiddleNode(null);
            return;
        }

        Vec3D direction = n2.getPosition().subtract(n1.getPosition());
        Vec3D newDirection = n2.getPosition().subtract(n1.getPosition());

        String lastNonEmptyInfraInfo = getMostUniqueInfraInformation(n2);
        MapNode nonEmptyMiddleNode = n2;
        do {
            direction = newDirection;
            i++;
            if(i >= path.size()) break;

            n1 = n2;
            n2 = path.get(i);
            newDirection = n2.getPosition().subtract(n1.getPosition());

            String info = getMostUniqueInfraInformation(n1);
            if(!info.isEmpty()) {
                lastNonEmptyInfraInfo = info;
                nonEmptyMiddleNode = n1;
            }
        } while(direction.hasSameDirectionAs(newDirection));

        String infraInfo;
        if(direction.hasSameDirectionAs(newDirection)) {
            infraInfo = getMostUniqueInfraInformation(n2);
            mapView.setMiddleNode(n2);
        }
        else {
            infraInfo = getMostUniqueInfraInformation(n1);
            mapView.setMiddleNode(n1);
        }

        if(infraInfo.isEmpty()) {
            if(lastNonEmptyInfraInfo.isEmpty()) {
                String kg = "Keep Going";
                setTextAndHighlightWithColor(turnString+kg, getResources().getColor(R.color.middle_pin_color), turnString.length(), turnString.length()+kg.length());
                mapView.setMiddleNode(n1);
            }
            else {
                setTextAndHighlightWithColor(turnString+Goto+lastNonEmptyInfraInfo, getResources().getColor(R.color.middle_pin_color), turnString.length()+Goto.length(), turnString.length()+Goto.length()+lastNonEmptyInfraInfo.length());
                mapView.setMiddleNode(nonEmptyMiddleNode);
            }
            return;
        }

        setTextAndHighlightWithColor(turnString+Goto+infraInfo, getResources().getColor(R.color.middle_pin_color), turnString.length()+Goto.length(), turnString.length()+Goto.length()+infraInfo.length());
    }

    private void setTextAndHighlightWithColor(String str, int colorResource, int start, int end) {
        Spannable spannable = new SpannableString(str);

        spannable.setSpan(new ForegroundColorSpan(colorResource), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        navigationTextView.setText(spannable, TextView.BufferType.SPANNABLE);
    }

    private FloorChanger getFloorChangerInfra(MapNode n1, MapNode n2) {
        List<FloorChanger> floorChangerList1 = getFloorChangers(n1);
        List<FloorChanger> floorChangerList2 = getFloorChangers(n2);

        for(FloorChanger fc1 : floorChangerList1) {
            for(FloorChanger fc2 : floorChangerList2) {
                if(((fc1.getInfratype() == Infratype.StairsDown && fc2.getInfratype() == Infratype.StairsUp) ||
                    (fc1.getInfratype() == Infratype.StairsUp && fc2.getInfratype() == Infratype.StairsDown) ||
                    (fc1.getInfratype() == Infratype.LiftUp && fc2.getInfratype() == Infratype.LiftDown) ||
                    (fc1.getInfratype() == Infratype.LiftDown && fc2.getInfratype() == Infratype.LiftUp)) &&
                        fc1.getIndex() == fc2.getIndex() &&
                    fc1.getOtherEnd().equals(n2.getPosition())) return fc1;
            }
        }

        return null;
    }

    private List<FloorChanger> getFloorChangers(MapNode n) {
        ArrayList<FloorChanger> toRet = new ArrayList<>();

        for(Map<Integer, Map<Double, FloorChanger>> indexMap : floorChangerMap.values()) {
            for(Map<Double, FloorChanger> floorMap : indexMap.values()) {
                for(FloorChanger fc : floorMap.values()) {
                    if((usingWheelChair && !fc.isAccessible()) || fc.getPosition() != n.getPosition()) continue;

                    toRet.add(fc);
                }
            }
        }

        return toRet;
    }

    private String getMostUniqueInfraInformation(MapNode n) {
        List<Integer> infraIndexList = nodeToInfra.get(n);
        if(infraIndexList == null || infraIndexList.isEmpty()) return "";

        Infra infra = null;
        for(int i : infraIndexList) {
            if(infra == null) {
                infra = infraList.get(i);
                continue;
            }

            Infra inf = infraList.get(i);

            if(preferInfratypeTo(inf.getInfratype(), infra.getInfratype())) {
                infra = inf;
            }
        }

        return infra.getName();
    }

    private boolean preferInfratypeTo(Infratype t1, Infratype t2) {
        if(t1 == Infratype.Room) return true;
        if(t1 == Infratype.Washroom) {
            if(t2 == Infratype.Room) return false;
        }
        if(t1 == Infratype.DrinkingWater) {
            if(t2 == Infratype.Room) return false;
            if(t2 == Infratype.Washroom) return false;
        }
        if(t1 == Infratype.LiftDown || t1 == Infratype.LiftUp) {
            if(t2 == Infratype.Room) return false;
            if(t2 == Infratype.Washroom) return false;
            if(t2 == Infratype.DrinkingWater) return false;
        }

        if(t1 == Infratype.StairsDown || t1 == Infratype.StairsUp) {
            if(t2 == Infratype.Room) return false;
            if(t2 == Infratype.Washroom) return false;
            if(t2 == Infratype.DrinkingWater) return false;
            if(t2 == Infratype.LiftDown || t2 == Infratype.LiftUp) return false;
        }

        return true;
    }

    private void updatePath() {
        if(startInfra == null || destinationInfra == null) return;

        MapNode n1 = startInfra.getMapNode();
        MapNode n2 = destinationInfra.getMapNode();
        findPath(n1, n2, usingWheelChair);
        mapView.setPath(path);
        moveToFloor((int)path.get(0).getPosition().getZ());
    }

    private void updateTransportButtons() {
        walkButton.setEnabled(usingWheelChair);
        wheelchairButton.setEnabled(!usingWheelChair);
    }

    private void initializeFloorButtons() {
        floorButtonsLayout = findViewById(R.id.floorButtonsLayout);
        Integer[] floorArr = new Integer[floorSet.size()];
        floorSet.toArray(floorArr);
        Arrays.sort(floorArr);
        for(int floor : floorArr) {
            Button floorButton = (Button) LayoutInflater.from(this).inflate(R.layout.floor_button, null);
            floorButton.setText(String.valueOf(floor));
            floorButton.setOnClickListener((View view) -> {
                int btnFloor = Integer.parseInt(((Button) view).getText().toString());
                for(int i = 0; i < floorButtonsLayout.getChildCount(); i++) {
                    View v = floorButtonsLayout.getChildAt(i);
                    if(v instanceof Button) {
                        if(v == view) {
                            ((Button) v).setTextColor(getResources().getColor(R.color.white));
                        }
                        else {
                            ((Button) v).setTextColor(getResources().getColor(R.color.very_dark_gray));
                        }
                    }
                }
                moveToFloor(btnFloor);
            });

            currentFloor = 0;
            if(floor == 0) floorButton.setTextColor(getResources().getColor(R.color.white));

            floorButtonsLayout.addView(floorButton, 0);
            floorButton.setTextSize((int)MapView.pxFromDp(this, 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int)MapView.pxFromDp(this, 60), (int)MapView.pxFromDp(this, 60));
            params.setMargins(10, 10, 10, 20);
            floorButton.setLayoutParams(params);
        }

        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams((int)MapView.pxFromDp(this, 60), (int)MapView.pxFromDp(this, 40));
        arrowParams.setMargins(10, 0, 10, 0);

        Button upArrow = new Button(this);
        Button downArrow = new Button(this);

        upArrow.setBackground(getResources().getDrawable(R.drawable.ic_baseline_keyboard_arrow_up_24));
        downArrow.setBackground(getResources().getDrawable(R.drawable.ic_baseline_keyboard_arrow_down_24));

        upArrow.setOnClickListener((v) -> {
            if(currentFloor < maxFloor) moveToFloor(currentFloor+1);
            if(currentFloor == maxFloor) upArrow.setEnabled(false);
            if(currentFloor > minFloor) downArrow.setEnabled(true);
        });
        downArrow.setOnClickListener((v) -> {
            if(currentFloor > minFloor) moveToFloor(currentFloor-1);
            if(currentFloor == minFloor) downArrow.setEnabled(false);
            if(currentFloor < maxFloor) upArrow.setEnabled(true);
        });

        floorButtonsLayout.addView(upArrow, 0, arrowParams);
        floorButtonsLayout.addView(downArrow, arrowParams);

        // try to refresh floorButtonsLayout, invalidate() with/without requestLayout() doesn't work
        relativeLayout.removeView(floorButtonsLayout);
        relativeLayout.addView(floorButtonsLayout);
    }

    @Override
    public void onBackPressed() {
        if(lookingForDirections) {
            relativeLayout.removeAllViews();
            if(!selectingSourceLocation && !selectingDestinationLocation) {
                relativeLayout.addView(searchView);
                lookingForDirections = false;
                moveToMapView();
            }
            else {
                moveToDirections();
            }
        }
        else if(!searchFocus && !mapViewOn) {
            moveToMapView();
        }
    }

    public boolean isLookingForDirections() { return lookingForDirections; }

    public void setStartInfra(Infra startInfra) {
        this.startInfra = startInfra;
        moveToDirections();
    }

    public void setDestinationInfra(Infra destinationInfra) {
        this.destinationInfra = destinationInfra;
        moveToDirections();
    }

    public void focusLocation(Vec3D worldCoords) {
        moveToMapView();
        searchView.clearFocus();
        moveToFloor((int)worldCoords.getZ());
        mapView.centerWorldCoords(worldCoords);
    }

    public void focusNode(MapNode node) {
        moveToMapView();
        searchView.clearFocus();
        moveToFloor((int)node.getPosition().getZ());
        mapView.centerWorldCoords(node.getPosition());
        mapView.setShowCoordNode(node);
    }

    public void searchResult(Infra infra) {
        searchView.setQuery(infra.getName() + ", Floor: " + (int)infra.getPosition().getZ(), false);
        destinationInfra = searchViewInfra = infra;
        focusNode(infra.getMapNode());
    }

    private void moveToFloor(int floor) {
        System.out.println(floor);
        if(floor == 1) {
            mapView.setStartNode(nodeList.get(35));
            currentFloor = 1;
        }
        else if(floor == 0) {
            mapView.setStartNode(nodeList.get(0));
            currentFloor = 0;
        }

        for(int i = 0; i < floorButtonsLayout.getChildCount(); i++) {
            View v = floorButtonsLayout.getChildAt(i);
            if(v instanceof Button) {
                Button btn = (Button) v;
                if(btn.getText().equals(floor + "")) {
                    btn.setTextColor(getResources().getColor(R.color.white));
                }
                else {
                    btn.setTextColor(getResources().getColor(R.color.very_dark_gray));
                }
            }
        }
    }

    private void moveToMapView() {
        mapView.setHighlightNode(null);
        mapView.setMiddleNode(null);
        relativeLayout.addView(mapView);
        relativeLayout.addView(floorButtonsLayout);
        relativeLayout.removeView(searchResultsView);
        relativeLayout.addView(directionsLayout);
        relativeLayout.removeView(navigationLayout);
        directionsButton.setOnClickListener((View view) -> {
            moveToDirections();
        });
        path.clear();
        mapViewOn = true;
        bringHudToFront();
    }

    private void filterSearchResults(String filterText) {
        searchResults.clear();

        if(!filterText.isEmpty()) {
            for (Infra infra : infraList) {
                if ((infra.getName()+", Floor: " + (int)infra.getPosition().getZ()).toLowerCase(Locale.ROOT).contains(filterText.toLowerCase(Locale.ROOT))) {
                    searchResults.add(new SearchResult(infra, infra.getMapNode()));
                }
            }
        }
    }

    private void bringHudToFront() {
        relativeLayout.bringChildToFront(searchView);
        relativeLayout.bringChildToFront(floorButtonsLayout);
        relativeLayout.bringChildToFront(directionsLayout);
    }

    private class HeapNode implements Comparable<HeapNode> {
        double dist;
        MapNode node;

        public HeapNode(double dist, MapNode node) {
            this.dist = dist;
            this.node = node;
        }

        public int compareTo(HeapNode n) {
            if(dist < n.dist) return -1;
            if(dist > n.dist) return 1;
            return 0;
        }
    }

    private void setPath(int[] parents, int id) {
        if(id == -1) return;

        setPath(parents, parents[id]);
        path.add(nodeMap.get(nodeList.get(id).getPosition()));
        System.out.println("Id: " + id + "\t" + nodeList.get(id).getPosition());
    }

    private void findPath(MapNode n1, MapNode n2, boolean onWheelchair) {
        double[] dist = new double[nodeList.size()];
        PriorityQueue<HeapNode> pq = new PriorityQueue<>();
        pq.add(new HeapNode(0, n1));
        dist[n1.getId()] = 0;
        int[] parents = new int[nodeList.size()];

        for(int i = 0; i < dist.length; i++) {
            if(i != n1.getId()) {
                dist[i] = Double.POSITIVE_INFINITY;
                parents[i] = -1;
            }
        }

        parents[n1.getId()] = -1;

        while(!pq.isEmpty()) {
            HeapNode hn = pq.poll();
            int u = hn.node.getId();

//            System.out.println("Checking node: " + hn.node.getId() + "\t" + hn.node.getPosition() + "\t" + hn.dist);

            List<MapNode> nodes = graph.get(hn.node);
            for(MapNode node : nodes) {
//                System.out.println("Node: " + node.getId() + "\t" + node.getPosition());
//                System.out.println("U: " + dist[u] + "\tV: " + dist[node.getId()]);

                if(!transitionValid(hn.node, node, onWheelchair)) continue;

                double weight = hn.node.getPosition().dist(node.getPosition());
                int v = node.getId();
//                System.out.println(dist[u] + weight);
//                System.out.println(dist[v]);
//                System.out.println(dist[v] > dist[u] + weight);

                if(dist[v] > dist[u] + weight) {
                    dist[v] = dist[u] + weight;
                    pq.add(new HeapNode(weight, node));
                    parents[v] = u;
//                    System.out.println("Set parent of " + v + " to " + u);
                }
            }
        }

        path.clear();
        setPath(parents, n2.getId());

    }

    // Assumes the nodes are connected properly
    private boolean transitionValid(MapNode n1, MapNode n2, boolean onWheelchair) {
        if(!onWheelchair) return true;
        if(n1.getPosition().getZ() == n2.getPosition().getZ()) return true;

        List<Integer> l1 = nodeToInfra.get(n1);

//        System.out.println("Checking at " + n1.getId() + " and " + n2.getId());
        // n1 accessibility checked here
        Set<Infratype> floorChangerTypes1 = new HashSet<>();
        for(Integer i : l1) {
            Infra infra = infraList.get(i);
//            System.out.println(infra.getInfratype());
            if(infra instanceof FloorChanger && ((FloorChanger)infra).isAccessible()) {
                floorChangerTypes1.add(infra.getInfratype());
            }
        }

        double dz = n1.getPosition().getZ() - n2.getPosition().getZ();

//        System.out.println("Checking for dz: " + dz);
//        System.out.println(floorChangerTypes1.size());

        // n2 accessibility checked here
        for(Infratype t : floorChangerTypes1) {
            if(dz < 0) {
                if(t == Infratype.LiftUp && accessibleFloorChangerTypeExistsAtNode(Infratype.LiftDown, n2)) return true;
                if(t == Infratype.StairsUp && accessibleFloorChangerTypeExistsAtNode(Infratype.StairsDown, n2)) return true;
            }
            else {
                if(t == Infratype.LiftDown && accessibleFloorChangerTypeExistsAtNode(Infratype.LiftUp, n2)) return true;
                if(t == Infratype.StairsDown && accessibleFloorChangerTypeExistsAtNode(Infratype.StairsUp, n2)) return true;
            }
        }

        return false;
    }

    private boolean accessibleFloorChangerTypeExistsAtNode(Infratype infraType, MapNode n) {
//        System.out.println("Checking for " + infraType + " at " + n.getPosition());
        for(Integer infraId : Objects.requireNonNull(nodeToInfra.get(n))) {
            Infra infra = infraList.get(infraId);

            if(infra instanceof FloorChanger) {
                FloorChanger fc = (FloorChanger) infra;

                if(!fc.isAccessible()) continue;
                if(infra.getInfratype() == infraType) return true;
            }
        }

        return false;
    }

    private void addNodeToList(Vec3D pos) {
        if(!nodeMap.containsKey(pos)) {
            MapNode n = new MapNode(nodeList.size(), pos);
            nodeList.add(n);
            nodeMap.put(pos, n);
        }

        lastNode = nodeMap.get(pos);

        floorSet.add((int)pos.getZ());
    }

    private static String getRoomName(String[] params) {
        String str = "";
        for(int i = 1; i < params.length; i++) str += params[i] + " ";
        str = str.trim();
        return str;
    }

    private static Orientation getOrientation(char direction) {
        if(direction == 'U') return Orientation.Up;
        if(direction == 'L') return Orientation.Left;
        if(direction == 'D') return Orientation.Down;
        return Orientation.Right;
    }

    private static Vec3D moveVector(char direction, Vec3D curPos, double magnitude) {
        if(direction == 'U') curPos = curPos.add(new Vec3D(0, magnitude, 0));
        else if(direction == 'L') curPos = curPos.add(new Vec3D(-magnitude, 0, 0));
        else if(direction == 'D') curPos = curPos.add(new Vec3D(0, -magnitude, 0));
        else curPos = curPos.add(new Vec3D(magnitude, 0, 0));

        return curPos;
    }

    private Vec3D climbFloorChanger(String[] params, double currentFloor) {
        String direction = params[0];
        String floorChangerType = params[1];
        int floorChangerIndex = Integer.parseInt(params[2]);

        Infratype infratype = null;
        if(floorChangerType.equals("Stairs")) {
            if(direction.equals("Up")) infratype = Infratype.StairsUp;
            else if(direction.equals("Down")) infratype = Infratype.StairsDown;
        }
        else if(floorChangerType.equals("Lift")) {
            if(direction.equals("Up")) infratype = Infratype.LiftUp;
            else if(direction.equals("Down")) infratype = Infratype.LiftDown;
        }

        FloorChanger fc = floorChangerMap.get(infratype).get(floorChangerIndex).get(currentFloor);

//        System.out.println("CLIMBING");
//        System.out.println(fc);

        return fc.getOtherEnd();
    }

    private FloorChanger handleFloorChanger(String[] params, Orientation orientation, Vec3D position) {
        FloorChanger fc;

        String name = "";
        Infratype infratype = null;
        int index = Integer.parseInt(params[1]);
        boolean accessible;
        double xd, yd, zd = 0;

        if(params[0].equals("Stairs")) {
            if(params[2].equals("Up")) {
                infratype = Infratype.StairsUp;
                zd = 1;
            }
            else {
                infratype = Infratype.StairsDown;
                zd = -1;
            }
        }
        else if(params[0].equals("Lift")) {
            if(params[2].equals("Up")) {
                infratype = Infratype.LiftUp;
                zd = 1;
            }
            else {
                infratype = Infratype.LiftDown;
                zd = -1;
            }
        }

        name += params[0] + params[2] + params[1];

        accessible = !params[3].equals("NoRamp");
        xd = Double.parseDouble(params[4]);
        yd = Double.parseDouble(params[5]);
        Vec3D otherEnd = position.add(new Vec3D(xd, yd, zd));

        fc = new FloorChanger(name, infraList.size(), orientation, lastNode, position, otherEnd, infratype, accessible, index);
        infraList.add(fc);
        if(!floorChangerMap.containsKey(infratype)) floorChangerMap.put(infratype, new HashMap<>());
        Map<Integer, Map<Double, FloorChanger>> tmp = floorChangerMap.get(infratype);
        if(!tmp.containsKey(index)) tmp.put(index, new HashMap<>());
        tmp.get(index).put(fc.getPosition().getZ(), fc);

        return fc;
    }

    private void connectNodes(MapNode n1, MapNode n2) {
        if(!graph.containsKey(n1)) graph.put(n1, new ArrayList<>());
        if(!graph.containsKey(n2)) graph.put(n2, new ArrayList<>());

        graph.get(n1).add(n2);
        graph.get(n2).add(n1);
    }

    private void connectFloorChangers() {
        for(Infratype infratype : floorChangerMap.keySet()) {
            Map<Integer, Map<Double, FloorChanger>> indexFloorChangers = floorChangerMap.get(infratype);
            for(Integer index : indexFloorChangers.keySet()) {
                Map<Double, FloorChanger> floorFloorChangers = indexFloorChangers.get(index);
                for(Double z : floorFloorChangers.keySet()) {
                    FloorChanger fc = floorFloorChangers.get(z);

                    Map<Integer, Map<Double, FloorChanger>> otherIndexFloorChangers;
                    Map<Double, FloorChanger> otherFloorFloorChangers;

                    Infratype otherInfraType = null;
                    int otherIndex = index;
                    double otherFloor = fc.getPosition().getZ();

                    if(infratype == Infratype.LiftUp) {
                        otherInfraType = Infratype.LiftDown;
                        otherFloor += 1;
                    }
                    else if(infratype == Infratype.LiftDown) {
                        otherInfraType = Infratype.LiftUp;
                        otherFloor -= 1;
                    }
                    else if(infratype == Infratype.StairsUp) {
                        otherInfraType = Infratype.StairsDown;
                        otherFloor += 1;
                    }
                    else if(infratype == Infratype.StairsDown) {
                        otherInfraType = Infratype.StairsUp;
                        otherFloor -= 1;
                    }

                    otherIndexFloorChangers = floorChangerMap.get(otherInfraType);

                    if(otherIndexFloorChangers != null) {
                        otherFloorFloorChangers = otherIndexFloorChangers.get(otherIndex);
                        if(otherFloorFloorChangers != null) {
                            FloorChanger otherFc = otherFloorFloorChangers.get(otherFloor);

                            if(otherFc != null) {
                                connectNodes(fc.getMapNode(), otherFc.getMapNode());
                                System.out.println(fc);
                                System.out.println(otherFc);
                                assert(fc.getPosition().equals(otherFc.getOtherEnd()));
                                assert(otherFc.getPosition().equals(fc.getOtherEnd()));
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean isSelectingSourceLocation() { return selectingSourceLocation; }

    public boolean isSelectingDestinationLocation() { return selectingDestinationLocation; }
}
