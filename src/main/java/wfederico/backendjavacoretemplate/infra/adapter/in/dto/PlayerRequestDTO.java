package wfederico.backendjavacoretemplate.infra.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import wfederico.backendjavacoretemplate.core.constants.ValidationConstants;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerRequestDTO {

    @NotNull(message = ValidationConstants.PLAYER_NAME_REQUIRED)
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s.,&\\-()/]+$", message = "El nombre del jugador tiene caracteres invalidos")
    @JsonProperty("first_name")
    private String firstName;

    @NotNull(message = ValidationConstants.PLAYER_NAME_REQUIRED)
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s.,&\\-()/]+$", message = "El nombre del jugador tiene caracteres invalidos")
    @JsonProperty("last_name")
    private String lastName;

    @NotNull(message = ValidationConstants.PLAYER_POSITION_REQUIRED)
    @JsonProperty("position")
    private String position;

    @NotNull(message = ValidationConstants.PLAYER_ALTER_POSITION_REQUIRED)
    @JsonProperty("alter_position")
    private String alterPosition;

    @JsonProperty("team_id")
    private Long teamId;
}

