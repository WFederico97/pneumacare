package wfederico.backendjavacoretemplate.application.port.out;

import wfederico.backendjavacoretemplate.domain.model.team.TeamEntity;

import java.util.Optional;

public interface TeamPersistencePort {

    Optional<TeamEntity> findById(Long id);

    TeamEntity save(TeamEntity entity);

    void delete(TeamEntity entity);
}

