package com.vague.crewtally.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.vague.crewtally.R
import com.vague.crewtally.data.contacts.ContactSuggestion
import com.vague.crewtally.data.contacts.ContactsPermissionStore
import com.vague.crewtally.data.contacts.ContactsSearcher
import com.vague.crewtally.ui.theme.CrewTallyTheme
import kotlinx.coroutines.delay

/** Pause after the last keystroke before querying the device contacts. */
private const val SEARCH_DEBOUNCE_MS = 250L

/**
 * The shared name+phone pair used at both Phase 1 call sites — a clerk's name/phone and a
 * company's contact person/phone (Phase 1 LOCKED: "one component, two call sites").
 *
 * Typing in the name field live-searches the device address book (debounced, capped at
 * [com.vague.crewtally.data.contacts.MAX_CONTACT_SUGGESTIONS] suggestions) and offers
 * matches that fill both fields in one tap; picking a match drops focus and the keyboard,
 * matching JAPPED's `JappedContactSheet` behavior. The phone field stays a plain, always-
 * editable field underneath — typing there directly is just as valid as picking a contact.
 *
 * Permission handling lives entirely inside this component: the very first time the name
 * field gains focus, if `READ_CONTACTS` has never been asked for before, it requests the
 * permission once via [ContactsPermissionStore]. If denied (now or already), the field
 * quietly behaves as a plain text field forever — no error, no repeat prompts.
 *
 * IME chaining (Phase 6 review-debt paydown): the name field's keyboard shows "Next", which
 * jumps to the phone field via an internal [FocusRequester] — deliberately explicit rather
 * than a directional `moveFocus`, so pressing Next while suggestions are on screen lands on
 * the phone field instead of the first suggestion row. When [onImeNext] is supplied the phone
 * field also shows "Next" and invokes it (the caller moves focus on to the following field,
 * e.g. Notes). [nameFocusRequester] lets a preceding field (the company form's separate name
 * field) chain INTO this component's name field.
 */
@Composable
fun ContactSearchField(
    name: String,
    onNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    nameLabel: String,
    phoneLabel: String,
    modifier: Modifier = Modifier,
    namePlaceholder: String? = null,
    isNameError: Boolean = false,
    nameSupportingText: String? = null,
    nameFocusRequester: FocusRequester? = null,
    onImeNext: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val searcher = remember(context) { ContactsSearcher(context) }
    val permissionStore = remember(context) { ContactsPermissionStore(context) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val phoneFocusRequester = remember { FocusRequester() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var isNameFocused by remember { mutableStateOf(false) }
    // Set when a suggestion is tapped, so the resulting name change doesn't immediately
    // re-query and re-open the list under the user's finger.
    var justPicked by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<ContactSuggestion>()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionStore.hasRequestedOnce = true
    }

    fun pick(suggestion: ContactSuggestion) {
        keyboard?.hide()
        focusManager.clearFocus()
        justPicked = true
        onNameChange(suggestion.name)
        // Only fill a blank the contact actually has a value for, so picking a
        // phone-less contact doesn't wipe something already typed.
        if (suggestion.phone.isNotBlank()) onPhoneChange(suggestion.phone)
        suggestions = emptyList()
    }

    LaunchedEffect(name, isNameFocused, hasPermission) {
        if (justPicked) {
            justPicked = false
            suggestions = emptyList()
            return@LaunchedEffect
        }
        if (!isNameFocused || !hasPermission || name.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        // Debounce: cancelled and restarted on every keystroke via the `name` key, so the
        // query only fires once typing pauses.
        delay(SEARCH_DEBOUNCE_MS)
        suggestions = searcher.search(name)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceSm),
    ) {
        CrewTallyTextField(
            value = name,
            onValueChange = onNameChange,
            label = nameLabel,
            placeholder = namePlaceholder,
            leadingIcon = Icons.Filled.Person,
            isError = isNameError,
            supportingText = nameSupportingText,
            focusRequester = nameFocusRequester,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { phoneFocusRequester.requestFocus() }),
            onFocusChanged = { focusState ->
                isNameFocused = focusState.isFocused
                if (focusState.isFocused && !hasPermission && !permissionStore.hasRequestedOnce) {
                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            },
        )

        if (suggestions.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CrewTallyTheme.dimens.spaceXs),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                suggestions.forEach { suggestion ->
                    CrewTallyListRow(
                        title = suggestion.name,
                        subtitle = suggestion.phone.ifBlank { null },
                        leadingIcon = Icons.Filled.Person,
                        onClick = { pick(suggestion) },
                        contentDescription = if (suggestion.phone.isNotBlank()) {
                            stringResource(R.string.cd_contact_suggestion, suggestion.name, suggestion.phone)
                        } else {
                            suggestion.name
                        },
                    )
                }
            }
        }

        CrewTallyTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = phoneLabel,
            leadingIcon = Icons.Filled.Phone,
            focusRequester = phoneFocusRequester,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = if (onImeNext != null) ImeAction.Next else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onNext = { onImeNext?.invoke() }),
        )
    }
}
