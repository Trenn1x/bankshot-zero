package game;

/** Run with java game.TouchStickTest. Verifies the shipped touch-control math. */
public final class TouchStickTest {
    private static int assertions;

    public static void main(String[] args) {
        deadZoneAndAnalogRange();
        radialClamping();
        orientationMapping();
        releaseAndIndependentPointers();
        invalidInput();
        System.out.println("TouchStickTest: " + assertions + " assertions passed.");
    }

    private static void deadZoneAndAnalogRange() {
        TouchStick stick = new TouchStick();
        check(!stick.active(), "new stick has no held pointer");
        stick.set(0, 0, 50);
        check(stick.active(), "touching the center holds the pad");
        near(stick.x(), 0, "center has no horizontal input");
        near(stick.y(), 0, "center has no vertical input");
        stick.set(3, 4, 50);
        near(stick.x(), 0, "radial dead zone ignores thumb jitter x");
        near(stick.y(), 0, "radial dead zone ignores thumb jitter y");
        near(stick.knobX(), .06, "knob still follows motion inside dead zone x");
        near(stick.knobY(), .08, "knob still follows motion inside dead zone y");
        stick.set(6, 0, 50);
        near(stick.x(), 0, "dead-zone boundary stays still");
        stick.set(6.000001, 0, 50);
        check(stick.x() > 0 && stick.x() < .000001,
                "motion begins continuously above dead zone");
        stick.set(28, 0, 50);
        near(stick.x(), .5, "remaining travel maps to analog half speed");
        stick.set(50, 0, 50);
        near(stick.x(), 1, "full travel reaches full speed");
        // Browser zoom and differently sized pads preserve the same input.
        stick.set(56, 0, 100);
        near(stick.x(), .5, "CSS radius sets the pad's input scale");
    }

    private static void radialClamping() {
        TouchStick stick = new TouchStick();
        stick.set(60, 80, 50);
        near(stick.x(), .6, "oversized drag keeps its horizontal direction");
        near(stick.y(), .8, "oversized drag keeps its vertical direction");
        near(Math.hypot(stick.x(), stick.y()), 1, "radial clamping caps diagonal speed");
        near(stick.knobX(), .6, "knob stays on the pad circumference x");
        near(stick.knobY(), .8, "knob stays on the pad circumference y");
        stick.set(-50, -50, 50);
        near(stick.x(), -Math.sqrt(.5), "negative diagonal keeps its direction x");
        near(stick.y(), -Math.sqrt(.5), "negative diagonal keeps its direction y");
        stick.set(15, 20, 50);
        near(stick.x() / stick.y(), .75, "analog dead-zone remapping keeps aim angle");
        near(Math.hypot(stick.x(), stick.y()), (.5 - TouchStick.DEAD_ZONE)
                / (1 - TouchStick.DEAD_ZONE), "analog diagonal uses radial magnitude");
    }

    private static void orientationMapping() {
        TouchStick stick = new TouchStick();
        double[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (double[] direction : directions) {
            stick.set(direction[0] * 50, direction[1] * 50, 50);
            near(stick.worldX(false), direction[0], "landscape retains screen x");
            near(stick.worldY(false), direction[1], "landscape retains screen y");
            near(stick.worldX(true), direction[1], "portrait inverse rotation maps world x");
            near(stick.worldY(true), -direction[0], "portrait inverse rotation maps world y");
            // Rendering a world vector clockwise must recover the thumb direction.
            near(-stick.worldY(true), direction[0], "portrait motion follows thumb on screen x");
            near(stick.worldX(true), direction[1], "portrait motion follows thumb on screen y");
        }
    }

    private static void releaseAndIndependentPointers() {
        TouchStick movement = new TouchStick();
        TouchStick aim = new TouchStick();
        movement.set(50, 0, 50);
        aim.set(0, -50, 50);
        movement.release();
        check(!movement.active(), "pointer release clears held state");
        near(movement.x(), 0, "release clears horizontal movement");
        near(movement.y(), 0, "release clears vertical movement");
        near(movement.knobX(), 0, "release centers knob x");
        near(movement.knobY(), 0, "release centers knob y");
        check(aim.active(), "releasing movement leaves the aiming pointer held");
        near(aim.y(), -1, "releasing movement preserves aim");
        movement.set(-50, 0, 50);
        check(movement.active(), "a fresh pointer can hold the released pad");
        near(movement.x(), -1, "a fresh touch does not retain old direction");
        movement.release();
        movement.release();
        check(!movement.active(), "duplicate cancel/release remains safe");
    }

    private static void invalidInput() {
        TouchStick stick = new TouchStick();
        double[][] invalid = {{Double.NaN, 0, 50}, {0, Double.NaN, 50},
                {Double.POSITIVE_INFINITY, 0, 50}, {0, Double.NEGATIVE_INFINITY, 50},
                {0, 0, 0}, {0, 0, -1}, {0, 0, Double.NaN},
                {0, 0, Double.POSITIVE_INFINITY}};
        for (double[] input : invalid) {
            stick.set(50, 0, 50);
            stick.set(input[0], input[1], input[2]);
            check(!stick.active(), "invalid pointer geometry releases the pad");
            near(stick.x(), 0, "invalid geometry cannot leave stuck horizontal input");
            near(stick.y(), 0, "invalid geometry cannot leave stuck vertical input");
        }
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) <= .00000001,
                message + " (" + actual + " vs " + expected + ")");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
