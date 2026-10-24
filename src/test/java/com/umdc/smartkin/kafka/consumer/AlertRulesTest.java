package com.umdc.smartkin.kafka.consumer;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Keeps `docs/ops/smartkin-events.rules.yml` honest: it must parse, every rule must be complete, and every metric it queries must still
/// be one that [ErrorTopicMonitor] registers (Micrometer name with dots turned into underscores and `_total` for counters).
class AlertRulesTest {

    private static final Path RULES = Path.of("docs", "ops", "smartkin-events.rules.yml");
    private static final Pattern METRIC = Pattern.compile("smartkin_events_[a-z_]+");

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rules() throws IOException {
        Map<String, Object> file;
        try (var reader = Files.newBufferedReader(RULES)) {
            file = new Yaml().load(reader);
        }
        var groups = (List<Map<String, Object>>) file.get("groups");
        return groups.stream().flatMap(group -> ((List<Map<String, Object>>) group.get("rules")).stream()).toList();
    }

    private static String prometheusName(String micrometerName) {
        return micrometerName.replace('.', '_') + "_total";
    }

    @Test
    void everyRuleHasANameAnExpressionASeverityAndAnExplanation() throws IOException {
        var rules = rules();

        assertEquals(Set.of("SmartKinEventDeadLettered", "SmartKinDeadLetterFailed", "SmartKinEventRetriesHigh"),
                rules.stream().map(rule -> (String) rule.get("alert")).collect(Collectors.toSet()));
        for (var rule : rules) {
            var labels = (Map<?, ?>) rule.get("labels");
            var annotations = (Map<?, ?>) rule.get("annotations");
            assertFalse(((String) rule.get("expr")).isBlank(), rule.get("alert").toString());
            assertTrue(Set.of("warning", "critical").contains(labels.get("severity")), rule.get("alert").toString());
            assertFalse(((String) annotations.get("summary")).isBlank());
            assertFalse(((String) annotations.get("description")).isBlank());
        }
    }

    @Test
    void everyQueriedMetricIsOneTheCodeRegisters() throws IOException {
        var emitted = Set.of(prometheusName(ErrorTopicMonitor.DELIVERY_FAILED), prometheusName(ErrorTopicMonitor.DEAD_LETTERED),
                prometheusName(ErrorTopicMonitor.DEAD_LETTER_FAILED));
        var queried = rules().stream().flatMap(rule -> METRIC.matcher((String) rule.get("expr")).results().map(match -> match.group()))
                .collect(Collectors.toSet());

        assertEquals(emitted, queried);
    }

    @Test
    void theCriticalAlertIsTheOneForTheErrorTopicItself() throws IOException {
        var critical = rules().stream().filter(rule -> "critical".equals(((Map<?, ?>) rule.get("labels")).get("severity"))).toList();

        assertEquals(1, critical.size());
        assertTrue(((String) critical.get(0).get("expr")).contains(prometheusName(ErrorTopicMonitor.DEAD_LETTER_FAILED)));
    }
}
