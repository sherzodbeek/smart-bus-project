package com.smartbus.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OpenApiContractValidationTests {

  private static final Path CONTRACT_DIRECTORY = Path.of("..", "..", "contracts", "openapi");

  @Test
  void openApiContractsArePresentAndMachineReadable() throws IOException {
    List<String> expectedFiles = List.of(
        "gateway.v1.yaml",
        "booking-service.v1.yaml",
        "schedule-service.v1.yaml",
        "payment-service.v1.yaml",
        "notification-service.v1.yaml"
    );

    for (String fileName : expectedFiles) {
      Path contractPath = CONTRACT_DIRECTORY.resolve(fileName);
      assertTrue(Files.exists(contractPath), "Missing contract file " + contractPath);

      Map<?, ?> document;
      try (InputStream inputStream = Files.newInputStream(contractPath)) {
        document = new Yaml().load(inputStream);
      }

      assertNotNull(document, "Contract should parse to a document: " + fileName);
      assertEquals("3.1.0", document.get("openapi"), "Unexpected OpenAPI version for " + fileName);

      Map<?, ?> info = nestedMap(document, "info");
      assertFalse(info.isEmpty(), "Missing info section in " + fileName);
      assertNotNull(info.get("title"), "Missing info.title in " + fileName);
      assertNotNull(info.get("version"), "Missing info.version in " + fileName);

      Map<?, ?> paths = nestedMap(document, "paths");
      assertFalse(paths.isEmpty(), "Missing paths section in " + fileName);

      boolean foundExample = false;
      for (Object pathItemValue : paths.values()) {
        Map<?, ?> pathItem = assertMap(pathItemValue);
        for (Object operationValue : pathItem.values()) {
          Map<?, ?> operation = assertMap(operationValue);
          assertNotNull(operation.get("operationId"), "Missing operationId in " + fileName);
          assertNotNull(operation.get("responses"), "Missing responses in " + fileName);
          if (containsExample(operation)) {
            foundExample = true;
          }
        }
      }

      assertTrue(foundExample, "Expected request or response examples in " + fileName);

      Map<?, ?> components = nestedMap(document, "components");
      Map<?, ?> schemas = nestedMap(components, "schemas");
      assertFalse(schemas.isEmpty(), "Missing components.schemas in " + fileName);
      if (!"gateway.v1.yaml".equals(fileName)) {
        assertTrue(schemas.containsKey("ApiErrorResponse"), "Missing ApiErrorResponse schema in " + fileName);
      }
    }
  }

  private Map<?, ?> nestedMap(Map<?, ?> source, String key) {
    Object value = source.get(key);
    return value == null ? Map.of() : assertMap(value);
  }

  private Map<?, ?> assertMap(Object value) {
    assertInstanceOf(Map.class, value);
    return (Map<?, ?>) value;
  }

  private boolean containsExample(Map<?, ?> operation) {
    Object requestBody = operation.get("requestBody");
    if (requestBody instanceof Map<?, ?> requestBodyMap && mediaTypeHasExample(nestedMap(requestBodyMap, "content"))) {
      return true;
    }

    Object responses = operation.get("responses");
    if (responses instanceof Map<?, ?> responsesMap) {
      for (Object responseValue : responsesMap.values()) {
        if (responseValue instanceof Map<?, ?> responseMap && mediaTypeHasExample(nestedMap(responseMap, "content"))) {
          return true;
        }
      }
    }

    return false;
  }

  private boolean mediaTypeHasExample(Map<?, ?> content) {
    for (Object mediaTypeValue : content.values()) {
      if (mediaTypeValue instanceof Map<?, ?> mediaTypeMap) {
        if (mediaTypeMap.containsKey("example") || mediaTypeMap.containsKey("examples")) {
          return true;
        }
      }
    }
    return false;
  }
}
