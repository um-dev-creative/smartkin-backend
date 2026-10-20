package com.umdc.smartkin.jpa;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Proves decision D-3: after V2 only the owner and the smartkin_app role can use the schema; the Supabase API roles cannot,
/// and even an accidental grant to them would show no rows because Row Level Security is on.
@Testcontainers
class DirectAccessRestrictionContainerTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @BeforeAll
    static void migrate() throws SQLException {
        // The roles Supabase provides; a plain PostgreSQL does not have them.
        try (var owner = owner(); Statement sql = owner.createStatement()) {
            sql.execute("create role anon nologin");
            sql.execute("create role authenticated nologin");
            sql.execute("create role service_role nologin");
        }
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("smartkin").schemas("smartkin").createSchemas(true)
                .load().migrate();
        try (var owner = owner(); Statement sql = owner.createStatement()) {
            sql.execute("alter role smartkin_app with login password 'app-secret'");
            sql.execute("alter role anon with login password 'anon-secret'");
            sql.execute("alter role service_role with login password 'service-secret'");
        }
    }

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static Connection as(String user, String password) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), user, password);
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("select count(*) from smartkin." + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    void everyTableHasRowLevelSecurityEnabled() throws SQLException {
        List<String> withoutRls = new ArrayList<>();
        try (var owner = owner(); var statement = owner.createStatement(); var rows = statement.executeQuery("""
                select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'smartkin' and c.relkind = 'r' and c.relname <> 'flyway_schema_history' and not c.relrowsecurity""")) {
            while (rows.next()) {
                withoutRls.add(rows.getString(1));
            }
        }

        assertEquals(List.of(), withoutRls);
    }

    @Test
    void theApplicationRoleCanReadAndWriteRows() throws SQLException {
        try (var app = as("smartkin_app", "app-secret"); var statement = app.createStatement()) {
            statement.executeUpdate("insert into smartkin.topics (name) values ('Screen time')");

            assertTrue(count(app, "topics") >= 1);

            statement.executeUpdate("delete from smartkin.topics where name = 'Screen time'");
        }
    }

    @Test
    void theApplicationRoleCannotChangeTheSchema() throws SQLException {
        try (var app = as("smartkin_app", "app-secret"); var statement = app.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute("create table smartkin.intruder (id int)"));
            assertThrows(SQLException.class, () -> statement.execute("truncate smartkin.topics"));
            assertThrows(SQLException.class, () -> statement.execute("drop table smartkin.topics"));
        }
    }

    @Test
    void theSupabaseApiRolesCannotTouchTheSchema() throws SQLException {
        try (var anon = as("anon", "anon-secret"); var service = as("service_role", "service-secret")) {
            var denied = assertThrows(SQLException.class, () -> count(anon, "topics"));
            assertTrue(denied.getMessage().contains("permission denied"), denied.getMessage());
            assertThrows(SQLException.class, () -> count(service, "child"));
        }
    }

    @Test
    void anAccidentalGrantToAnApiRoleStillShowsNoRows() throws SQLException {
        var id = UUID.randomUUID();
        try (var owner = owner(); Statement sql = owner.createStatement()) {
            sql.executeUpdate("insert into smartkin.rewards (reward_id, name, description, points_cost) values ('" + id + "', 'Game time', '30 min', 100)");
            sql.execute("grant usage on schema smartkin to anon");
            sql.execute("grant select on smartkin.rewards to anon");
            try (var anon = as("anon", "anon-secret")) {
                assertEquals(0, count(anon, "rewards"));
            }
            assertEquals(1, count(owner, "rewards"));
            sql.execute("revoke select on smartkin.rewards from anon");
            sql.execute("revoke usage on schema smartkin from anon");
            sql.executeUpdate("delete from smartkin.rewards where reward_id = '" + id + "'");
        }
    }
}
