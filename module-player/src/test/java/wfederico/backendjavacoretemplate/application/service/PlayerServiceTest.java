package wfederico.backendjavacoretemplate.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import wfederico.backendjavacoretemplate.application.port.out.PlayerPersistencePort;
import wfederico.backendjavacoretemplate.application.port.out.TeamPersistencePort;
import wfederico.backendjavacoretemplate.core.messaging.DomainEventPublisher;
import wfederico.backendjavacoretemplate.domain.exception.BusinessLayerException;
import wfederico.backendjavacoretemplate.domain.model.player.PlayerEntity;
import wfederico.backendjavacoretemplate.domain.model.team.TeamEntity;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerPatchDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerRequestDTO;
import wfederico.backendjavacoretemplate.infra.adapter.in.dto.PlayerResponseDTO;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock private PlayerPersistencePort playerPersistencePort;
    @Mock private TeamPersistencePort   teamPersistencePort;
    @Mock private ModelMapper           modelMapper;
    @Mock private DomainEventPublisher  eventPublisher;

    @InjectMocks
    private PlayerService playerService;

    private PlayerEntity      playerEntity;
    private PlayerResponseDTO playerResponseDTO;
    private PlayerRequestDTO  playerRequestDTO;

    @BeforeEach
    void setUp() {
        playerEntity = PlayerEntity.builder()
                .id(1L).firstName("Lionel").lastName("Messi")
                .position("Forward").alterPosition("Midfielder").build();

        playerResponseDTO = PlayerResponseDTO.builder()
                .id(1L).firstName("Lionel").lastName("Messi")
                .position("Forward").alterPosition("Midfielder").build();

        playerRequestDTO = PlayerRequestDTO.builder()
                .firstName("Lionel").lastName("Messi")
                .position("Forward").alterPosition("Midfielder").build();
    }

    @Test
    void getAllPlayers_returnsListWhenPlayersExist() {
        when(playerPersistencePort.findAll()).thenReturn(List.of(playerEntity));
        when(modelMapper.map(playerEntity, PlayerResponseDTO.class)).thenReturn(playerResponseDTO);
        List<PlayerResponseDTO> result = playerService.getAllPlayers();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Lionel");
    }

    @Test
    void getAllPlayers_throwsWhenEmpty() {
        when(playerPersistencePort.findAll()).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> playerService.getAllPlayers())
                .isInstanceOf(BusinessLayerException.class);
    }

    @Test
    void getAllPlayersPaged_returnsPageWhenPlayersExist() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PlayerEntity> page = new PageImpl<>(List.of(playerEntity), pageable, 1);
        when(playerPersistencePort.findAll(pageable)).thenReturn(page);
        when(modelMapper.map(playerEntity, PlayerResponseDTO.class)).thenReturn(playerResponseDTO);
        Page<PlayerResponseDTO> result = playerService.getAllPlayersPaged(pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getAllPlayersPaged_throwsWhenEmpty() {
        Pageable pageable = PageRequest.of(0, 10);
        when(playerPersistencePort.findAll(pageable)).thenReturn(Page.empty(pageable));
        assertThatThrownBy(() -> playerService.getAllPlayersPaged(pageable))
                .isInstanceOf(BusinessLayerException.class);
    }

    @Test
    void getPlayerById_returnsPlayerWhenFound() {
        when(playerPersistencePort.findById(1L)).thenReturn(Optional.of(playerEntity));
        when(modelMapper.map(playerEntity, PlayerResponseDTO.class)).thenReturn(playerResponseDTO);
        assertThat(playerService.getPlayerById(1L).getId()).isEqualTo(1L);
    }

    @Test
    void getPlayerById_throwsWhenNotFound() {
        when(playerPersistencePort.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> playerService.getPlayerById(99L))
                .isInstanceOf(BusinessLayerException.class);
    }

    @Test
    void createPlayer_persistsAndReturnsDTO() {
        when(modelMapper.map(playerRequestDTO, PlayerEntity.class)).thenReturn(playerEntity);
        when(playerPersistencePort.save(playerEntity)).thenReturn(playerEntity);
        when(modelMapper.map(playerEntity, PlayerResponseDTO.class)).thenReturn(playerResponseDTO);
        PlayerResponseDTO result = playerService.createPlayer(playerRequestDTO);
        assertThat(result.getFirstName()).isEqualTo("Lionel");
        verify(playerPersistencePort).save(playerEntity);
        verify(eventPublisher).publish(any());
    }

    @Test
    void createPlayer_resolvesTeamWhenTeamIdProvided() {
        playerRequestDTO.setTeamId(10L);
        TeamEntity team = TeamEntity.builder().id(10L).name("Argentina").city("Buenos Aires").country("AR").build();
        when(modelMapper.map(playerRequestDTO, PlayerEntity.class)).thenReturn(playerEntity);
        when(teamPersistencePort.findById(10L)).thenReturn(Optional.of(team));
        when(playerPersistencePort.save(playerEntity)).thenReturn(playerEntity);
        when(modelMapper.map(playerEntity, PlayerResponseDTO.class)).thenReturn(playerResponseDTO);
        playerService.createPlayer(playerRequestDTO);
        assertThat(playerEntity.getTeam()).isEqualTo(team);
    }

    @Test
    void createPlayer_throwsWhenTeamNotFound() {
        playerRequestDTO.setTeamId(999L);
        when(modelMapper.map(playerRequestDTO, PlayerEntity.class)).thenReturn(playerEntity);
        when(teamPersistencePort.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> playerService.createPlayer(playerRequestDTO))
                .isInstanceOf(BusinessLayerException.class);
    }

    @Test
    void updatePlayer_appliesFullReplacementAndPersists() {
        PlayerRequestDTO req = PlayerRequestDTO.builder()
                .firstName("Leo").lastName("Messi").position("Midfielder").alterPosition("Forward").build();
        when(playerPersistencePort.findById(1L)).thenReturn(Optional.of(playerEntity));
        when(playerPersistencePort.save(playerEntity)).thenReturn(playerEntity);
        when(modelMapper.map(playerEntity, PlayerResponseDTO.class)).thenReturn(playerResponseDTO);
        playerService.updatePlayer(1L, req);
        assertThat(playerEntity.getFirstName()).isEqualTo("Leo");
        assertThat(playerEntity.getPosition()).isEqualTo("Midfielder");
        verify(eventPublisher).publish(any());
    }

    @Test
    void patchPlayer_appliesOnlyNonNullFields() {
        PlayerPatchDTO patch = PlayerPatchDTO.builder().firstName("Leo").build();
        when(playerPersistencePort.findById(1L)).thenReturn(Optional.of(playerEntity));
        when(playerPersistencePort.save(playerEntity)).thenReturn(playerEntity);
        when(modelMapper.map(playerEntity, PlayerResponseDTO.class)).thenReturn(playerResponseDTO);
        playerService.patchPlayer(1L, patch);
        assertThat(playerEntity.getFirstName()).isEqualTo("Leo");
        assertThat(playerEntity.getLastName()).isEqualTo("Messi");
        verify(eventPublisher).publish(any());
    }

    @Test
    void deletePlayer_deletesExistingEntity() {
        when(playerPersistencePort.findById(1L)).thenReturn(Optional.of(playerEntity));
        playerService.deletePlayer(1L);
        verify(playerPersistencePort).delete(playerEntity);
        verify(eventPublisher).publish(any());
    }

    @Test
    void deletePlayer_throwsWhenNotFound() {
        when(playerPersistencePort.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> playerService.deletePlayer(99L))
                .isInstanceOf(BusinessLayerException.class);
    }
}

