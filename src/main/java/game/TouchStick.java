package game;

/** Browser-independent radial touch-stick math in CSS screen coordinates. */
public final class TouchStick {
    public static final double DEAD_ZONE = 0.12;

    private boolean active;
    private double x, y, knobX, knobY;

    /**
     * Updates a held pointer's displacement from the pad center. The radial dead
     * zone is remapped continuously to preserve the full remaining analog range.
     */
    public void set(double dx, double dy, double radius) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)
                || !Double.isFinite(radius) || radius <= 0) {
            release();
            return;
        }
        double distance = Math.hypot(dx, dy);
        if (!Double.isFinite(distance)) {
            release();
            return;
        }
        active = true;
        if (distance == 0) {
            x = y = knobX = knobY = 0;
            return;
        }
        double magnitude = Math.min(1, distance / radius);
        knobX = dx / distance * magnitude;
        knobY = dy / distance * magnitude;
        double output = Math.max(0, (magnitude - DEAD_ZONE) / (1 - DEAD_ZONE));
        x = dx / distance * output;
        y = dy / distance * output;
    }

    /** Call for pointer up/cancel, focus loss, and gameplay/orientation changes. */
    public void release() {
        active = false;
        x = y = knobX = knobY = 0;
    }

    public boolean active() { return active; }
    public double x() { return x; }
    public double y() { return y; }

    /** Inverse of the clockwise canvas rotation used by the portrait renderer. */
    public double worldX(boolean portrait) { return portrait ? y : x; }
    public double worldY(boolean portrait) { return portrait ? -x : y; }

    /** Raw clamped pad position, normalized to its radius, before the dead zone. */
    public double knobX() { return knobX; }
    public double knobY() { return knobY; }
}
