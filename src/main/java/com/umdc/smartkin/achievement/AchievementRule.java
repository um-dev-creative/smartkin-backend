package com.umdc.smartkin.achievement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/// An achievement rule: the child earns it when the count of `type` reaches `threshold`. It is stored as JSON in `achievements.requirements`,
/// for example `{"type":"QUIZZES_PASSED","threshold":3}`.
///
/// @param type      what is counted
/// @param threshold the count that earns the achievement, at least 1
public record AchievementRule(AchievementRuleType type, int threshold) {

    private static final Logger logger = LoggerFactory.getLogger(AchievementRule.class);

    /// Reads a rule from the text of `achievements.requirements`.
    ///
    /// @param requirements the stored text
    /// @param mapper       the JSON mapper
    /// @return the rule, or empty when the text is not a valid rule (unknown type, missing or non positive threshold, not JSON); such an
    ///         achievement is never awarded
    public static Optional<AchievementRule> parse(String requirements, ObjectMapper mapper) {
        try {
            var rule = mapper.readValue(requirements, AchievementRule.class);
            if (rule == null || rule.type() == null || rule.threshold() < 1) {
                logger.warn("Ignoring achievement requirements without a valid type and threshold: {}", requirements);
                return Optional.empty();
            }
            return Optional.of(rule);
        } catch (JacksonException e) {
            logger.warn("Ignoring achievement requirements that are not a rule: {}", requirements);
            return Optional.empty();
        }
    }
}
