package com.calendaralarm.data.calendar

import com.calendaralarm.domain.AuthRepository
import com.calendaralarm.domain.CalendarEvent
import com.calendaralarm.domain.CalendarInfo
import com.calendaralarm.domain.CalendarRepository
import com.calendaralarm.domain.NotAuthorizedException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** The Calendar API as seen by one Google account. */
interface AccountCalendarApi {
    suspend fun calendars(): List<CalendarInfo>
    suspend fun upcomingEvents(calendarIds: Set<String>, from: Instant, to: Instant): List<CalendarEvent>
    suspend fun event(calendarId: String, eventId: String): CalendarEvent?
}

/**
 * [CalendarRepository] over every connected account: asks each account with its own token, tags results
 * with the account, and merges them. One account failing (e.g. revoked) doesn't hide the others.
 */
class MultiAccountCalendarRepository(
    private val auth: AuthRepository,
    private val apiFor: (account: String) -> AccountCalendarApi,
) : CalendarRepository {
    /** calendarId → account that lists it, from the latest [calendars] call. */
    private val owners = ConcurrentHashMap<String, String>()

    override suspend fun calendars(): List<CalendarInfo> {
        val results = perAccount { account -> apiFor(account).calendars().map { it.copy(account = account) } }
        return results.flatten().distinctBy { it.id }.also { list -> list.forEach { owners[it.id] = it.account } }
    }

    override suspend fun upcomingEvents(calendarIds: Set<String>, from: Instant, to: Instant): List<CalendarEvent> {
        val accounts = connected()
        if (calendarIds.any { it != PRIMARY && !owners.containsKey(it) }) runCatching { calendars() }
        val byAccount = calendarIds.groupBy { id -> if (id == PRIMARY) accounts.first() else owners[id] }
            .filterKeys { it != null && it in accounts }
            .mapKeys { it.key!! }
        if (byAccount.isEmpty()) return emptyList()
        val results = perAccount(byAccount.keys.toList()) { account ->
            apiFor(account).upcomingEvents(byAccount.getValue(account).toSet(), from, to).map { it.copy(account = account) }
        }
        return results.flatten()
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.start }, { it.title }))
    }

    override suspend fun event(account: String?, calendarId: String, eventId: String): CalendarEvent? {
        val accounts = connected()
        val chosen = account?.takeIf { it in accounts } ?: owners[calendarId]?.takeIf { it in accounts } ?: accounts.first()
        return apiFor(chosen).event(calendarId, eventId)?.copy(account = chosen)
    }

    private fun connected(): List<String> = auth.accounts.value.ifEmpty { throw NotAuthorizedException() }

    /** Runs [block] for each account in parallel; succeeds if any account does, else rethrows the first error. */
    private suspend fun <T> perAccount(
        accounts: List<String> = connected(),
        block: suspend (String) -> T,
    ): List<T> = coroutineScope {
        val outcomes = accounts.map { account ->
            async {
                try {
                    Result.success(block(account))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }.awaitAll()
        val successes = outcomes.mapNotNull { it.getOrNull() }
        if (successes.isEmpty()) throw outcomes.first().exceptionOrNull()!!
        successes
    }

    private companion object {
        const val PRIMARY = "primary"
    }
}
