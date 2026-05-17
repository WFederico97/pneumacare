package wfederico.backendjavacoretemplate.infra.adapter.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerPatchDTO {

    @Pattern(regexp = "^[\\p{L}\\p{N}\\s.,&\\-()/]+$", message = "Invalid characters in first name")
    @JsonProperty("first_name")
    private String firstName;

    @Pattern(regexp = "^[\\p{L}\\p{N}\\s.,&\\-()/]+$", message = "Invalid characters in last name")
    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("position")
    private String position;

    @JsonProperty("alter_position")
    private String alterPosition;

    @JsonProperty("team_id")
    private Long teamId;
}

