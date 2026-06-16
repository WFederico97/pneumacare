package wfederico.pneumacare.shift;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.CurrentUserPort;
import wfederico.pneumacare.shift.application.ShiftHandoverService;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverRepository;
import wfederico.pneumacare.shift.web.dto.CreateHandoverRequest;
import wfederico.pneumacare.shift.web.dto.HandoverResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftHandoverServiceTest {

    private static final UUID SHIFT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID AUTHOR_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID ICU_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID HANDOVER_ID = UUID.fromString("ffffffff-0000-0000-0000-000000000001");

    @Mock
    private ShiftHandoverRepository handoverRepository;
    @Mock
    private MedicalShiftRepository shiftRepository;
    @Mock
    private CurrentUserPort currentUserPort;

    @InjectMocks
    private ShiftHandoverService service;

    private MedicalShiftJpaEntity shift(ShiftStatus status) {
        return MedicalShiftJpaEntity.builder()
                .id(SHIFT_ID).icuId(ICU_ID).chiefUserId(AUTHOR_ID).status(status).build();
    }

    private void echoSavedHandover() {
        when(handoverRepository.save(any(ShiftHandoverJpaEntity.class))).thenAnswer(inv -> {
            ShiftHandoverJpaEntity e = inv.getArgument(0);
            e.setId(HANDOVER_ID);
            return e;
        });
    }

    @Test
    @DisplayName("note on an OPEN shift is persisted and returned")
    void create_openShift_persists() {
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shift(ShiftStatus.OPEN)));
        when(currentUserPort.currentUserId()).thenReturn(AUTHOR_ID);
        echoSavedHandover();

        HandoverResponse response = service.create(SHIFT_ID, new CreateHandoverRequest("Cama 3 estable"));

        assertThat(response.id()).isEqualTo(HANDOVER_ID);
        assertThat(response.shiftId()).isEqualTo(SHIFT_ID);
        assertThat(response.authorId()).isEqualTo(AUTHOR_ID);
        assertThat(response.notesContent()).isEqualTo("Cama 3 estable");

        ArgumentCaptor<ShiftHandoverJpaEntity> captor = ArgumentCaptor.forClass(ShiftHandoverJpaEntity.class);
        verify(handoverRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorId()).isEqualTo(AUTHOR_ID);
    }

    @Test
    @DisplayName("note on a CLOSED shift throws 409 and writes nothing")
    void create_closedShift_throws409() {
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.of(shift(ShiftStatus.CLOSED)));

        assertThatThrownBy(() -> service.create(SHIFT_ID, new CreateHandoverRequest("note")))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(handoverRepository, never()).save(any());
    }

    @Test
    @DisplayName("note on a non-existent shift throws 404")
    void create_unknownShift_throws404() {
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(SHIFT_ID, new CreateHandoverRequest("note")))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(handoverRepository, never()).save(any());
    }

    @Test
    @DisplayName("blank content throws 422 before any shift lookup")
    void create_blankContent_throws422() {
        assertThatThrownBy(() -> service.create(SHIFT_ID, new CreateHandoverRequest("   ")))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        verify(shiftRepository, never()).findById(any());
        verify(handoverRepository, never()).save(any());
    }

    @Test
    @DisplayName("null content throws 422")
    void create_nullContent_throws422() {
        assertThatThrownBy(() -> service.create(SHIFT_ID, new CreateHandoverRequest(null)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        verify(handoverRepository, never()).save(any());
    }

    @Test
    @DisplayName("content over 4000 chars throws 422")
    void create_contentTooLong_throws422() {
        String tooLong = "x".repeat(4001);

        assertThatThrownBy(() -> service.create(SHIFT_ID, new CreateHandoverRequest(tooLong)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        verify(handoverRepository, never()).save(any());
    }

    @Test
    @DisplayName("listing returns the shift's notes mapped newest-first")
    void getForShift_returnsMappedNotes() {
        when(shiftRepository.existsById(SHIFT_ID)).thenReturn(true);
        ShiftHandoverJpaEntity note = ShiftHandoverJpaEntity.builder()
                .id(HANDOVER_ID).shiftId(SHIFT_ID).authorId(AUTHOR_ID).notesContent("note")
                .build();
        when(handoverRepository.findByShiftIdOrderByCreatedAtDesc(SHIFT_ID)).thenReturn(List.of(note));

        List<HandoverResponse> result = service.getForShift(SHIFT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(HANDOVER_ID);
        assertThat(result.get(0).notesContent()).isEqualTo("note");
    }

    @Test
    @DisplayName("listing for a non-existent shift throws 404")
    void getForShift_unknownShift_throws404() {
        when(shiftRepository.existsById(SHIFT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getForShift(SHIFT_ID))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(handoverRepository, never()).findByShiftIdOrderByCreatedAtDesc(any());
    }
}
