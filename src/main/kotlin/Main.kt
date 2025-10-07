import kotlin.system.exitProcess

fun main() {
    // For local testing - run the Quordle game directly
    val gameRunner = QuordleGameRunner()
    gameRunner.runGame()
    exitProcess(0)
}