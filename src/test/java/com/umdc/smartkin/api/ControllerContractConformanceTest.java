package com.umdc.smartkin.api;

import com.umdc.smartkin.api.v1.controller.ChildController;
import com.umdc.smartkin.api.v1.controller.ParentController;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Checks that the implemented controllers expose exactly the operations (id, HTTP method and path) the contract
/// `docs/api/openapi-v1.yaml` defines for them. Each controller ticket adds its controller to [#IMPLEMENTED].
class ControllerContractConformanceTest {

    private static final String BASE = "/api/v1";
    private static final List<Class<?>> IMPLEMENTED = List.of(ParentController.class, ChildController.class);

    @SuppressWarnings("unchecked")
    private static Map<String, String> contractOperations() throws IOException {
        Map<String, Object> contract;
        try (var reader = Files.newBufferedReader(Path.of("docs", "api", "openapi-v1.yaml"))) {
            contract = new Yaml().load(reader);
        }
        var operations = new TreeMap<String, String>();
        ((Map<String, Object>) contract.get("paths")).forEach((path, item) ->
                ((Map<String, Object>) item).forEach((key, value) -> {
                    if (List.of("get", "put", "post", "delete", "patch").contains(key)) {
                        operations.put(((Map<String, Object>) value).get("operationId").toString(), key.toUpperCase() + " " + path);
                    }
                }));
        return operations;
    }

    private static Map<String, String> implementedOperations(Class<?> controller) {
        var base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class).path()[0];
        var operations = new TreeMap<String, String>();
        for (var method : controller.getMethods()) {
            var operation = AnnotatedElementUtils.findMergedAnnotation(method, Operation.class);
            var mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (operation == null || mapping == null) {
                continue;
            }
            RequestMethod verb = mapping.method()[0];
            var suffix = mapping.path().length == 0 ? "" : mapping.path()[0];
            operations.put(operation.operationId(), verb + " " + (base + suffix).substring(BASE.length()));
        }
        return operations;
    }

    @Test
    void everyImplementedOperationMatchesTheContract() throws IOException {
        var contract = contractOperations();
        for (var controller : IMPLEMENTED) {
            var implemented = implementedOperations(controller);
            assertFalse(implemented.isEmpty(), controller.getSimpleName() + " exposes no documented operation");
            implemented.forEach((id, verbAndPath) -> assertEquals(contract.get(id), verbAndPath,
                    controller.getSimpleName() + "." + id + " differs from the contract"));
        }
    }

    @Test
    void theParentAndChildControllersCoverTheirTags() throws IOException {
        var contract = contractOperations();
        var expected = Arrays.asList("getMyParentProfile", "putMyParentProfile", "listMyChildren", "linkChild", "getChild", "updateChild");
        var implemented = new TreeMap<String, String>();
        IMPLEMENTED.forEach(controller -> implemented.putAll(implementedOperations(controller)));

        assertEquals(new java.util.TreeSet<>(expected), implemented.keySet());
        expected.forEach(id -> assertEquals(contract.get(id), implemented.get(id)));
    }
}
