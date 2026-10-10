import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler

class LambdaRuntimeInterface : RequestHandler<Map<String, Any?>, Unit> {

    override fun handleRequest(input: Map<String, Any?>, context: Context?) {
        return try {
            context?.logger?.log("Starting Quordle Lambda execution...")
            val gameRunner = QuordleGameRunner(RunConfiguration.from(input))
            gameRunner.runGame()
        } catch (e: Exception) {
            context?.logger?.log("Error during Quordle execution: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
