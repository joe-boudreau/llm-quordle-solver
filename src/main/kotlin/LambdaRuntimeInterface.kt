import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import kotlinx.serialization.Serializable

@Serializable
data class LambdaRequest(
    val message: String = "run_quordle"
)

@Serializable
data class LambdaResponse(
    val statusCode: Int,
    val body: String,
    val success: Boolean
)

class LambdaRuntimeInterface : RequestHandler<LambdaRequest, LambdaResponse> {

    override fun handleRequest(input: LambdaRequest?, context: Context?): LambdaResponse {
        return try {
            context?.logger?.log("Starting Quordle Lambda execution...")
            val gameRunner = QuordleGameRunner()
            gameRunner.runGame()
            LambdaResponse(200, "Quordle game completed successfully", true)
        } catch (e: Exception) {
            context?.logger?.log("Error during Quordle execution: ${e.message}")
            e.printStackTrace()
            LambdaResponse(500, "Error: ${e.message}", false)
        }
    }
}
