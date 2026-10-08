package com.thuvstu.hayatemod.core.engine;

/** Pure 3D vector. Core never touches Minecraft classes. */
public record Vec3(double x, double y, double z) {
    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x(), y + o.y(), z + o.z());
    }

    public Vec3 scale(double f) {
        return new Vec3(x * f, y * f, z * f);
    }

    public double distanceTo(Vec3 o) {
        double dx = x - o.x();
        double dy = y - o.y();
        double dz = z - o.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public Vec3 normalize() {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-9) {
            return new Vec3(0.0, 0.0, 0.0);
        }
        return new Vec3(x / len, y / len, z / len);
    }

    public double dot(Vec3 o) {
        return x * o.x() + y * o.y() + z * o.z();
    }
}
