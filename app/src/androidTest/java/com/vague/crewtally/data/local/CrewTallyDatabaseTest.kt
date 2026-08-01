package com.vague.crewtally.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/**
 * DAO smoke tests over an in-memory Room database. INSTRUMENTED — needs a connected device or
 * emulator (`./gradlew connectedDebugAndroidTest`); it will not run in a headless CI box without
 * one. Covers a standalone Company and Clerk, then the full money graph:
 * Project -> RosterEntry -> AttendanceEntry -> ExtraPayLine, plus a Payment.
 */
@RunWith(AndroidJUnit4::class)
class CrewTallyDatabaseTest {

    private lateinit var db: CrewTallyDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CrewTallyDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun company_insertAndRead() = runBlocking {
        val company = CompanyEntity(id = uuid(), name = "Acme")
        db.companyDao().upsert(company)

        val all = db.companyDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Acme", all.first().name)
    }

    @Test
    fun clerk_insertAndRead() = runBlocking {
        val clerk = ClerkEntity(id = uuid(), name = "Sam")
        db.clerkDao().upsert(clerk)

        assertEquals("Sam", db.clerkDao().getById(clerk.id)?.name)
    }

    @Test
    fun moneyGraph_insertAndRead() = runBlocking {
        // Arrange: company + clerk parents.
        val company = CompanyEntity(id = uuid(), name = "Acme")
        val clerk = ClerkEntity(id = uuid(), name = "Sam")
        db.companyDao().upsert(company)
        db.clerkDao().upsert(clerk)

        // Project under the company.
        val project = ProjectEntity(
            id = uuid(),
            companyId = company.id,
            name = "Warehouse Count",
            startDate = LocalDate.of(2026, 8, 1),
            currency = "USD",
        )
        db.projectDao().upsert(project)

        // Roster assignment with a daily rate (minor units: $80.00 -> 8000 cents).
        val roster = RosterEntryEntity(
            id = uuid(),
            projectId = project.id,
            clerkId = clerk.id,
            dailyRate = 8000,
        )
        db.rosterEntryDao().upsert(roster)

        // Attendance for the day, snapshotting the rate.
        val attendance = AttendanceEntryEntity(
            id = uuid(),
            projectId = project.id,
            clerkId = clerk.id,
            date = LocalDate.of(2026, 8, 1),
            present = true,
            rateSnapshot = 8000,
        )
        db.attendanceEntryDao().upsert(attendance)

        // Two extras: a positive bonus and a negative deduction.
        val bonus = ExtraPayLineEntity(uuid(), attendance.id, "Bonus", 1500)
        val deduction = ExtraPayLineEntity(uuid(), attendance.id, "Damage", -500)
        db.extraPayLineDao().upsert(bonus)
        db.extraPayLineDao().upsert(deduction)

        // A partial payment.
        val payment = PaymentEntity(
            id = uuid(),
            projectId = project.id,
            clerkId = clerk.id,
            date = LocalDate.of(2026, 8, 2),
            amount = 5000,
            note = "cash",
        )
        db.paymentDao().upsert(payment)

        // Assert: every leg reads back through its DAO with values intact.
        assertNotNull(db.projectDao().getById(project.id))
        assertEquals(ProjectStatus.ACTIVE, db.projectDao().getById(project.id)?.status)
        assertEquals(8000, db.rosterEntryDao().getForPair(project.id, clerk.id)?.dailyRate)

        val dayRoster = db.attendanceEntryDao()
            .observeByProjectAndDate(project.id, LocalDate.of(2026, 8, 1)).first()
        assertEquals(1, dayRoster.size)
        assertEquals(8000, dayRoster.first().rateSnapshot)

        val extras = db.extraPayLineDao().getForAttendance(attendance.id)
        assertEquals(2, extras.size)
        assertEquals(1000L, extras.sumOf { it.amount }) // 1500 - 500

        val payments = db.paymentDao().observeForClerkOnProject(project.id, clerk.id).first()
        assertEquals(1, payments.size)
        assertEquals(5000, payments.first().amount)
    }

    private fun uuid(): String = UUID.randomUUID().toString()
}
