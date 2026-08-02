package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.balance.BalanceCalculator
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.PaymentWriter
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.util.Money
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The live money context the payment form derives its prompts from: the clerk's current owed on
 * this project (with this payment already counted, for an edit), the amount of the payment being
 * edited, and the owed with that payment removed (the threshold a new/edited amount overpays).
 */
data class PaymentFormContext(
    val currentOwed: Long = 0L,
    val oldAmount: Long = 0L,
    val existing: PaymentEntity? = null,
    val currency: String = "",
    val isLoaded: Boolean = false,
) {
    /** Owed with the payment being edited added back — the overpayment threshold. */
    val owedBeforeThisPayment: Long get() = currentOwed + oldAmount
}

/** An overpayment prompt for a brand-new payment: [amount] over-pays by [overBy] (both minor units). */
data class AdvancePrompt(val amount: Long, val overBy: Long)

/** A balance-impact prompt for an edit or delete: owed moves from [fromOwed] to [toOwed]. */
data class BalanceImpactPrompt(val amount: Long, val fromOwed: Long, val toOwed: Long)

data class PaymentFormUiState(
    val isEditing: Boolean = false,
    val amountInput: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val amountError: Boolean = false,
    val isSaving: Boolean = false,
    val saveComplete: Boolean = false,
    /** Overpay confirm for a NEW payment (record). */
    val advanceConfirm: AdvancePrompt? = null,
    /** Balance-impact confirm for an EDIT save. */
    val editConfirm: BalanceImpactPrompt? = null,
    /** Balance-impact confirm for a DELETE. */
    val deleteConfirm: BalanceImpactPrompt? = null,
)

sealed interface PaymentFormEvent {
    data class AmountChanged(val value: String) : PaymentFormEvent
    data class DateChanged(val value: LocalDate) : PaymentFormEvent
    data class NoteChanged(val value: String) : PaymentFormEvent
    data object Save : PaymentFormEvent
    data object ConfirmAdvance : PaymentFormEvent
    data object ConfirmEdit : PaymentFormEvent
    data object RequestDelete : PaymentFormEvent
    data object ConfirmDelete : PaymentFormEvent
    data object DismissDialogs : PaymentFormEvent
}

/**
 * Backs the full-screen payment form for both recording a new payment and editing/deleting an
 * existing one (LOCKED forms are full-screen pages). A new payment pre-fills the FULL owed
 * (paid-in-full; editable down for a partial), and overpaying prompts an advance confirm (LOCKED
 * #7). Editing or deleting paid history is always allowed but each sits behind a confirm that
 * shows the balance impact before it applies (LOCKED #11). The re-entrancy guard makes a fast
 * double tap record exactly one write.
 */
