# Repository guide for coding agents

## Scope

These instructions apply to the entire repository. The project is a small Kotlin/JVM application that plays the live daily Quordle puzzle through Selenium, asks OpenAI models for guesses and an optional victory image, persists statistics, and generates a self-contained HTML replay. It can run locally in Docker or as an AWS Lambda container.

## Repository map

- `src/main/kotlin/Main.kt`: local application entry point.
- `src/main/kotlin/LambdaRuntimeInterface.kt`: AWS Lambda entry point; delegates to the same game runner.
- `src/main/kotlin/QuordleGameRunner.kt`: top-level orchestration and final-message/image assembly.
- `src/main/kotlin/QuordleWebDriver.kt`: Chrome setup, live-site navigation, DOM selectors, board parsing, and keyboard input.
- `src/main/kotlin/LLMQuordleGuesser.kt`: chat prompt, structured response schema, retry logic, and conversation history.
- `src/main/kotlin/LLMImageGenerator.kt`: victory-image prompt and image API call.
- `src/main/kotlin/Models.kt`: serializable game-domain models and derived state.
- `src/main/kotlin/GameResultSerialization.kt`: local replay JSON persistence.
- `src/main/kotlin/GuesserStatsRepository.kt`: local/S3 statistics persistence.
- `src/main/kotlin/S3BucketRepository.kt`: synchronous S3 adapter, fixed to `ca-central-1`.
- `src/main/kotlin/HtmlGenerator.kt`: replay HTML/CSS/JavaScript generation; also has a standalone `HtmlGeneratorKt` main that rebuilds HTML from saved replay JSON.
- `uBOL-ext/`: vendored uBlock Origin Lite extension loaded by Chrome. Do not broadly reformat, regenerate, or edit it for ordinary application changes.
- `output/`: local generated replay, stats, and image artifacts. Treat contents as disposable runtime data, not fixtures.
- `Dockerfile`: local/runtime image. `Dockerfile-AWS` and `docker-build-AWS.sh` build and publish the Lambda image.

## How the application fits together

`MainKt` or the Lambda handler starts `QuordleGameRunner`. The runner initializes Chrome, reads all four boards into one `GameState`, sends that state to `LLMQuordleGuesser`, enters the returned five-letter word, and reparses the page until the game is solved or all nine shared attempts are used. A word rejected by the site does not advance the board; in that case the runner removes the corresponding user/assistant messages before retrying. At the end it updates stats, optionally generates victory art, writes replay JSON and HTML locally, and uploads selected artifacts when S3 is configured.

Changes across these boundaries should remain synchronized:

- If Quordle changes its DOM or CSS state classes, update the selectors and parsing assumptions in `QuordleWebDriver.kt` together.
- If `GameState`, `QuordleGuessResponse`, or chat serialization changes, check replay JSON encoding/decoding and HTML generation.
- If output names or locations change, update the runner, serialization repositories, HTML image reference, Docker volume expectations, and S3 keys as applicable.
- A guess is shared across four boards. `GameState.numAttempts()` intentionally uses the largest board attempt count, and `usedLettersAfterAttempt()` reads the common guess sequence from board 1.

## Toolchain and commands

- Use the checked-in wrapper: `./gradlew ...`.
- The build targets Java 22, Kotlin 2.1, and Gradle 9.0.0.
- Compile and run automated checks with `./gradlew test`.
- Build the executable fat JAR with `./gradlew shadowJar`; the output is `build/libs/quordle-solver-all.jar` and its main class is `MainKt`.
- Use `./gradlew clean test shadowJar` for a fuller verification when build configuration, dependencies, packaging, or entry points change.
- There is currently no configured formatter/linter and no authored `src/test` suite. Do not claim test coverage from `test` when Gradle reports `NO-SOURCE`.

`./gradlew run`, `docker-run.sh`, and running the fat JAR execute the real daily game. They require network access, Chrome/Chromedriver compatibility, and paid/external services, and they mutate replay/stat outputs. Do not use them as routine validation. Never run `docker-build-AWS.sh` unless the user explicitly requests a deployment: it logs into a fixed ECR registry and pushes an image.

