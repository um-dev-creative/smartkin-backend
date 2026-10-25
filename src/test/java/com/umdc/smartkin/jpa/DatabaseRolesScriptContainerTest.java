package com.umdc.smartkin.jpa;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// `docs/db/smartkin_roles.sql` run with real psql on PostgreSQL: it creates the owner and the application role, the owner can run all
/// the Flyway migrations, the application role can use the data and nothing else, and the script can be run again.
@Testcontainers
class DatabaseRolesScriptContainerTest {

    private static final String SCRIPT = "/tmp/smartkin_roles.sql";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docs/db/smartkin_roles.sql")), SCRIPT);

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (var admin = admin(); var sql = admin.createStatement()) {
            sql.execute("drop schema if exists smartkin cascade");
            sql.execute("drop owned by smartkin_app");
            sql.execute("drop role if exists smartkin_app");
            sql.execute("drop owned by smartkin_owner");
            sql.execute("drop role if exists smartkin_owner");
        } catch (SQLException e) {
            // first run: the roles do not exist yet
            try (var admin = admin(); var sql = admin.createStatement()) {
                sql.execute("drop schema if exists smartkin cascade");
            }
        }
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static Connection as(String user, String password) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), user, password);
    }

    private static ExecResult runScript(String... variables) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("psql", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(),
                "-v", "ON_ERROR_STOP=1", "-q"));
        for (String variable : variables) {
            command.add("-v");
            command.add(variable);
        }
        command.addAll(List.of("-f", SCRIPT));
        return postgres.execInContainer(command.toArray(new String[0]));
    }

    private static void migrateAs(String user, String password) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), user, password)
                .locations("classpath:db/migration")
                .defaultSchema("smartkin").schemas("smartkin").createSchemas(true)
                .load().migrate();
    }

    private static String scalar(Connection connection, String query) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            rows.next();
            return rows.getString(1);
        }
    }

    @Test
    void createsTwoLoginRolesWithoutPrivilegesBeyondTheirJob() throws Exception {
        ExecResult result = runScript("owner_password=owner-secret", "app_password=app-secret");

        assertEquals(0, result.getExitCode(), result.getStderr());
        try (var admin = admin(); var statement = admin.createStatement(); var rows = statement.executeQuery(
                "select rolname, rolcanlogin, rolsuper, rolcreaterole, rolcreatedb, rolbypassrls, rolreplication from pg_roles "
                        + "where rolname in ('smartkin_owner', 'smartkin_app') order by rolname")) {
            int seen = 0;
            while (rows.next()) {
                seen++;
                assertTrue(rows.getBoolean("rolcanlogin"), rows.getString("rolname"));
                assertFalse(rows.getBoolean("rolsuper"), rows.getString("rolname"));
                assertFalse(rows.getBoolean("rolcreaterole"), rows.getString("rolname"));
                assertFalse(rows.getBoolean("rolcreatedb"), rows.getString("rolname"));
                assertFalse(rows.getBoolean("rolbypassrls"), rows.getString("rolname"));
                assertFalse(rows.getBoolean("rolreplication"), rows.getString("rolname"));
            }
            assertEquals(2, seen);
        }
        try (var admin = admin()) {
            assertEquals("smartkin_owner", scalar(admin, "select pg_get_userbyid(nspowner) from pg_namespace where nspname = 'smartkin'"));
        }
    }

    @Test
    void theOwnerRunsEveryMigrationAndTheApplicationRoleOnlyUsesTheData() throws Exception {
        assertEquals(0, runScript("owner_password=owner-secret", "app_password=app-secret").getExitCode());

        migrateAs("smartkin_owner", "owner-secret");

        try (var app = as("smartkin_app", "app-secret"); var statement = app.createStatement()) {
            UUID parent = UUID.randomUUID();
            statement.execute("insert into smartkin.topics(name) values ('t-" + parent + "')");
            assertEquals("1", scalar(app, "select count(*) from smartkin.topics where name = 't-" + parent + "'"));
            statement.execute("update smartkin.topics set name = 'u-" + parent + "' where name = 't-" + parent + "'");
            statement.execute("delete from smartkin.topics where name = 'u-" + parent + "'");
            assertThrows(SQLException.class, () -> statement.execute("create table smartkin.intruder(id int)"));
            assertThrows(SQLException.class, () -> statement.execute("drop table smartkin.topics"));
            assertThrows(SQLException.class, () -> statement.execute("truncate smartkin.topics"));
            assertThrows(SQLException.class, () -> statement.execute("alter table smartkin.topics add column x int"));
            assertThrows(SQLException.class, () -> statement.execute("create role intruder"));
        }
        try (var admin = admin()) {
            // every table and enum, V6's child_invitation included, belongs to the owner
            assertEquals("0", scalar(admin, "select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                    + "where n.nspname = 'smartkin' and c.relkind in ('r', 'S') and pg_get_userbyid(c.relowner) <> 'smartkin_owner' "
                    + "and not exists (select 1 from pg_depend d where d.objid = c.oid and d.deptype in ('a', 'i') and c.relkind = 'S')"));
        }
    }

    @Test
    void tablesCreatedByAnotherRoleBeforeTheScriptMoveToTheOwner() throws Exception {
        // an environment migrated as postgres, the way the schema was provisioned before the roles existed
        migrateAs(postgres.getUsername(), postgres.getPassword());

        ExecResult result = runScript("owner_password=owner-secret", "app_password=app-secret");

        assertEquals(0, result.getExitCode(), result.getStderr());
        try (var admin = admin()) {
            assertEquals("smartkin_owner", scalar(admin,
                    "select pg_get_userbyid(relowner) from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                            + "where n.nspname = 'smartkin' and c.relname = 'child_invitation'"));
            assertEquals("smartkin_owner", scalar(admin,
                    "select pg_get_userbyid(typowner) from pg_type t join pg_namespace n on n.oid = t.typnamespace "
                            + "where n.nspname = 'smartkin' and t.typname = 'goal_status'"));
        }
        try (var app = as("smartkin_app", "app-secret"); var statement = app.createStatement()) {
            statement.execute("select count(*) from smartkin.child_invitation");
        }
        migrateAs("smartkin_owner", "owner-secret");   // nothing pending, and the owner can still run Flyway
    }

    @Test
    void runningItAgainIsHarmlessAndRotatesThePasswords() throws Exception {
        assertEquals(0, runScript("owner_password=owner-secret", "app_password=app-secret").getExitCode());
        migrateAs("smartkin_owner", "owner-secret");

        ExecResult again = runScript("owner_password=new-owner", "app_password=new-app");

        assertEquals(0, again.getExitCode(), again.getStderr());
        assertThrows(SQLException.class, () -> as("smartkin_app", "app-secret"));
        try (var app = as("smartkin_app", "new-app")) {
            assertEquals("0", scalar(app, "select count(*) from smartkin.child_invitation"));
        }
        try (var owner = as("smartkin_owner", "new-owner")) {
            assertNotEquals("", scalar(owner, "select current_user"));
        }
    }

    @Test
    void aPasswordIsRequired() throws Exception {
        ExecResult noOwner = runScript("app_password=app-secret");
        ExecResult noApp = runScript("owner_password=owner-secret");

        assertNotEquals(0, noOwner.getExitCode());
        assertTrue(noOwner.getStderr().contains("owner_password is required"), noOwner.getStderr());
        assertNotEquals(0, noApp.getExitCode());
        assertTrue(noApp.getStderr().contains("app_password is required"), noApp.getStderr());
        try (var admin = admin()) {
            assertEquals("0", scalar(admin, "select count(*) from pg_roles where rolname in ('smartkin_owner', 'smartkin_app')"));
        }
    }

    @Test
    void refusesToRunInTheWrongDatabase() throws Exception {
        ExecResult wrong = runScript("owner_password=owner-secret", "app_password=app-secret", "expected_database=umdevc");
        ExecResult right = runScript("owner_password=owner-secret", "app_password=app-secret",
                "expected_database=" + postgres.getDatabaseName());

        assertNotEquals(0, wrong.getExitCode());
        assertTrue(wrong.getStderr().contains("Wrong database"), wrong.getStderr());
        assertEquals(0, right.getExitCode(), right.getStderr());
    }
}