class PaymentFormViewModel(
    private val projectId: String,
    private val clerkId: String,
    private val paymentId: String?,
    projectDao: ProjectDao,
    attendanceEntryDao: AttendanceEntryDao,
    extraPayLineDao: ExtraPayLineDao,
    paymentDao: PaymentDao,
    private val writer: PaymentWriter,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentFormUiState(isEditing = paymentId != null))
    val state: StateFlow<PaymentFormUiState> = _state.asStateFlow()

    val context: StateFlow<PaymentFormContext> = combine(
        attendanceEntryDao.observeForClerkOnProject(projectId, clerkId),
        extraPayLineDao.observeForClerkOnProject(projectId, clerkId),
        paymentDao.observeForClerkOnProject(projectId, clerkId),
        paymentId?.let { paymentDao.observeById(it) } ?: flowOf(null),
        projectDao.observeById(projectId),
    ) { attendance, extras, payments, existing, project ->
        val earned = BalanceCalculator.earnedFrom(attendance)
        val extrasTotal = extras.sumOf { it.line.amount }
        val paid = payments.sumOf { it.amount }
        PaymentFormContext(
            currentOwed = BalanceCalculator.owed(earned, extrasTotal, paid),
            oldAmount = existing?.amount ?: 0L,
            existing = existing,
            currency = project?.currency.orEmpty(),
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PaymentFormContext())

    private var prefilled = false

    init {
        // Pre-fill once the real balance (record) or the edited payment (edit) has loaded. The
        // first combined emission already carries real query results, so this fires once.
        viewModelScope.launch {
            context.collect { ctx ->
                if (prefilled || !ctx.isLoaded) return@collect
                if (paymentId != null) {
                    val existing = ctx.existing ?: return@collect
                    prefilled = true
                    _state.update {
                        it.copy(
                            amountInput = Money.formatForInput(existing.amount),
                            date = existing.date,
                            note = existing.note,
                        )
                    }
                } else {
                    prefilled = true
                    val fullOwed = ctx.owedBeforeThisPayment
                    _state.update {
                        it.copy(amountInput = if (fullOwed > 0L) Money.formatForInput(fullOwed) else "")
                    }
                }
            }
        }
    }

    fun onEvent(event: PaymentFormEvent) {
        when (event) {
            is PaymentFormEvent.AmountChanged -> _state.update { it.copy(amountInput = event.value, amountError = false) }
            is PaymentFormEvent.DateChanged -> _state.update { it.copy(date = event.value) }
            is PaymentFormEvent.NoteChanged -> _state.update { it.copy(note = event.value.take(MAX_NOTE_LENGTH)) }
            PaymentFormEvent.Save -> onSave()
            PaymentFormEvent.ConfirmAdvance -> onConfirmAdvance()
            PaymentFormEvent.ConfirmEdit -> onConfirmEdit()
            PaymentFormEvent.RequestDelete -> onRequestDelete()
            PaymentFormEvent.ConfirmDelete -> onConfirmDelete()
            PaymentFormEvent.DismissDialogs ->
                _state.update { it.copy(advanceConfirm = null, editConfirm = null, deleteConfirm = null) }
        }
    }

    private fun onSave() {
        if (_state.value.isSaving) return
        val amount = Money.parseToMinorUnits(_state.value.amountInput)
        if (amount == null || amount <= 0L) {
            _state.update { it.copy(amountError = true) }
            return
        }
        val ctx = context.value
        if (paymentId != null) {
            // Editing paid history: always confirm, showing the resulting balance.
            val toOwed = BalanceCalculator.owedAfterChange(ctx.currentOwed, ctx.oldAmount, amount)
            _state.update { it.copy(editConfirm = BalanceImpactPrompt(amount, ctx.currentOwed, toOwed)) }
        } else if (BalanceCalculator.isAdvance(amount, ctx.owedBeforeThisPayment)) {
            _state.update {
                it.copy(advanceConfirm = AdvancePrompt(amount, amount - ctx.owedBeforeThisPayment))
            }
        } else {
            write(amount)
        }
    }

    private fun onConfirmAdvance() {
        val amount = _state.value.advanceConfirm?.amount ?: return
        _state.update { it.copy(advanceConfirm = null) }
        write(amount)
    }

    private fun onConfirmEdit() {
        val amount = _state.value.editConfirm?.amount ?: return
        _state.update { it.copy(editConfirm = null) }
        write(amount)
    }

    private fun onRequestDelete() {
        val ctx = context.value
        val toOwed = BalanceCalculator.owedAfterChange(ctx.currentOwed, ctx.oldAmount, 0L)
        _state.update { it.copy(deleteConfirm = BalanceImpactPrompt(0L, ctx.currentOwed, toOwed)) }
    }

    private fun onConfirmDelete() {
        if (_state.value.isSaving) return
        val existing = context.value.existing ?: return
        _state.update { it.copy(deleteConfirm = null, isSaving = true) }
        viewModelScope.launch {
            writer.deletePayment(existing)
            _state.update { it.copy(isSaving = false, saveComplete = true) }
        }
    }

    private fun write(amount: Long) {
        if (_state.value.isSaving) return
        val current = _state.value
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val existing = context.value.existing
            if (paymentId != null && existing != null) {
                writer.updatePayment(existing.copy(amount = amount, date = current.date, note = current.note.trim()))
            } else {
                writer.recordPayment(projectId, clerkId, current.date, amount, current.note.trim())
            }
            _state.update { it.copy(isSaving = false, saveComplete = true) }
        }
    }

    companion object {
        /** A payment note is free text with no error UI, so it needs a hard ceiling. */
        private const val MAX_NOTE_LENGTH = 140

        fun factory(
            projectId: String,
            clerkId: String,
            paymentId: String?,
            projectDao: ProjectDao,
            attendanceEntryDao: AttendanceEntryDao,
            extraPayLineDao: ExtraPayLineDao,
            paymentDao: PaymentDao,
            writer: PaymentWriter,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PaymentFormViewModel(
                    projectId,
                    clerkId,
                    paymentId,
                    projectDao,
                    attendanceEntryDao,
                    extraPayLineDao,
                    paymentDao,
                    writer,
                )
            }
        }
    }
}
