package wfederico.pneumacare.shift;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shift.application.CurrentIcuPort;
import wfederico.pneumacare.shared.security.CurrentUserPort;
import wfederico.pneumacare.shift.application.IcuExistencePort;
import wfederico.pneumacare.shift.application.MedicalShiftService;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;
import wfederico.pneumacare.shift.web.dto.ShiftResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalShiftServiceTest {

    private static final UUID ICU_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID CHIEF_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID SHIFT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @Mock
    private MedicalShiftRepository shiftRepository;
    @Mock
    private IcuExistencePort icuExistencePort;
    @Mock
    private CurrentUserPort currentUserPort;
    @Mock
    private CurrentIcuPort currentIcuPort;

    @InjectMocks
    private MedicalShiftService service;

    @Test
    @DisplayName("open_validRequest_returnsOpenShift (AC1)")
    void open_validRequest_returnsOpenShift() {
        when(currentIcuPort.currentIcuId()).thenReturn(ICU_ID);
        when(icuExistencePort.exists(ICU_ID)).thenReturn(true);
        when(shiftRepository.existsByIcuIdAndStatus(ICU_ID, ShiftStatus.OPEN)).thenReturn(false);
        when(currentUserPort.currentUserId()).thenReturn(CHIEF_ID);

        MedicalShiftJpaEntity saved = MedicalShiftJpaEntity.builder()
                .id(SHIFT_ID)
                .icuId(ICU_ID)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.OPEN)
                .build();
        when(shiftRepository.save(any(MedicalShiftJpaEntity.class))).thenReturn(saved);

        ShiftResponse response = service.open();

        assertThat(response.id()).isEqualTo(SHIFT_ID);
        assertThat(response.icuId()).isEqualTo(ICU_ID);
        assertThat(response.startedBy()).isEqualTo(CHIEF_ID);
        assertThat(response.status()).isEqualTo(ShiftStatus.OPEN);
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.endTime()).isNull();
    }

    @Test
    @DisplayName("open_duplicateOpenForIcu_throws409 (AC2)")
    void open_duplicateOpenForIcu_throws409() {
        when(currentIcuPort.currentIcuId()).thenReturn(ICU_ID);
        when(icuExistencePort.exists(ICU_ID)).thenReturn(true);
        when(shiftRepository.existsByIcuIdAndStatus(ICU_ID, ShiftStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> service.open())
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(shiftRepository, never()).save(any());
    }

    @Test
    @DisplayName("open_unknownIcu_throws422 (AC3)")
    void open_unknownIcu_throws422() {
        when(currentIcuPort.currentIcuId()).thenReturn(ICU_ID);
        when(icuExistencePort.exists(ICU_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.open())
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        verify(shiftRepository, never()).save(any());
    }

    @Test
    @DisplayName("open_concurrentRaceHitsUniqueIndex_throws409 (AC2 concurrency)")
    void open_concurrentRace_throws409() {
        when(currentIcuPort.currentIcuId()).thenReturn(ICU_ID);
        when(icuExistencePort.exists(ICU_ID)).thenReturn(true);
        when(shiftRepository.existsByIcuIdAndStatus(ICU_ID, ShiftStatus.OPEN)).thenReturn(false);
        when(currentUserPort.currentUserId()).thenReturn(CHIEF_ID);
        when(shiftRepository.save(any(MedicalShiftJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("unique index"));

        assertThatThrownBy(() -> service.open())
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("close_openShift_returnsClosedWithEndTime (AC4)")
    void close_openShift_returnsClosed() {
        MedicalShiftJpaEntity open = MedicalShiftJpaEntity.builder()
                .id(SHIFT_ID)
                .icuId(ICU_ID)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.OPEN)
                .build();
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(open));
        when(currentIcuPort.currentIcuId()).thenReturn(ICU_ID);
        when(shiftRepository.save(any(MedicalShiftJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ShiftResponse response = service.close(SHIFT_ID);

        assertThat(response.status()).isEqualTo(ShiftStatus.CLOSED);
        assertThat(response.endTime()).isNotNull();
    }

    @Test
    @DisplayName("close_alreadyClosed_throws409 (AC5)")
    void close_alreadyClosed_throws409() {
        MedicalShiftJpaEntity closed = MedicalShiftJpaEntity.builder()
                .id(SHIFT_ID)
                .icuId(ICU_ID)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.CLOSED)
                .build();
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(closed));
        when(currentIcuPort.currentIcuId()).thenReturn(ICU_ID);

        assertThatThrownBy(() -> service.close(SHIFT_ID))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(shiftRepository, never()).save(any());
    }

    @Test
    @DisplayName("close_shiftInAnotherIcu_throws404AndDoesNotClose")
    void close_shiftInAnotherIcu_throws404() {
        UUID otherIcuId = UUID.fromString("dddddddd-0000-0000-0000-000000000009");
        MedicalShiftJpaEntity foreignShift = MedicalShiftJpaEntity.builder()
                .id(SHIFT_ID)
                .icuId(otherIcuId)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.OPEN)
                .build();
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(foreignShift));
        when(currentIcuPort.currentIcuId()).thenReturn(ICU_ID);

        assertThatThrownBy(() -> service.close(SHIFT_ID))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        assertThat(foreignShift.getStatus()).isEqualTo(ShiftStatus.OPEN);
        verify(shiftRepository, never()).save(any());
    }

    @Test
    @DisplayName("close_unknownShift_throws404 (AC6)")
    void close_unknownShift_throws404() {
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(SHIFT_ID))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("getActiveShift_openShiftExists_returnsShift")
    void getActiveShift_openShiftExists_returnsShift() {
        when(currentIcuPort.currentIcuId()).thenReturn(ICU_ID);
        MedicalShiftJpaEntity open = MedicalShiftJpaEntity.builder()
                .id(SHIFT_ID)
                .icuId(ICU_ID)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.OPEN)
                .build();
        when(shiftRepository.findByIcuIdAndStatus(ICU_ID, ShiftStatus.OPEN)).thenReturn(Optional.of(open));

        Optional<ShiftResponse> result = service.getActiveShift();

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(SHIFT_ID);
        assertThat(result.get().icuId()).isEqualTo(ICU_ID);
        assertThat(result.get().status()).isEqualTo(ShiftStatus.OPEN);
    }

    @Test
    @DisplayName("getActiveShift_noOpenShift_returnsEmpty")
    void getActiveShift_noOpenShift_returnsEmpty() {
        when(currentIcuPort.currentIcuId()).thenReturn(ICU_ID);
        when(shiftRepository.findByIcuIdAndStatus(ICU_ID, ShiftStatus.OPEN)).thenReturn(Optional.empty());

        assertThat(service.getActiveShift()).isEmpty();
    }
}
