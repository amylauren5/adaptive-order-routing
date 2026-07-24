package ict.um.orders.workload;

import java.util.List;
import java.util.Random;

public class OlistSampling {

    // Single PRNG for reproducible sampling
    private static Random random = new Random(42);

    // Allow experiments to set a seed explicitly
    public static void setSeed(long seed) {
        random = new Random(seed);
    }

    // Expose the PRNG for workload generator
    public static Random random() {
        return random;
    }

    // Inter-arrival times (seconds → ms)
    private static final List<Long> INTER_ARRIVAL = List.of(
            83_000L, 222_000L, 507_000L
    );

    // Approval delay
    private static final List<Long> APPROVAL_DELAY = List.of(
            774_000L, 1_236_000L, 52_491_000L
    );

    // Dispatch delay
    private static final List<Long> DISPATCH_DELAY = List.of(
            75_644_000L, 157_109_500L, 309_352_500L
    );

    // Delivery delay
    private static final List<Long> DELIVERY_DELAY = List.of(
            354_235_500L, 613_420_000L, 1_039_315_500L
    );

    // Order value
    private static final List<Double> ORDER_VALUE = List.of(
            62.01, 105.29, 176.97
    );

    // Top categories
    private static final List<String> CATEGORIES = List.of(
            "bed_bath_table", "health_beauty", "sports_leisure",
            "furniture_decor", "computers_accessories", "housewares",
            "watches_gifts", "telephony", "garden_tools", "auto"
    );

    private static <T> T sample(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    public static long sampleInterArrival() {
        return sample(INTER_ARRIVAL);
    }

    public static long sampleApprovalDelay() {
        return sample(APPROVAL_DELAY);
    }

    public static long sampleDispatchDelay() {
        return sample(DISPATCH_DELAY);
    }

    public static long sampleDeliveryDelay() {
        return sample(DELIVERY_DELAY);
    }

    public static double sampleOrderValue() {
        return sample(ORDER_VALUE);
    }

    public static String sampleCategory() {
        return sample(CATEGORIES);
    }
}
