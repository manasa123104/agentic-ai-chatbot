package com.agentic.chatbot.mcp;

import com.agentic.chatbot.stage.StageRequest;
import com.agentic.chatbot.stage.StageResponse;
import com.agentic.chatbot.stage.StageService;
import com.agentic.chatbot.stage.StageType;
import com.agentic.chatbot.web.StageApiController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Model Context Protocol (JSON-RPC 2.0) surface for BOT.
 * Agents call {@code tools/list} and {@code tools/call}.
 *
 * <p>Endpoint: {@code POST /api/mcp} with body {@code {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}}.</p>
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpProperties props;
    private final StageService stages;
    private final StageApiController stagesApi;

    public McpController(McpProperties props, StageService stages, StageApiController stagesApi) {
        this.props = props;
        this.stages = stages;
        this.stagesApi = stagesApi;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(@RequestBody Map<String, Object> body,
                                      @RequestHeader(value = "X-BOT-MCP-Key", required = false) String key,
                                      HttpServletRequest request) {
        if (!props.isEnabled()) {
            return error(body.get("id"), -32000, "MCP is disabled (agentic.mcp.enabled=false).");
        }
        if (!props.getApiKey().isBlank() && !props.getApiKey().equals(key == null ? "" : key.trim())) {
            return error(body.get("id"), -32001, "Missing or invalid X-BOT-MCP-Key.");
        }
        String method = String.valueOf(body.getOrDefault("method", "")).trim();
        Object id = body.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        try {
            Object result = switch (method) {
                case "initialize" -> initialize();
                case "ping" -> Map.of("ok", true);
                case "tools/list" -> Map.of("tools", tools());
                case "tools/call" -> callTool(params, request);
                default -> throw new IllegalArgumentException("Unknown MCP method: " + method
                        + ". Use initialize, tools/list, tools/call or ping.");
            };
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("jsonrpc", "2.0");
            ok.put("id", id);
            ok.put("result", result);
            return ok;
        } catch (IllegalArgumentException e) {
            return error(id, -32602, e.getMessage());
        } catch (Exception e) {
            return error(id, -32603, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private Map<String, Object> initialize() {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", "bot-stages");
        server.put("version", "1.0.0");
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("tools", Map.of("listChanged", false));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("protocolVersion", "2024-11-05");
        out.put("capabilities", caps);
        out.put("serverInfo", server);
        out.put("instructions", "BOT stage pipeline MCP. Prefer bot/* branches for push_run.");
        return out;
    }

    private List<Map<String, Object>> tools() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(tool("run_stage",
                "Run one BOT stage (ba, developer, tester, data-analyst, deploy, trainer).",
                Map.of("type", "object", "properties", Map.of(
                        "stage", Map.of("type", "string"),
                        "domain", Map.of("type", "string"),
                        "input", Map.of("type", "string"),
                        "runId", Map.of("type", "string"),
                        "language", Map.of("type", "string"),
                        "offline", Map.of("type", "boolean")
                ), "required", List.of("stage", "domain", "input"))));
        list.add(tool("run_all_stages",
                "Run the full BOT pipeline (BA → Developer → Tester → Data Analyst → Deploy → Trainer).",
                Map.of("type", "object", "properties", Map.of(
                        "domain", Map.of("type", "string"),
                        "input", Map.of("type", "string"),
                        "language", Map.of("type", "string"),
                        "offline", Map.of("type", "boolean")
                ), "required", List.of("domain", "input"))));
        list.add(tool("push_run",
                "Push a whole run (or one stage) to a new GitHub branch.",
                Map.of("type", "object", "properties", Map.of(
                        "runId", Map.of("type", "string"),
                        "repoUrl", Map.of("type", "string"),
                        "token", Map.of("type", "string"),
                        "branch", Map.of("type", "string"),
                        "folder", Map.of("type", "string"),
                        "stage", Map.of("type", "string", "description", "blank or run = whole run"),
                        "pull", Map.of("type", "boolean")
                ), "required", List.of("runId", "repoUrl"))));
        list.add(tool("test_github_token",
                "Ask GitHub whether a token can write to a repository (no push).",
                Map.of("type", "object", "properties", Map.of(
                        "repoUrl", Map.of("type", "string"),
                        "token", Map.of("type", "string")
                ), "required", List.of("repoUrl", "token"))));
        list.add(tool("get_run_status",
                "Check whether a run still exists on the server and how many files it has.",
                Map.of("type", "object", "properties", Map.of(
                        "runId", Map.of("type", "string")
                ), "required", List.of("runId"))));
        list.add(tool("list_stages",
                "List BOT stage ids and titles in pipeline order.",
                Map.of("type", "object", "properties", Map.of())));
        return list;
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("description", description);
        t.put("inputSchema", inputSchema);
        return t;
    }

    private Map<String, Object> callTool(Map<String, Object> params, HttpServletRequest request) throws Exception {
        String name = String.valueOf(params.getOrDefault("name", "")).trim();
        @SuppressWarnings("unchecked")
        Map<String, Object> args = params.get("arguments") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        Object payload = switch (name) {
            case "list_stages" -> listStages();
            case "get_run_status" -> stagesApi.runStatus(str(args, "runId"));
            case "run_stage" -> runStage(args);
            case "run_all_stages" -> runAll(args);
            case "test_github_token" -> testToken(args, request);
            case "push_run" -> pushRun(args, request);
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", payload instanceof String s ? s : String.valueOf(payload));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", List.of(content));
        out.put("structuredContent", payload);
        return out;
    }

    private List<Map<String, String>> listStages() {
        List<Map<String, String>> out = new ArrayList<>();
        for (StageType s : StageType.values()) {
            out.add(Map.of("id", s.id(), "title", s.title(), "produces", s.produces()));
        }
        return out;
    }

    private StageResponse runStage(Map<String, Object> args) {
        String stage = str(args, "stage");
        StageType type = StageType.fromId(stage).orElseThrow(() -> new IllegalArgumentException(
                "Unknown stage '" + stage + "'. Use ba, developer, tester, data-analyst, deploy or trainer."));
        return stages.run(type, stageRequest(args));
    }

    private Map<String, Object> runAll(Map<String, Object> args) {
        List<StageResponse> results = stages.runAll(stageRequest(args));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", results.get(0).runId());
        body.put("stages", results.stream().map(StageResponse::stage).toList());
        body.put("files", results.stream().mapToInt(r -> r.files().size()).sum());
        return body;
    }

    private Map<String, Object> testToken(Map<String, Object> args, HttpServletRequest request) {
        return stagesApi.checkToken(new StageApiController.TokenCheckRequest(str(args, "repoUrl"), str(args, "token")),
                request);
    }

    private Map<String, Object> pushRun(Map<String, Object> args, HttpServletRequest request) throws Exception {
        String runId = str(args, "runId");
        Boolean pull = args.get("pull") instanceof Boolean b ? b : Boolean.TRUE;
        StageApiController.PushRequest body = new StageApiController.PushRequest(
                str(args, "stage"),
                str(args, "repoUrl"),
                str(args, "branch"),
                str(args, "baseBranch"),
                str(args, "folder"),
                str(args, "commitMessage"),
                str(args, "token"),
                pull);
        return stagesApi.push(runId, body, request);
    }

    private StageRequest stageRequest(Map<String, Object> args) {
        Map<String, String> options = new LinkedHashMap<>();
        String language = str(args, "language");
        if (!language.isBlank()) {
            options.put("language", language);
        }
        if (args.get("offline") instanceof Boolean off && off) {
            options.put("offline", "true");
        }
        String runId = str(args, "runId");
        return new StageRequest(str(args, "domain"), str(args, "input"), options, runId.isBlank() ? null : runId);
    }

    private static String str(Map<String, Object> a, String... keys) {
        for (String k : keys) {
            Object v = a.get(k);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v).trim();
            }
        }
        return "";
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        return error(id, code, message, Map.of());
    }

    private static Map<String, Object> error(Object id, int code, String message, Map<String, Object> data) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message == null ? "error" : message);
        if (data != null && !data.isEmpty()) {
            err.put("data", data);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jsonrpc", "2.0");
        out.put("id", id);
        out.put("error", err);
        return out;
    }
}
