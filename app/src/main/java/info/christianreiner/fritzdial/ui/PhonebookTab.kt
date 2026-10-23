package info.christianreiner.fritzdial.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import info.christianreiner.fritzdial.R
import info.christianreiner.fritzdial.data.DeviceContacts
import info.christianreiner.fritzdial.data.SpeedDial
import info.christianreiner.fritzdial.data.SpeedDials
import info.christianreiner.fritzdial.tr064.DialingCodes
import info.christianreiner.fritzdial.tr064.Phonebook
import info.christianreiner.fritzdial.tr064.PhonebookContact
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * The number picker's "Phone books" tab: searchable list of all contacts from the box' phone
 * books (if used for the box in use) and optionally the phone's own. Tapping a number puts it into
 * the dialer – or, while a speed dial is being set up, makes it that speed dial. A long press on a
 * number makes it one of this phone's speed dials.
 */
@Composable
fun PhonebookTab(
    routerBooks: List<Phonebook>,
    /** The phone books of the box in use are switched on in the settings. */
    routerBooksEnabled: Boolean,
    deviceContacts: Phonebook?,
    /** The optional "this phone's contacts" feature is switched on in the settings. */
    deviceContactsEnabled: Boolean,
    /** Country and area code of the box in use, for recognising a number written differently. */
    dialingCodes: DialingCodes?,
    loaded: Boolean,
    busy: Boolean,
    /** A number was tapped: contact name and number. */
    onPick: (String, String) -> Unit,
    /** This phone's speed dials; null when switched off (then a long press does nothing). */
    phoneSpeedDials: List<SpeedDial>?,
    /** Long press on a number, slot chosen: make it this phone's speed dial (slot, name, number). */
    onSetSpeedDial: (String, String, String) -> Unit,
    /** Setting up this phone's speed dial with this slot: the next tapped number becomes it. */
    assigningSpeedDial: String?,
    onCancelAssigning: () -> Unit,
    /** Called when the feature is on and access is (or became) granted: (re)load the contacts. */
    onDeviceContactsAllowed: () -> Unit,
    /** Switches the feature off (used when the permission was revoked in Android). */
    onDisableDeviceContacts: () -> Unit,
    /** Ask now whether to include the phone's contacts (feature off, not declined for good). */
    askForDeviceContacts: Boolean,
    onDeviceContactsAnswer: (DeviceContactsAnswer) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Names of the phone books that are filtered *out*; empty = show all.
    var hidden by rememberSaveable { mutableStateOf(listOf<String>()) }
    // Name and number long-pressed to become a speed dial: choosing its slot.
    var speedDialFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    speedDialFor?.let { (name, number) ->
        SpeedDialSlotDialog(
            name = name,
            taken = phoneSpeedDials.orEmpty(),
            onChoose = { slot ->
                speedDialFor = null
                onSetSpeedDial(slot, name, number)
            },
            onDismiss = { speedDialFor = null },
        )
    }

    // --- the phone's own contacts: optional, switched on in the settings ---
    val context = LocalContext.current
    var contactsAllowed by remember { mutableStateOf(DeviceContacts.hasPermission(context)) }
    // Re-check when coming back, e.g. from the system app settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        contactsAllowed = DeviceContacts.hasPermission(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsAllowed = granted
        onDeviceContactsAnswer(if (granted) DeviceContactsAnswer.ALLOWED else DeviceContactsAnswer.NOT_NOW)
    }
    if (askForDeviceContacts) {
        DeviceContactsQuestion(
            onAllow = {
                if (DeviceContacts.hasPermission(context)) {
                    contactsAllowed = true
                    onDeviceContactsAnswer(DeviceContactsAnswer.ALLOWED)
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            },
            onNotNow = { onDeviceContactsAnswer(DeviceContactsAnswer.NOT_NOW) },
            onNever = { onDeviceContactsAnswer(DeviceContactsAnswer.NEVER) },
        )
    }
    val useDeviceContacts = deviceContactsEnabled && contactsAllowed
    LaunchedEffect(useDeviceContacts) {
        if (useDeviceContacts) onDeviceContactsAllowed()
    }

    val phonebooks = routerBooks + listOfNotNull(deviceContacts?.takeIf { useDeviceContacts })

    val countryIso = remember { deviceCountryIso(context) }
    val filtered = remember(phonebooks, query, hidden, dialingCodes) {
        val key = { number: String -> numberKey(number, countryIso, dialingCodes) }
        // FRITZ!Box books come first, so their entries win over duplicates from "This phone".
        val visible = phonebooks.filter { it.name !in hidden }.flatMap { it.contacts }
        dedupeNumbers(visible, key)
            .filter { it.matches(query, key) }
            // Locale-aware: "Ärzte" sorts with A, not after Z.
            .sortedWith(compareBy(Collator.getInstance()) { it.name })
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val letters = remember(filtered) { filtered.map { indexLetter(it.name) } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.phonebook_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.phonebook_clear_search))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        if (assigningSpeedDial != null) {
            AssigningCard(slot = assigningSpeedDial, onCancel = onCancelAssigning)
        }

        // Feature is on, but the permission was revoked in Android meanwhile.
        if (deviceContactsEnabled && !contactsAllowed) {
            ContactsRevokedCard(
                onOpenSettings = { context.startActivity(DeviceContacts.appSettingsIntent(context)) },
                onTurnOff = onDisableDeviceContacts,
            )
        }

        // One chip per phone book, only when there is more than one.
        if (phonebooks.size > 1) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(phonebooks, key = { it.id }) { book ->
                    val selected = book.name !in hidden
                    FilterChip(
                        selected = selected,
                        onClick = {
                            hidden = if (selected) hidden + book.name else hidden - book.name
                        },
                        label = { Text(stringResource(R.string.phonebook_chip, bookLabel(book.name), book.contacts.size)) },
                    )
                }
            }
        }

        when {
            !routerBooksEnabled && !deviceContactsEnabled -> Message(stringResource(R.string.phonebook_none_enabled))
            phonebooks.isEmpty() && busy -> Message(stringResource(R.string.phonebook_loading))
            phonebooks.isEmpty() && routerBooksEnabled && !loaded ->
                Message(stringResource(R.string.phonebook_load_failed))
            phonebooks.all { it.contacts.isEmpty() } -> Message(stringResource(R.string.phonebook_empty))
            filtered.isEmpty() -> Message(stringResource(R.string.phonebook_no_match))
            else -> Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    items(filtered) { contact ->
                        ContactRow(
                            contact,
                            showBook = phonebooks.size > 1,
                            onPick = { number -> onPick(contact.name, number) },
                            onLongPress = phoneSpeedDials?.let { { number -> speedDialFor = contact.name to number } },
                        )
                        HorizontalDivider()
                    }
                }
                AlphabetBar(
                    available = letters.toSet(),
                    onLetter = { letter ->
                        val index = jumpIndex(letters, letter)
                        if (index >= 0) scope.launch { listState.scrollToItem(index) }
                    },
                )
            }
        }
    }
}

