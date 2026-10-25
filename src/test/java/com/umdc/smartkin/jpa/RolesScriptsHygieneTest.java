package com.umdc.smartkin.jpa;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The database-roles scripts in `docs/db` are run by operators, who fill in two passwords. If they do it in the repository file, real
/// passwords end up in a versioned file and the container tests (which substitute the `CHANGE_ME` placeholders) fail in confusing ways.
/// This test needs no Docker, so it also fails a pull request that carries a password.
class RolesScriptsHygieneTest {

    private static final Path EDITOR_SCRIPT = Path.of("docs/db/smartkin_roles_sql_editor.sql");
    private static final String RESTORE = " Copy your edited version outside the repository (for example ~/smartkin_roles.local.sql) and restore the "
            + "original: git checkout -- " + EDITOR_SCRIPT;
    private static final Pattern PASSWORD_ASSIGNMENT =
            Pattern.compile("(?m)^\\s*(owner_password|app_password)\\s+constant\\s+text\\s*:=\\s*'([^']*)'");

    @Test
    void theEditorScriptKeepsBothPlaceholdersAndTheExpectedDatabase() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        assertTrue(script.contains("owner_password    constant text := 'CHANGE_ME_OWNER_PASSWORD'"),
                "the owner password placeholder is gone." + RESTORE);
        assertTrue(script.contains("app_password      constant text := 'CHANGE_ME_APP_PASSWORD'"),
                "the application password placeholder is gone." + RESTORE);
        assertTrue(script.contains("expected_database constant text := 'umdevc'"),
                "the expected database was changed." + RESTORE);
    }

    @Test
    void noSqlFileOfDocsDbHoldsALiteralPassword() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("docs/db"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".sql")).toList()) {
                var matcher = PASSWORD_ASSIGNMENT.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (!matcher.group(2).startsWith("CHANGE_ME")) {
                        found.add(file + " sets " + matcher.group(1) + " to a literal value");
                    }
                }
            }
        }

        assertEquals(List.of(), found, "a password was written in a versioned file." + RESTORE);
    }

    @Test
    void theGitignoreKeepsLocalCopiesOutOfTheRepository() throws IOException {
        assertTrue(Files.readString(Path.of(".gitignore")).lines().anyMatch("*.local.sql"::equals),
                "*.local.sql must be git-ignored so a filled-in copy of the script cannot be committed");
    }
}
