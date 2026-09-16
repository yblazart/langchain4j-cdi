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

## Recorded results (2026-09-15, after the SEP-2243 `x-mcp-header` fixture)

| Run | Passed | Failed |
|---|---|---|
| `--spec-version 2026-07-28 --suite all` | 133 | 9 |
| `--spec-version 2025-11-25 --suite all` | 74 | 1 |
| stable line `@0.1.16`, default suite | 40 | 0 |

`http-custom-header-server-validation` used to emit 6 checks, 5 of them "not testable" for want of a fixture
carrying a designation. It now emits 10 real checks, 4 passing. The three that still fail need the *request*
half of SEP-2243 — reading the `Mcp-Param-*` headers a client mirrors back and rejecting a mismatch with HTTP
400 + `-32020` — which the server does not implement.

## Recorded results (2026-09-15, after the SEP-2243 request half)

| Run | Passed | Failed |
|---|---|---|
| `--spec-version 2026-07-28 --suite all` | 139 | 3 |
| `--spec-version 2025-11-25 --suite all` | 74 | 1 |
| stable line `@0.1.16`, default suite | 40 | 0 |

`http-custom-header-server-validation` is now **10 checks, 10 passing**: the server validates every
`Mcp-Param-<designation>` header against the request body before dispatching `tools/call`, so the six rows that
used to fail (invalid Base64 padding ×2, non-alphabet Base64 characters ×2, and a header omitted while the body
carries the value ×2 — each case emits both a status row and a `-32020` error-code row) are green. SEP-2243 is
closed in both halves. The three remaining 2026-07-28 failures and the single 2025-11-25 one are missing
features or known API limitations (custom tool `inputSchema`, SEP-2322 input-request naming, one pending input
request per round); see the two baseline files and `langchain4j-cdi-mcp/README.md` ("Conformance").

## Recorded results (2026-09-16, after tool title/annotations and the hand-written inputSchema)

| Run | Passed | Failed |
|---|---|---|
| `--spec-version 2026-07-28 --suite all` | 146 | 2 |
| `--spec-version 2025-11-25 --suite all` | 81 | 0 |
| stable line `@0.1.16`, default suite | 40 | 0 |

`json_schema_2020_12_tool` (`ConformanceTools`) now exists, published via the new `@McpInputSchema` with the
scenario's exact `JSON_SCHEMA_2020_12_FIXTURE` verbatim, so `json-schema-2020-12` goes from 1 failing check
(early-return "tool not found") to **8 checks, 8 passing** in both the 2026-07-28 and 2025-11-25 runs: `$schema`,
`$defs`, `additionalProperties` (SEP-1613), and the `allOf`/`anyOf` composition, `if`/`then`/`else` conditional,
and `$anchor` keywords (SEP-2106) all survive `tools/list` unmangled. Both baseline files drop their
`json-schema-2020-12:json-schema-2020-12-tool-found` entry.

The jump from 139/3 to 146/2 (not 140/2) and from 74/1 to 81/0 (not 75/0) is not all this fixture's doing: the
`json-schema-2020-12` scenario itself accounts for +7/2026-07-28 and +7/2025-11-25 (8 passing checks now running
where only 1 failing one ran before), and `input-required-result-ignore-extra-params` — previously baselined as
failing — is passing on both 2026-07-28 runs measured here; `@alpha` is a floating tag, so some drift between
measurements independent of this server's code is expected. The two 2026-07-28 failures remaining
(`input-required-result-basic-elicitation`, `input-required-result-multiple-input-requests`) are the same
pre-existing SEP-2322 input-request-naming limitations as before.

## Recorded results (2026-09-16, after SEP-2322 server-chosen keys and batch interactions)

| Run | Passed | Failed |
|---|---|---|
| `--spec-version 2026-07-28 --suite all` | 150 | 0 |
| `--spec-version 2025-11-25 --suite all` | 81 | 0 |
| stable line `@0.1.16`, default suite | 40 | 0 |

Both remaining 2026-07-28 SEP-2322 failures are fixed. `ConformanceMrtrTools.askName` now calls
`ElicitationRequest.Builder.setKey("user_name")`, so `test_input_required_result_elicitation` publishes exactly the
key `input-required-result-basic-elicitation`'s `sep-2322-elicitation-incomplete` check requires verbatim (now
3/3). `test_input_required_result_multiple_inputs` now injects `McpInteractions` and declares a batch of one
elicitation, one sampling and one `roots/list` request, awaited together with `Batch.awaitAll()`; the resulting
`input_required` result lists all three requests at once (never just the first), which is what
`input-required-result-multiple-input-requests`'s `sep-2322-multiple-inputs-incomplete` check requires (now 3/3).

`input-required-result-ignore-extra-params:sep-2322-ignore-unexpected-params` stays baselined (warning-severity):
the fixture's single, ungated call (no prior round, no `requestState`) can never be accepted by a server that
requires a valid `requestState` before honouring `inputResponses` — see `conformance-baseline.yml` for the full
rationale. The plain summary above buckets this check as "1 passed, 0 failed" for `input-required-result-ignore-extra-params`
(a WARNING status does not count as a scenario-level failure there), but under `--expected-failures` the run still
reports it as a matched, non-stale expected failure (`~`), because the gate treats warnings as failures that must be
listed; with the entry kept, `--expected-failures conformance-baseline.yml` exits 0 (verified against this exact
150/0 run).

## Fixtures

`ConformanceTools` — `test_simple_text`, `test_image_content`, `test_audio_content`, `test_embedded_resource`,
`test_multiple_content_types`, `test_error_handling`, `test_tool_with_logging`, `test_logging_tool`,
`test_tool_with_progress`, `test_streaming_elicitation`, `test_sampling`, `test_elicitation`,
`test_elicitation_sep1034_defaults`, `test_elicitation_sep1330_enums`, `test_missing_capability`,
`test_custom_header_tool` (the only tool carrying SEP-2243 `x-mcp-header` designations — one argument of each
permitted primitive type; `http-custom-header-server-validation` picks the first tool with any designation and
then its first *string*-typed designated argument), `json_schema_2020_12_tool` (the only tool carrying a
hand-written `@McpInputSchema` — the `json-schema-2020-12` scenario's canonical `JSON_SCHEMA_2020_12_FIXTURE`,
published verbatim; see `langchain4j-cdi-mcp/README.md`, "Hand-written input schema").

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

- `test_reconnection` — needs raw control of the response stream framing.
- The `tasks-*` extension — not implemented.

### Notes

- The whole `langchain4j-cdi-mcp` tree now compiles with `-parameters`, so an unannotated argument is advertised
  and bound under its Java parameter name instead of `arg0`. `@ToolArg(name = …)`, `@PromptArg(name = …)` and
  `@ResourceTemplateArg(name = …)` are honoured and take precedence.
- `test_elicitation_sep1330_enums` builds its titled enums as `oneOf: [{const, title}]` and
  `items.anyOf: [{const, title}]`, which is what SEP-1330 asks of a *titled* enum; `enum` + `enumNames` is kept
  only for the `legacyEnum` field the suite checks for the deprecated shape.
