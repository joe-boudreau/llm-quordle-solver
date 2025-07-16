import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.Keys
import org.openqa.selenium.interactions.Actions
import java.io.File
import java.time.Duration

class QuordleWebDriver {
    private lateinit var driver: WebDriver

    /**
     * List of 4 game boards, each containing a list of 9 rows
     */
    private lateinit var boardRowTiles: List<List<List<WebElement>>>

    fun initializeDriver() {
        println("Initializing WebDriver...")
        val options = ChromeOptions().apply {
            //addArguments("--disable-blink-features=AutomationControlled")
            addArguments("--no-sandbox")
            //addArguments("--disable-dev-shm-usage")
            // Add these to improve headless performance
            addArguments("--disable-gpu")
            addArguments("--disable-extensions")
            addArguments("--blink-settings=imagesEnabled=false")

            // Optional: If you want to see what's happening, comment this out
            addArguments("--headless=new")
            // Set a user agent that doesn't reveal it's a headless browser
            addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            //setExperimentalOption("excludeSwitches", listOf("enable-automation"))
            //setExperimentalOption("useAutomationExtension", false)
        }

        try {

            println("Setting up Chrome options...")
            driver = ChromeDriver(options)
            println("WebDriver initialized.")

            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(10))
            println("Set page load timeout to 10 seconds.")
            println("Attempting to navigate to Quordle page...")
            try {
                driver.get("https://www.merriam-webster.com/games/quordle")
                println("Navigation command sent.")
            } catch (e: org.openqa.selenium.TimeoutException) {
                // Continue even if we get a timeout - the page might be loaded enough
                println("Page load timed out, but continuing anyway...")
            }

            WebDriverWait(driver, Duration.ofSeconds(5))
                .until { it.findElements(By.cssSelector("div[aria-label='Game Boards']")).isNotEmpty() }
            println("Game boards loaded.")

            val board1 = driver.findElement(By.cssSelector("div[aria-label='Game Boards'] > div[aria-label='Game Boards Row 1'] > div[aria-label='Game Board 1"))
            val board2 = driver.findElement(By.cssSelector("div[aria-label='Game Boards'] > div[aria-label='Game Boards Row 1'] > div[aria-label='Game Board 2"))
            val board3 = driver.findElement(By.cssSelector("div[aria-label='Game Boards'] > div[aria-label='Game Boards Row 2'] > div[aria-label='Game Board 3"))
            val board4 = driver.findElement(By.cssSelector("div[aria-label='Game Boards'] > div[aria-label='Game Boards Row 2'] > div[aria-label='Game Board 4"))
            val gameBoards = listOf(board1, board2, board3, board4)

            boardRowTiles = gameBoards.map { board ->
                val rows = board.findElements(By.cssSelector("div.quordle-guess-row"))
                rows.map {it.findElements(By.cssSelector("div.quordle-box"))} // all the tiles
            }

        } catch (e: Exception) {
            println("Error during setup: ${e.message}")
            e.printStackTrace()
            driver.quit()
            throw e
        }
    }

    fun parseGameState(): GameState {
        println("Parsing game state...")
        val boardStates = boardRowTiles.map { rowsForBoard ->
            val attempts = rowsForBoard.map { parseAttempt(it) }
            BoardState(attempts.filterNot { it.word.isEmpty() })
        }
        return GameState(boardStates)
    }

    private fun parseAttempt(tiles: List<WebElement>): Attempt {

        val word = tiles.joinToString("") { tile ->
            tile.findElement(By.cssSelector("div.quordle-box-content")).text.trim().uppercase()
        }.trim()

        // CSS class bg-box-diff means the letter is in the word but in the wrong position
        // CSS class bg-box-correct means the letter is in the word and in the correct position
        val feedback = tiles.map { tile ->
            val classAttr = tile.getAttribute("class")!!
            when {
                classAttr.contains("bg-box-correct") -> TileState.CORRECT
                classAttr.contains("bg-box-diff") -> TileState.PRESENT
                classAttr.contains("bg-zinc-200") -> TileState.ABSENT
                else -> TileState.EMPTY
            }
        }

        return Attempt(word, feedback)
    }


    fun enterGuess(word: String) {
        // Create an Actions instance
        val actions = Actions(driver)

        // Type each letter in the word
        word.forEach { letter -> actions.sendKeys(letter.toString()).perform() }

        actions.sendKeys(Keys.ENTER).perform()
    }

    fun close() {
        driver.quit()
    }
}

