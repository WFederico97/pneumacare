package wfederico.pneumacare.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wfederico.pneumacare.analytics.application.ExecutiveAnalyticsService;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorRepository;
import wfederico.pneumacare.notification.infrastructure.persistence.ClinicalAlertLogRepository;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutiveAnalyticsServiceTest {

    @Mock
    private IcuBedRepository beds;
    @Mock
    private ClinicalAlertLogRepository alerts;
    @Mock
    private PhysicalVentilatorRepository ventilators;

    @InjectMocks
    private ExecutiveAnalyticsService service;

    @Test
    @DisplayName("occupancy is occupied/total as a percentage (5 of 10 -> 50.0)")
    void occupancyPercentage() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(5L);
        when(beds.count()).thenReturn(10L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(0L);
        when(ventilators.countByStatus(VentilatorStatus.MAINTENANCE)).thenReturn(0L);

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.occupancyRatePercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("occupancy is 0.0 when there are no beds (no division by zero)")
    void occupancyZeroBeds() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(0L);
        when(beds.count()).thenReturn(0L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(0L);
        when(ventilators.countByStatus(VentilatorStatus.MAINTENANCE)).thenReturn(0L);

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.occupancyRatePercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("alert frequency uses a 7-day lookback window and passes the count through")
    void alertFrequencyWindow() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(0L);
        when(beds.count()).thenReturn(0L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(3L);
        when(ventilators.countByStatus(VentilatorStatus.MAINTENANCE)).thenReturn(0L);

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.alertFrequencyLast7Days()).isEqualTo(3L);

        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(alerts).countByCreatedAtAfter(since.capture());
        OffsetDateTime expected = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        assertThat(since.getValue()).isCloseTo(expected, within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("maintenance count reflects ventilators in MAINTENANCE")
    void maintenanceCount() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(0L);
        when(beds.count()).thenReturn(0L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(0L);
        when(ventilators.countByStatus(VentilatorStatus.MAINTENANCE)).thenReturn(4L);

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.equipmentInMaintenanceCount()).isEqualTo(4L);
    }
}
