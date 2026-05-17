package wfederico.backendjavacoretemplate.infra.adapter.out.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import wfederico.backendjavacoretemplate.application.port.out.PlayerPersistencePort;
import wfederico.backendjavacoretemplate.domain.model.player.PlayerEntity;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PlayerPersistenceAdapter implements PlayerPersistencePort {

    private final PlayerRepository playerRepository;

    @Override
    public List<PlayerEntity> findAll() {
        return playerRepository.findAll();
    }

    @Override
    public Page<PlayerEntity> findAll(Pageable pageable) {
        return playerRepository.findAll(pageable);
    }

    @Override
    public Optional<PlayerEntity> findById(Long id) {
        return playerRepository.findById(id);
    }

    @Override
    public PlayerEntity save(PlayerEntity entity) {
        return playerRepository.save(entity);
    }

    @Override
    public void delete(PlayerEntity entity) {
        playerRepository.delete(entity);
    }
}

