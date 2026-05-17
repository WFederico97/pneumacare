package wfederico.backendjavacoretemplate.application.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.backendjavacoretemplate.application.port.in.PlayerUseCase;
import wfederico.backendjavacoretemplate.application.port.out.PlayerPersistencePort;
import wfederico.backendjavacoretemplate.application.port.out.TeamPersistencePort;
import wfederico.backendjavacoretemplate.core.messaging.DomainEventPublisher;
import wfederico.backendjavacoretemplate.domain.event.PlayerEvent;
import wfederico.backendjavacoretemplate.domain.exception.BusinessLayerException;
import wfederico.backendjavacoretemplate.domain.model.player.PlayerEntity;
import wfederico.backendjavacoretemplate.domain.model.team.TeamEntity;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerPatchDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerRequestDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerResponseDTO;

import java.util.List;

import static wfederico.backendjavacoretemplate.core.constants.ExceptionMessageConstants.*;

@Service
@RequiredArgsConstructor
@Observed(name = "player.service")
public class PlayerService implements PlayerUseCase {

    private final PlayerPersistencePort playerPersistencePort;
    private final TeamPersistencePort teamPersistencePort;
    private final ModelMapper modelMapper;
    private final DomainEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "players", key = "'all'")
    public List<PlayerResponseDTO> getAllPlayers() {
        List<PlayerEntity> playerList = playerPersistencePort.findAll();
        if (playerList.isEmpty()) {
            throw new BusinessLayerException(PLAYERS_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return playerList.stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "players", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    public Page<PlayerResponseDTO> getAllPlayersPaged(Pageable pageable) {
        Page<PlayerEntity> page = playerPersistencePort.findAll(pageable);
        if (page.isEmpty()) {
            throw new BusinessLayerException(PLAYERS_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return page.map(this::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "player", key = "#id")
    public PlayerResponseDTO getPlayerById(Long id) {
        PlayerEntity player = findPlayerOrThrow(id);
        return toResponseDTO(player);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"players", "player"}, allEntries = true)
    public PlayerResponseDTO createPlayer(PlayerRequestDTO playerToCreate) {
        PlayerEntity newPlayer = modelMapper.map(playerToCreate, PlayerEntity.class);
        resolveTeam(playerToCreate.getTeamId(), newPlayer);
        PlayerEntity savedPlayer = playerPersistencePort.save(newPlayer);
        eventPublisher.publish(PlayerEvent.created(savedPlayer.getId()));
        return toResponseDTO(savedPlayer);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"players", "player"}, allEntries = true)
    public PlayerResponseDTO updatePlayer(Long id, PlayerRequestDTO updatedPlayer) {
        PlayerEntity existingPlayer = findPlayerOrThrow(id);
        existingPlayer.setFirstName(updatedPlayer.getFirstName());
        existingPlayer.setLastName(updatedPlayer.getLastName());
        existingPlayer.setPosition(updatedPlayer.getPosition());
        existingPlayer.setAlterPosition(updatedPlayer.getAlterPosition());
        resolveTeam(updatedPlayer.getTeamId(), existingPlayer);
        PlayerEntity savedPlayer = playerPersistencePort.save(existingPlayer);
        eventPublisher.publish(PlayerEvent.updated(savedPlayer.getId()));
        return toResponseDTO(savedPlayer);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"players", "player"}, allEntries = true)
    public PlayerResponseDTO patchPlayer(Long id, PlayerPatchDTO patchData) {
        PlayerEntity existingPlayer = findPlayerOrThrow(id);

        if (patchData.getFirstName() != null) {
            existingPlayer.setFirstName(patchData.getFirstName());
        }
        if (patchData.getLastName() != null) {
            existingPlayer.setLastName(patchData.getLastName());
        }
        if (patchData.getPosition() != null) {
            existingPlayer.setPosition(patchData.getPosition());
        }
        if (patchData.getAlterPosition() != null) {
            existingPlayer.setAlterPosition(patchData.getAlterPosition());
        }
        if (patchData.getTeamId() != null) {
            resolveTeam(patchData.getTeamId(), existingPlayer);
        }

        PlayerEntity savedPlayer = playerPersistencePort.save(existingPlayer);
        eventPublisher.publish(PlayerEvent.updated(savedPlayer.getId()));
        return toResponseDTO(savedPlayer);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"players", "player"}, allEntries = true)
    public void deletePlayer(Long id) {
        PlayerEntity existingPlayer = findPlayerOrThrow(id);
        playerPersistencePort.delete(existingPlayer);
        eventPublisher.publish(PlayerEvent.deleted(id));
    }

    private PlayerEntity findPlayerOrThrow(Long id) {
        return playerPersistencePort.findById(id)
                .orElseThrow(() -> new BusinessLayerException(PLAYER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private void resolveTeam(Long teamId, PlayerEntity player) {
        if (teamId == null) {
            player.setTeam(null);
            return;
        }
        TeamEntity team = teamPersistencePort.findById(teamId)
                .orElseThrow(() -> new BusinessLayerException(TEAM_NOT_FOUND, HttpStatus.NOT_FOUND));
        player.setTeam(team);
    }

    private PlayerResponseDTO toResponseDTO(PlayerEntity entity) {
        PlayerResponseDTO dto = modelMapper.map(entity, PlayerResponseDTO.class);
        dto.setTeamId(entity.getTeam() != null ? entity.getTeam().getId() : null);
        return dto;
    }
}
