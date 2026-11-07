package factorycraft.simulation.power;

/** Integer world coordinates; negative coordinates use floor-division in the spatial index. */
public record PowerPoint(int x,int y,int z) {
    public boolean within(PowerPoint other,int radius) {
        long dx=(long)x-other.x,dy=(long)y-other.y,dz=(long)z-other.z;
        return dx*dx+dy*dy+dz*dz<=(long)radius*radius;
    }
}
