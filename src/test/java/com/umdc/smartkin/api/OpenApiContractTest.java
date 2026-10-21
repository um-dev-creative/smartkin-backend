package com.umdc.smartkin.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Keeps `docs/api/openapi-v1.yaml` (X-2) well formed, so the backend controllers and the frontend client can be built against it.
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("docs", "api", "openapi-v1.yaml");
    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch");

    private static Map<String, Object> contract;

    @BeforeAll
    static void loadContract() throws IOException {
        try (var reader = Files.newBufferedReader(CONTRACT)) {
            contract = new Yaml().load(reader);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object node) {
        return (Map<String, Object>) node;
    }

    private static List<Map<String, Object>> operations() {
        var operations = new ArrayList<Map<String, Object>>();
        map(contract.get("paths")).values().forEach(item ->
                map(item).forEach((key, value) -> {
                    if (HTTP_METHODS.contains(key)) {
                        operations.add(map(value));
                    }
                }));
        return operations;
    }

    private static void collectRefs(Object node, List<String> refs) {
        if (node instanceof Map<?, ?> children) {
            children.forEach((key, value) -> {
                if ("$ref".equals(key)) {
                    refs.add((String) value);
                } else {
                    collectRefs(value, refs);
                }
            });
        } else if (node instanceof List<?> items) {
            items.forEach(item -> collectRefs(item, refs));
        }
    }

    private static Object resolve(String ref) {
        Object node = contract;
        for (var part : ref.substring(2).split("/")) {
            node = map(node).get(part);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    @Test
    void declaresTheVersionAndTheSessionTokenSecurity() {
        assertEquals("3.0.3", contract.get("openapi"));
        var scheme = map(map(map(contract.get("components")).get("securitySchemes")).get("sessionToken"));
        assertEquals("apiKey", scheme.get("type"));
        assertEquals("header", scheme.get("in"));
        assertEquals("session-token", scheme.get("name"));
        assertEquals(List.of(Map.of("sessionToken", List.of())), contract.get("security"));
    }

    @Test
    void everyOperationHasAUniqueIdAndASuccessResponse() {
        var ids = new HashSet<String>();
        for (var operation : operations()) {
            var id = (String) operation.get("operationId");
            assertNotNull(id, "operationId missing");
            assertTrue(ids.add(id), "duplicated operationId " + id);
            assertTrue(map(operation.get("responses")).keySet().stream().anyMatch(code -> code.toString().startsWith("2")),
                    id + " has no 2xx response");
        }
    }

    @Test
    void coversTheOperationsOfTheMvpSlice() {
        var ids = operations().stream().map(operation -> (String) operation.get("operationId")).toList();
        assertEquals(25, ids.size());
        assertTrue(ids.containsAll(List.of("linkChild", "listMyChildren", "listTopicLessons", "getLesson", "assignActivity",
                "listAssignments", "updateProgress", "submitQuizAttempt", "getChildPoints", "listChildAchievements",
                "listNotifications", "markNotificationRead", "markAllNotificationsRead", "getUnreadNotificationCount")));
    }

    @Test
    void everyReferenceResolves() {
        var refs = new ArrayList<String>();
        collectRefs(contract, refs);
        assertFalse(refs.isEmpty());
        for (var ref : refs) {
            assertTrue(ref.startsWith("#/"), ref);
            assertNotNull(resolve(ref), "unresolved " + ref);
        }
    }

    @Test
    void keepsTheCorrectAnswerOutOfTheLessonOptions() {
        var option = map(map(map(map(contract.get("components")).get("schemas")).get("Option")).get("properties"));
        assertFalse(option.containsKey("correct"));
        assertEquals(Set.of("id", "text"), option.keySet());
    }

    @Test
    void doesNotRedefineTheExistingUserEndpoints() {
        var paths = map(contract.get("paths")).keySet();
        assertTrue(paths.stream().noneMatch(path -> path.startsWith("/users") || path.startsWith("/user-register")));
    }

    @Test
    void pagedListsShareTheSameEnvelope() {
        var schemas = map(map(contract.get("components")).get("schemas"));
        var page = map(map(schemas.get("PageMeta")).get("properties")).keySet();
        assertEquals(Set.of("page", "size", "totalElements", "totalPages"), page);
        for (var name : List.of("AssignmentPage", "NotificationPage")) {
            var refs = new ArrayList<String>();
            collectRefs(schemas.get(name), refs);
            assertTrue(refs.contains("#/components/schemas/PageMeta"), name);
        }
    }
}