/** Asks whether to include the phone's own contacts, with a way to stop asking for good. */
@Composable
private fun DeviceContactsQuestion(onAllow: () -> Unit, onNotNow: () -> Unit, onNever: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        icon = { Icon(ContactsIcon, contentDescription = null) },
        title = { Text(stringResource(R.string.device_contacts_question_title)) },
        text = {
            Text(stringResource(R.string.device_contacts_question_text))
        },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onNever) { Text(stringResource(R.string.dont_ask_again)) }
                TextButton(onClick = onNotNow) { Text(stringResource(R.string.not_now)) }
            }
        },
    )
}

/** Shown when "this phone's contacts" is switched on but Android no longer allows access. */
@Composable
private fun ContactsRevokedCard(
    onOpenSettings: () -> Unit,
    onTurnOff: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.device_contacts_revoked),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onTurnOff) { Text(stringResource(R.string.turn_off)) }
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.app_settings)) }
            }
        }
    }
}

@Composable
internal fun Message(text: String) {
    Text(
        text,
        modifier = Modifier.padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Says that the next tapped number becomes speed dial [slot], with a way to cancel. */
@Composable
private fun AssigningCard(slot: String, onCancel: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.speed_dial_choose_number, slot),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** Which of this phone's slots a long-pressed number becomes; taken ones show who is there now. */
@Composable
private fun SpeedDialSlotDialog(
    name: String,
    taken: List<SpeedDial>,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speed_dial_for, name)) },
        text = {
            LazyColumn {
                items(SpeedDials.PHONE_SLOTS) { slot ->
                    val current = taken.firstOrNull { it.slot == slot }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(slot) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            slot,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(32.dp),
                        )
                        Text(
                            current?.name ?: stringResource(R.string.speed_dial_free),
                            color = if (current == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Name, then one tappable line per number; a long press makes the number a speed dial. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: PhonebookContact,
    showBook: Boolean,
    onPick: (String) -> Unit,
    /** Null when this phone's speed dials are switched off. */
    onLongPress: ((String) -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                contact.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (showBook) {
                Text(
                    bookLabel(contact.book),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        contact.numbers.forEach { n ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onPick(n.number) },
                        onLongClickLabel = onLongPress?.let { stringResource(R.string.speed_dial_make) },
                        onLongClick = onLongPress?.let {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                it(n.number)
                            }
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(n.number, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    numberTypeLabel(n.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * List position for [letter]: the first entry with that letter, else the first entry of the
 * next letter that has entries, else the last entry.
 */
private fun jumpIndex(letters: List<Char>, letter: Char): Int {
    if (letters.isEmpty()) return -1
    val wanted = INDEX_LETTERS.indexOf(letter)
    val i = letters.indexOfFirst { INDEX_LETTERS.indexOf(it) >= wanted }
    return if (i >= 0) i else letters.lastIndex
}

/** Display name of a phone book: the phone's own contacts in the app's language, the box' as they are. */
@Composable
private fun bookLabel(name: String): String =
    if (name == DeviceContacts.BOOK_NAME) stringResource(R.string.this_phone) else name

/**
 * Label of a number's type. The box tags them "home", "mobile", …; the phone's own contacts
 * already bring Android's label in the system language, which is shown as it is.
 */
@Composable
private fun numberTypeLabel(type: String): String = when (type.lowercase()) {
    "home" -> stringResource(R.string.number_type_home)
    "mobile" -> stringResource(R.string.number_type_mobile)
    "work" -> stringResource(R.string.number_type_work)
    "fax_work", "fax" -> stringResource(R.string.number_type_fax)
    "intern" -> stringResource(R.string.number_type_internal)
    "memo" -> stringResource(R.string.number_type_memo)
    "other", "" -> stringResource(R.string.number_type_other)
    else -> type.replaceFirstChar { it.uppercase() }
}

/** Case-insensitive name match; number match ignores spaces, dashes and the like. */
private fun PhonebookContact.matches(query: String, key: (String) -> String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (name.contains(q, ignoreCase = true)) return true
    return numbers.any { numberMatches(it.number, q, key) }
}
