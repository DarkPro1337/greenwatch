package io.github.darkpro1337.db

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class WatchRepositoryTest {
    private lateinit var dbFile: File
    private lateinit var repo: WatchRepository

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("greenwatch-test", ".db")
        DatabaseFactory.init(dbFile.absolutePath)
        repo = WatchRepository()
    }

    @AfterTest
    fun tearDown() {
        dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }

    @Test
    fun `creates watch with filters and lists for user`() {
        val watch = repo.createWatch(
            userId = 42L,
            boardToken = "jetbrains",
            name = "JB Armenia Dev",
            offices = listOf(OfficeFilterRecord(4029421101L, "Armenia")),
            metadata = listOf(
                MetadataFilterRecord(11295787101L, "Job Category", "Software Development"),
            ),
        )

        assertTrue(watch.id > 0)
        val filters = watch.toFilters()
        assertEquals(setOf(4029421101L), filters.officeIds)
        assertEquals(
            mapOf(11295787101L to setOf("Software Development")),
            filters.metadataByField,
        )

        val listed = repo.listWatches(42L)
        assertEquals(1, listed.size)

        repo.markSeen(watch.id, listOf(1L, 2L, 2L))
        assertEquals(setOf(1L, 2L), repo.seenJobIds(watch.id))

        assertTrue(repo.deactivateWatch(42L, watch.id))
        assertFalse(repo.deactivateWatch(42L, watch.id))
        assertTrue(repo.listWatches(42L).isEmpty())
    }

    @Test
    fun `creates missing parent directory for sqlite file`() {
        val dir = File(dbFile.parentFile, "nested-${System.nanoTime()}")
        val nested = File(dir, "greenwatch.db")
        assertFalse(dir.exists())
        DatabaseFactory.ensureWritableDatabaseFile(nested.absolutePath)
        assertTrue(dir.isDirectory)
        assertTrue(dir.canWrite())
        dir.deleteRecursively()
    }
}
