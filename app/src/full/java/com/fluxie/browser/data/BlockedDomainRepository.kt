package com.fluxie.browser.data

import com.fluxie.browser.common.BlocklistParser
import com.fluxie.browser.common.DomainRules
import kotlinx.coroutines.flow.Flow

/** Outcome of adding one typed entry. */
sealed interface AddResult {
    data class Added(val domain: String) : AddResult
    data class AlreadyListed(val domain: String) : AddResult
    data object Invalid : AddResult
}

/** Outcome of importing a text file: lines that were new, already listed, or unusable. */
data class ImportResult(val added: Int, val alreadyListed: Int, val skippedLines: Int)

class BlockedDomainRepository(
    private val dao: BlockedDomainDao,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun observeAll(): Flow<List<BlockedDomain>> = dao.observeAll()

    fun observe(category: BlockCategory): Flow<List<BlockedDomain>> = dao.observeByCategory(category.name)

    suspend fun getAll(): List<BlockedDomain> = dao.getAll()

    suspend fun add(input: String, category: BlockCategory): AddResult {
        val domain = DomainRules.normalize(input) ?: return AddResult.Invalid
        val inserted = dao.insertAll(listOf(BlockedDomain(domain, category.name, clock())))
        return if (inserted.single() == -1L) AddResult.AlreadyListed(domain) else AddResult.Added(domain)
    }

    suspend fun remove(domain: String) {
        dao.delete(domain)
    }

    suspend fun clear(category: BlockCategory) {
        dao.deleteCategory(category.name)
    }

    /** Adds every valid domain in [text] (see [BlocklistParser] for the accepted formats). */
    suspend fun importText(text: String, category: BlockCategory): ImportResult {
        val parsed = BlocklistParser.parse(text)
        val now = clock()
        val results = dao.insertAll(parsed.domains.map { BlockedDomain(it, category.name, now) })
        val added = results.count { it != -1L }
        return ImportResult(added, parsed.domains.size - added, parsed.skippedLines)
    }
}
