package com.example.bitsmap;

public class Vec3D implements Comparable<Vec3D> {

    private double x, y, z;

    public Vec3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    public Vec3D add(Vec3D v) { return new Vec3D(x+v.x, y+v.y, z+v.z); }
    public Vec3D subtract(Vec3D v) { return new Vec3D(x-v.x, y-v.y, z-v.z); }
    public Vec3D clone() { return new Vec3D(x, y, z); }
    public String toString() { return "X: " + x + "\tY: " + y + "\tZ: " + z; }
    public boolean equals(Vec3D v) { return x==v.x && y==v.y && z==v.z; }
    public double distSq(Vec3D v) { return Math.pow(x-v.x, 2) + Math.pow(y-v.y, 2) + Math.pow(z-v.z, 2); }
    public int compareTo(Vec3D v) {
        if(x == v.x) {
            if(y == v.y) {
                if(z == v.z) return 0;
                if(z < v.z) return -1;
                return 1;
            }
            if(y < v.y) return -1;
            return 1;
        }
        if(x < v.x) return -1;
        return 1;
    }

}
