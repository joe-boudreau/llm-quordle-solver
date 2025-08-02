import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.File

class S3BucketRepository(private val bucketName: String) {
    private val s3Client = S3Client.builder()
        .region(Region.CA_CENTRAL_1) // You can make this configurable if needed
        .build()

    fun uploadFile(key: String, content: String) {
        try {
            val putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("text/plain")
                .build()

            s3Client.putObject(putRequest, RequestBody.fromString(content))
            println("Successfully uploaded $key to S3")
        } catch (e: Exception) {
            println("Failed to upload $key to S3: ${e.message}")
            throw e
        }
    }

    fun uploadFile(key: String, file: File) {
        try {
            val putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(getContentType(key))
                .build()

            s3Client.putObject(putRequest, RequestBody.fromFile(file))
            println("Successfully uploaded $key to S3")
        } catch (e: Exception) {
            println("Failed to upload $key to S3: ${e.message}")
            throw e
        }
    }

    fun downloadFile(key: String): String? {
        return try {
            val getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            val content = s3Client.getObject(getRequest, ResponseTransformer.toBytes())
            String(content.asByteArray())
        } catch (e: NoSuchKeyException) {
            println("File $key not found in S3")
            null
        } catch (e: Exception) {
            println("Failed to download $key from S3: ${e.message}")
            throw e
        }
    }

    fun downloadFileToLocal(key: String, localFile: File): Boolean {
        return try {
            val getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            s3Client.getObject(getRequest, ResponseTransformer.toFile(localFile.toPath()))
            println("Successfully downloaded $key from S3 to ${localFile.absolutePath}")
            true
        } catch (e: NoSuchKeyException) {
            println("File $key not found in S3")
            false
        } catch (e: Exception) {
            println("Failed to download $key from S3: ${e.message}")
            false
        }
    }

    fun fileExists(key: String): Boolean {
        return try {
            val headRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            s3Client.headObject(headRequest)
            true
        } catch (e: NoSuchKeyException) {
            false
        } catch (e: Exception) {
            println("Error checking if $key exists in S3: ${e.message}")
            false
        }
    }

    private fun getContentType(key: String): String {
        return when {
            key.endsWith(".html") -> "text/html"
            key.endsWith(".json") -> "application/json"
            key.endsWith(".png") -> "image/png"
            key.endsWith(".jpg") || key.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
}