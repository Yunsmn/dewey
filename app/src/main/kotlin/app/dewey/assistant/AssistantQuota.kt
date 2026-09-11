package app.dewey.assistant

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * The day and count [AssistantQuota] has persisted, or null when nothing has
 * been asked yet on this install.
 *
 * A plain data class rather than the [Preferences] object itself so
 * [consume], [giveBack] and [remaining] — the rules that actually matter —
 * are pure functions a test can drive with no [DataStore] and no coroutine.
 */
internal data class QuotaRecord(val day: Long, val count: Int)

/** [record]'s count if it belongs to [today], zero for any other day — including no record at all. */
private fun countFor(record: QuotaRecord?, today: Long): Int = if (record?.day == today) record.count else 0

/**
 * Spends one question against [today], if [limit] allows it.
 *
 * Returns the record to persist either way, and whether the spend was
 * allowed — a stale record from yesterday resets to zero before spending, so
 * the local calendar day rolling over is what gives the budget back, not a
 * background job.
 */
internal fun consume(record: QuotaRecord?, today: Long, limit: Int): Pair<QuotaRecord, Boolean> {
    val count = countFor(record, today)
    return if (count < limit) QuotaRecord(today, count + 1) to true else QuotaRecord(today, count) to false
}

/** The inverse of one [consume] call that turned out not to have spent anything real — see [DocumentAssistant]. */
internal fun giveBack(record: QuotaRecord?, today: Long): QuotaRecord =
    QuotaRecord(today, (countFor(record, today) - 1).coerceAtLeast(0))

/** Questions still available [today], never negative. */
internal fun remaining(record: QuotaRecord?, today: Long, limit: Int): Int =
    (limit - countFor(record, today)).coerceAtLeast(0)

/**
 * At most [DAILY_LIMIT] cloud questions per local calendar day, shared by the
 * Documents ask bar and the full-screen assistant — both read and spend from
 * the same [store], so a question asked in one counts against the other.
 *
 * The day is [LocalDate.now] against [clock] in [zone]: local midnight, not
 * UTC midnight, so someone west of Greenwich does not lose today's budget
 * hours early. [clock] and [zone] are separated from `Clock.systemDefaultZone()`
 * so a test can hold the day fixed, or move it, without touching a real clock.
 */
class AssistantQuota(
    private val store: DataStore<Preferences>,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = clock.zone,
) {

    /**
     * Recomputed at every local midnight as well as on every write. Mapping the
     * store alone read "today" only when something was saved, so a screen left
     * open overnight kept showing yesterday's count until the next question.
     */
    val remainingToday: Flow<Int> =
        combine(store.data, dayTicks()) { prefs, today -> remaining(prefs.toRecord(), today, DAILY_LIMIT) }

    /** Today's epoch day now, then again each time the local day rolls over. */
    private fun dayTicks(): Flow<Long> = flow {
        while (true) {
            val now = ZonedDateTime.now(clock.withZone(zone))
            emit(now.toLocalDate().toEpochDay())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1))
        }
    }

    /** Spends one question of today's budget. False once [DAILY_LIMIT] is already spent today. */
    suspend fun tryConsume(): Boolean {
        var allowed = false
        store.edit { prefs ->
            val (updated, wasAllowed) = consume(prefs.toRecord(), today(), DAILY_LIMIT)
            prefs.writeRecord(updated)
            allowed = wasAllowed
        }
        return allowed
    }

    /** Gives back a slot spent on a question that never actually reached the model. */
    suspend fun release() {
        store.edit { prefs -> prefs.writeRecord(giveBack(prefs.toRecord(), today())) }
    }

    private fun today(): Long = LocalDate.now(clock.withZone(zone)).toEpochDay()

    private fun Preferences.toRecord(): QuotaRecord? {
        val day = this[DAY_KEY] ?: return null
        return QuotaRecord(day, this[COUNT_KEY] ?: 0)
    }

    private fun MutablePreferences.writeRecord(record: QuotaRecord) {
        this[DAY_KEY] = record.day
        this[COUNT_KEY] = record.count
    }

    companion object {
        const val DAILY_LIMIT = 50

        private val DAY_KEY = longPreferencesKey("day")
        private val COUNT_KEY = intPreferencesKey("count")
    }
}

/** Declared the way `Context.recentFilesStore` is — see [app.dewey.data.recent.RecentFiles]. */
internal val Context.assistantQuotaStore: DataStore<Preferences> by preferencesDataStore(name = "assistant_quota")
