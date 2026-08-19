# Technical Decisions — Running Checklist

**Companion to:** Voice Entry Shop Calculator — General Requirements v0.2
**Date:** 2026-08-18
**Purpose:** Track every technical decision, its status, and what it blocks. Revisit before and during the technical specification.

Status key: **[DECIDED]** · **[PENDING]** — needs an answer before the tech spec · **[DEFERRED]** — can be settled during implementation

---

## A. Decided

- [x] **Language** — Kotlin
- [x] **Architecture** — MVVM
- [x] **Navigation** — single activity, replaceable fragments
- [x] **DI framework** — Hilt (Dagger with Android boilerplate pregenerated), using KSP not kapt
- [x] **Persistence** — Room
- [x] **minSdk** — 26
- [x] **Popup strategy** — see section B below (agreed, needs writing up)

---

## B. Popup and overlay strategy — [DECIDED, needs spec text]

- [x] Recording is an **in-layout overlay anchored to the PTT button**, not a `DialogFragment`. Must host the live partial transcript (FR-2.3) and slide-to-cancel (FR-2.2).
- [x] Processing reuses the **same overlay** with swapped content. No separate modal — a spinner that flashes for 300 ms reads as a glitch against the 1.5 s latency target.
- [x] Errors are **tiered**: Snackbar for recoverable/common cases (silence, nothing parseable), modal only when a decision is required.
- [x] Disambiguation is the **only true dialog**. Must be a `DialogFragment` (survives rotation) and driven by a **queue** in the ViewModel, since FR-6.3 allows several per utterance.
- [ ] Write the full overlay state machine: `idle → listening → processing → (result | error)`, including cancel and rotation paths.

---

## C. Blocking the technical specification — [PENDING]

- [ ] **C1. XML Views vs Jetpack Compose.**
  Currently leaning XML. If confirmed, requirements v0.2 §4 and NFR-10 must be amended — `WindowSizeClass` is replaced by resource qualifiers (`layout-land`, `layout-sw600dp`) plus `SlidingPaneLayout` for the two-pane case.
  If XML: ViewBinding throughout; RecyclerView + `ListAdapter` + DiffUtil for bill and catalog lists.
  *Blocks: all UI sections of the tech spec.*

- [ ] **C2. SOLID / clean code — concrete rules.** (your review item #1)
  "Where possible" is untestable. Needs to become specific decisions:
  - Which seams get interfaces. Proposed: `SpeechSource` (Strategy across the four tiers — the important one), `ProductRepository`, `Parser`, `Matcher`, `Clock`.
  - Which do not. Proposed: no interface per ViewModel, no `IUseCase` wrapper around passthroughs.
  - Package-by-feature vs package-by-layer. Proposed: by feature, single Gradle module.
  - **Hard rule to confirm:** `Parser` and `Matcher` are pure Kotlin, zero Android imports, so threshold tuning against the AC-1 corpus runs as fast JVM unit tests. Enforce via detekt rule or module boundary.
  *Blocks: package structure, testing strategy.*

- [ ] **C3. State model.** (your review item #2)
  - `StateFlow` + `UiState` data class vs `LiveData`.
  - One-shot events (navigation, Snackbar, dialog trigger): `Channel`/`SharedFlow` vs `SingleLiveEvent`.
  - Whether each screen gets one `UiState` or several smaller flows.
  *Trade-offs to review: lifecycle safety, testability without Android, boilerplate, and how each handles the disambiguation queue.*
  *Blocks: every ViewModel signature.*

---

## D. Deferred — settle during implementation

### Concurrency
- [ ] **D1. Threading.** Recognizer callbacks arrive on main. Matching against up to 1,000 products (NFR-4) must not run there — `Dispatchers.Default`. Define which layer switches dispatchers.
- [ ] **D2. Coroutine scoping.** Cancellation on slide-to-cancel and on fragment destruction; ensure a cancelled utterance cannot deliver a late result.
- [ ] **D3. Injectable dispatchers.** Pass a `CoroutineDispatcher` provider through DI so tests can substitute a test dispatcher.

### Data layer
- [ ] **D4. Catalog index ownership.** Who holds the in-memory index (FR-3.4), at what scope, and what triggers a rebuild when Room emits a change. Rebuild cost at 1,000 products / 5,000 aliases.
- [ ] **D5. Room migrations.** Schema export on, `fallbackToDestructiveMigration` **off** — destructive migration would wipe a user's entire catalog. Migration tests from v1 onward.
- [ ] **D6. Unit seeding.** The system unit list ships as prepopulated data; decide `createFromAsset` vs a callback on first open.
- [ ] **D7. Scaled-integer quantity.** Confirm the scale factor (thousandths proposed) and centralize the money/quantity arithmetic in one tested utility, not scattered at call sites.

### Error handling
- [ ] **D8. Result types.** Sealed classes for parse and match outcomes rather than exceptions. Map the §8 edge-case matrix onto concrete sealed variants so the compiler enforces exhaustive handling.
- [ ] **D9. Recognizer tier fallback.** Where tier detection and demotion live, and how a tier change surfaces to the UI indicator (REQ-ASR-1, REQ-ASR-5).

### Cross-cutting
- [ ] **D10. Logging.** No transcripts, product names, or prices in logs — including debug builds (NFR-6). Decide the logging facade and strip rules.
- [ ] **D11. Permission flow.** Where `RECORD_AUDIO` rationale and denial state live; manual-mode fallback path (FR-10.2).
- [ ] **D12. Build config.** Version catalog (`libs.versions.toml`), R8 for release, two signing configs (sideload prototype vs Play release), and the Data Safety / privacy policy work required by NFR-12.
- [ ] **D13. Testing strategy.** Unit tests for parser/matcher against the AC-1 corpus; Robolectric or instrumentation boundary; how the recognizer is faked in tests.
- [ ] **D14. Static analysis.** detekt and/or ktlint, and whether they gate the build.

---

## E. Carried over from requirements v0.2

Not technical, but still open and still blocking the speech processing spec:

- [ ] The actual unit list and its quantity types (§12.1) — needs review by someone who runs a warung
- [ ] Custom units allowed? (§12.2) — recommend no for v1
- [ ] Fixed word order for quantity vs product name (§12.3)
- [ ] Confidence thresholds (§12.4) — cannot be set before baseline measurement
- [ ] Automatic vs prompted alias learning (§12.5)
- [ ] Seed catalog or start empty (§12.6)
- [ ] Quantity precision (§12.7)

---

## Change log

| Version | Date | Notes |
|---|---|---|
| 0.1 | 2026-08-18 | Initial checklist. Hilt and popup strategy decided; XML-vs-Compose, SOLID rules, and state model flagged as blocking. |
