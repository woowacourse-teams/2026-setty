package setty.performance;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;

import io.gatling.javaapi.core.OpenInjectionStep;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class RegistrationSchedule {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final List<Duration> arrivals;

    private RegistrationSchedule(List<Duration> arrivals) {
        this.arrivals = List.copyOf(arrivals);
    }

    static RegistrationSchedule none() {
        return new RegistrationSchedule(List.of());
    }

    static RegistrationSchedule random(double perMinute, Duration duration, long seed) {
        if (perMinute <= 0 || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("RATE_PER_MINUTE and RATE_DURATION_MINUTES must be positive");
        }
        Random random = new Random(seed);
        double meanGapMillis = ONE_MINUTE.toMillis() / perMinute;
        List<Duration> arrivals = new ArrayList<>();
        Duration at = nextGap(random, meanGapMillis);
        while (at.compareTo(duration) < 0) {
            arrivals.add(at);
            at = at.plus(nextGap(random, meanGapMillis));
        }
        return new RegistrationSchedule(arrivals);
    }

    private static Duration nextGap(Random random, double meanGapMillis) {
        return Duration.ofMillis(Math.round(-Math.log(1 - random.nextDouble()) * meanGapMillis));
    }

    int size() {
        return arrivals.size();
    }

    List<Long> firstOffsetsMillis(int count) {
        return arrivals.stream().limit(count).map(Duration::toMillis).toList();
    }

    List<OpenInjectionStep> injectionSteps() {
        List<OpenInjectionStep> steps = new ArrayList<>();
        Duration previous = Duration.ZERO;
        for (Duration arrival : arrivals) {
            steps.add(nothingFor(arrival.minus(previous)));
            steps.add(atOnceUsers(1));
            previous = arrival;
        }
        return steps;
    }
}
