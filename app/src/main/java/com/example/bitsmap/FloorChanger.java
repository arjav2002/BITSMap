package com.example.bitsmap;

public class FloorChanger extends Infra {

    private Vec3D otherEnd;
    private boolean accessible;
    private int index;

    public FloorChanger(String name, int id, Orientation orientation, MapNode mapNode, Vec3D position, Vec3D otherEnd, Infratype infratype, boolean accessible, int index) {
        super(name, id, position, infratype, orientation, mapNode);
        this.otherEnd = otherEnd;
        this.index = index;
        this.accessible = accessible;
    }

    public Vec3D getOtherEnd() { return otherEnd; }
    public boolean isAccessible() { return accessible; }
    public int getIndex() { return index; }
    public String toString() { return "NodeId: " + getMapNode().getId() + "\tPosition: " + getPosition() + "\tType: " + getInfratype() + "\tIndex: " + getIndex() + "\totherPos: " + getOtherEnd() + "\tAcc: " + isAccessible(); }
}
