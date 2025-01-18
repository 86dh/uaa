package org.cloudfoundry.identity.uaa.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricsUtilTest {

    private static final double DELTA = 1e-15;

    @Test
    void addToAverage() {
        double average = 1.0;
        double avergeCount = 1.0;

        double newAverage = MetricsUtil.addToAverage(avergeCount, average, 1.0, 1.0);
        assertThat(newAverage).isCloseTo(1.0, within(DELTA));

        newAverage = MetricsUtil.addToAverage(avergeCount, average, 20.0, 20.0);
        assertThat(newAverage).isCloseTo(1.0, within(DELTA));

        newAverage = MetricsUtil.addToAverage(avergeCount, average, 0, 0);
        assertThat(newAverage).isCloseTo(1.0, within(DELTA));
    }

    @Test
    void addAverages() {
        double average = 1.0;
        double avergeCount = 1.0;

        double newAverage = MetricsUtil.addAverages(avergeCount, average, 5.0, 1.0);
        assertThat(newAverage).isCloseTo(1.0, within(DELTA));

        newAverage = MetricsUtil.addAverages(avergeCount, average, 20.0, 1.0);
        assertThat(newAverage).isCloseTo(1.0, within(DELTA));

        newAverage = MetricsUtil.addAverages(avergeCount, average, 0, 0);
        assertThat(newAverage).isCloseTo(1.0, within(DELTA));
    }
}