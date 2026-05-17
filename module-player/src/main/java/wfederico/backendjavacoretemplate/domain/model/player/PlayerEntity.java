package wfederico.backendjavacoretemplate.domain.model.player;

import jakarta.persistence.*;
import lombok.*;
import wfederico.backendjavacoretemplate.core.data.EntityBase;
import wfederico.backendjavacoretemplate.domain.model.team.TeamEntity;

@Entity
@Table(name = "players", indexes = {
        @Index(name = "idx_players_last_name", columnList = "last_name"),
        @Index(name = "idx_players_position",  columnList = "position"),
        @Index(name = "idx_players_team_id",   columnList = "team_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerEntity extends EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String position;

    @Column(name = "alter_position", nullable = false)
    private String alterPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private TeamEntity team;
}

