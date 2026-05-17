package wfederico.backendjavacoretemplate.infra.adapter.in.graphql;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import wfederico.backendjavacoretemplate.application.port.in.PlayerUseCase;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerPatchDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerRequestDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerResponseDTO;

@Controller
@RequiredArgsConstructor
public class PlayerQueryResolver {

    private final PlayerUseCase playerUseCase;

    @QueryMapping
    public Page<PlayerResponseDTO> players(@Argument int page, @Argument int size,
                                           @Argument String sortBy, @Argument String direction) {
        Sort sort = "desc".equalsIgnoreCase(direction) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return playerUseCase.getAllPlayersPaged(pageable);
    }

    @QueryMapping
    public PlayerResponseDTO player(@Argument Long id) {
        return playerUseCase.getPlayerById(id);
    }

    @MutationMapping
    public PlayerResponseDTO createPlayer(@Argument PlayerRequestDTO input) {
        return playerUseCase.createPlayer(input);
    }

    @MutationMapping
    public PlayerResponseDTO updatePlayer(@Argument Long id, @Argument PlayerRequestDTO input) {
        return playerUseCase.updatePlayer(id, input);
    }

    @MutationMapping
    public PlayerResponseDTO patchPlayer(@Argument Long id, @Argument PlayerPatchDTO input) {
        return playerUseCase.patchPlayer(id, input);
    }

    @MutationMapping
    public boolean deletePlayer(@Argument Long id) {
        playerUseCase.deletePlayer(id);
        return true;
    }
}

