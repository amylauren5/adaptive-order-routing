package ict.um.orders.workload;

import java.util.List;
import java.util.Random;

public final class OlistSampling {

    private static Random random = new Random(42);

    // Compresses Olist-derived lifecycle durations.
    private static double timeScale = 0.0001;

    // Controls baseline arrival intensity without changing lifecycle timing.
    // Larger values mean slower arrivals; smaller values mean faster arrivals.
    private static double arrivalScale = 1.0;

    private static final double CANCELLATION_RATE =
            0.006285133898;

    private static final List<String> CANCELLATION_REASONS = List.of(
            "customer_request",
            "payment_issue",
            "inventory_unavailable",
            "fraud_suspected"
    );

    public static String sampleCancellationReason() {
        return CANCELLATION_REASONS.get(
                random.nextInt(CANCELLATION_REASONS.size())
        );
    }

    private static final List<WeightedValue<Long>>
            INTER_ARRIVAL_DISTRIBUTION = List.of(
            seconds(12, 9949),
            seconds(46, 9871),
            seconds(85, 9960),
            seconds(132, 9834),
            seconds(190, 9860),
            seconds(264, 9909),
            seconds(364, 9865),
            seconds(510, 9880),
            seconds(770, 9867),
            seconds(1650, 9879)
    );

    private static final List<WeightedValue<Long>>
            APPROVAL_DELAY_DISTRIBUTION = List.of(
            seconds(544, 9857),
            seconds(680, 9777),
            seconds(786, 9834),
            seconds(910, 9742),
            seconds(1106, 9789),
            seconds(1529, 9797),
            seconds(4323, 9793),
            seconds(53863, 9799),
            seconds(93212, 9798),
            seconds(175667, 9799)
    );

    private static final List<WeightedValue<Long>>
            DISPATCH_DELAY_DISTRIBUTION = List.of(
            seconds(27891, 9629),
            seconds(56438, 9628),
            seconds(77893, 9629),
            seconds(99748, 9628),
            seconds(137849, 9629),
            seconds(180655, 9628),
            seconds(242443, 9629),
            seconds(313122, 9628),
            seconds(427794, 9628),
            seconds(705120, 9629)
    );

    private static final List<WeightedValue<Long>>
            DELIVERY_DELAY_DISTRIBUTION = List.of(
            seconds(97190, 9645),
            seconds(244660, 9644),
            seconds(354486, 9645),
            seconds(469305, 9643),
            seconds(579011, 9645),
            seconds(672492, 9644),
            seconds(804826, 9644),
            seconds(1039468, 9644),
            seconds(1311122, 9644),
            seconds(2091890, 9645)
    );

    private static final List<WeightedValue<Double>>
            ORDER_VALUE_DISTRIBUTION = List.of(
            weighted(19.0, 9903),
            weighted(30.0, 9969),
            weighted(45.95, 9782),
            weighted(59.7, 9848),
            weighted(76.89, 9907),
            weighted(98.0, 9828),
            weighted(119.9, 9860),
            weighted(149.9, 9837),
            weighted(205.0, 9909),
            weighted(399.9, 9823)
    );

    private static final List<WeightedValue<Integer>>
            ITEM_COUNT_DISTRIBUTION = List.of(
            weighted(1, 88863),
            weighted(2, 7516),
            weighted(3, 1322),
            weighted(4, 505),
            weighted(5, 204),
            weighted(6, 198),
            weighted(7, 22),
            weighted(8, 8),
            weighted(9, 3),
            weighted(10, 8),
            weighted(11, 4),
            weighted(12, 5),
            weighted(13, 1),
            weighted(14, 2),
            weighted(15, 2),
            weighted(20, 2),
            weighted(21, 1)
    );

    private static final List<WeightedValue<String>>
            CATEGORY_DISTRIBUTION = List.of(
            weighted("bed_bath_table", 11115),
            weighted("health_beauty", 9670),
            weighted("sports_leisure", 8641),
            weighted("furniture_decor", 8334),
            weighted("computers_accessories", 7827),
            weighted("housewares", 6964),
            weighted("watches_gifts", 5991),
            weighted("telephony", 4545),
            weighted("garden_tools", 4347),
            weighted("auto", 4235),
            weighted("other", 39354)
    );

    private OlistSampling() {
    }

    public static void setSeed(long seed) {
        random = new Random(seed);
    }

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

    public static long sampleInterArrival() {
        return sampleInterArrival(1.0);
    }

    public static long sampleInterArrival(
            double interArrivalMultiplier
    ) {
        if (!Double.isFinite(interArrivalMultiplier)
                || interArrivalMultiplier <= 0.0) {
            throw new IllegalArgumentException(
                    "Inter-arrival multiplier must be finite "
                            + "and greater than zero."
            );
        }

        long empiricalMillis =
                sampleWeighted(INTER_ARRIVAL_DISTRIBUTION);

        return Math.max(
                1L,
                Math.round(
                        empiricalMillis
                                * timeScale
                                * arrivalScale
                                * interArrivalMultiplier
                )
        );
    }

    public static long sampleApprovalDelay() {
        return scale(
                sampleWeighted(APPROVAL_DELAY_DISTRIBUTION)
        );
    }

    public static long sampleDispatchDelay() {
        return scale(
                sampleWeighted(DISPATCH_DELAY_DISTRIBUTION)
        );
    }

    public static long sampleDeliveryDelay() {
        return scale(
                sampleWeighted(DELIVERY_DELAY_DISTRIBUTION)
        );
    }

    public static double sampleOrderValue() {
        return sampleWeighted(ORDER_VALUE_DISTRIBUTION);
    }

    public static int sampleItemCount() {
        return sampleWeighted(ITEM_COUNT_DISTRIBUTION);
    }

    public static String sampleCategory() {
        return sampleWeighted(CATEGORY_DISTRIBUTION);
    }

    public static boolean sampleCancellation() {
        return random.nextDouble() < CANCELLATION_RATE;
    }

    private static long scale(long durationMillis) {
        return Math.max(
                1L,
                Math.round(durationMillis * timeScale)
        );
    }

    private static <T> T sampleWeighted(
            List<WeightedValue<T>> distribution
    ) {
        if (distribution.isEmpty()) {
            throw new IllegalArgumentException(
                    "Distribution must not be empty."
            );
        }

        long totalWeight = distribution.stream()
                .mapToLong(WeightedValue::weight)
                .sum();

        long draw = random.nextLong(totalWeight);

        long cumulativeWeight = 0L;

        for (WeightedValue<T> entry : distribution) {
            cumulativeWeight += entry.weight();

            if (draw < cumulativeWeight) {
                return entry.value();
            }
        }

        throw new IllegalStateException(
                "Weighted sampling failed."
        );
    }

    private static WeightedValue<Long> seconds(
            long seconds,
            long weight
    ) {
        return weighted(
                Math.multiplyExact(seconds, 1000L),
                weight
        );
    }

    private static <T> WeightedValue<T> weighted(
            T value,
            long weight
    ) {
        return new WeightedValue<>(value, weight);
    }

    private record WeightedValue<T>(
            T value,
            long weight
    ) {
        private WeightedValue {
            if (value == null) {
                throw new IllegalArgumentException(
                        "Weighted value must not be null."
                );
            }

            if (weight <= 0L) {
                throw new IllegalArgumentException(
                        "Weight must be greater than zero."
                );
            }
        }
    }
}