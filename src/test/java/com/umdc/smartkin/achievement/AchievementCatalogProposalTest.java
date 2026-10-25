package com.umdc.smartkin.achievement;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Keeps the product team's draft `docs/db/achievements_catalog_proposal.sql` runnable: every rule parses with [AchievementRule], ids are unique,
/// names fit the 100 characters of the column, and the three placeholders of migration V5 are kept so running it updates them instead of duplicating.
class AchievementCatalogProposalTest {

    private static final Path PROPOSAL = Path.of("docs", "db", "achievements_catalog_proposal.sql");
    private static final Pattern ROW = Pattern.compile(
            "\\('(a1000000-[0-9a-f-]{27})', '((?:[^']|'')*)', '((?:[^']|'')*)', '([a-z-]+)', (\\d+),\\s*'(\\{[^']*})'\\)");

    @Test
    void everyProposedAchievementHasAValidRuleAndAFittingName() throws IOException {
        var sql = Files.readString(PROPOSAL);
        var matcher = ROW.matcher(sql);
        var ids = new HashSet<String>();
        var mapper = JsonMapper.builder().build();
        var rows = 0;

        while (matcher.find()) {
            rows++;
            assertTrue(ids.add(matcher.group(1)), "duplicated id " + matcher.group(1));
            assertTrue(matcher.group(2).length() <= 100, matcher.group(2));
            assertTrue(Integer.parseInt(matcher.group(5)) > 0, "points must be positive for " + matcher.group(2));
            assertTrue(AchievementRule.parse(matcher.group(6), mapper).isPresent(), "invalid rule " + matcher.group(6));
        }

        assertEquals(8, rows);
        assertTrue(ids.containsAll(java.util.List.of("a1000000-0000-4000-8000-000000000001", "a1000000-0000-4000-8000-000000000002",
                "a1000000-0000-4000-8000-000000000003")));
    }

    @Test
    void theScriptIsIdempotentAndRunsInOneTransaction() throws IOException {
        var sql = Files.readString(PROPOSAL);

        assertTrue(sql.contains("on conflict (achievement_id) do update"));
        assertTrue(sql.contains("begin;") && sql.contains("commit;"));
    }
}
