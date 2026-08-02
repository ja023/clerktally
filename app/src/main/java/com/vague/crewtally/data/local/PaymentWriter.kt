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
 * instead of racing.
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