## Runtime configuration

The application reads configuration directly from environment variables:

- `OPENAI_API_KEY`: required for chat and image API calls; secret.
- `OPENAI_CHAT_MODEL_ID`: required chat model ID.
- `OPENAI_REASONING_EFFORT`: optional chat reasoning effort.
- `OPENAI_IMAGE_MODEL_ID`: required on the solved-game image path.
- `OUTPUT_FILEPATH`: output directory, default `./`. Existing code concatenates filenames as strings, so configured values must end with a path separator (for example `/output/`).
- `UBLOCK_EXT_PATH`: unpacked extension directory, default `uBOL-ext`.
- `DEBUG_MODE`: enables verbose Chrome/driver logging when parsed as `true`.
- `S3_BUCKET_NAME`: enables S3-backed stats plus HTML/image uploads when nonblank.
- `AWS_LAMBDA_FUNCTION_NAME`: supplied by Lambda and switches on Lambda-specific Chrome flags and temporary directories.

Local `.env-local` and `.env-prod` files may contain credentials and are presently untracked, not ignored by the root `.gitignore`. Never read their values into logs, include them in patches, or commit them. Do not commit generated files from `output/`, `.idea/`, `.gradle/`, or `build/`.

## Implementation conventions

- Keep source in the existing default package unless a deliberate repository-wide package migration is requested.
- Follow the surrounding Kotlin style: four-space indentation, small data classes, expression-bodied helpers where they remain readable, and explicit names at I/O boundaries.
- Keep serializable state simple and deterministic. Add `@Serializable` to persisted model types and consider compatibility with existing replay/stats JSON before renaming or removing fields.
- Keep pure game-state logic separate from Selenium, OpenAI, filesystem, and AWS code. New pure behavior belongs in the models or a focused helper and should receive unit tests.
- Preserve cleanup guarantees around Chrome. The runner closes the driver in `finally`; setup failures also quit the partially initialized driver.
- Treat live-page selectors as brittle integration points. Prefer semantic/ARIA selectors already exposed by the site, use explicit waits for dynamic UI, and avoid unrelated selector churn.
- Preserve the strict structured-output contract: the LLM response must decode to `QuordleGuessResponse`, reject extra schema properties, and produce exactly five letters after normalization.
- Do not log secrets, authorization headers, model responses containing sensitive input, or AWS credentials. Be cautious when increasing client/driver logging.
- HTML final-message content is deliberately inserted with `unsafe`; escape or validate any new content that can originate outside trusted application-generated strings.

## Testing and verification

Add tests under `src/test/kotlin` using `kotlin.test`. Prefer fast, deterministic tests for:

- `GameState`, `BoardState`, attempt counts, solved/failed transitions, and letter sets;
- JSON round trips and backward-compatible defaults;
- stats transitions and generated HTML fragments;
- parsing helpers extracted from Selenium elements or snapshots.

For ordinary Kotlin changes, run `./gradlew test`. Also run `./gradlew shadowJar` when touching Gradle configuration, dependencies, main classes, serialization, or Lambda/Docker packaging. For HTML/CSS/JavaScript changes, inspect the generated replay in a browser using non-production fixture data and verify playback at all speed settings. For Selenium changes, document that a live smoke test is still required if credentials/network access were intentionally not used.

Do not update checked-in or local daily stats merely to test a change. Use a temporary output directory with a trailing separator and disposable fixture data. Mock or wrap OpenAI, WebDriver, filesystem, and S3 boundaries rather than calling external services from unit tests.

## Change hygiene

- Check `git status` before and after work. The working tree may contain user-owned untracked environment, IDE, wrapper, and output files; preserve them.
- Keep changes focused. Avoid committing generated fat JARs, replay artifacts, screenshots, IDE metadata, or wholesale changes under `uBOL-ext/`.
- If a dependency version is changed, confirm compatibility among Selenium, Chrome/Chromedriver, and the pinned DevTools artifact, then build the fat JAR.
- If behavior depends on the current Quordle page or an external API, state what was verified locally and what remains a live integration assumption.
