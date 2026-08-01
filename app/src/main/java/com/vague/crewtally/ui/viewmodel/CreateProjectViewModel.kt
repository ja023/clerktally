package com.vague.crewtally.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectRosterWriter
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.data.local.RosterEntryDao
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.util.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three full-screen steps of the create-project wizard (LOCKED Phase 2 decision). */
enum class CreateProjectStep { DETAILS, ROSTER, RATES }

/** Every editable field across the three create-project steps, plus their validation state. */
data class CreateProjectUiState(
    val step: CreateProjectStep = CreateProjectStep.DETAILS,
    // Step 1 — details
    val name: String = "",
    val nameError: Boolean = false,
    val selectedCompanyId: String? = null,
    val companyError: Boolean = false,
    val currency: String = "",
    val currencyError: Boolean = false,
    val location: String = "",
    val notes: String = "",
    val startDate: LocalDate = LocalDate.now(),
    // Step 2 — roster
    val clerkSearchQuery: String = "",
    val selectedClerkIds: Set<String> = emptySet(),
    // Step 3 — rates
    val rateInputs: Map<String, String> = emptyMap(),
    val rateErrors: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val createdProjectId: String? = null,
)

sealed interface CreateProjectEvent {
    data class NameChanged(val value: String) : CreateProjectEvent
    data class CompanySelected(val companyId: String) : CreateProjectEvent
    data class CurrencyChanged(val value: String) : CreateProjectEvent
    data class LocationChanged(val value: String) : CreateProjectEvent
    data class NotesChanged(val value: String) : CreateProjectEvent
    data class StartDateChanged(val value: LocalDate) : CreateProjectEvent
    data object NextFromDetails : CreateProjectEvent
    data class ClerkSearchChanged(val value: String) : CreateProjectEvent
    data class ClerkToggled(val clerkId: String) : CreateProjectEvent
    data object NextFromRoster : CreateProjectEvent
    data class RateChanged(val clerkId: String, val value: String) : CreateProjectEvent
    data object StepBack : CreateProjectEvent
    data object Save : CreateProjectEvent
    data object SaveHandled : CreateProjectEvent
}

/**
 * Drives the three-step create-project wizard (LOCKED Phase 2 decision): details, roster,
 * rates. All three steps share one state object and one ViewModel so wizard progress
 * survives moving back and forth between steps without losing edits.
 *
 * The final save goes through [writer] rather than [ProjectDao] directly so the project row
 * and its initial roster rows land in one atomic transaction (see [ProjectRosterWriter]).
 */
