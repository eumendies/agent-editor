package com.agent.editor.agent.mcp.client;

import com.agent.editor.agent.tool.RecoverableToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.util.List;
import java.util.Map;

/**
 * Bridges LangChain4j's MCP SDK client to the project's lightweight
 * {@link McpClient} abstraction.
 * <p>
 * The adapter keeps protocol handling inside the SDK while normalizing tool
 * schemas and results into structures already understood by the existing
 * registry and tool loop runtime.
 */
public class SdkMcpClientAdapter implements McpClient {

    private final dev.langchain4j.mcp.client.McpClient sdkClient;
    private final ObjectMapper objectMapper;

    /**
     * @param sdkClient SDK-managed MCP client responsible for transport/session handling
     * @param objectMapper mapper used to normalize SDK schemas and results
     */
    public SdkMcpClientAdapter(dev.langchain4j.mcp.client.McpClient sdkClient,
                               ObjectMapper objectMapper) {
        this.sdkClient = sdkClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Converts SDK-listed tools into local descriptors while preserving the
     * original {@link ToolSpecification} for downstream reuse.
     *
     * @return normalized remote tool descriptors
     */
    @Override
    public List<McpToolDescriptor> listTools() {
        try {
            return sdkClient.listTools().stream()
                    .map(this::toDescriptor)
                    .toList();
        } catch (Exception exception) {
            throw new RecoverableToolException("Failed to list MCP tools via SDK", exception);
        }
    }

    /**
     * Executes a remote MCP tool through the SDK and normalizes the result into
     * the project's existing result contract.
     *
     * @param toolName remote MCP tool name
     * @param argumentsJson serialized JSON arguments
     * @return normalized tool call result
     */
    @Override
    public McpToolCallResult callTool(String toolName, String argumentsJson) {
        try {
            ToolExecutionResult result = sdkClient.executeTool(ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(argumentsJson)
                    .build());
            return new McpToolCallResult(
                    result.isError(),
                    result.result() == null ? null : objectMapper.valueToTree(result.result()),
                    result.resultText()
            );
        } catch (RecoverableToolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RecoverableToolException(
                    "Failed to execute MCP tool via SDK: " + toolName + " - " + exception.getMessage(),
                    exception
            );
        }
    }

    private McpToolDescriptor toDescriptor(ToolSpecification specification) {
        return new McpToolDescriptor(
                specification.name(),
                specification.description(),
                specification.parameters() == null ? null : toJsonSchema(specification.parameters()),
                specification
        );
    }

    private JsonNode toJsonSchema(JsonSchemaElement schemaElement) {
        // SDK schema对象不会被Jackson直接序列化，这里显式降成兼容旧handler的JSON Schema视图。
        if (schemaElement == null) {
            return null;
        }
        if (schemaElement instanceof JsonObjectSchema objectSchema) {
            return toObjectSchemaNode(objectSchema);
        }
        if (schemaElement instanceof JsonStringSchema stringSchema) {
            return toPrimitiveSchemaNode("string", stringSchema.description());
        }
        if (schemaElement instanceof JsonIntegerSchema integerSchema) {
            return toPrimitiveSchemaNode("integer", integerSchema.description());
        }
        if (schemaElement instanceof JsonNumberSchema numberSchema) {
            return toPrimitiveSchemaNode("number", numberSchema.description());
        }
        if (schemaElement instanceof JsonBooleanSchema booleanSchema) {
            return toPrimitiveSchemaNode("boolean", booleanSchema.description());
        }
        if (schemaElement instanceof JsonArraySchema arraySchema) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "array");
            addDescription(node, arraySchema.description());
            JsonNode items = toJsonSchema(arraySchema.items());
            if (items != null) {
                node.set("items", items);
            }
            return node;
        }
        if (schemaElement instanceof JsonEnumSchema enumSchema) {
            ObjectNode node = toPrimitiveSchemaNode("string", enumSchema.description());
            ArrayNode values = node.putArray("enum");
            for (String value : enumSchema.enumValues()) {
                values.add(value);
            }
            return node;
        }
        if (schemaElement instanceof JsonReferenceSchema referenceSchema) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("$ref", referenceSchema.reference());
            addDescription(node, referenceSchema.description());
            return node;
        }
        if (schemaElement instanceof JsonAnyOfSchema anyOfSchema) {
            ObjectNode node = objectMapper.createObjectNode();
            addDescription(node, anyOfSchema.description());
            ArrayNode anyOf = node.putArray("anyOf");
            for (JsonSchemaElement element : anyOfSchema.anyOf()) {
                JsonNode item = toJsonSchema(element);
                if (item != null) {
                    anyOf.add(item);
                }
            }
            return node;
        }
        if (schemaElement instanceof JsonRawSchema rawSchema) {
            try {
                JsonNode node = objectMapper.readTree(rawSchema.schema());
                if (node instanceof ObjectNode objectNode) {
                    addDescription(objectNode, rawSchema.description());
                }
                return node;
            } catch (Exception exception) {
                throw new RecoverableToolException("Failed to parse MCP raw schema", exception);
            }
        }
        if (schemaElement instanceof JsonNullSchema nullSchema) {
            return toPrimitiveSchemaNode("null", nullSchema.description());
        }

        throw new RecoverableToolException("Unsupported MCP schema element: " + schemaElement.getClass().getName());
    }

    private ObjectNode toObjectSchemaNode(JsonObjectSchema schema) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "object");
        addDescription(node, schema.description());
        if (schema.additionalProperties() != null) {
            node.put("additionalProperties", schema.additionalProperties());
        }
        if (!schema.properties().isEmpty()) {
            ObjectNode propertiesNode = node.putObject("properties");
            for (Map.Entry<String, JsonSchemaElement> entry : schema.properties().entrySet()) {
                JsonNode propertyNode = toJsonSchema(entry.getValue());
                if (propertyNode != null) {
                    propertiesNode.set(entry.getKey(), propertyNode);
                }
            }
        }
        if (!schema.required().isEmpty()) {
            ArrayNode requiredNode = node.putArray("required");
            for (String requiredProperty : schema.required()) {
                requiredNode.add(requiredProperty);
            }
        }
        if (!schema.definitions().isEmpty()) {
            ObjectNode definitionsNode = node.putObject("$defs");
            for (Map.Entry<String, JsonSchemaElement> entry : schema.definitions().entrySet()) {
                JsonNode definitionNode = toJsonSchema(entry.getValue());
                if (definitionNode != null) {
                    definitionsNode.set(entry.getKey(), definitionNode);
                }
            }
        }
        return node;
    }

    private ObjectNode toPrimitiveSchemaNode(String type, String description) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        addDescription(node, description);
        return node;
    }

    private void addDescription(ObjectNode node, String description) {
        if (description != null && !description.isBlank()) {
            node.put("description", description);
        }
    }
}
