package wfederico.backendjavacoretemplate.infra.adapter.out.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import wfederico.backendjavacoretemplate.core.config.JpaAuditingConfig;
import wfederico.backendjavacoretemplate.domain.model.player.PlayerEntity;
import wfederico.backendjavacoretemplate.domain.model.team.TeamEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class PlayerRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void save_persistsPlayer() {
        PlayerEntity player = PlayerEntity.builder()
                .firstName("Lionel")
                .lastName("Messi")
                .position("Forward")
                .alterPosition("Midfielder")
                .build();

        PlayerEntity saved = playerRepository.save(player);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFirstName()).isEqualTo("Lionel");
    }

    @Test
    void findById_returnsPlayerWhenExists() {
        PlayerEntity player = PlayerEntity.builder()
                .firstName("Angel")
                .lastName("Di Maria")
                .position("Winger")
                .alterPosition("Midfielder")
                .build();
        entityManager.persistAndFlush(player);

        Optional<PlayerEntity> found = playerRepository.findById(player.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getLastName()).isEqualTo("Di Maria");
    }

    @Test
    void findById_returnsEmptyWhenNotExists() {
        Optional<PlayerEntity> found = playerRepository.findById(999L);
        assertThat(found).isEmpty();
    }

    @Test
    void findAll_returnsAllPlayers() {
        entityManager.persistAndFlush(PlayerEntity.builder()
                .firstName("A").lastName("B").position("P").alterPosition("AP").build());
        entityManager.persistAndFlush(PlayerEntity.builder()
                .firstName("C").lastName("D").position("P").alterPosition("AP").build());

        assertThat(playerRepository.findAll()).hasSize(2);
    }

    @Test
    void delete_removesPlayer() {
        PlayerEntity player = entityManager.persistAndFlush(PlayerEntity.builder()
                .firstName("X").lastName("Y").position("P").alterPosition("AP").build());

        playerRepository.delete(player);
        entityManager.flush();

        assertThat(playerRepository.findById(player.getId())).isEmpty();
    }

    @Test
    void save_playerWithTeamRelationship() {
        TeamEntity team = entityManager.persistAndFlush(TeamEntity.builder()
                .name("Argentina").city("Buenos Aires").country("AR").build());

        PlayerEntity player = PlayerEntity.builder()
                .firstName("Lionel")
                .lastName("Messi")
                .position("Forward")
                .alterPosition("Midfielder")
                .team(team)
                .build();

        PlayerEntity saved = playerRepository.save(player);
        entityManager.flush();
        entityManager.clear();

        PlayerEntity found = playerRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTeam()).isNotNull();
        assertThat(found.getTeam().getName()).isEqualTo("Argentina");
    }
}



