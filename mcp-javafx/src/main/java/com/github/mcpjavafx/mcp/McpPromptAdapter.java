package com.github.mcpjavafx.mcp;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;

/**
 * Registers MCP prompts for common UI testing scenarios.
 */
public class McpPromptAdapter {

    /**
     * Registers all prompts with the MCP server.
     */
    public void registerPrompts(McpStatelessSyncServer server) {
        server.addPrompt(createInspectUiPrompt());
        server.addPrompt(createTestClickPrompt());
        server.addPrompt(createFillFormPrompt());
    }

    private McpStatelessServerFeatures.SyncPromptSpecification createInspectUiPrompt() {
        // Prompt(name, description, arguments)
        var prompt = new Prompt(
                "inspect_ui",
                "Inspect the current UI structure and understand the scene graph",
                List.of());

        return new McpStatelessServerFeatures.SyncPromptSpecification(
                prompt,
                (exchange, request) -> new GetPromptResult(
                        "Inspect UI Structure",
                        List.of(new PromptMessage(
                                Role.USER,
                                new TextContent(
                                        """
                                                I want to understand the current UI structure.

                                                Please use the ui_get_snapshot tool with mode="compact" to get the current UI tree.
                                                After getting the snapshot, explain:
                                                1. The main layout structure (what containers are used)
                                                2. Interactive elements (buttons, text fields, etc.)
                                                3. The hierarchy and how elements are organized

                                                Focus on elements that have id attributes as they are likely important for interaction.
                                                """)))));
    }

    private McpStatelessServerFeatures.SyncPromptSpecification createTestClickPrompt() {
        // PromptArgument(name, description, required)
        var prompt = new Prompt(
                "test_click",
                "Find an element and click it",
                List.of(new PromptArgument(
                        "element",
                        "Text or CSS selector to find the element",
                        true)));

        return new McpStatelessServerFeatures.SyncPromptSpecification(
                prompt,
                (exchange, request) -> {
                    var element = request.arguments() != null
                            ? request.arguments().getOrDefault("element", "button")
                            : "button";
                    return new GetPromptResult(
                            "Test Click: " + element,
                            List.of(new PromptMessage(
                                    Role.USER,
                                    new TextContent(String.format(
                                            """
                                                    I want to click on an element: "%s"

                                                    Please:
                                                    1. Use ui_query with selector.text="%s" (or selector.css if it looks like a CSS selector)
                                                    2. If found, use ui_perform with action type="click" targeting the element's ref.uid
                                                    3. After clicking, use ui_get_snapshot to verify the UI state changed

                                                    Report what happened after the click.
                                                    """,
                                            element, element)))));
                });
    }

    private McpStatelessServerFeatures.SyncPromptSpecification createFillFormPrompt() {
        var prompt = new Prompt(
                "fill_form",
                "Fill form fields with provided values",
                List.of(new PromptArgument(
                        "fields",
                        "JSON object mapping field names/selectors to values",
                        true)));

        return new McpStatelessServerFeatures.SyncPromptSpecification(
                prompt,
                (exchange, request) -> {
                    var fields = request.arguments() != null
                            ? request.arguments().getOrDefault("fields", "{}")
                            : "{}";
                    return new GetPromptResult(
                            "Fill Form",
                            List.of(new PromptMessage(
                                    Role.USER,
                                    new TextContent(String.format("""
                                            I want to fill a form with these values: %s

                                            Please:
                                            1. Use ui_get_snapshot to see the current form fields
                                            2. For each field in the provided values:
                                               - Use ui_query with selector.text or selector.css to find the field
                                               - Use ui_perform with action type="setText" to set the value
                                            3. After filling all fields, take a snapshot to verify

                                            If a submit button is visible, ask if I want to click it.
                                            """, fields)))));
                });
    }
}
