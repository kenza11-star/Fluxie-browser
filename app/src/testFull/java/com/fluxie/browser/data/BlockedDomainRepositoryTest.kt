package com.fluxie.browser.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockedDomainRepositoryTest {

    private lateinit var db: FluxieDatabase
    private lateinit var repository: BlockedDomainRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FluxieDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = BlockedDomainRepository(db.blockedDomainDao(), clock = { 1000L })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `add normalizes and lists the domain`() = runTest {
        assertEquals(AddResult.Added("example.com"), repository.add("https://Example.com/path", BlockCategory.GENERAL))
        assertEquals(listOf("example.com"), repository.observe(BlockCategory.GENERAL).first().map { it.domain })
    }

    @Test
    fun `duplicates and invalid input are reported, not stored`() = runTest {
        repository.add("example.com", BlockCategory.GENERAL)
        assertEquals(AddResult.AlreadyListed("example.com"), repository.add("EXAMPLE.com", BlockCategory.GENERAL))
        // One domain lives in one list only.
        assertEquals(AddResult.AlreadyListed("example.com"), repository.add("example.com", BlockCategory.ADULT))
        assertEquals(AddResult.Invalid, repository.add("not a domain", BlockCategory.GENERAL))
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun `lists are separate per category`() = runTest {
        repository.add("a.com", BlockCategory.GENERAL)
        repository.add("b.com", BlockCategory.ADULT)
        assertEquals(listOf("a.com"), repository.observe(BlockCategory.GENERAL).first().map { it.domain })
        assertEquals(listOf("b.com"), repository.observe(BlockCategory.ADULT).first().map { it.domain })
        assertEquals(2, repository.observeAll().first().size)
    }

    @Test
    fun `remove deletes only that domain`() = runTest {
        repository.add("a.com", BlockCategory.GENERAL)
        repository.add("b.com", BlockCategory.GENERAL)
        repository.remove("a.com")
        assertEquals(listOf("b.com"), repository.getAll().map { it.domain })
    }

    @Test
    fun `clear removes one category only`() = runTest {
        repository.add("a.com", BlockCategory.GENERAL)
        repository.add("b.com", BlockCategory.ADULT)
        repository.clear(BlockCategory.ADULT)
        assertEquals(listOf("a.com"), repository.getAll().map { it.domain })
    }

    @Test
    fun `import adds valid lines and reports duplicates and skipped lines`() = runTest {
        repository.add("old.com", BlockCategory.GENERAL)
        val result = repository.importText(
            "# list\nold.com\nnew.com\n0.0.0.0 hosts.net\n||adblock.org^\nexample.com##.ad\n",
            BlockCategory.GENERAL
        )
        assertEquals(ImportResult(added = 3, alreadyListed = 1, skippedLines = 1), result)
        assertEquals(setOf("old.com", "new.com", "hosts.net", "adblock.org"), repository.getAll().map { it.domain }.toSet())
    }

    @Test
    fun `empty import changes nothing`() = runTest {
        assertEquals(ImportResult(0, 0, 0), repository.importText("", BlockCategory.GENERAL))
    }
}
