# CrewTally — Play Data Safety form answers (DRAFT for approval)

The Play Console Data safety form is a questionnaire. Below are the exact answers to give,
each with the justification, so Jad can transcribe them with confidence. Nothing is submitted
by the tooling.

## Summary

CrewTally collects no data, shares no data, and requires no account. Everything the user
enters stays in the app's private storage on the device. The only sensitive-looking capability
is an optional contacts read used purely for on-device autocomplete.

## Section 1: Data collection and sharing

**Does your app collect or share any of the required user data types?**

> No.

Justification: "Collect" in Play's definition means transmitting data off the device. CrewTally
has no network permission at all (no INTERNET permission is declared in the manifest), so it is
technically incapable of transmitting anything. All companies, clerks, projects, attendance,
extras, and payments are written only to the app's local Room database on the device.

Because the answer to the collection question is No, the form does not ask the per-type
follow-up questions. If the Console still surfaces them, every data type is marked **not
collected** and **not shared**.

## Section 2: Data shared with other companies or organizations

> No data shared.

Justification: No third-party SDKs, no analytics, no advertising, no crash reporting that
leaves the device. Nothing is sent anywhere.

## Section 3: Data processed ephemerally

**Is any user data processed ephemerally?**

> No.

Justification and nuance for Jad: Play uses "processed ephemerally" to describe personal data
that is sent to a server, used in memory, and not retained. CrewTally sends nothing to any
server, so the ephemeral-processing category (which is about server-side handling) does not
apply. Answer No.

The contacts autocomplete described below IS momentary and on-device, but it is not "collection"
under Play's definition (nothing is transmitted or stored by the app), so it does not turn the
collection or ephemeral answers into Yes.

## Section 4: Contacts permission (READ_CONTACTS)

Play may ask about the declared `READ_CONTACTS` permission separately from the data-safety data
types. Explanation to give if prompted (and to keep on hand for review):

- The permission is **optional**. It is requested at runtime only the first time the user taps
  into the contact-search field on the clerk or company form, and only once. If the user
  denies it, the field works as a plain text field forever, with no repeat prompts.
- When granted, CrewTally reads a contact's **name and phone number** only at the moment the
  user is typing, only to show matching suggestions in that field.
- Nothing from the contacts is stored beyond the single name and number the user chooses to
  save into their own clerk or company record, and nothing from the contacts is ever
  transmitted (the app has no network access).

## Section 5: Data deletion

- Account deletion: not applicable. CrewTally has no accounts.
- Data deletion: the user controls all data on-device. Uninstalling the app removes the local
  database. There is no server-side copy because there is no server.

## Section 6: Security practices (informational)

- Data is not encrypted in transit because no data is ever in transit.
- Data at rest lives in the app's private, OS-sandboxed storage.
- `android:allowBackup` is set to false, so the OS auto-backup does not copy the database off
  the device; the only backups are the ones the user explicitly exports and shares themselves.

## Backups (context for the reviewer)

The user can export a single backup file on request from More, then Backup. That file is handed
to the Android share sheet so the user sends it wherever they choose. CrewTally never creates or
uploads a backup on its own and has no cloud destination of its own.
