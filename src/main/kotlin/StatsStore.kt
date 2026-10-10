import java.io.File

data class StatsSnapshot(val content: String, val version: String)

interface StatsStore {
    fun read(): StatsSnapshot?
    fun write(content: String, expectedVersion: String?): Boolean
}

class FileStatsStore(private val file: File) : StatsStore {
    override fun read(): StatsSnapshot? = if (file.exists()) StatsSnapshot(file.readText(), "local") else null

    override fun write(content: String, expectedVersion: String?): Boolean {
        file.writeText(content)
        return true
    }
}

class S3StatsStore(private val repository: S3BucketRepository, private val key: String) : StatsStore {
    override fun read(): StatsSnapshot? = repository.downloadVersionedFile(key)

    override fun write(content: String, expectedVersion: String?): Boolean =
        repository.uploadFileIfUnchanged(key, content, expectedVersion)
}
