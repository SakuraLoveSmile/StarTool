package app.startool.android.acceptance.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.startool.android.MaintenanceLock
import app.startool.android.data.RoomStarToolRepository
import app.startool.android.data.StarToolDatabase
import app.startool.android.domain.ConflictChoice
import app.startool.android.domain.HourlyEntry
import app.startool.android.domain.HourlyKey
import app.startool.android.domain.ImportApplyResult
import app.startool.android.domain.OpResult
import app.startool.android.domain.ValidatedBackup
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStarToolRepositoryTest {

    private lateinit var db: StarToolDatabase
    private lateinit var repo: RoomStarToolRepository
    private var testInstant: Instant = Instant.parse("2026-09-23T10:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
        override fun instant(): Instant = testInstant
    }
    private val maintenance = MaintenanceLock()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StarToolDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomStarToolRepository(db, clock, maintenance)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSaveInsertAndObserve() = runBlocking {
        val date = LocalDate.of(2026, 9, 23)
        val key = HourlyKey(date, 9)
        val res = repo.saveEntry(key, 8, 7, "早晨")
        assertEquals(OpResult.Success, res)

        val list = repo.observeDay(date).first()
        assertEquals(1, list.size)
        assertEquals(key, list[0].key)
        assertEquals(8, list[0].mentalScore)
        assertEquals(7, list[0].physicalScore)
        assertEquals("早晨", list[0].note)
        assertEquals(testInstant, list[0].createdAt)
        assertEquals(testInstant, list[0].updatedAt)
    }

    @Test
    fun testSaveNoChangeWhenIdentical() = runBlocking {
        val date = LocalDate.of(2026, 9, 23)
        val key = HourlyKey(date, 10)
        repo.saveEntry(key, 5, 5, "平静")

        testInstant = Instant.parse("2026-09-23T11:00:00Z")
        val res = repo.saveEntry(key, 5, 5, "平静")
        assertEquals(OpResult.NoChange, res)

        val list = repo.observeDay(date).first()
        assertEquals(1, list.size)
        // updatedAt 应保持最初的时间
        assertEquals(Instant.parse("2026-09-23T10:00:00Z"), list[0].updatedAt)
    }

    @Test
    fun testSaveUpdateDifferentContent() = runBlocking {
        val date = LocalDate.of(2026, 9, 23)
        val key = HourlyKey(date, 10)
        repo.saveEntry(key, 5, 5, "旧备注")

        testInstant = Instant.parse("2026-09-23T11:00:00Z")
        val res = repo.saveEntry(key, 6, 5, "新备注")
        assertEquals(OpResult.Success, res)

        val list = repo.observeDay(date).first()
        assertEquals(1, list.size)
        assertEquals(Instant.parse("2026-09-23T10:00:00Z"), list[0].createdAt)
        assertEquals(Instant.parse("2026-09-23T11:00:00Z"), list[0].updatedAt)
        assertEquals(6, list[0].mentalScore)
        assertEquals("新备注", list[0].note)
    }

    @Test
    fun testDeleteEntry() = runBlocking {
        val date = LocalDate.of(2026, 9, 23)
        val key = HourlyKey(date, 12)
        repo.saveEntry(key, 7, 7, "")

        val resDelete = repo.deleteEntry(key)
        assertEquals(OpResult.Success, resDelete)

        val list = repo.observeDay(date).first()
        assertTrue(list.isEmpty())

        // 再次删除不存在记录
        val resSecond = repo.deleteEntry(key)
        assertEquals(OpResult.NoChange, resSecond)
    }

    @Test
    fun testMaintenanceLockBlocksWrite() = runBlocking {
        val date = LocalDate.of(2026, 9, 23)
        val key = HourlyKey(date, 13)

        maintenance.runExclusive {
            val res = repo.saveEntry(key, 6, 6, "")
            assertEquals(OpResult.BlockedByMaintenance, res)

            val delRes = repo.deleteEntry(key)
            assertEquals(OpResult.BlockedByMaintenance, delRes)
        }
    }

    @Test
    fun testPreviewAndApplyImport() = runBlocking {
        val date = LocalDate.of(2026, 9, 23)
        // 本机已有记录：09:00 (5,5) 与 10:00 (6,6)
        repo.saveEntry(HourlyKey(date, 9), 5, 5, "不变")
        repo.saveEntry(HourlyKey(date, 10), 6, 6, "本机旧内容")

        val backupEntries = listOf(
            HourlyEntry(HourlyKey(date, 9), 5, 5, "不变", Instant.EPOCH, Instant.EPOCH), // identical
            HourlyEntry(HourlyKey(date, 10), 8, 8, "备份新内容", Instant.EPOCH, Instant.EPOCH), // conflict
            HourlyEntry(HourlyKey(date, 11), 7, 7, "备份新增", Instant.EPOCH, Instant.EPOCH), // addition
        )
        val backup = ValidatedBackup(1, Instant.now(), backupEntries)

        val plan = repo.previewImport(backup)
        assertEquals(1, plan.identical.size)
        assertEquals(1, plan.conflicts.size)
        assertEquals(1, plan.additions.size)

        // 冲突选择：使用备份
        val choices = mapOf(HourlyKey(date, 10) to ConflictChoice.UseBackup)
        val result = repo.applyImport(plan, choices)
        assertTrue(result is ImportApplyResult.Completed)
        val report = (result as ImportApplyResult.Completed).report
        assertEquals(1, report.added)
        assertEquals(1, report.replacedWithBackup)
        assertEquals(0, report.keptLocal)
        assertEquals(1, report.skippedIdentical)

        val dayEntries = repo.observeDay(date).first()
        assertEquals(3, dayEntries.size)
        assertEquals(8, dayEntries.first { it.key.hour == 10 }.mentalScore)
        assertEquals("备份新内容", dayEntries.first { it.key.hour == 10 }.note)
        assertEquals(7, dayEntries.first { it.key.hour == 11 }.mentalScore)
    }
}
