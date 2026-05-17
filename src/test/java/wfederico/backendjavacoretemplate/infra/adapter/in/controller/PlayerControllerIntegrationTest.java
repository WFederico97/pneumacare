package wfederico.backendjavacoretemplate.infra.adapter.in.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.backendjavacoretemplate.application.port.in.PlayerUseCase;
import wfederico.backendjavacoretemplate.domain.exception.BusinessLayerException;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerRequestDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerResponseDTO;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlayerController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlayerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerUseCase playerUseCase;

    private final PlayerResponseDTO sampleResponse = PlayerResponseDTO.builder()
            .id(1L)
            .firstName("Lionel")
            .lastName("Messi")
            .position("Forward")
            .alterPosition("Midfielder")
            .build();

    @Test
    void getAll_returns200WithPagedResults() throws Exception {
        Page<PlayerResponseDTO> page = new PageImpl<>(List.of(sampleResponse), PageRequest.of(0, 10), 1);
        when(playerUseCase.getAllPlayersPaged(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/players")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data.content[0].id", is(1)));
    }

    @Test
    void getPlayer_returns200WhenFound() throws Exception {
        when(playerUseCase.getPlayerById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/players/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.first_name", is("Lionel")));
    }

    @Test
    void getPlayer_returns404WhenNotFound() throws Exception {
        when(playerUseCase.getPlayerById(99L)).thenThrow(new BusinessLayerException("Not found", NOT_FOUND));

        mockMvc.perform(get("/api/v1/players/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPlayer_returns201OnValidInput() throws Exception {
        when(playerUseCase.createPlayer(any(PlayerRequestDTO.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "first_name": "Lionel",
                                    "last_name": "Messi",
                                    "position": "Forward",
                                    "alter_position": "Midfielder"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is(201)))
                .andExpect(jsonPath("$.data.id", is(1)));
    }

    @Test
    void createPlayer_returns400OnMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePlayer_returns200OnValidInput() throws Exception {
        when(playerUseCase.updatePlayer(eq(1L), any(PlayerRequestDTO.class))).thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/players/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "first_name": "Leo",
                                    "last_name": "Messi",
                                    "position": "Forward",
                                    "alter_position": "Midfielder"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)));
    }

    @Test
    void patchPlayer_returns200OnPartialUpdate() throws Exception {
        when(playerUseCase.patchPlayer(eq(1L), any())).thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/v1/players/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "first_name": "Leo" }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void deletePlayer_returns200() throws Exception {
        doNothing().when(playerUseCase).deletePlayer(1L);

        mockMvc.perform(delete("/api/v1/players/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)));
    }
}



