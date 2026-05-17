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
        List<PlayerEntity> list = playerPersistencePort.findAll();
        if (list.isEmpty()) {
            throw new BusinessLayerException(PLAYERS_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return list.stream().map(this::toResponseDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "players",
               key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
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
        return toResponseDTO(findPlayerOrThrow(id));
    }

    @Override
    @Transactional
    @CacheEvict(value = {"players", "player"}, allEntries = true)
    public PlayerResponseDTO createPlayer(PlayerRequestDTO dto) {
        PlayerEntity entity = modelMapper.map(dto, PlayerEntity.class);
        resolveTeam(dto.getTeamId(), entity);
        PlayerEntity saved = playerPersistencePort.save(entity);
        eventPublisher.publish(PlayerEvent.created(saved.getId()));
        return toResponseDTO(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"players", "player"}, allEntries = true)
    public PlayerResponseDTO updatePlayer(Long id, PlayerRequestDTO dto) {
        PlayerEntity entity = findPlayerOrThrow(id);
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setPosition(dto.getPosition());
        entity.setAlterPosition(dto.getAlterPosition());
        resolveTeam(dto.getTeamId(), entity);
        PlayerEntity saved = playerPersistencePort.save(entity);
        eventPublisher.publish(PlayerEvent.updated(saved.getId()));
        return toResponseDTO(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"players", "player"}, allEntries = true)
    public PlayerResponseDTO patchPlayer(Long id, PlayerPatchDTO patch) {
        PlayerEntity entity = findPlayerOrThrow(id);
        if (patch.getFirstName()    != null) entity.setFirstName(patch.getFirstName());
        if (patch.getLastName()     != null) entity.setLastName(patch.getLastName());
        if (patch.getPosition()     != null) entity.setPosition(patch.getPosition());
        if (patch.getAlterPosition()!= null) entity.setAlterPosition(patch.getAlterPosition());
        if (patch.getTeamId()       != null) resolveTeam(patch.getTeamId(), entity);
        PlayerEntity saved = playerPersistencePort.save(entity);
        eventPublisher.publish(PlayerEvent.updated(saved.getId()));
        return toResponseDTO(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"players", "player"}, allEntries = true)
    public void deletePlayer(Long id) {
        PlayerEntity entity = findPlayerOrThrow(id);
        playerPersistencePort.delete(entity);
        eventPublisher.publish(PlayerEvent.deleted(id));
    }

    // --- private helpers ---

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

