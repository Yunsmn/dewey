package app.dewey.assistant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [consume], [giveBack] and [remaining] are the whole rollover-and-cap rule,
 * kept as pure functions precisely so a rollover can be tested as "day 100
 * then day 101" with no clock, no coroutine and no [androidx.datastore.core.DataStore].
 */
class AssistantQuotaLogicTest {

    @Test
    fun `consume starts a fresh record from nothing`() {
        val (record, allowed) = consume(record = null, today = 100, limit = 3)

        assertThat(allowed).isTrue()
        assertThat(record).isEqualTo(QuotaRecord(day = 100, count = 1))
    }

    @Test
    fun `consume adds to today's own count`() {
        val (record, allowed) = consume(record = QuotaRecord(day = 100, count = 2), today = 100, limit = 3)

        assertThat(allowed).isTrue()
        assertThat(record).isEqualTo(QuotaRecord(day = 100, count = 3))
    }

    @Test
    fun `consume refuses once the limit for today is already spent`() {
        val (record, allowed) = consume(record = QuotaRecord(day = 100, count = 3), today = 100, limit = 3)

        assertThat(allowed).isFalse()
        assertThat(record).isEqualTo(QuotaRecord(day = 100, count = 3))
    }

    @Test
    fun `a new day resets the count before spending, even if yesterday was at the limit`() {
        val (record, allowed) = consume(record = QuotaRecord(day = 100, count = 50), today = 101, limit = 50)

        assertThat(allowed).isTrue()
        assertThat(record).isEqualTo(QuotaRecord(day = 101, count = 1))
    }

    @Test
    fun `giveBack takes one off today's count`() {
        val record = giveBack(record = QuotaRecord(day = 100, count = 5), today = 100)

        assertThat(record).isEqualTo(QuotaRecord(day = 100, count = 4))
    }

    @Test
    fun `giveBack never goes below zero`() {
        val record = giveBack(record = QuotaRecord(day = 100, count = 0), today = 100)

        assertThat(record).isEqualTo(QuotaRecord(day = 100, count = 0))
    }

    @Test
    fun `giveBack against a stale day starts today at zero rather than going negative`() {
        val record = giveBack(record = QuotaRecord(day = 99, count = 7), today = 100)

        assertThat(record).isEqualTo(QuotaRecord(day = 100, count = 0))
    }

    @Test
    fun `remaining counts down from the limit`() {
        assertThat(remaining(QuotaRecord(day = 100, count = 12), today = 100, limit = 50)).isEqualTo(38)
    }

    @Test
    fun `remaining is the full limit for a day with no record yet`() {
        assertThat(remaining(record = null, today = 100, limit = 50)).isEqualTo(50)
    }

    @Test
    fun `remaining is the full limit again once the stored day is not today`() {
        assertThat(remaining(QuotaRecord(day = 99, count = 50), today = 100, limit = 50)).isEqualTo(50)
    }
}

/**
 * [AssistantQuota] wired to a real [androidx.datastore.core.DataStore] over a
 * temp file — no Robolectric and no [android.content.Context] needed, since
 * [PreferenceDataStoreFactory.create] takes a plain file. [clock] is fixed so
 * "today" is a value this test chooses rather than whatever day it happens to
 * run on.
 */
class AssistantQuotaTest {

    /**
     * A fresh, unopened [DataStore] per test: [androidx.datastore.core.FileStorage]
     * refuses a second live [DataStore] over the same file within one process,
     * so a test that needs to look at the same file as of two different days
     * (see the rollover test) shares one [DataStore] across two [AssistantQuota]
     * wrappers rather than creating a second [PreferenceDataStoreFactory.create].
     */
    private fun store(): DataStore<Preferences> {
        val file = File.createTempFile("assistant_quota", ".preferences_pb").apply { deleteOnExit() }
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun quota(store: DataStore<Preferences>, instant: Instant = Instant.parse("2026-09-10T12:00:00Z")) =
        AssistantQuota(store = store, clock = Clock.fixed(instant, ZoneOffset.UTC), zone = ZoneOffset.UTC)

    @Test
    fun `tryConsume allows exactly the daily limit, then refuses`() = runTest {
        val instance = quota(store())

        val results = (1..AssistantQuota.DAILY_LIMIT + 1).map { instance.tryConsume() }

        assertThat(results.take(AssistantQuota.DAILY_LIMIT)).containsExactly(*Array(AssistantQuota.DAILY_LIMIT) { true })
        assertThat(results.last()).isFalse()
    }

    @Test
    fun `remainingToday reflects what has been spent`() = runTest {
        val instance = quota(store())

        repeat(3) { instance.tryConsume() }

        assertThat(instance.remainingToday.first()).isEqualTo(AssistantQuota.DAILY_LIMIT - 3)
    }

    @Test
    fun `release gives back one question of today's budget`() = runTest {
        val instance = quota(store())
        instance.tryConsume()
        instance.tryConsume()

        instance.release()

        assertThat(instance.remainingToday.first()).isEqualTo(AssistantQuota.DAILY_LIMIT - 1)
    }

    @Test
    fun `a new local day gives the full budget back`() = runTest {
        val sharedStore = store()
        val today = quota(sharedStore, instant = Instant.parse("2026-09-10T23:00:00Z"))
        repeat(AssistantQuota.DAILY_LIMIT) { today.tryConsume() }
        assertThat(today.tryConsume()).isFalse()

        // Same store, a new day: two AssistantQuota wrappers rather than a
        // second DataStore over the same file — see [store]'s own note.
        val tomorrow = quota(sharedStore, instant = Instant.parse("2026-09-11T01:00:00Z"))

        assertThat(tomorrow.remainingToday.first()).isEqualTo(AssistantQuota.DAILY_LIMIT)
        assertThat(tomorrow.tryConsume()).isTrue()
    }
}
