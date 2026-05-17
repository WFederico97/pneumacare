package wfederico.backendjavacoretemplate.domain.model.team;

import jakarta.persistence.*;
import lombok.*;
import wfederico.backendjavacoretemplate.core.data.EntityBase;
import wfederico.backendjavacoretemplate.domain.model.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamEntity extends EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<PlayerEntity> players = new ArrayList<>();
}

