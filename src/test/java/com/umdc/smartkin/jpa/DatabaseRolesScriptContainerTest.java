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
import java.nio.file.Files;
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

/// `docs/db/smartkin_roles.sql` run with real psql on PostgreSQL, and `docs/db/smartkin_roles_sql_editor.sql` run over JDBC the way a SQL
/// editor (DataGrip, DBeaver, the Supabase editor) runs it: it creates the owner and the application role, the owner can run all
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
            sql.execute("alter database " + postgres.getDatabaseName() + " owner to " + postgres.getUsername());
            for (String role : List.of("smartkin_app", "smartkin_owner", "ops_admin")) {
                // each role on its own: one that does not exist must not stop the others from being dropped
                sql.execute("do $$ begin if exists (select 1 from pg_roles where rolname = '" + role + "') then drop owned by " + role
                        + "; drop role " + role + "; end if; end $$");
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

    private static final Path EDITOR_SCRIPT = Path.of("docs/db/smartkin_roles_sql_editor.sql");

    /// The repository file with its placeholders; a file an operator edited in place would make every test below fail in a confusing way.
    private static String editorTemplate() throws IOException {
        String template = Files.readString(EDITOR_SCRIPT);
        assertTrue(template.contains("'CHANGE_ME_OWNER_PASSWORD'") && template.contains("'CHANGE_ME_APP_PASSWORD'"),
                EDITOR_SCRIPT + " has no CHANGE_ME placeholders (someone wrote real passwords in it). Copy your edited version outside the "
                        + "repository and restore this one: git checkout -- " + EDITOR_SCRIPT);
        return template;
    }

    /// Runs the SQL-editor variant as one statement over JDBC, after editing its three constants the way the operator does.
    private static void runEditorScript(String ownerPassword, String appPassword, String expectedDatabase) throws Exception {
        String sql = editorTemplate()
                .replace("'CHANGE_ME_OWNER_PASSWORD'", "'" + ownerPassword + "'")
                .replace("'CHANGE_ME_APP_PASSWORD'", "'" + appPassword + "'")
                .replace("expected_database constant text := 'umdevc'", "expected_database constant text := '" + expectedDatabase + "'");
        try (var admin = admin(); var statement = admin.createStatement()) {
            statement.execute(sql);
        }
    }

    /// What Supabase's `postgres` is: not a superuser, but it can create roles and databases, has REPLICATION and BYPASSRLS and owns the
    /// database. A non-superuser may not name SUPERUSER, REPLICATION or BYPASSRLS in ALTER ROLE, which the first version of the scripts did.
    private static void createSupabaseLikeAdmin() throws SQLException {
        try (var admin = admin(); var sql = admin.createStatement()) {
            sql.execute("create role ops_admin login createrole createdb replication bypassrls password 'ops-secret'");
            sql.execute("alter database " + postgres.getDatabaseName() + " owner to ops_admin");
        }
    }

    private static ExecResult runScriptAs(String user, String... variables) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("psql", "-U", user, "-d", postgres.getDatabaseName(), "-v", "ON_ERROR_STOP=1", "-q"));
        for (String variable : variables) {
            command.add("-v");
            command.add(variable);
        }
        command.addAll(List.of("-f", SCRIPT));
        return postgres.execInContainer(command.toArray(new String[0]));
    }

    private static void runEditorScriptAs(String user, String password, String ownerPassword, String appPassword) throws Exception {
        String sql = editorTemplate()
                .replace("'CHANGE_ME_OWNER_PASSWORD'", "'" + ownerPassword + "'")
                .replace("'CHANGE_ME_APP_PASSWORD'", "'" + appPassword + "'")
                .replace("expected_database constant text := 'umdevc'", "expected_database constant text := '" + postgres.getDatabaseName() + "'");
        try (var connection = as(user, password); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void assertRolesWorkEndToEnd(String ownerPassword, String appPassword) throws Exception {
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
        migrateAs("smartkin_owner", ownerPassword);
        try (var app = as("smartkin_app", appPassword); var statement = app.createStatement()) {
            statement.execute("insert into smartkin.topics(name) values ('nosu-" + UUID.randomUUID() + "')");
            assertThrows(SQLException.class, () -> statement.execute("create table smartkin.intruder(id int)"));
            assertThrows(SQLException.class, () -> statement.execute("create role intruder"));
        }
    }

    @Test
    void thePsqlVersionWorksForAnAdminThatIsNotASuperuser() throws Exception {
        createSupabaseLikeAdmin();

        ExecResult result = runScriptAs("ops_admin", "owner_password=owner-secret", "app_password=app-secret");

        assertEquals(0, result.getExitCode(), result.getStderr());
        assertRolesWorkEndToEnd("owner-secret", "app-secret");
    }

    @Test
    void theSqlEditorVersionWorksForAnAdminThatIsNotASuperuser() throws Exception {
        createSupabaseLikeAdmin();

        runEditorScriptAs("ops_admin", "ops-secret", "owner-secret", "app-secret");

        assertRolesWorkEndToEnd("owner-secret", "app-secret");
    }

    @Test
    void anAdminThatIsNotASuperuserCanHandOverTablesItCreatedAndRotateTheRoles() throws Exception {
        createSupabaseLikeAdmin();
        migrateAs("ops_admin", "ops-secret");   // an environment provisioned by the platform's postgres role

        runEditorScriptAs("ops_admin", "ops-secret", "owner-secret", "app-secret");
        runEditorScriptAs("ops_admin", "ops-secret", "owner-two", "app-two");

        try (var app = as("smartkin_app", "app-two")) {
            assertEquals("0", scalar(app, "select count(*) from smartkin.child_invitation"));
        }
        migrateAs("smartkin_owner", "owner-two");
    }

    @Test
    void aRoleThatAlreadyHasSuperuserPrivilegesIsReportedNotSilentlyAccepted() throws Exception {
        createSupabaseLikeAdmin();
        try (var admin = admin(); var sql = admin.createStatement()) {
            sql.execute("create role smartkin_owner login superuser password 'x'");
        }

        var error = assertThrows(SQLException.class, () -> runEditorScriptAs("ops_admin", "ops-secret", "owner-secret", "app-secret"));

        assertTrue(error.getMessage().contains("smartkin_owner") && error.getMessage().contains("privileges"), error.getMessage());
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
    void theSqlEditorVariantCreatesTheSameRolesAndTheOwnerMigrates() throws Exception {
        runEditorScript("owner-secret", "app-secret", postgres.getDatabaseName());

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
        migrateAs("smartkin_owner", "owner-secret");
        try (var app = as("smartkin_app", "app-secret"); var statement = app.createStatement()) {
            statement.execute("insert into smartkin.topics(name) values ('editor-" + UUID.randomUUID() + "')");
            assertThrows(SQLException.class, () -> statement.execute("create table smartkin.intruder(id int)"));
            assertThrows(SQLException.class, () -> statement.execute("truncate smartkin.topics"));
            assertThrows(SQLException.class, () -> statement.execute("create role intruder"));
        }
    }

    @Test
    void theSqlEditorVariantHandsOverObjectsAndRotatesPasswords() throws Exception {
        migrateAs(postgres.getUsername(), postgres.getPassword());
        runEditorScript("owner-secret", "app-secret", postgres.getDatabaseName());
        try (var admin = admin()) {
            assertEquals("smartkin_owner", scalar(admin,
                    "select pg_get_userbyid(relowner) from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                            + "where n.nspname = 'smartkin' and c.relname = 'child_invitation'"));
        }

        runEditorScript("new-owner", "new-app", postgres.getDatabaseName());

        assertThrows(SQLException.class, () -> as("smartkin_app", "app-secret"));
        try (var app = as("smartkin_app", "new-app")) {
            assertEquals("0", scalar(app, "select count(*) from smartkin.child_invitation"));
        }
        migrateAs("smartkin_owner", "new-owner");
    }

    @Test
    void theSqlEditorVariantStopsWhileThePasswordsAreStillPlaceholders() throws Exception {
        String untouched = Files.readString(EDITOR_SCRIPT);
        try (var admin = admin(); var statement = admin.createStatement()) {
            var error = assertThrows(SQLException.class, () -> statement.execute(untouched));
            assertTrue(error.getMessage().contains("Edit owner_password and app_password"), error.getMessage());
        }
        try (var admin = admin()) {
            assertEquals("0", scalar(admin, "select count(*) from pg_roles where rolname in ('smartkin_owner', 'smartkin_app')"));
        }
    }

    @Test
    void theSqlEditorVariantRefusesTheWrongDatabase() throws Exception {
        var error = assertThrows(SQLException.class, () -> runEditorScript("owner-secret", "app-secret", "umdevc"));

        assertTrue(error.getMessage().contains("Wrong database"), error.getMessage());
        assertEquals("0", scalarAsAdmin("select count(*) from pg_roles where rolname in ('smartkin_owner', 'smartkin_app')"));
    }

    private static String scalarAsAdmin(String query) throws SQLException {
        try (var admin = admin()) {
            return scalar(admin, query);
        }
    }

    @Test
    void thePsqlVersionSaysWhereToGoWhenAClientIgnoresItsMetaCommands() throws Exception {
        // A SQL editor splits the file into statements and cannot act on \if, so the first plain statement it reaches is the guard
        // `do $$ ... raise exception ... $$` (exactly what an operator saw in DataGrip).
        String psqlOnly = Files.readString(Path.of("docs/db/smartkin_roles.sql"));
        var guard = java.util.regex.Pattern.compile("do \\$\\$.*?\\$\\$;", java.util.regex.Pattern.DOTALL).matcher(psqlOnly);
        assertTrue(guard.find());
        try (var admin = admin(); var statement = admin.createStatement()) {
            var error = assertThrows(SQLException.class, () -> statement.execute(guard.group()));
            assertTrue(error.getMessage().contains("owner_password is required"), error.getMessage());
            assertTrue(error.getMessage().contains("smartkin_roles_sql_editor.sql"), error.getMessage());
        }
        assertTrue(psqlOnly.contains("THIS FILE NEEDS psql"));
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
