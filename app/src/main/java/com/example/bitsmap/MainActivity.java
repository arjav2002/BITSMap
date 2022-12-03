package com.example.bitsmap;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

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

    private LinearLayout floorButtonsLayout;
    private Set<Integer> floorSet;

    private SearchResultViewHolder searchResultViewHolder;

    private Button directionsButton;

    private boolean mapViewOn;
    private boolean searchFocus;
    private boolean lookingForDirections;
    private boolean selectingSourceLocation;
    private boolean selectingDestinationLocation;
    private boolean pathFound;

    public Infra startInfra;
    public Infra destinationInfra;
    private ArrayList<MapNode> path;

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
        startInfra = destinationInfra = null;
        pathFound = true;

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

        // initializing our view.
        relativeLayout = findViewById(R.id.idRLView);
        mapView = new MapView(this, graph, nodeToInfra, nodeList.get(0), infraList);
        relativeLayout.addView(mapView);
        mapViewOn = true;

        searchView = findViewById(R.id.searchView);
        searchResultsView = findViewById(R.id.searchResultsRecycler);
        relativeLayout.removeView(searchResultsView);
        bringHudToFront();

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
                relativeLayout.removeView(directionsButton);
                relativeLayout.addView(searchResultsView);
                mapViewOn = false;

                filterSearchResults("");
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
    }

    private void moveToDirections() {
        lookingForDirections = true;
        selectingSourceLocation = selectingDestinationLocation = false;
        relativeLayout.removeAllViews();
        relativeLayout.addView(mapView);

        SearchView sourceSearchView = (SearchView) LayoutInflater.from(this).inflate(R.layout.source_search_bar, relativeLayout, false);
        SearchView destinationSearchView = (SearchView)  LayoutInflater.from(this).inflate(R.layout.destination_search_bar, relativeLayout, false);
        relativeLayout.addView(sourceSearchView, sourceSearchView.getLayoutParams());

        if(startInfra != null) {
            sourceSearchView.setQuery(startInfra.getName() + ", Floor: " + (int)startInfra.getPosition().getZ(), false);
        }

        if(destinationInfra != null) {
            destinationSearchView.setQuery(destinationInfra.getName() + ", Floor: " + (int)destinationInfra.getPosition().getZ(), false);
        }

        ViewGroup.LayoutParams params = destinationSearchView.getLayoutParams();
        RelativeLayout.LayoutParams destinationParams = new RelativeLayout.LayoutParams(params);

        sourceSearchView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        sourceSearchView.layout(0, 0, sourceSearchView.getMeasuredWidth(), sourceSearchView.getMeasuredHeight());
        destinationParams.setMargins((int)MapView.pxFromDp(this, 8),
                8 + sourceSearchView.getHeight() + 24,
                (int)MapView.pxFromDp(this, 8),
                (int)MapView.pxFromDp(this, 8));
        relativeLayout.addView(destinationSearchView, destinationParams);

        sourceSearchView.setOnQueryTextFocusChangeListener ((View v, boolean hasFocus) -> {
            searchFocus = hasFocus;

            if(hasFocus && lookingForDirections) {
                relativeLayout.removeView(mapView);
                relativeLayout.removeView(floorButtonsLayout);
                relativeLayout.removeView(destinationSearchView);

                RelativeLayout.LayoutParams searchResultsViewParams = new RelativeLayout.LayoutParams(searchResultsView.getLayoutParams());
                searchResultsViewParams.setMargins((int)MapView.pxFromDp(this, 8),
                        8 + sourceSearchView.getHeight() + 24,
                        (int)MapView.pxFromDp(this, 8),
                        (int)MapView.pxFromDp(this, 8));
                relativeLayout.addView(searchResultsView, searchResultsViewParams);
                mapViewOn = false;
                selectingSourceLocation = true;
                selectingDestinationLocation = false;

                filterSearchResults("");
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
                relativeLayout.removeView(sourceSearchView);

                destinationParams.topMargin = (int) MapView.pxFromDp(this, 8);
                destinationSearchView.setLayoutParams(destinationParams);

                RelativeLayout.LayoutParams searchResultsViewParams = new RelativeLayout.LayoutParams(searchResultsView.getLayoutParams());
                searchResultsViewParams.setMargins((int)MapView.pxFromDp(this, 8),
                        destinationSearchView.getHeight() + (int)MapView.pxFromDp(this, 16),
                        (int)MapView.pxFromDp(this, 8),
                        (int)MapView.pxFromDp(this, 8));
                relativeLayout.addView(searchResultsView, searchResultsViewParams);
                mapViewOn = false;
                selectingSourceLocation = false;
                selectingDestinationLocation = true;

                filterSearchResults("");
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
            MapNode n1 = startInfra.getMapNode();
            MapNode n2 = destinationInfra.getMapNode();
            findPath(n1, n2, false);
            mapView.setPath(path);

            relativeLayout.addView(directionsButton, directionsButton.getLayoutParams());
            directionsButton.setOnClickListener((view) -> {

            });
        }

        relativeLayout.addView(floorButtonsLayout, floorButtonsLayout.getLayoutParams());
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
                            ((Button) v).setTextColor(getResources().getColor(R.color.red));
                        }
                        else {
                            ((Button) v).setTextColor(getResources().getColor(R.color.white));
                        }
                    }
                }
                moveToFloor(btnFloor);
            });

            if(floor == 0) floorButton.setTextColor(getResources().getColor(R.color.red));

            floorButtonsLayout.addView(floorButton, 0);
            floorButton.setTextSize((int)MapView.pxFromDp(this, 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int)MapView.pxFromDp(this, 60), (int)MapView.pxFromDp(this, 60));
            params.setMargins(10, 10, 10, 10);
            floorButton.setLayoutParams(params);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int)MapView.pxFromDp(this, 60), (int)MapView.pxFromDp(this, 60));
        params.setMargins(10, 0, 10, 10);
        floorButtonsLayout.getChildAt(0).setLayoutParams(params);
        params.setMargins(10, 10, 10, 0);
        floorButtonsLayout.getChildAt(floorButtonsLayout.getChildCount()-1).setLayoutParams(params);

        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams((int)MapView.pxFromDp(this, 40), (int)MapView.pxFromDp(this, 40));
        arrowParams.setMargins(0, 0, 0, 0);

        ImageView upArrow = new ImageView(this);
        upArrow.setImageDrawable(getResources().getDrawable(R.drawable.ic_baseline_keyboard_arrow_up_24));
        floorButtonsLayout.addView(upArrow, 0, arrowParams);

        ImageView downArrow = new ImageView(this);
        downArrow.setImageDrawable(getResources().getDrawable(R.drawable.ic_baseline_keyboard_arrow_down_24));
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

    public void focusInfra(Infra infra) {
        searchView.setQuery(infra.getName() + ", Floor: " + (int)infra.getPosition().getZ(), false);
        focusNode(infra.getMapNode());
    }

    private void moveToFloor(int floor) {
        if(floor == 1)
            mapView.setStartNode(nodeList.get(35));
        else if(floor == 0)
            mapView.setStartNode(nodeList.get(0));
    }

    private void moveToMapView() {
        relativeLayout.addView(mapView);
        relativeLayout.addView(floorButtonsLayout);
        relativeLayout.removeView(searchResultsView);
        relativeLayout.addView(directionsButton);
        mapViewOn = true;
        bringHudToFront();
    }

    private void filterSearchResults(String filterText) {
        searchResults.clear();

        if(!filterText.isEmpty()) {
            for (Infra infra : infraList) {
                if (infra.getName().toLowerCase(Locale.ROOT).contains(filterText.toLowerCase(Locale.ROOT))) {
                    searchResults.add(new SearchResult(infra, infra.getMapNode()));
                }
            }
        }
    }

    private void bringHudToFront() {
        relativeLayout.bringChildToFront(searchView);
        relativeLayout.bringChildToFront(floorButtonsLayout);
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
