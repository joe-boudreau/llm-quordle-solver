import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.interactions.Actions
import java.time.Duration

class QuordleWebDriver {
    private lateinit var driver: ChromeDriver

    private val DEBUG_MODE = System.getenv("DEBUG_MODE")?.toBoolean() ?: false

    /**
     * List of 4 game boards, each containing a list of 9 rows
     */
    private lateinit var boardRowTiles: List<List<List<WebElement>>>

    fun initializeDriver() {
        println("Initializing WebDriver...")
        val options = ChromeOptions().apply {
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
            addArguments("--disable-gpu")
            addArguments("--disable-extensions")
            addArguments("--blink-settings=imagesEnabled=false")
            addArguments("--headless=new")
            addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            if (DEBUG_MODE) {
                addArguments("--verbose")
            }

            if (System.getenv("AWS_LAMBDA_FUNCTION_NAME") != null) {
                val dir1 = mkdtemp()
                val dir2 = mkdtemp()
                val dir3 = mkdtemp()
                addArguments("--user-data-dir=$dir1")
                addArguments("--data-path=$dir2")
                addArguments("--disk-cache-dir=$dir3")
                addArguments("--log-path=/tmp")
                println("Running in AWS Lambda environment, using temporary directories for Chrome data.")
                // Make the directories
                listOf(dir1, dir2, dir3).forEach { dir ->
                    val process = ProcessBuilder("mkdir", "-p", dir).start()
                    process.waitFor()
                    if (process.exitValue() != 0) {
                        throw RuntimeException("Failed to create directory: $dir")
                    }
                }
                println("Temporary directories created: $dir1, $dir2, $dir3")
            }
        }

        try {

            println("Setting up Chrome options...")
            driver = ChromeDriver(options)
            println("WebDriver initialized.")

            driver.manage().timeouts()
                .pageLoadTimeout(Duration.ofSeconds(30))
                .implicitlyWait(Duration.ofSeconds(30))
                .scriptTimeout(Duration.ofSeconds(30))

            println("Attempting to navigate to Quordle page...")
            try {
                driver.get("https://www.merriam-webster.com/games/quordle")
                println("Request sent")
            } catch (_: org.openqa.selenium.TimeoutException) {
                // Continue even if we get a timeout - the page might be loaded enough
                println("Page load timed out, but continuing anyway...")
            }

            driver.findElements(By.cssSelector("div[aria-label='Game Boards']")).isNotEmpty()
            println("Game boards loaded.")

            val board1 = driver.findElement(By.cssSelector("div[aria-label='Game Boards'] > div[aria-label='Game Boards Row 1'] > div[aria-label='Game Board 1']"))
            val board2 = driver.findElement(By.cssSelector("div[aria-label='Game Boards'] > div[aria-label='Game Boards Row 1'] > div[aria-label='Game Board 2']"))
            val board3 = driver.findElement(By.cssSelector("div[aria-label='Game Boards'] > div[aria-label='Game Boards Row 2'] > div[aria-label='Game Board 3']"))
            val board4 = driver.findElement(By.cssSelector("div[aria-label='Game Boards'] > div[aria-label='Game Boards Row 2'] > div[aria-label='Game Board 4']"))
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

    fun getWordAnswers(): List<String> {
        // Find all spans with aria-labels that contain answer information
        val answerSpans = driver.findElements(By.cssSelector("span[aria-label*='Answer is']"))

        // Extract words from aria-labels and sort by game board number
        val answers = answerSpans.mapNotNull { span ->
            val ariaLabel = span.getAttribute("aria-label") ?: return@mapNotNull null

            // Parse aria-label like "Answer is ACTOR for game board 1. Unsolved."
            val regex = Regex("Answer is (\\w+) for game board (\\d+)")
            val matchResult = regex.find(ariaLabel)

            if (matchResult != null) {
                val word = matchResult.groupValues[1]
                val boardNumber = matchResult.groupValues[2].toInt()
                Pair(boardNumber, word)
            } else null
        }.sortedBy { it.first }.map { it.second }

        assert(answers.size == 4) { "Expected exactly 4 answers, but found ${answers.size}" }
        return answers
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

    fun mkdtemp(): String {
        // Create a unique temporary directory under the /tmp folder
        return "/tmp/" + "quordle-webdriver-${System.currentTimeMillis()}-" + (0..9999).random()
    }
}