class CreateProjectViewModel(
    projectDao: ProjectDao,
    companyDao: CompanyDao,
    clerkDao: ClerkDao,
    private val rosterEntryDao: RosterEntryDao,
    private val writer: ProjectRosterWriter,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateProjectUiState())
    val state: StateFlow<CreateProjectUiState> = _state.asStateFlow()

    val companies: StateFlow<List<CompanyEntity>> = companyDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** All active clerks, unfiltered — step 3 uses this to resolve names for selected ids. */
    val clerks: StateFlow<List<ClerkEntity>> = clerkDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Active clerks matching the step-2 search box, name-sorted (already sorted by the DAO). */
    val filteredClerks: StateFlow<List<ClerkEntity>> = combine(clerks, _state) { allClerks, form ->
        val query = form.clerkSearchQuery.trim()
        if (query.isEmpty()) allClerks else allClerks.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        // Currency defaults to the last-used one (LOCKED decision #2) but never overwrites
        // whatever the user has already typed by the time this suspend call returns.
        viewModelScope.launch {
            val lastCurrency = projectDao.getMostRecentCurrency()
            if (lastCurrency != null) {
                _state.update { if (it.currency.isBlank()) it.copy(currency = lastCurrency) else it }
            }
        }
    }

    fun onEvent(event: CreateProjectEvent) {
        when (event) {
            is CreateProjectEvent.NameChanged -> _state.update { it.copy(name = event.value, nameError = false) }
            is CreateProjectEvent.CompanySelected ->
                _state.update { it.copy(selectedCompanyId = event.companyId, companyError = false) }
            is CreateProjectEvent.CurrencyChanged ->
                _state.update { it.copy(currency = event.value, currencyError = false) }
            is CreateProjectEvent.LocationChanged -> _state.update { it.copy(location = event.value) }
            is CreateProjectEvent.NotesChanged -> _state.update { it.copy(notes = event.value) }
            is CreateProjectEvent.StartDateChanged -> _state.update { it.copy(startDate = event.value) }
            CreateProjectEvent.NextFromDetails -> onNextFromDetails()
            is CreateProjectEvent.ClerkSearchChanged -> _state.update { it.copy(clerkSearchQuery = event.value) }
            is CreateProjectEvent.ClerkToggled -> onClerkToggled(event.clerkId)
            CreateProjectEvent.NextFromRoster -> onNextFromRoster()
            is CreateProjectEvent.RateChanged -> onRateChanged(event.clerkId, event.value)
            CreateProjectEvent.StepBack -> onStepBack()
            CreateProjectEvent.Save -> onSave()
            CreateProjectEvent.SaveHandled -> _state.update { it.copy(createdProjectId = null) }
        }
    }

    private fun onNextFromDetails() {
        val current = _state.value
        val nameError = current.name.isBlank()
        val companyError = current.selectedCompanyId == null
        val currencyError = current.currency.isBlank()
        if (nameError || companyError || currencyError) {
            _state.update { it.copy(nameError = nameError, companyError = companyError, currencyError = currencyError) }
            return
        }
        _state.update { it.copy(step = CreateProjectStep.ROSTER) }
    }

    private fun onClerkToggled(clerkId: String) {
        _state.update { current ->
            val selection = if (clerkId in current.selectedClerkIds) {
                current.selectedClerkIds - clerkId
            } else {
                current.selectedClerkIds + clerkId
            }
            current.copy(selectedClerkIds = selection)
        }
    }

    /** Zero clerks is a valid roster (LOCKED Phase 2 decision) — this step never blocks Next. */
    private fun onNextFromRoster() {
        viewModelScope.launch {
            val current = _state.value
            val missing = current.selectedClerkIds - current.rateInputs.keys
            val suggestions = missing.associateWith { clerkId -> suggestedRateInput(clerkId) }
            _state.update { it.copy(step = CreateProjectStep.RATES, rateInputs = it.rateInputs + suggestions) }
        }
    }

    /** The rate-suggestion rule: most recent roster rate for this clerk anywhere, or blank. */
    private suspend fun suggestedRateInput(clerkId: String): String =
        rosterEntryDao.getMostRecentRateForClerk(clerkId)?.let(Money::formatForInput).orEmpty()

    private fun onRateChanged(clerkId: String, value: String) {
        _state.update {
            it.copy(rateInputs = it.rateInputs + (clerkId to value), rateErrors = it.rateErrors - clerkId)
        }
    }

    private fun onStepBack() {
        _state.update {
            val previous = when (it.step) {
                CreateProjectStep.DETAILS -> CreateProjectStep.DETAILS
                CreateProjectStep.ROSTER -> CreateProjectStep.DETAILS
                CreateProjectStep.RATES -> CreateProjectStep.ROSTER
            }
            it.copy(step = previous)
        }
    }

    private fun onSave() {
        val current = _state.value
        val selectedClerkIds = current.selectedClerkIds.toList()
        val parsedRates = selectedClerkIds.associateWith { Money.parseToMinorUnits(current.rateInputs[it].orEmpty()) }
        val invalidClerkIds = parsedRates.filterValues { rate -> rate == null || rate <= 0L }.keys
        if (invalidClerkIds.isNotEmpty()) {
            _state.update { it.copy(rateErrors = invalidClerkIds) }
            return
        }
        val companyId = current.selectedCompanyId ?: return
        saveProject(current, companyId, selectedClerkIds, parsedRates)
    }

    private fun saveProject(
        formState: CreateProjectUiState,
        companyId: String,
        selectedClerkIds: List<String>,
        parsedRates: Map<String, Long?>,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val projectId = UUID.randomUUID().toString()
            val project = ProjectEntity(
                id = projectId,
                companyId = companyId,
                name = formState.name.trim(),
                location = formState.location.trim(),
                startDate = formState.startDate,
                status = ProjectStatus.ACTIVE,
                currency = formState.currency.trim().uppercase(),
                notes = formState.notes.trim(),
            )
            val rosterEntries = selectedClerkIds.map { clerkId ->
                RosterEntryEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    clerkId = clerkId,
                    dailyRate = requireNotNull(parsedRates[clerkId]) { "validated non-null above" },
                )
            }
            writer.createProjectWithRoster(project, rosterEntries)
            _state.update { it.copy(isSaving = false, createdProjectId = projectId) }
        }
    }

    companion object {
        fun factory(
            projectDao: ProjectDao,
            companyDao: CompanyDao,
            clerkDao: ClerkDao,
            rosterEntryDao: RosterEntryDao,
            writer: ProjectRosterWriter,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CreateProjectViewModel(projectDao, companyDao, clerkDao, rosterEntryDao, writer) }
        }
    }
}
