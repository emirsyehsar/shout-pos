# CLAUDE.md

Project context for Claude Code. **Read this before reading the specifications.**

---

## What this project is

An Android app for Indonesian shopkeepers (warung / kios). The shopkeeper holds a button, speaks product names and quantities, and the app shows an itemized total. It is a **calculator with a voice front-end** — not a POS, not inventory, not accounting.

Language of the spoken input: **Indonesian (`id-ID`)**.

---

## ⚠️ Current phase: PROTOTYPE, not v1

This is the single most important thing to understand.

The specification documents describe a **complete v1 application**. We are **not building that yet**. We are building a throwaway prototype whose only purpose is to answer one question:

> Does Indonesian platform ASR plus fuzzy matching actually work well enough in a noisy shop?

Nobody has tested this. Until it is tested, the v1 spec is an unvalidated plan. Treat the specs as a **destination**, not a work order.

**Do not build features from the specification unless this file explicitly places them in the prototype scope.** If a spec requirement seems missing from the prototype, that is intentional.

---

## Prototype scope

### Build

- Single screen, single button (push-to-talk)
- Android platform `SpeechRecognizer`, language `id-ID`
- Product data from a **hardcoded JSON asset** — roughly 20 products
- **One item per utterance.** No multi-item parsing
- Quantities limited to 1–10, plus `setengah` (half) and `se-` prefix forms (`sekilo`, `sebungkus`)
- The UI displays **every pipeline stage**, in order:
  1. Raw transcript
  2. Cleaned text — with removed tokens shown alongside what remained
  3. Tokenization — extracted product phrase and quantity
  4. Match result from the JSON catalog

The staged display is the entire point. This is a **diagnostic instrument**, not a demo. Do not collapse the stages into a single result view.

### Do NOT build

Not because they are bad ideas — all are in the v1 spec — but because they add cost before the core question is answered:

- Room / any database
- Hilt / dependency injection
- Fragments, navigation, multiple screens
- Product CRUD or the price lookup screen
- Aliases or alias learning
- Multi-item utterances, separator words, reserved-word validation
- Disambiguation dialog and queue
- Discrete vs decimal quantity types
- The four-tier recognizer fallback (prototype: try on-device, fall back to online, that's it)
- Import / export
- Editable bill / line items
- Landscape and tablet layouts
- Full Indonesian numeral grammar (`belas`/`puluh`/`ratus`/`ribu`)

Keep the architecture as flat as it can be. One Activity is acceptable. Ceremony here is waste — this code is expected to be discarded.

### Proposed instrumentation — CONFIRM WITH THE USER BEFORE BUILDING

These were suggested but not yet confirmed. They exist so the prototype yields numbers rather than impressions:

- Display all 5 N-best hypotheses, not just the top one — "the right words were in position 3" and "never heard at all" are different problems
- At the match stage, show the top 3 candidates **with scores**, not just the winner — this is the only way a confidence threshold can be derived
- Label which recognizer tier ran (on-device vs online) — their accuracy differs and must not be averaged together
- A correct / incorrect button per attempt, appending to an exportable log file

---

## Technical constraints

| Item | Value |
|---|---|
| Language | Kotlin |
| IDE | Android Studio |
| `minSdk` | 26 |
| UI toolkit | **XML Views** (see open decision C1) |
| Currency | Indonesian Rupiah, integer only — no sub-unit |
| ASR | Android platform `SpeechRecognizer`, `id-ID` |

**Hard constraints for the whole project, prototype included:**

- **No LLM.** On-device or remote. This is non-negotiable and is the reason the parser is rule-based.
- **No backend.** No server we own, no API keys, no accounts.
- **Offline-first.** Online recognition is a fallback, never a requirement.
- **No telemetry.** No transcripts, product names, or prices in logs — including debug builds.

---

## Indonesian language notes

Traps that will produce silently wrong results if missed:

- **`ons` means 100 grams in Indonesia**, not the English ounce. Common at market.
- **`se-` prefix fuses the number one into the unit**: `sekilo` = 1 kg, `sebungkus` = 1 packet, `sebotol` = 1 bottle. These must be expanded before parsing, or the quantity reads as absent.
- **`setengah`** = 0.5, **`seperempat`** = 0.25. Both common.
- The recognizer may return **digits** ("2") rather than words ("dua"). Handle both.
- Product names are frequently brand names (*Indomie*, *Aqua*, *Rinso*, *Teh Botol*) and may be code-switched.

---

## Documents

| File | Contents |
|---|---|
| `voice-shop-requirements-v0.2.md` | Full v1 functional and non-functional requirements. **A destination, not a work order.** |
| `technical-decisions-checklist.md` | Every technical decision: decided, pending, or deferred. Check here before assuming a choice has been made. |

---

## Decisions with non-obvious reasoning

Do not "fix" these — each was argued through:

- **Quantity type lives on `Unit`, not `Product`.** Otherwise two products priced per kg could disagree about whether fractions are allowed.
- **Quantity is a scaled integer, never a float** (thousandths: 1.5 kg = `1500`). Float quantities multiplied by Rupiah prices produce visible off-by-one totals, which destroys trust in a calculator.
- **Recording is an in-layout overlay, not a `DialogFragment`.** The user's finger is on the button; a modal under their thumb that must also host a live transcript and slide-to-cancel fights the framework.
- **Only disambiguation is a true dialog.** Common errors (silence, unparseable) are Snackbar-level — a modal you must dismiss to retry punishes the most frequent failure.
- **Matching is fuzzy against an in-memory index.** SQL `LIKE` is explicitly rejected; it fails the moment the recognizer hears "rise" for "rice".
- **Bundled offline models were tried and rejected.** Vosk ships no official Indonesian model; the available community model is trained on children's speech. Hence platform ASR.
- **`Parser` and `Matcher` must be pure Kotlin with zero Android imports** (proposed, see C2). This is what makes threshold tuning against a recorded corpus possible as fast JVM tests.

---

## Open — needs a human answer, do not guess

1. **The 20 prototype products.** Not yet chosen. Must be *real* products from a *real* shop, deliberately including hard cases: near-homophones, multi-word variants (Indomie Goreng vs Indomie Kari Ayam), brand names. A conveniently distinct set will work beautifully and prove nothing.
2. **C1 — XML vs Compose.** Leaning XML. If this changes, requirements §4 and NFR-10 need amending.
3. **C2 — Concrete SOLID/clean-code rules.** Under discussion.
4. **C3 — State model.** `StateFlow` + `UiState` vs `LiveData`. Under discussion.
5. **The unit list and its quantity types.** Needs review by someone who runs a warung.
6. **Confidence thresholds.** Cannot be set before the prototype produces baseline numbers. This is a primary output of the prototype.

---

## How to work on this

- Ask before adding anything not listed in the prototype scope.
- Prefer the smallest thing that answers the question over the most correct thing.
- When the specs and this file disagree about scope, **this file wins**.
- The prototype's success criterion is a measured accuracy number, not a working app.
