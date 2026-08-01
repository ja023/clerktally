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

## Deferred / v2 candidates

- Optional biometric/PIN app lock · project cost summary screen · Arabic + RTL ·
  auto rolling local backup · company receivables/invoicing · Drive-integrated backup.
