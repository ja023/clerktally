# CrewTally

Offline-first Android app for crew supervisors: track companies, projects, clerk
attendance, extras, and payments. Built for a TOCS (inventory stock-count firm,
Beirut) supervisor but **fully generic — no TOCS name, branding, or business
assumptions anywhere in code, copy, or assets**. Will be published publicly on
Google Play.

- App name: **CrewTally** · package id: **com.vague.crewtally** (LOCKED — unchangeable after first Play upload)
- Repo: `/home/jad/crewtally` (LOCAL ONLY — do not push unless Jad asks)
- Stack: Kotlin + Jetpack Compose + Room + Material 3. Offline-only, single device, no accounts, no network permission.
- Build: agent builds APK on this Fedora box (`~/android-dev`, SDK at `~/Android/Sdk`), Jad tap-installs on phone.
- Store-readiness from day one: target latest SDK, adaptive icon, data-safety = "no data collected", privacy policy (offline), versioned signing.
- Accessibility is a requirement, not polish: TalkBack content descriptions, ≥48dp targets, dynamic type, WCAG 2.2 contrast, reduced-motion respect. a11y-architect agent audits every phase.

## LOCKED DECISIONS (2026-08-01 scoping with Jad — do not re-litigate; note reversals here)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Data locality | Offline-only, one device, Room DB. No cloud, no sync. |
| 2 | Currency | Cosmetic only (symbol, zero conversion math). Chosen **per project** at creation, defaulting to last used. Cross-project totals grouped per currency ("$450 + €200"). |
| 3 | Payment ledger | **Per-project settlement.** Each payment belongs to one project; project books close independently. Clerk profile still shows combined owed across projects (grouped by currency). |
| 4 | Language | English only at launch. All strings in resources from day one (Arabic/RTL possible later). |
| 5 | Branding | Fully generic, public-use. Name CrewTally, id com.vague.crewtally. |
| 6 | Attendance granularity | **Full day only** — present or absent. No half-days, no multipliers. Odd cases handled via extra-pay lines. |
| 7 | Advances & deductions | **Both supported.** Payment may exceed owed (balance shows as advance); extra-pay lines may be negative (deduction: damage, penalty, advance repayment). |
| 8 | Reports (v1) | **Clerk statement** (per clerk per project: days, rate, extras, payments, balance; shareable PDF/text) + **Company totals** (aggregate per company, broken down by project inside the report). NO standalone project-cost-summary screen in v1. |
| 9 | App lock | **None in v1.** Rely on device lock. (Optional biometric toggle is a v2 candidate.) |
| 10 | Backup | Manual **export/import single file** via share sheet + gentle nudge if no backup in 30 days. No accounts, no cloud code. |
| 11 | Editing paid history | **Always allowed, never locked** — but if an edit changes a balance already covered by payments, show a clear warning with the balance impact before saving. |
| 12 | Half-day / hourly | Explicitly out of scope (see #6). |
| 13 | Company receivables (what companies owe HIM) | Out of scope for v1. |

## Data model (LOCKED shape — field-level details settled during Phase 0)

- **Company**: name, contact, notes, archived.
- **Clerk**: name, phone, notes, active.
- **Project**: companyId, name, location, startDate, endDate?, status (active/completed/archived), currency.
- **RosterEntry** (project × clerk): dailyRate. When assigning a clerk, pre-fill rate from their most recent roster entry anywhere (editable).
- **AttendanceEntry** (project × clerk × date): present flag + **rateSnapshot** copied from roster at save time (mid-project rate edits never rewrite history). Day view can add walk-ins / remove people for that day only without touching the roster.
- **ExtraPayLine** (attached to AttendanceEntry): label (presets Lunch / Transport / Bonus + custom), amount (may be negative = deduction).
- **Payment**: projectId, clerkId, date, amount, note. "Paid in full" = payment equal to outstanding balance (UI shortcut, not a flag).
- **Balance is always derived, never stored**: Σ(present × rateSnapshot) + Σ(extras) − Σ(payments), per project per clerk.

## Design system (LOCKED 2026-08-01 — Jad's explicit constraints)

- **Centralized design choices**: ALL colors, typography, spacing, shapes, and component styles live in the theme layer (`ui/theme/` + shared components in `ui/components/`). One source of truth per UI element. NO ad-hoc inline styling, NO hardcoded dp/sp/color values in screens.
- **Primary user is ~55 years old.** This overrides aesthetic preferences:
  - NO small fonts: body text ≥16sp, list items ≥18sp, key numbers (rates, balances) large and bold. Full dynamic-type support on top of that.
  - Touch targets ≥48dp everywhere; primary daily actions (attendance toggles, pay button) ≥56dp.
  - Shallow, obvious navigation: visible labeled tabs/buttons, no hidden gestures, no long-press-only actions, no hamburger-buried features. Every screen answers "where am I, what can I do" at a glance.
  - High contrast, generous spacing, one primary action per screen.
- Jad doesn't care much about aesthetics beyond this — don't ask MCQs about visual styling; DO still ask about behavior/scope.
- Top-level nav (amendable after first install): bottom bar with **Home · Projects · Clerks · More** (More = Companies, Backup, Settings).

## Core UX commitments

- Daily attendance loop must take <30s: roster list, one-tap present/absent, "mark all present", inline extras.
- Dashboard: active projects, total outstanding (per currency).
- Outstanding-balances screen sorted by amount — the "who do I need to pay" view.
- Clerk profile: history across projects, totals earned/paid/owed.
- Same clerk on two projects same day: allowed silently.

## Build phases (each ends with reviews + installable APK for Jad)

| Phase | Content | Agents |
|-------|---------|--------|
| 0 | Scaffold: Gradle project, theme, nav, Room schema + DAOs + migrations policy | Opus architect |
| 1 | Companies + Clerks CRUD | Sonnet (parallel) |
| 2 | Projects + roster + rate suggestion | Sonnet |
| 3 | Attendance daily loop + extras | Opus |
| 4 | Payments + derived balances + paid-edit warnings | Opus |
| 5 | Reports (clerk statement, company totals) + backup export/import + nudge | Sonnet |
| 6 | Accessibility audit, polish, icon, Play packaging | a11y-architect + kotlin-reviewer + code-reviewer |

Per-phase gate: kotlin-reviewer + code-reviewer pass; kotlin-build-resolver on build breaks.
Fable orchestrates; sub-decisions inside each phase still go to Jad as MCQs before building (per standing clarify-first rule).

## Phase 1 LOCKED decisions (2026-08-01)

- **Clerk fields**: name, phone, notes. Name/phone fill via **live contact search like JAPPED's `JappedContactSheet`** (`/home/jad/JAD/app/src/main/java/com/japped/designsystem/component/JappedContactSheet.kt` — copy its behavior: typing the name queries device contacts debounced, ≤5 suggestions, one tap fills name+phone; picking drops focus/keyboard). Needs `READ_CONTACTS` (runtime-request on first use of the search; if denied the form works as a plain form, suggestions silently absent, no nagging). Play data-safety impact handled in Phase 6.
- **Company fields**: name, contact person, phone, notes. Contact person/phone reuse the SAME contact-search component (one component, two call sites).
- **Archive/delete UX**: primary action is Archive (hidden from lists behind a "Show archived" toggle, reversible). Hard Delete is offered ONLY when the record has zero history (no roster/attendance/payment rows) — e.g. typo records. Never a cascade delete.
- **Forms**: full-screen pages (not bottom sheets) — better for the senior user. Label above field, big inputs, one Save button. Name required; everything else optional.
- Room v1 is **amendable until the first APK is installed on the phone** (no real data exists). Company entity gains contactPerson/phone as part of Phase 1; regenerate schemas/1.json, do NOT add v2 yet.
- Checkpoint flow: commit per phase; first phone install happens at END of Phase 1.

## Phase 2 LOCKED decisions (2026-08-01)

- **Project form**: name + company + currency required; location + notes optional; startDate auto = today (editable). NO end-date field — a project ends via an explicit "Mark completed" action (sets endDate = that day, status = completed). Currency defaults to last used (locked decision #2).
- **Projects tab**: segmented Active / Completed (one big segmented control, senior-friendly). Project rows show name, company, and roster size. Archived projects out of scope until needed.
- **Roster at creation**: step 1 multi-select clerks (searchable list); step 2 a rate list — every chosen clerk with a big numeric rate field pre-filled from their most recent RosterEntry rate anywhere (editable). Blank rates block save.
- **Roster after creation — fully editable anytime**: add clerk (asks rate, pre-filled from last job), edit a clerk's rate (affects FUTURE attendance days only; past days keep rateSnapshot), remove clerk (history intact, they stop appearing on new days; roster row soft-removed, not deleted, since it may carry the rate history).
- ProjectEntity gains a notes field if missing — still schema v1 (amendable until first install).

## Phase 3 LOCKED decisions (2026-08-01) — Attendance daily loop

- Entry: project detail gains a prominent "Take attendance" action → day screen, default TODAY, prev/next day arrows + date picker (backfill any date, including before startDate — warn, don't block). AMENDED 2026-08-02 (Phase 3 review, autonomous calls in the spirit of locked rules): (a) dates AFTER today get the same warn-don't-block treatment as backfill; (b) the day screen works on COMPLETED projects too (locked #11: history never locks) with a soft "project is completed" notice; (c) deleting the last extra on a carrier-only row (present=false, never explicitly marked) removes the carrier row so it can't masquerade as a real Absent.
- **New day starts UNMARKED** (no AttendanceEntry row = unmarked). One big "Mark all present" button, then tap exceptions. Row tap cycles/toggles Present ↔ Absent, ≥56dp targets, state visually unmistakable (not color-only). A day with unmarked clerks shows a soft reminder line, never blocks.
- **Instant auto-save per tap** — no Save button. Present=true / Absent=false rows written immediately with rateSnapshot copied from the roster at write time. Unmarking (back to no-answer) deletes the row (only if it has no extras; with extras, warn first).
- **Extras attach to the day regardless of present/absent** (fronted gas on a day off, deductions). Extras UI per clerk row: opens a FULL-SCREEN editor (AMENDED 2026-08-02 from "sheet" — the older locked "forms are full-screen pages" rule wins; recorded during Phase 3 review) listing that day's lines: preset labels Lunch / Transport / Bonus + free-text label, amount (negative allowed = deduction, wording "Deduction" with minus shown). An absent/unmarked clerk with extras gets an AttendanceEntry row (present=false) to carry them.
- **Walk-ins: day-only + optional roster join.** "Add clerk for this day" → picker of active clerks not on today's list → rate (pre-filled from last rate anywhere) → checkbox "Also add to project roster" (default OFF). Day-only walk-in gets an AttendanceEntry with rateSnapshot but NO roster row. Remove-for-day removes that day's row (warn if it has extras).
- Attendance history: project detail also gets a compact per-day summary list (date, N present, extras total) → tapping opens that day.

## Phase 4 LOCKED decisions (2026-08-01) — Payments + balances + Home

- **Clerk balance screen** (per project): project detail → tap clerk → balance: days worked × rate + extras − payments = owed, big number, full ledger beneath (attendance-derived earnings by day, extras, payments). "Record payment" pre-fills FULL owed (= paid in full); editable down for partial. Payment fields: amount, date (default today), optional note.
- **Advance = overpay with confirm dialog**: "This is X more than owed. Record the extra as an advance?" Balance may go negative, displayed as "Advance" not a red error.
- **Payments editable AND deletable** afterwards, always behind a confirm showing the balance impact (consistent with locked never-lock-always-warn rule).
- **Home tab = projects-first dashboard** (Jad's explicit pick over money-first): active projects on top, each with a "Take attendance" shortcut; outstanding totals (per currency) below; then owed-clerks list sorted by amount (tap → balance screen).
- **Clerk profile** (Clerks tab → clerk): cross-project totals earned/paid/owed grouped per currency + per-project rows linking to balance screens.
- Cross-currency amounts are NEVER summed — one total per currency, always.

## Phase 5 LOCKED decisions (2026-08-01) — Reports + backup + settings

- **Reports in TEXT + PDF**, both offered via the system share sheet. Clerk statement (per clerk per project): days worked, rate, extras lines, payments, balance. Company totals (per company): per-project breakdown + grand totals per currency. Text is WhatsApp-clean; PDF simple and printable (no branding beyond app name).
- **Backup**: single-file JSON export of the whole DB via share sheet + "Restore from file" (SAF picker) with a confirm that restore REPLACES current data. Gentle nudge (non-blocking banner on More) if no export in 30 days. Schema-versioned payload; import validates before touching the DB.
- **Settings** (More): backup/restore entries, the 30-day nudge toggle, About (version, privacy policy text viewable offline). Nothing else in v1.

## Phase 6 LOCKED decisions (2026-08-01) — Polish + packaging

- **App icon: TALLY MARKS concept** (four strokes + diagonal fifth), adaptive icon, monochrome layer for themed icons.
- Final a11y audit sweep across all phases; fix everything CRITICAL/HIGH.
- Release signing config + keystore generated locally (NEVER committed; stored outside repo, path + instructions in a local note for Jad).
- Play-readiness artifacts drafted for Jad's approval (nothing uploaded by the agent): store listing copy, data-safety answers (offline, READ_CONTACTS optional-use disclosure), privacy policy text.
- Version 1.0.0, versionCode 1. Room schema FROZEN at v1 the moment the final APK installs on Jad's phone — every later change is a migration.

## Autonomous run contract (Jad, 2026-08-01)

Phases 3→6 run WITHOUT check-in questions: build → 3-reviewer pass (kotlin, general, a11y) → fix batch → green gate → commit, phase by phase, sequentially in the main repo. **ONE final APK after Phase 6** pushed to the phone (plus the already-requested Phase 0-2 APK install happening now). Stop and ask ONLY if something contradicts a LOCKED decision. Device feedback from Jad folds in whenever it arrives.

## Review debt (accepted findings, deliberately deferred — address in the phase noted)

- **Wizard step transitions don't move AT focus** to the new step's first field (CreateProject, AddRosterClerk, AttendanceWalkIn — liveRegion announcement exists, focus move doesn't). Phase 6 polish, fix all three together with one FocusRequester pattern.

- **Extract shared `RecordLifecycleActions` composable** (archive/unarchive/delete buttons + 3 confirm dialogs, ~70 lines duplicated between ClerkFormScreen and CompanyFormScreen) — do this in Phase 3 BEFORE a third form copies the pattern.
- **Contact-search tests**: ContactsSearcher LIKE-escaping, union+dedupe, ContactsPermissionStore ask-once — needs Robolectric; add when a Robolectric setup exists (Phase 5/6).
- **IME action chaining** Name→Phone→Notes→Save in forms (Phase 6 polish).
- **Nav route builders** instead of literal `"clerks/$id/edit"` templates (whenever nav is next touched).
- **Permission re-check on resume** in ContactSearchField (granted via system Settings while form open) — only if it shows up in real use.
- **Disabled More-rows** need a visual (not just semantic) disabled treatment (Phase 5 when Backup/Settings go live anyway).
- ⚠️ **Type.kt `labelMedium`/`labelSmall` are 14sp** — currently unused for body content; never reach for them for anything a user must read (locked ≥16sp rule).

## Deferred / v2 candidates

- Optional biometric/PIN app lock · project cost summary screen · Arabic + RTL ·
  auto rolling local backup · company receivables/invoicing · Drive-integrated backup.
