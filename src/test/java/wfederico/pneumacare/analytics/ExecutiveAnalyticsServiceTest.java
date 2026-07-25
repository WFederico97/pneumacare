package wfederico.pneumacare.analytics;

import org.junit.jupiter.api.BeforeEach;
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
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.domain.Disposition;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventRepository;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private PatientRepository patients;
    @Mock
    private SbtRepository sbts;
    @Mock
    private AirwayEventRepository airwayEvents;

    @InjectMocks
    private ExecutiveAnalyticsService service;

    @BeforeEach
    void noAdmittedPatientsByDefault() {
        // Overridable per-test; lenient so tests that don't assert stay days don't trip strict stubs.
        lenient().when(patients.findAdmissionDatesByClinicalStatus(ClinicalStatus.ADMITTED))
                .thenReturn(List.of());
        lenient().when(patients.findClosedEpisodeIntervals(any())).thenReturn(List.of());
        lenient().when(patients.countReadmissionsWithinHours(any(), anyInt())).thenReturn(0L);
        lenient().when(sbts.findPatientIdsWithFailedSbt()).thenReturn(List.of());
        lenient().when(airwayEvents.findAllByOrderByPatientIdAscEventTimeAsc()).thenReturn(List.of());
    }

    @Test
    @DisplayName("occupancy is occupied/total as a percentage (5 of 10 -> 50.0)")
    void occupancyPercentage() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(5L);
        when(beds.count()).thenReturn(10L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(0L);
        when(ventilators.countByStatus(any())).thenReturn(0L);

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.occupancyRatePercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("occupancy is 0.0 when there are no beds (no division by zero)")
    void occupancyZeroBeds() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(0L);
        when(beds.count()).thenReturn(0L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(0L);
        when(ventilators.countByStatus(any())).thenReturn(0L);

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.occupancyRatePercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("alert frequency uses a 7-day lookback window and passes the count through")
    void alertFrequencyWindow() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(0L);
        when(beds.count()).thenReturn(0L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(3L);
        when(ventilators.countByStatus(any())).thenReturn(0L);

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
        when(ventilators.countByStatus(any())).thenReturn(0L);
        when(ventilators.countByStatus(VentilatorStatus.MAINTENANCE)).thenReturn(4L);

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.equipmentInMaintenanceCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("asset utilization matrix: in-use / (in-use + available + maintenance)")
    void assetUtilizationMatrix() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(0L);
        when(beds.count()).thenReturn(0L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(0L);
        when(ventilators.countByStatus(VentilatorStatus.IN_USE)).thenReturn(3L);
        when(ventilators.countByStatus(VentilatorStatus.AVAILABLE)).thenReturn(5L);
        when(ventilators.countByStatus(VentilatorStatus.MAINTENANCE)).thenReturn(2L);

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.assetUtilization().inUse()).isEqualTo(3L);
        assertThat(response.assetUtilization().available()).isEqualTo(5L);
        assertThat(response.assetUtilization().maintenance()).isEqualTo(2L);
        assertThat(response.assetUtilization().utilizationPercent()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("census mean stay is the mean of now minus admission over admitted patients")
    void currentCensusMeanStayDays() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(0L);
        when(beds.count()).thenReturn(0L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(0L);
        when(ventilators.countByStatus(any())).thenReturn(0L);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(patients.findAdmissionDatesByClinicalStatus(ClinicalStatus.ADMITTED))
                .thenReturn(List.of(now.minusDays(2), now.minusDays(4)));

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.currentCensusMeanStayDays()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("true ALOS and bed turnover derive from closed episodes in the window")
    void dashboardComputesTrueAlosAndTurnoverFromClosedEpisodes() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(0L);
        when(beds.count()).thenReturn(4L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(0L);
        when(ventilators.countByStatus(any())).thenReturn(0L);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID e1 = UUID.randomUUID();
        UUID e2 = UUID.randomUUID();
        when(patients.findClosedEpisodeIntervals(any())).thenReturn(List.of(
                new Object[]{e1, now.minusDays(10), now.minusDays(6), Disposition.HOME},   // 4 days
                new Object[]{e2, now.minusDays(9), now.minusDays(1), Disposition.DECEASED} // 8 days
        ));

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.averageStayDays()).isEqualTo(6.0);   // (4+8)/2
        assertThat(response.bedTurnover()).isEqualTo(0.5);       // 2 closed / 4 beds
        assertThat(response.mortality().closedEpisodes()).isEqualTo(2);
        assertThat(response.mortality().deceased()).isEqualTo(1);
        assertThat(response.mortality().icuMortalityPercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("weaning-failure mortality counts only cohort deaths")
    void dashboardWeaningFailureMortalityCountsOnlyCohortDeaths() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(0L);
        when(beds.count()).thenReturn(0L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(0L);
        when(ventilators.countByStatus(any())).thenReturn(0L);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID cohortDeceased = UUID.randomUUID();    // failed SBT + died
        UUID nonCohortDeceased = UUID.randomUUID(); // died without weaning failure
        when(patients.findClosedEpisodeIntervals(any())).thenReturn(List.of(
                new Object[]{cohortDeceased, now.minusDays(10), now.minusDays(2), Disposition.DECEASED},
                new Object[]{nonCohortDeceased, now.minusDays(8), now.minusDays(1), Disposition.DECEASED}
        ));
        when(sbts.findPatientIdsWithFailedSbt()).thenReturn(List.of(cohortDeceased));

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.mortality().weaningFailureCohort()).isEqualTo(1);
        assertThat(response.mortality().weaningFailureDeceased()).isEqualTo(1);
        assertThat(response.mortality().weaningFailureMortalityPercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("readmission rates use the closed-episode denominator")
    void dashboardReadmissionRatesUseClosedEpisodeDenominator() {
        when(beds.countByStatus(BedStatus.OCCUPIED)).thenReturn(0L);
        when(beds.count()).thenReturn(0L);
        when(alerts.countByCreatedAtAfter(any())).thenReturn(0L);
        when(ventilators.countByStatus(any())).thenReturn(0L);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(patients.findClosedEpisodeIntervals(any())).thenReturn(List.of(
                new Object[]{UUID.randomUUID(), now.minusDays(10), now.minusDays(6), Disposition.HOME},
                new Object[]{UUID.randomUUID(), now.minusDays(9), now.minusDays(1), Disposition.WARD}
        ));
        when(patients.countReadmissionsWithinHours(any(), eq(48))).thenReturn(0L);
        when(patients.countReadmissionsWithinHours(any(), eq(168))).thenReturn(1L);

        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response.readmissions().readmissions7d()).isEqualTo(1);
        assertThat(response.readmissions().rate7dPercent()).isEqualTo(50.0);
        assertThat(response.readmissions().rate48hPercent()).isEqualTo(0.0);
    }
}
