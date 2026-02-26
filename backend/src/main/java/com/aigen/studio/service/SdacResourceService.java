package com.aigen.studio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class SdacResourceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ResourceLoader resourceLoader;

    @Value("${aigen.sdac.schemas.ui-spec:classpath:sdac/schemas/ui_spec.v1.schema.json}")
    private String uiSpecSchemaLocation;

    @Value("${aigen.sdac.schemas.implementation-plan:classpath:sdac/schemas/implementation_plan.v1.schema.json}")
    private String implementationPlanSchemaLocation;

    @Value("${aigen.sdac.schemas.evidence-manifest:classpath:sdac/schemas/evidence_manifest.v1.schema.json}")
    private String evidenceManifestSchemaLocation;

    @Value("${aigen.sdac.schemas.preview-contract:classpath:sdac/schemas/preview_contract.v1.schema.json}")
    private String previewContractSchemaLocation;

    @Value("${aigen.sdac.templates.ai2ai-state-update:classpath:sdac/templates/ai2ai_state_update.template.md}")
    private String ai2aiStateUpdateTemplateLocation;

    @Value("${aigen.sdac.gates.rules:classpath:sdac/gates/gate_rules.v1.yml}")
    private String gateRulesLocation;

    private volatile Map<String, String> cachedDefaultGateStatuses;

    public void validateUiSpec(Map<String, Object> payload) {
        validateBySchemaLocation(uiSpecSchemaLocation, payload, "UI_Spec.v1");
    }

    public void validateImplementationPlan(Map<String, Object> payload) {
        validateBySchemaLocation(implementationPlanSchemaLocation, payload, "Implementation_Plan.v1");
    }

    public void validateEvidenceManifest(Map<String, Object> payload) {
        validateBySchemaLocation(evidenceManifestSchemaLocation, payload, "Evidence_Manifest.v1");
    }

    public void validatePreviewContract(Map<String, Object> payload) {
        validateBySchemaLocation(previewContractSchemaLocation, payload, "Preview_Contract.v1");
    }

    public Map<String, String> loadDefaultGateStatuses() {
        Map<String, String> local = cachedDefaultGateStatuses;
        if (local != null) {
            return new LinkedHashMap<>(local);
        }

        synchronized (this) {
            if (cachedDefaultGateStatuses != null) {
                return new LinkedHashMap<>(cachedDefaultGateStatuses);
            }

            LinkedHashMap<String, String> defaults = new LinkedHashMap<>();
            defaults.put("REQ", "PENDING");
            defaults.put("UI", "PENDING");
            defaults.put("IMP", "PENDING");
            defaults.put("PREVIEW", "PENDING");

            try {
                Resource resource = resourceLoader.getResource(gateRulesLocation);
                if (resource.exists()) {
                    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
                    yaml.setResources(resource);
                    Properties properties = yaml.getObject();
                    if (properties != null) {
                        for (String gate : defaults.keySet()) {
                            String key = "defaultStatus." + gate;
                            String configured = safeText(properties.getProperty(key));
                            if (!configured.isBlank()) {
                                defaults.put(gate, configured.toUpperCase());
                            }
                        }
                    }
                } else {
                    log.warn("SDAC gate rule file not found: {}", gateRulesLocation);
                }
            } catch (Exception e) {
                log.warn("Failed to load gate defaults from {}, fallback to built-in defaults", gateRulesLocation, e);
            }

            cachedDefaultGateStatuses = defaults;
            return new LinkedHashMap<>(defaults);
        }
    }

    public String renderAi2AiStateUpdate(String changeSummary,
                                         String impact,
                                         List<String> commands,
                                         String result,
                                         List<String> artifacts,
                                         List<String> knownLimits) {
        String template = loadTextResource(ai2aiStateUpdateTemplateLocation, "AI2AI state update template");
        String commandSummary = commands == null || commands.isEmpty() ? "N/A" : String.join("；", commands);
        String artifactSummary = artifacts == null || artifacts.isEmpty() ? "N/A" : String.join("；", artifacts);
        String limitSummary = knownLimits == null || knownLimits.isEmpty() ? "无" : String.join("；", knownLimits);
        String resultSummary = safeText(result).isBlank() ? "UNKNOWN" : safeText(result).toUpperCase();

        return template
                .replace("- 变更点：", "- 变更点：" + safeOneLine(changeSummary))
                .replace("- 影响面：", "- 影响面：" + safeOneLine(impact))
                .replace("- 命令：", "- 命令：" + commandSummary)
                .replace("- 结果摘要（PASS/FAIL）：", "- 结果摘要（PASS/FAIL）：" + resultSummary)
                .replace("- 关键产物路径/链接：", "- 关键产物路径/链接：" + artifactSummary)
                .replace("- ...", "- " + limitSummary);
    }

    private void validateBySchemaLocation(String schemaLocation, Object payload, String schemaName) {
        JsonNode schema = loadJsonResource(schemaLocation, schemaName);
        JsonNode data = OBJECT_MAPPER.valueToTree(payload);
        List<String> errors = new ArrayList<>();
        validateNode("$", schema, data, errors);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(schemaName + " validation failed: " + String.join("; ", errors));
        }
    }

    private JsonNode loadJsonResource(String location, String schemaName) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException(schemaName + " not found at " + location);
        }
        try (var inputStream = resource.getInputStream()) {
            return OBJECT_MAPPER.readTree(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + schemaName + " from " + location, e);
        }
    }

    private String loadTextResource(String location, String name) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException(name + " not found at " + location);
        }
        try (var inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + name + " from " + location, e);
        }
    }

    private void validateNode(String path, JsonNode schema, JsonNode value, List<String> errors) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return;
        }

        String type = safeText(schema.path("type").asText(""));
        if (!type.isBlank() && !matchesType(type, value)) {
            errors.add(path + " expected type " + type + " but was " + describeType(value));
            return;
        }

        JsonNode enumNode = schema.path("enum");
        if (enumNode.isArray() && enumNode.size() > 0) {
            boolean matched = false;
            for (JsonNode enumValue : enumNode) {
                if (Objects.equals(value, enumValue) || Objects.equals(value.asText(""), enumValue.asText(""))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                errors.add(path + " must be one of " + enumNode);
                return;
            }
        }

        if ("object".equals(type) && value.isObject()) {
            JsonNode requiredNode = schema.path("required");
            if (requiredNode.isArray()) {
                for (JsonNode requiredField : requiredNode) {
                    String fieldName = safeText(requiredField.asText(""));
                    if (fieldName.isBlank()) {
                        continue;
                    }
                    if (!value.has(fieldName) || value.get(fieldName).isNull()) {
                        errors.add(path + "." + fieldName + " is required");
                    }
                }
            }

            JsonNode propertiesNode = schema.path("properties");
            if (propertiesNode.isObject()) {
                var fields = propertiesNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String fieldName = entry.getKey();
                    if (value.has(fieldName) && !value.get(fieldName).isNull()) {
                        validateNode(path + "." + fieldName, entry.getValue(), value.get(fieldName), errors);
                    }
                }
            }
            return;
        }

        if ("array".equals(type) && value.isArray()) {
            int minItems = schema.path("minItems").asInt(-1);
            if (minItems >= 0 && value.size() < minItems) {
                errors.add(path + " requires at least " + minItems + " items");
            }
            JsonNode itemsSchema = schema.path("items");
            if (!itemsSchema.isMissingNode()) {
                for (int i = 0; i < value.size(); i++) {
                    validateNode(path + "[" + i + "]", itemsSchema, value.get(i), errors);
                }
            }
        }
    }

    private boolean matchesType(String expectedType, JsonNode value) {
        return switch (expectedType) {
            case "object" -> value != null && value.isObject();
            case "array" -> value != null && value.isArray();
            case "string" -> value != null && value.isTextual();
            case "integer" -> value != null && value.isIntegralNumber();
            case "number" -> value != null && value.isNumber();
            case "boolean" -> value != null && value.isBoolean();
            default -> true;
        };
    }

    private String describeType(JsonNode value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        if (value.isObject()) {
            return "object";
        }
        if (value.isArray()) {
            return "array";
        }
        if (value.isTextual()) {
            return "string";
        }
        if (value.isIntegralNumber()) {
            return "integer";
        }
        if (value.isNumber()) {
            return "number";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        return "unknown";
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeOneLine(String value) {
        return safeText(value).replace("\n", " ").replace("\r", " ");
    }
}
