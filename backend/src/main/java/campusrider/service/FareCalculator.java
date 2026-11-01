package campusrider.service;

/**
 * Campus rickshaw fare in Bangladeshi Taka.
 *
 *   fare = max(MIN_FARE, roundUpTo5(BASE + PER_KM * km)) * seats
 *   shared rides get SHARED_DISCOUNT off (rounded up to 5 Tk again)
 */
public final class FareCalculator {
    public static final int BASE_FARE = 10;
    public static final int PER_KM = 15;
    public static final int MIN_FARE = 15;
    public static final double SHARED_DISCOUNT = 0.20;

    private FareCalculator() {
    }

    public static int estimate(double distanceMeters, int seats, boolean shared) {
        double perSeat = Math.max(MIN_FARE, roundUpTo5(BASE_FARE + PER_KM * distanceMeters / 1000.0));
        double total = perSeat * Math.max(1, seats);
        if (shared) {
            total = roundUpTo5(total * (1.0 - SHARED_DISCOUNT));
        }
        return (int) total;
    }

    private static double roundUpTo5(double value) {
        return Math.ceil(value / 5.0) * 5.0;
    }
}
