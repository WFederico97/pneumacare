package wfederico.backendjavacoretemplate.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerEvent {

    private String type;
    private Long playerId;
    private Instant timestamp;

    public static PlayerEvent created(Long playerId) {
        return PlayerEvent.builder()
                .type("PLAYER_CREATED")
                .playerId(playerId)
                .timestamp(Instant.now())
                .build();
    }

    public static PlayerEvent updated(Long playerId) {
        return PlayerEvent.builder()
                .type("PLAYER_UPDATED")
                .playerId(playerId)
                .timestamp(Instant.now())
                .build();
    }

    public static PlayerEvent deleted(Long playerId) {
        return PlayerEvent.builder()
                .type("PLAYER_DELETED")
                .playerId(playerId)
                .timestamp(Instant.now())
                .build();
    }
}

