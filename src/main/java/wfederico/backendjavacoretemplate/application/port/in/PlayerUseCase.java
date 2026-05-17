package wfederico.backendjavacoretemplate.application.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerPatchDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerRequestDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerResponseDTO;

import java.util.List;

public interface PlayerUseCase {

    List<PlayerResponseDTO> getAllPlayers();

    Page<PlayerResponseDTO> getAllPlayersPaged(Pageable pageable);

    PlayerResponseDTO getPlayerById(Long id);

    PlayerResponseDTO createPlayer(PlayerRequestDTO playerToCreate);

    PlayerResponseDTO updatePlayer(Long id, PlayerRequestDTO updatedPlayer);

    PlayerResponseDTO patchPlayer(Long id, PlayerPatchDTO patchData);

    void deletePlayer(Long id);
}

