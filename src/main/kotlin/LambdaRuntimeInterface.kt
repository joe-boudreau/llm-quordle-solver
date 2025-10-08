import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler

class LambdaRuntimeInterface : RequestHandler<Unit, Unit> {

    override fun handleRequest(input: Unit, context: Context?) {
        return try {
            context?.logger?.log("Starting Quordle Lambda execution...")
            val gameRunner = QuordleGameRunner()
            gameRunner.runGame()
        } catch (e: Exception) {
            context?.logger?.log("Error during Quordle execution: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
