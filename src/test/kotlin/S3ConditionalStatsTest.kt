import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception

class S3ConditionalStatsTest {
    @Test
    fun `creation uses if none match and updates use exact read etag`() {
        val requests = mutableListOf<PutObjectRequest>()
        val client = client { name, args ->
            when (name) {
                "getObject" -> ResponseBytes.fromByteArray(
                    GetObjectResponse.builder().eTag("\"etag-1\"").build(),
                    "{}".toByteArray(),
                )
                "putObject" -> {
                    requests.add(args[0] as PutObjectRequest)
                    PutObjectResponse.builder().build()
                }
                else -> error("Unexpected S3 call: $name")
            }
        }
        val repository = S3BucketRepository("test-bucket", client)
        val snapshot = repository.downloadVersionedFile("stats.json")!!
        assertEquals("{}", snapshot.content)
        assertEquals("\"etag-1\"", snapshot.version)
        assertTrue(repository.uploadFileIfUnchanged("stats.json", "{}", null))
        assertTrue(repository.uploadFileIfUnchanged("stats.json", "{}", snapshot.version))
        assertEquals(mapOf("If-None-Match" to listOf("*")), requests[0].overrideConfiguration().get().headers())
        assertEquals(mapOf("If-Match" to listOf("\"etag-1\"")), requests[1].overrideConfiguration().get().headers())
        assertEquals("application/json", requests[1].contentType())
    }

    @Test
    fun `missing object returns no snapshot`() {
        val repository = S3BucketRepository("test-bucket", client { _, _ ->
            throw NoSuchKeyException.builder().statusCode(404).build()
        })
        assertNull(repository.downloadVersionedFile("stats.json"))
    }

    @Test
    fun `write conflicts are retryable but other service errors propagate`() {
        for (code in listOf(409, 412, 404, 403, 500)) {
            val repository = S3BucketRepository("test-bucket", client { _, _ ->
                throw S3Exception.builder().statusCode(code).build()
            })
            if (code in listOf(409, 412, 404)) {
                assertFalse(repository.uploadFileIfUnchanged("stats.json", "{}", "etag"))
            } else {
                assertFailsWith<S3Exception> { repository.uploadFileIfUnchanged("stats.json", "{}", "etag") }
            }
        }
    }

    private fun client(call: (String, Array<out Any?>) -> Any?): S3Client = Proxy.newProxyInstance(
        S3Client::class.java.classLoader,
        arrayOf(S3Client::class.java),
    ) { _, method, args -> call(method.name, args ?: emptyArray()) } as S3Client
}
