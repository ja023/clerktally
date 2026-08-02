package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.PaymentWriter
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeExtraPayLineDao
import com.vague.crewtally.testutil.FakePaymentDao
import com.vague.crewtally.testutil.FakePaymentWriter
import com.vague.crewtally.testutil.FakeProjectDao
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Headless tests for [PaymentFormViewModel]: the paid-in-full pre-fill, the advance-confirm
 * threshold, the edit/delete balance-impact prompts, and the double-tap re-entrancy guard that
 * makes a fast double tap record exactly one payment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentFormViewModelTest {

    private lateinit var projectDao: FakeProjectDao
    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var paymentDao: FakePaymentDao
    private lateinit var writer: FakePaymentWriter

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan2 = LocalDate.of(2026, 1, 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        projectDao = FakeProjectDao()
        attendanceEntryDao = FakeAttendanceEntryDao()
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        attendanceEntryDao.extraPayLineDao = extraPayLineDao
        paymentDao = FakePaymentDao()
        writer = FakePaymentWriter(paymentDao)

        projectDao.seed(ProjectEntity(id = "p1", companyId = "co1", name = "Warehouse", startDate = jan1, currency = "USD"))
        // Two present days at 5000 → earned 10000; no extras; no payments yet → owed 10000.
        attendanceEntryDao.seed(
            AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000),
            AttendanceEntryEntity("a2", "p1", "c1", jan2, present = true, rateSnapshot = 5000),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newForm(paymentId: String? = null, gatedWriter: PaymentWriter? = null) = PaymentFormViewModel(
        projectId = "p1",
        clerkId = "c1",
        paymentId = paymentId,
        projectDao = projectDao,
        attendanceEntryDao = attendanceEntryDao,
        extraPayLineDao = extraPayLineDao,
        paymentDao = paymentDao,
        writer = gatedWriter ?: writer,
    )

    @Test
    fun `record form pre-fills the full owed (paid in full)`() = runTest {
        val vm = newForm()
        // Full owed is 10000 minor units → "100.00".
        assertEquals("100.00", vm.state.value.amountInput)
    }

    @Test
    fun `record form pre-fills blank when nothing is owed`() = runTest {
        paymentDao.seed(PaymentEntity("pay0", "p1", "c1", jan1, 10000, "")) // already settled
        val vm = newForm()
        assertEquals("", vm.state.value.amountInput)
    }

    @Test
    fun `overpaying a new payment prompts an advance confirm and does not write until confirmed`() = runTest {
        val vm = newForm()
        vm.onEvent(PaymentFormEvent.AmountChanged("120")) // 12000 > 10000 owed
        vm.onEvent(PaymentFormEvent.Save)

        val prompt = vm.state.value.advanceConfirm
        assertNotNull("overpay must prompt an advance confirm", prompt)
        assertEquals(2000L, prompt!!.overBy) // 12000 - 10000
        assertEquals(0, writer.recordCallCount)

        vm.onEvent(PaymentFormEvent.ConfirmAdvance)
        assertEquals(1, writer.recordCallCount)
        assertTrue(vm.state.value.saveComplete)
    }

    @Test
    fun `a partial or exact payment records directly with no advance confirm`() = runTest {
        val vm = newForm()
        vm.onEvent(PaymentFormEvent.AmountChanged("100")) // exactly owed
        vm.onEvent(PaymentFormEvent.Save)

        assertNull(vm.state.value.advanceConfirm)
        assertEquals(1, writer.recordCallCount)
    }

    @Test
    fun `editing pre-fills the existing payment and confirms the balance impact`() = runTest {
        // Existing full payment settles the balance: owed 0 with it, owed 10000 without it.
        paymentDao.seed(PaymentEntity("pay1", "p1", "c1", jan1, 10000, "cash"))
        val vm = newForm(paymentId = "pay1")

        assertEquals("100.00", vm.state.value.amountInput)
        assertEquals("cash", vm.state.value.note)

        vm.onEvent(PaymentFormEvent.AmountChanged("70")) // drop to 7000
        vm.onEvent(PaymentFormEvent.Save)

        val prompt = vm.state.value.editConfirm
        assertNotNull("editing paid history must confirm the balance impact", prompt)
        assertEquals(0L, prompt!!.fromOwed) // currently settled
        assertEquals(3000L, prompt.toOwed) // owed 3000 after dropping the payment by 3000
        assertEquals(0, writer.updateCallCount)

        vm.onEvent(PaymentFormEvent.ConfirmEdit)
        assertEquals(1, writer.updateCallCount)
    }

    @Test
    fun `deleting confirms the balance impact then deletes`() = runTest {
        paymentDao.seed(PaymentEntity("pay1", "p1", "c1", jan1, 4000, ""))
        val vm = newForm(paymentId = "pay1")

        vm.onEvent(PaymentFormEvent.RequestDelete)
        val prompt = vm.state.value.deleteConfirm
        assertNotNull(prompt)
        assertEquals(6000L, prompt!!.fromOwed) // 10000 earned - 4000 paid
        assertEquals(10000L, prompt.toOwed) // removing the 4000 payment restores owed to 10000

        vm.onEvent(PaymentFormEvent.ConfirmDelete)
        assertEquals(1, writer.deleteCallCount)
        assertTrue(vm.state.value.saveComplete)
    }

    @Test
    fun `double tap on save records exactly one payment`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gatedWriter = GatedPaymentWriter(writer, gate)
        val vm = newForm(gatedWriter = gatedWriter)
        vm.onEvent(PaymentFormEvent.AmountChanged("50")) // partial, records directly (no dialog)

        vm.onEvent(PaymentFormEvent.Save) // first tap suspends at the gate, isSaving stays true
        vm.onEvent(PaymentFormEvent.Save) // second tap must no-op via the re-entrancy guard

        gate.complete(Unit)

        assertEquals(1, gatedWriter.recordCalls)
        assertEquals(1, writer.recordCallCount)
    }
}

/**
 * Wraps a real [PaymentWriter] but suspends on [gate] before delegating [recordPayment], so a
 * test can hold a save mid-flight and fire a genuine concurrent second Save against the ViewModel's
 * `isSaving` guard — which an always-immediately-completing fake can't reproduce under Unconfined.
 */
private class GatedPaymentWriter(
    private val delegate: PaymentWriter,
    private val gate: CompletableDeferred<Unit>,
) : PaymentWriter {
    var recordCalls: Int = 0
        private set

    override suspend fun recordPayment(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        amount: Long,
        note: String,
    ) {
        recordCalls += 1
        gate.await()
        delegate.recordPayment(projectId, clerkId, date, amount, note)
    }

    override suspend fun updatePayment(payment: PaymentEntity) = delegate.updatePayment(payment)

    override suspend fun deletePayment(payment: PaymentEntity) = delegate.deletePayment(payment)
}
