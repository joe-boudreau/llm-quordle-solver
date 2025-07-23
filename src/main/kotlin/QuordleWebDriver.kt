import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.WebDriverWait
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