package com.example.bitsmap;

public class Infra {

    private String name;
    private Vec3D position;
    private Infratype infratype;
    private int id;
    private Orientation orientation;
    private MapNode mapNode;

    public Infra(String name, int id, Vec3D position, Infratype infratype, Orientation orientation, MapNode mapNode) {
        this.position = position;
        this.infratype = infratype;
        this.id = id;
        this.orientation = orientation;
        this.name = name;
        this.mapNode = mapNode;
    }

    public Vec3D getPosition() { return position; }
    public Infratype getInfratype() { return infratype; }
    public int getId() { return id; }
    public Orientation getOrientation() { return orientation; }
    public String getName() { return name; }
    public MapNode getMapNode() { return mapNode; }

}
