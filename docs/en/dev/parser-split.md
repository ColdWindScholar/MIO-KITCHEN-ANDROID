# Stage 5 — KrScript parser/runtime split

> Date: 2026-06-17
> Status: completed
> Related stages: after Stage 4 (storage/workspace), before Stage 6 (shell runtime)

---


The legacy `PageConfigReader` mixed three concerns in one class:

1. **XML parsing** — walking the XML tree and building `NodeInfoBase` nodes.
2. **Shell execution** — calling `ScriptEnvironmen.executeResultRoot` for
   `desc-sh`, `summary-sh`, `support`/`visible` attributes and `<getstate>` elements.
3. **Side effects** — extracting `<resource>` files via `ExtractAssets`, showing
   `Toast` on errors, posting to the main `Handler`.

This made the class impossible to unit-test without an Android device, hard to
reason about, and a blocker for the rest of the roadmap (firmware analyzer,
shell runtime, UI modernization).

Stage 5 introduces a pure parser that:

- does NOT call shell,
- does NOT show Toast or log,
- does NOT extract resources,
- does NOT depend on `Context`, `Handler`, `Looper` or `Toast`,
- delegates all dynamic value resolution to a `DynamicValueResolver` interface.

### New components

```text
krscript/parser/
  PageConfigSource.kt           # abstraction over where XML comes from
  StreamPageConfigSource.kt     # trivial stream-backed source (for tests/cache)
  DynamicValueResolver.kt       # interface + NoopDynamicValueResolver default
  PageConfigParser.kt           # pure XML -> List<NodeInfoBase>
  PageConfigRepository.kt       # source -> parser -> validator -> ParsedPageConfig

krscript/validator/
  PageConfigValidator.kt        # ValidationReport (errors + warnings)

krscript/runtime/
  RuntimeBinder.kt              # DynamicValueResolver impl that calls ScriptEnvironmen
                                # + extractResources entry point

krscript/config/
  AndroidPageConfigSource.kt    # adapter: PathAnalysis -> PageConfigSource

krscript/src/test/java/.../parser/
  PageConfigParserTest.kt       # JVM unit tests (no Robolectric needed)
```

### Key contracts

```text
parser  -> NO shell, NO UI, NO Context
validator -> NO shell, NO UI, NO Context
RuntimeBinder -> single place that calls ScriptEnvironmen for dynamic values
PageConfigSource -> opens an InputStream + reports absolutePath/parentDir
DynamicValueResolver -> resolveShellValue / isSupported / resolveText
```

### Backward compatibility

The legacy `PageConfigReader` is preserved unchanged so existing UI code
(`PageLayoutRender`, `ActionListFragment`, `PageMenuLoader`, …) continues to work.
The new architecture is the recommended path for new code, and once the UI
modernization stage (Stage 8) lands, the legacy reader will be retired.

### CI gate

```bash
python3 tools/check-parser-split.py
```

Expected output:

```text
PASS: KrScript parser/runtime split is in place
PASS: parser does not depend on Context/Handler/Toast
PASS: parser does not invoke ScriptEnvironmen or ExtractAssets
PASS: validator does not execute shell
PASS: RuntimeBinder is the single shell-bound DynamicValueResolver
PASS: parser unit tests and JVM test dependencies are present
PASS: legacy PageConfigReader is preserved for backward compatibility
```

### Why this matters

Stage 6 (Shell runtime) needs a parser that does not eagerly call shell, so that
the shell runtime can be replaced with a typed `ShellRuntime` interface. Stage 7
(Firmware analyzer) needs the same parser to be testable in plain JVM tests.
Stage 8 (UI modernization) needs the parser to be reusable from a ViewModel
without touching `Activity`/`Fragment`.

### What is intentionally NOT done here

- The legacy `PageConfigReader` is not removed (kept for backward compat).
- `<resource>` extraction still runs through `ExtractAssets` via `RuntimeBinder`;
  a future stage will replace this with a typed `ResourceExtractor`.
- The page renderer (`PageLayoutRender`) still consumes the legacy reader.
- The `PageConfigSh` dynamic config loader is not migrated yet.

---
