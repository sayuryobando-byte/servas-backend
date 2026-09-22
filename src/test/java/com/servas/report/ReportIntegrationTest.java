package com.servas.report;

import com.servas.application.report.ReportService;
import com.servas.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ReportIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ReportService reportService;

    @Test
    void reservationReportAggregatesSeededData() {
        var report = reportService.reservationsReport();
        assertThat(report.total()).isGreaterThanOrEqualTo(1);
        assertThat(report.byStatus()).isNotEmpty();
    }

    @Test
    void occupancyComputesPercentageWithinBounds() {
        var occupancy = reportService.occupancy();
        assertThat(occupancy.services()).isNotEmpty();
        assertThat(occupancy.overallPercent()).isBetween(0.0, 100.0);
        assertThat(occupancy.services()).allSatisfy(item ->
            assertThat(item.occupancyPercent()).isBetween(0.0, 100.0));
    }

    @Test
    void demandRankingIsNonIncreasing() {
        var demand = reportService.topDemand(10);
        assertThat(demand).isNotEmpty();
        for (int index = 1; index < demand.size(); index++) {
            assertThat(demand.get(index - 1).reservations())
                .isGreaterThanOrEqualTo(demand.get(index).reservations());
        }
    }
}