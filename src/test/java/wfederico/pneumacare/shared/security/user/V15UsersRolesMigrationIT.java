package wfederico.pneumacare.shared.security.user;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the V15 migration, applied on top of V1–V14, produces the
 * auth-foundation schema: a reshaped {@code users} table, a canonical
 * {@code user_roles} table whose {@code role} column is CHECK-constrained, and no
 * {@code roles} table.
 *
 * <p>Runs Flyway directly against a throwaway Postgres container rather than
 * loading the Spring context. This isolates the migration SQL itself and matches
 * what Flyway does in staging/prod — the project's other integration tests use
 * the {@code dev} profile (Hibernate {@code ddl-auto: update}), which never
 * exercises the migrations.
 *
 * <p>Disabled by convention; run individually:
 *   mvnw.cmd test -Dtest=V15UsersRolesMigrationIT
 */
@Disabled("Integration test — run individually with -Dtest=V15UsersRolesMigrationIT")
class V15UsersRolesMigrationIT {

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startAndMigrate() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void usersGainsIdentityColumnsAndDropsMultiTenancy() throws Exception {
        try (Connection c = connection()) {
            assertThat(columnExists(c, "users", "display_name")).isTrue();
            assertThat(columnExists(c, "users", "enabled")).isTrue();
            assertThat(columnExists(c, "users", "created_at")).isTrue();
            assertThat(columnExists(c, "users", "updated_at")).isTrue();
            assertThat(columnExists(c, "users", "hospital_id")).isFalse();
            assertThat(columnExists(c, "users", "status")).isFalse();
        }
    }

    @Test
    void rolesTableIsDroppedAndUserRolesRemains() throws Exception {
        try (Connection c = connection()) {
            assertThat(tableExists(c, "roles")).isFalse();
            assertThat(tableExists(c, "user_roles")).isTrue();
            assertThat(columnExists(c, "user_roles", "role")).isTrue();
            assertThat(columnExists(c, "user_roles", "role_id")).isFalse();
        }
    }

    @Test
    void userRolesAcceptsCanonicalRoleButRejectsOthers() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (id, username, password_hash) "
                    + "VALUES (gen_random_uuid(), 'check_user', 'bcrypt-hash')");

            String userId;
            try (ResultSet rs = s.executeQuery(
                    "SELECT id FROM users WHERE username = 'check_user'")) {
                assertThat(rs.next()).isTrue();
                userId = rs.getString(1);
            }

            insertRole(c, userId, "ROLE_ADMIN"); // canonical — succeeds

            Throwable rejected = catchThrowable(() -> insertRole(c, userId, "ROLE_HACKER"));
            assertThat(rejected).isInstanceOf(SQLException.class);
            assertThat(rejected.getMessage()).contains("ck_user_roles_role");
        }
    }

    private void insertRole(Connection c, String userId, String role) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO user_roles (user_id, role) VALUES (?::uuid, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, role);
            ps.executeUpdate();
        }
    }

    private boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean columnExists(Connection c, String table, String column) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
