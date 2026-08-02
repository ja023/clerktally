package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.PaymentWriter
import java.time.LocalDate
import java.util.UUID

/**
 * In-memory [PaymentWriter] over a [FakePaymentDao], mirroring [com.vague.crewtally.data.local.RoomPaymentWriter]:
 * record mints a fresh id, update reuses the entity's id, delete removes it. Call counts let the
 * double-tap re-entrancy test assert exactly one write happened.
 */
class FakePaymentWriter(private val paymentDao: FakePaymentDao) : PaymentWriter {

    var recordCallCount: Int = 0
        private set
    var updateCallCount: Int = 0
        private set
    var deleteCallCount: Int = 0
        private set

    override suspend fun recordPayment(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        amount: Long,
        note: String,
    ) {
        recordCallCount += 1
        paymentDao.upsert(
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

    override suspend fun updatePayment(payment: PaymentEntity) {
        updateCallCount += 1
        paymentDao.upsert(payment)
    }

    override suspend fun deletePayment(payment: PaymentEntity) {
        deleteCallCount += 1
        paymentDao.delete(payment)
    }
}
