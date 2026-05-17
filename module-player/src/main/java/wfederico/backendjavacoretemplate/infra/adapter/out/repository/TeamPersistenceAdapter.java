package wfederico.backendjavacoretemplate.infra.adapter.out.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.backendjavacoretemplate.application.port.out.TeamPersistencePort;
import wfederico.backendjavacoretemplate.domain.model.team.TeamEntity;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TeamPersistenceAdapter implements TeamPersistencePort {

    private final TeamRepository teamRepository;

    @Override
    public Optional<TeamEntity> findById(Long id) {
        return teamRepository.findById(id);
    }

    @Override
    public TeamEntity save(TeamEntity entity) {
        return teamRepository.save(entity);
    }

    @Override
    public void delete(TeamEntity entity) {
        teamRepository.delete(entity);
    }
}

