# langchain4j-cdi-mcp-conformance

A Helidon MP application that exposes the fixtures the **official MCP conformance suite**
([`@modelcontextprotocol/conformance`](https://github.com/modelcontextprotocol/conformance)) expects, so that
`langchain4j-cdi-mcp-server` can be scored against the de-facto MCP TCK in both eras it supports
(MCP 2025-03-26 legacy and MCP 2026-07-28 modern).

This module is **not published** (`maven.install.skip` / `maven.deploy.skip`) and is only built in the `unpublished`
profile, like the Helidon example.

## Build

```bash
mvn install -DskipTests -pl langchain4j-cdi-mcp/langchain4j-cdi-mcp-server,langchain4j-cdi-mcp/langchain4j-cdi-mcp-build-compatible-ext
mvn package -pl langchain4j-cdi-mcp/langchain4j-cdi-mcp-conformance
```

## Run the fixture server

```bash
cd langchain4j-cdi-mcp/langchain4j-cdi-mcp-conformance
java -jar target/langchain4j-cdi-mcp-conformance.jar &
echo $! > app.pid
# ... run the suite ...
kill $(cat app.pid) && rm app.pid
```

The MCP endpoint is `http://localhost:8080/mcp`.

## Run the suite

Node 20+ is required (Node 26 / npx 11 was used for the recorded baseline).

```bash
# MCP 2026-07-28 (modern, stateless) — the headline run
npx -y @modelcontextprotocol/conformance@alpha server \
    --url http://localhost:8080/mcp --spec-version 2026-07-28 --suite all

# MCP 2025-11-25 (legacy / stateful scenarios)
npx -y @modelcontextprotocol/conformance@alpha server \
    --url http://localhost:8080/mcp --spec-version 2025-11-25 --suite all

# stable line, default suite
npx -y @modelcontextprotocol/conformance@0.1.16 server --url http://localhost:8080/mcp

# scenario inventory
npx -y @modelcontextprotocol/conformance@alpha list
```

`--suite all` matters: the default `active` suite skips the draft-spec scenarios, which is where all the
2026-07-28 material lives (`server-stateless`, `caching`, `http-*`, the 15 `input-required-result-*` MRTR
scenarios). Add `-o results` to write one `checks.json` per scenario.

## Baselines

Two baseline files record the failures that are known and accepted today. A run with `--expected-failures`
exits 0 when every failure (and warning) is listed, and exits 1 on a regression **or** on a stale entry.

```bash
npx -y @modelcontextprotocol/conformance@alpha server --url http://localhost:8080/mcp \
    --spec-version 2026-07-28 --suite all --expected-failures conformance-baseline.yml

npx -y @modelcontextprotocol/conformance@alpha server --url http://localhost:8080/mcp \
    --spec-version 2025-11-25 --suite all --expected-failures conformance-baseline-legacy.yml

npx -y @modelcontextprotocol/conformance@0.1.16 server --url http://localhost:8080/mcp \
    --expected-failures conformance-baseline-legacy.yml
```

Two files are needed because the same scenario can fail in one era and pass in the other, and a baselined
scenario that passes is reported as a stale entry.

To refresh a baseline: run the suite without `--expected-failures`, take the failing scenarios from the summary
and the failing check ids from `results/server-<scenario>-*/checks.json`, and edit the YAML. Prefer
`<scenario>:<check-id>` entries so the remaining checks of that scenario stay enforced; warnings must be
baselined too, they are treated as failures by the gate.

## Recorded results (2026-09-14, after the first fix wave)

| Run | Passed | Failed | First measurement |
|---|---|---|---|
| `--spec-version 2026-07-28 --suite all` | 130 | 8 | 108 / 29 |
| `--spec-version 2025-11-25 --suite all` | 74 | 1 | 58 / 9 |
| stable line `@0.1.16`, default suite | 40 | 0 | 25 / 7 |

The eight remaining 2026-07-28 failures and the single 2025-11-25 one are missing features or known API
limitations (custom tool `inputSchema`, `x-mcp-header`, SEP-2322 input-request naming, one pending input request
per round); see the two baseline files and `langchain4j-cdi-mcp/README.md` ("Conformance").

## Fixtures

`ConformanceTools` — `test_simple_text`, `test_image_content`, `test_audio_content`, `test_embedded_resource`,
`test_multiple_content_types`, `test_error_handling`, `test_tool_with_logging`, `test_logging_tool`,
`test_tool_with_progress`, `test_streaming_elicitation`, `test_sampling`, `test_elicitation`,
`test_elicitation_sep1034_defaults`, `test_elicitation_sep1330_enums`, `test_missing_capability`.

`ConformanceMrtrTools` — the SEP-2322 family: `test_input_required_result_elicitation`, `_sampling`,
`_list_roots`, `_request_state`, `_multiple_inputs`, `_multi_round`, `_tampered_state`, `_capabilities`, and the
`test_input_required_result_prompt` prompt.

`ConformanceChangeTools` — `test_trigger_tool_change`, `test_trigger_prompt_change` (mutate the registries so
that `notifications/tools/list_changed` and `notifications/prompts/list_changed` can be observed).

`ConformanceResources` — `test://static-text`, `test://static-binary`, `test://embedded-resource`,
`test://mixed-content-resource`, `test://watched-resource` (updated every 3 s), and the
`test://template/{id}/data` template.

`ConformancePrompts` — `test_simple_prompt`, `test_prompt_with_arguments`, `test_prompt_with_embedded_resource`,
`test_prompt_with_image`.

### Fixtures that cannot be expressed with the current API

- `json_schema_2020_12_tool` — a tool's `inputSchema` is always generated from the Java signature
  (`JsonSchemaGenerator`); there is no way to supply a hand-written JSON Schema 2020-12 document.
- A tool carrying SEP-2243 `x-mcp-header` annotations — `Mcp-Param-*` headers are not implemented.
- `test_reconnection` — needs raw control of the response stream framing.
- The `tasks-*` extension — not implemented.

### Notes

- The whole `langchain4j-cdi-mcp` tree now compiles with `-parameters`, so an unannotated argument is advertised
  and bound under its Java parameter name instead of `arg0`. `@ToolArg(name = …)`, `@PromptArg(name = …)` and
  `@ResourceTemplateArg(name = …)` are honoured and take precedence.
- `test_elicitation_sep1330_enums` builds its titled enums as `oneOf: [{const, title}]` and
  `items.anyOf: [{const, title}]`, which is what SEP-1330 asks of a *titled* enum; `enum` + `enumNames` is kept
  only for the `legacyEnum` field the suite checks for the deprecated shape.
