package com.vague.crewtally.data.local

import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The atomic write seam for payments, mirroring [AttendanceWriter] and [ProjectRosterWriter].
 * Nothing outside a writer mutates the payments table — record / update / delete each run as a
 * single transaction so a killed process can never leave a half-written payment, and every
 * method is serialized behind ONE writer-scoped [Mutex] (see [RoomPaymentWriter]) so writes
 * fired from different screens (the payment form and, later, a bulk settle) apply in tap order
 * instead of racing — which holds only because every screen's ViewModel factory pulls
 * [CrewTallyApplication.paymentWriter] (the ONE app-wide instance) rather than constructing its
 * own `RoomPaymentWriter(database)`; a screen that built its own would get its own private
 * mutex, silently defeating this guarantee.
 *
 * An interface (not a concrete class) so ViewModel tests substitute an in-memory fake instead
 * of a real Room transaction.
 */
interface PaymentWriter {

    /**
     * Records a brand-new payment against one project's books for one clerk, minting a fresh id.
     * [amount] is in MINOR units and always positive (a payment's direction is fixed — the clerk
     * is paid); an overpayment simply drives the derived balance negative (an advance).
     */
    suspend fun recordPayment(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        amount: Long,
        note: String,
    )

    /** Updates an existing payment in place (id reused). Editing paid history is always allowed. */
    suspend fun updatePayment(payment: PaymentEntity)

    /** Deletes a payment. The derived balance recovers by exactly the deleted amount. */
    suspend fun deletePayment(payment: PaymentEntity)
}

/** Production [PaymentWriter] backed by a real [CrewTallyDatabase] transaction. */
class RoomPaymentWriter(private val database: CrewTallyDatabase) : PaymentWriter {

    /** Serializes every method below across every caller (every payment screen). */
    private val mutex = Mutex()

    override suspend fun recordPayment(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        amount: Long,
        note: String,
    ) {
        // Defense-in-depth: the ViewModel already blocks amount <= 0 before calling, but
        // PaymentEntity.amount's own invariant ("always positive; direction is fixed") should
        // hold at the write seam too, not just at whichever caller happens to check first.
        require(amount > 0) { "Payment amount must be positive (minor units); got $amount." }
        mutex.withLock {
            database.withTransaction {
                database.paymentDao().upsert(
                    PaymentEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        clerkId = clerkId,
                        date = date,
                        amount = amount,
                        note = note,
                    ),
                )
            }
        }
    }

    override suspend fun updatePayment(payment: PaymentEntity) {
        // Same invariant as recordPayment above — an edit must not be able to write a
        // zero/negative amount into PaymentEntity.amount either.
        require(payment.amount > 0) { "Payment amount must be positive (minor units); got ${payment.amount}." }
        mutex.withLock {
            database.withTransaction {
                database.paymentDao().upsert(payment)
            }
        }
    }

    override suspend fun deletePayment(payment: PaymentEntity) {
        mutex.withLock {
            database.withTransaction {
                database.paymentDao().delete(payment)
            }
        }
    }
}
