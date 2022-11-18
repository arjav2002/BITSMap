package com.example.bitsmap;

public class MapNode {

    private int id;
    private Vec3D position;

    public MapNode(int id, Vec3D position) {
        this.id = id;
        this.position = position;
    }

    public int getId() { return id; }
    public Vec3D getPosition() { return position; }
}
