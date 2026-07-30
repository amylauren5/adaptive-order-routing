package ict.um.orders.workload;

import java.util.List;
import java.util.Random;

public final class OlistSampling {

    // Single PRNG for reproducible sampling
    private static Random random = new Random(42);

    // Compresses Olist-derived lifecycle durations
    private static double timeScale = 0.0001;

    // Controls workload intensity without changing lifecycle timing
    // Larger values mean slower arrivals; smaller values mean faster arrivals
    private static double arrivalScale = 1.0;

    // Inter-arrival times (seconds → ms)
    private static final List<Long> INTER_ARRIVAL = List.of(
            83_000L,
            222_000L,
            507_000L
    );

    // Approval delay
    private static final List<Long> APPROVAL_DELAY = List.of(
            774_000L,
            1_236_000L,
            52_491_000L
    );

    // Dispatch delay
    private static final List<Long> DISPATCH_DELAY = List.of(
            77_893_000L,
            159_984_000L,
            313_118_000L
    );

    // Delivery delay
    private static final List<Long> DELIVERY_DELAY = List.of(
            354_372_000L,
            613_467_000L,
            1_039_430_000L
    );

    // Order value
    private static final List<Double> ORDER_VALUES = List.of(
            62.01,
            105.29,
            176.97
    );

    // Top categories
    private static final List<String> CATEGORIES = List.of(
            "bed_bath_table",
            "health_beauty",
            "sports_leisure",
            "furniture_decor",
            "computers_accessories",
            "housewares",
            "watches_gifts",
            "telephony",
            "garden_tools",
            "auto"
    );

    private OlistSampling() {

    }

    // Allow experiments to set a seed explicitly
    public static void setSeed(long seed) {
        random = new Random(seed);
    }

    // Allow experiments to adjust time compression
    public static void setTimeScale(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException(
                    "Time scale must be finite and greater than zero."
            );
        }

        timeScale = scale;
    }

    public static void setArrivalScale(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException(
                    "Arrival scale must be finite and greater than zero."
            );
        }

        arrivalScale = scale;
    }

    // Expose the PRNG for workload generator
    public static Random random() {
        return random;
    }

    public static long sampleInterArrival() {
        long empiricalDelay = sample(INTER_ARRIVAL);

        return Math.max(
                1L,
                Math.round(empiricalDelay * timeScale * arrivalScale)
        );
    }

    public static long sampleApprovalDelay() {
        return scale(sample(APPROVAL_DELAY));
    }

    public static long sampleDispatchDelay() {
        return scale(sample(DISPATCH_DELAY));
    }

    public static long sampleDeliveryDelay() {
        return scale(sample(DELIVERY_DELAY));
    }

    public static double sampleOrderValue() {
        return sample(ORDER_VALUES);
    }

    public static String sampleCategory() {
        return sample(CATEGORIES);
    }

    // Private helper methods

    private static <T> T sample(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    private static long scale(long durationMillis) {
        return Math.max(1L, Math.round(durationMillis * timeScale));
    }
}