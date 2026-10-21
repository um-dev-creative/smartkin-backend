package com.umdc.smartkin.achievement;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementRuleTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void readsTheThreeSupportedTypes() {
        assertEquals(new AchievementRule(AchievementRuleType.QUIZZES_PASSED, 3),
                AchievementRule.parse("{\"type\":\"QUIZZES_PASSED\",\"threshold\":3}", mapper).orElseThrow());
        assertEquals(new AchievementRule(AchievementRuleType.ACTIVITIES_COMPLETED, 1),
                AchievementRule.parse("{\"type\":\"ACTIVITIES_COMPLETED\",\"threshold\":1}", mapper).orElseThrow());
        assertEquals(new AchievementRule(AchievementRuleType.POINTS_REACHED, 100),
                AchievementRule.parse("{\"type\":\"POINTS_REACHED\",\"threshold\":100,\"note\":\"extra fields are ignored\"}", mapper).orElseThrow());
    }

    @Test
    void ignoresRequirementsThatAreNotAValidRule() {
        for (var bad : new String[]{"complete one lesson", "{}", "{\"type\":\"STREAK_DAYS\",\"threshold\":3}", "{\"type\":\"POINTS_REACHED\"}",
                "{\"type\":\"POINTS_REACHED\",\"threshold\":0}", "{\"type\":\"POINTS_REACHED\",\"threshold\":-5}", "{\"threshold\":3}", "null", ""}) {
            assertTrue(AchievementRule.parse(bad, mapper).isEmpty(), bad);
        }
    }
}
