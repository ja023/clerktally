package com.vague.crewtally.ui.screen.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.R
import com.vague.crewtally.ui.components.CrewTallyButton
import com.vague.crewtally.ui.components.CrewTallyConfirmDialog
import com.vague.crewtally.ui.components.CrewTallyDateField
import com.vague.crewtally.ui.components.CrewTallyTextField
import com.vague.crewtally.ui.theme.CrewTallyTheme
import com.vague.crewtally.ui.util.owedDisplayText
import com.vague.crewtally.ui.viewmodel.PaymentFormEvent
import com.vague.crewtally.ui.viewmodel.PaymentFormViewModel
import com.vague.crewtally.util.CurrencyCodes
import com.vague.crewtally.util.Money

/**
 * The full-screen payment form (LOCKED forms are full-screen pages), for recording a new payment
 * and editing/deleting an existing one. A new payment pre-fills the FULL owed (paid in full),
 * editable down for a partial; overpaying prompts an advance confirm. Editing or deleting shows
 * the balance impact ("<clerk>'s balance changes from X to Y") before it applies — unless the
 * amount itself didn't change (e.g. a note-only edit), in which case the confirm says so plainly
 * instead of claiming a balance change that never happens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentFormScreen(
    projectId: String,
    clerkId: String,
    paymentId: String?,
    clerkName: String,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as CrewTallyApplication
    val database = application.database
    val viewModel: PaymentFormViewModel = viewModel(
        factory = PaymentFormViewModel.factory(
            projectId = projectId,
            clerkId = clerkId,
            paymentId = paymentId,
            projectDao = database.projectDao(),
            attendanceEntryDao = database.attendanceEntryDao(),
            extraPayLineDao = database.extraPayLineDao(),
            paymentDao = database.paymentDao(),
            writer = application.paymentWriter,
        ),
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context by viewModel.context.collectAsStateWithLifecycle()
    val symbol = CurrencyCodes.symbolFor(context.currency)

    LaunchedEffect(state.saveComplete) {
        if (state.saveComplete) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.payment_edit_title else R.string.payment_new_title,
                        ),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(CrewTallyTheme.dimens.screenEdge),
            verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceLg),
        ) {
            CrewTallyTextField(
                label = stringResource(R.string.payment_amount_label),
                value = state.amountInput,
                onValueChange = { viewModel.onEvent(PaymentFormEvent.AmountChanged(it)) },
                leadingText = symbol,
                isError = state.amountError,
                supportingText = when {
                    state.amountError -> stringResource(R.string.payment_amount_error)
                    // Recording (not editing) pre-fills the full owed amount — explain that so
                    // the number isn't mistaken for something the app is charging by default.
                    !state.isEditing -> stringResource(R.string.payment_amount_prefill_hint)
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            CrewTallyDateField(
                label = stringResource(R.string.payment_date_label),
                value = state.date,
                onValueChange = { viewModel.onEvent(PaymentFormEvent.DateChanged(it)) },
            )

            CrewTallyTextField(
                label = stringResource(R.string.payment_note_label),
                value = state.note,
                onValueChange = { viewModel.onEvent(PaymentFormEvent.NoteChanged(it)) },
                singleLine = false,
                minLines = 2,
            )

            CrewTallyButton(
                text = stringResource(R.string.payment_save),
                onClick = { viewModel.onEvent(PaymentFormEvent.Save) },
                // Disabled until the real balance/payment has loaded — a tap that lands before
                // then would race the "record new" vs. "update existing" branch (see
                // PaymentFormViewModel.onSave's isLoaded guard).
                enabled = !state.isSaving && context.isLoaded,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isEditing) {
                TextButton(
                    onClick = { viewModel.onEvent(PaymentFormEvent.RequestDelete) },
                    enabled = context.isLoaded,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = CrewTallyTheme.dimens.minTarget),
                ) {
                    Text(stringResource(R.string.payment_delete), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // Advance confirm (recording an overpayment on a new payment).
    state.advanceConfirm?.let { prompt ->
        CrewTallyConfirmDialog(
            title = stringResource(R.string.payment_advance_confirm_title),
            body = stringResource(R.string.payment_advance_confirm_body, Money.formatWithSymbol(prompt.overBy, symbol)),
            confirmLabel = stringResource(R.string.action_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.onEvent(PaymentFormEvent.ConfirmAdvance) },
            onDismiss = { viewModel.onEvent(PaymentFormEvent.DismissDialogs) },
            isDestructive = false,
        )
    }

    // Balance-impact confirm for an edit save. When the amount didn't change (e.g. a note-only
    // edit), fromOwed == toOwed and "changes from X to Y" would misleadingly claim a balance
    // change that never happens — swap in a plain unchanged-amount confirm instead.
    state.editConfirm?.let { prompt ->
        val amountUnchanged = prompt.fromOwed == prompt.toOwed
        CrewTallyConfirmDialog(
            title = stringResource(R.string.payment_edit_confirm_title),
            body = if (amountUnchanged) {
                stringResource(R.string.payment_edit_confirm_unchanged_body)
            } else {
                stringResource(
                    R.string.balance_impact_body,
                    clerkName,
                    owedDisplayText(prompt.fromOwed, symbol),
                    owedDisplayText(prompt.toOwed, symbol),
                )
            },
            confirmLabel = stringResource(R.string.action_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.onEvent(PaymentFormEvent.ConfirmEdit) },
            onDismiss = { viewModel.onEvent(PaymentFormEvent.DismissDialogs) },
            isDestructive = false,
        )
    }

    // Balance-impact confirm for a delete.
    state.deleteConfirm?.let { prompt ->
        CrewTallyConfirmDialog(
            title = stringResource(R.string.payment_delete_confirm_title),
            body = stringResource(
                R.string.balance_impact_body,
                clerkName,
                owedDisplayText(prompt.fromOwed, symbol),
                owedDisplayText(prompt.toOwed, symbol),
            ),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.onEvent(PaymentFormEvent.ConfirmDelete) },
            onDismiss = { viewModel.onEvent(PaymentFormEvent.DismissDialogs) },
            isDestructive = true,
        )
    }
}
