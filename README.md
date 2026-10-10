# llm-quordle-solver
[Live Demo](https://flowtwo.io/daily-quordle-solver.html) <br>
[Blog Post](https://www.flowtwo.io/post/automating-quordle-with-an-llm)

Automates the daily Quordle puzzle using a large language model (LLM) <br>
If it wins, it generates a nice picture using the 4 words from the solution.

![img.png](img.png)


## Multiple daily Lambda runs

The Lambda invocation accepts a JSON object with optional overrides. Missing
keys fall back to the Lambda environment variables. Reasoning effort defaults
to `medium`, and `GENERATE_HTML_REPLAY` defaults to `true` to preserve the
existing daily output. `OPENAI_CHAT_MODEL_ID` must be set either in the payload
or environment. Payload model/effort values must be nonblank strings; the
replay flag must be a JSON boolean, not a quoted string. Overrides apply only
to that invocation, including when Lambda reuses the execution environment.

Use this payload for the main EventBridge target:

```json
{
  "OPENAI_CHAT_MODEL_ID": "gpt-5.4-mini",
  "OPENAI_REASONING_EFFORT": "medium",
  "GENERATE_HTML_REPLAY": true
}
```

Use a separate schedule/target for each comparison, for example:

```json
{
  "OPENAI_CHAT_MODEL_ID": "gpt-5-mini",
  "OPENAI_REASONING_EFFORT": "high",
  "GENERATE_HTML_REPLAY": false
}
```

Configure exactly one main run per day. Every run appends its date, outcome,
guess count, model, effort, duration and main/comparison designation to the same
`llm_guesser_stats.json` file (or S3 key). Comparison runs skip image generation,
HTML generation/upload and replay JSON output. They do not need an image model.
The main run still generates victory art only when it wins. Replay model/effort
labels use the invocation's effective configuration.

Only `dailyRuns` is persisted; the old aggregate totals and streak are removed
on the next stats save. The replay derives its summary from recorded main runs,
so it cannot reconstruct lifetime games absent from the daily history. Old
records with missing effort/duration remain unknown; records without `isMainRun`
are treated as main runs. S3 updates use conditional writes and retry after
conflicts, so overlapping writers merge with the latest history. All writers
must use this new version; an older writer can still overwrite the file. Each
invocation appends one record, including repeated invocations for the same day.

### One-time stats cleanup

Save the existing lifetime totals for your own records before deploying this
version. To clean a local copy without running the game:

```sh
python3 scripts/migrate_daily_stats.py /path/to/llm_guesser_stats.json
python3 scripts/migrate_daily_stats.py /path/to/llm_guesser_stats.json --in-place
```

The first command previews the change. The second creates a timestamped backup
alongside the original before replacing it. Alternatively, use `--output` with
a new path to keep the source untouched. Daily entries are preserved exactly;
only the four obsolete aggregate fields are removed. For S3, download a copy,
run the script, then upload the cleaned file during a window with no active
runs so the manual upload does not overwrite a new result.