fun main() {
    println("Starting Quordle...")
    val quordleDriver = QuordleWebDriver()
    val llmGuesser = LLMQuordleGuesser()

    try {
        quordleDriver.initializeDriver()

        while (true) {
            val gameState = quordleDriver.parseGameState()

            // Print current state
            println("Current Game State:")
            println(gameState)

            if (gameState.isSolved()) {
                saveHtmlReplay(gameState, llmGuesser.allMessages)
                updateLLMGuesserStats(gameState, llmGuesser.modelId.toString())
                println("All puzzles solved!")
                break
            }

            if (gameState.isFailed()) {
                saveHtmlReplay(gameState, llmGuesser.allMessages)
                updateLLMGuesserStats(gameState, llmGuesser.modelId.toString())
                println("You lose. Game over!")
                break
            }

            println("Making a guess...")
            val nextGuess = llmGuesser.guessWord(gameState)
            quordleDriver.enterGuess(nextGuess)
        }
    } finally {
        //Thread.sleep(60000) // lemme take a screenshot
        quordleDriver.close()
    }
}

fun updateLLMGuesserStats(gameState: GameState, modelId: String) {
    TODO("Not yet implemented")
}


fun saveHtmlReplay(
    gameState: GameState,
    allMessages: MutableList<ChatMessage>
) {
    // Generate HTML replay using kotlinx.html
    val htmlContent = createHTML().html {
        head {
            meta(charset = "utf-8")
            title { +"Quordle Replay" }
            style {
                unsafe {
                    raw("""
                    body { margin: 0; padding: 0; font-family: sans-serif; }
                        .container { display: flex; height: 100vh; }
                        .left { flex: 1; padding: 10px; overflow: auto; background: #f0f0f0; }
                        .right { flex: 1; padding: 10px; overflow: auto; background: #ffffff; position: relative; }
                        .boards-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; }
                        .board-container { background: #ffffff; padding: 10px; border-radius: 4px; }
                        .board { display: grid; grid-template-columns: repeat(5, 30px); gap: 4px; margin-bottom: 10px; }
                        .tile { width: 30px; height: 30px; display: inline-block; text-align: center; line-height: 30px; font-weight: bold; color: #000; }
                        .tile.CORRECT { background: #6aaa64; }
                        .tile.PRESENT { background: #c9b458; }
                        .tile.ABSENT { background: #787c7e; }
                        .tile.EMPTY { background: #ffffff; border: 1px solid #ccc; }
                        .message { margin-bottom: 20px; white-space: pre-wrap; }
                        .hidden { visibility: hidden; }
                        .board-row { visibility: hidden; }
                    """
                    )
                }
            }
        }
        body {
            div(classes = "container") {
                div(classes = "left") {
                    div(classes = "boards-grid") {
                        gameState.boardStates.forEach { boardState ->
                            div(classes = "board-container") {
                                // Nested grid of attempts per board
                                boardState.attempts.forEachIndexed { ai, attempt ->
                                    div(classes = "board-row") {
                                        attributes["data-attempt-index"] = ai.toString()
                                        div(classes = "board") {
                                            attempt.word.toCharArray().zip(attempt.feedback).forEach { (ch, state) ->
                                                span(classes = "tile ${state.name}") { +ch.toString() }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                div(classes = "right") {
                    allMessages.forEachIndexed { idx, msg ->
                        div(classes = "message hidden") {
                            attributes["data-index"] = idx.toString()
                            attributes["data-role"] = msg.role::class.simpleName ?: ""
                            when (msg.role) {
                                ChatRole.User -> p { b { +"User: " }; +msg.content.orEmpty() }
                                ChatRole.System -> p { i { +"System: " }; +msg.content.orEmpty() }
                                ChatRole.Assistant -> p { b { +"LLM: " }; +msg.content.orEmpty() }
                                else -> p { +msg.content.orEmpty() }
                            }
                        }
                    }
                }
            }
            script {
                unsafe {
                    raw("""
                        const messages = document.querySelectorAll('.message');
                        let current = 0;
                        let attemptCount = 0;
                        function revealRowsForAttempt(idx) {
                            document.querySelectorAll('[data-attempt-index="'+idx+'"]').forEach(el => el.style.visibility = 'visible');
                        }
                        function showNext() {
                            if (current >= messages.length) return;
                            const el = messages[current];
                            const role = el.getAttribute('data-role');
                            el.classList.remove('hidden');
                            const text = el.innerText;
                            el.innerText = '';
                            let i = 0;
                            function typeChar() {
                                if (i < text.length) {
                                    el.innerText += text.charAt(i);
                                    i++;
                                    setTimeout(typeChar, 20);
                                } else {
                                    if (role === 'Assistant') {
                                        revealRowsForAttempt(attemptCount);
                                        attemptCount++;
                                    }
                                    current++;
                                    setTimeout(showNext, 500);
                                }
                            }
                            typeChar();
                        }
                        window.onload = showNext;
                     """
                     )
                 }
             }
        }
    }
    // Write to static path
    File("replay.html").writeText(htmlContent)
    println("Replay saved to replay.html")
}
