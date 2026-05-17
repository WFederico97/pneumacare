package wfederico.backendjavacoretemplate.application.port.out;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import wfederico.backendjavacoretemplate.domain.model.player.PlayerEntity;

import java.util.List;
import java.util.Optional;

public interface PlayerPersistencePort {

    List<PlayerEntity> findAll();

    Page<PlayerEntity> findAll(Pageable pageable);

    Optional<PlayerEntity> findById(Long id);

    PlayerEntity save(PlayerEntity entity);

    void delete(PlayerEntity entity);
}

