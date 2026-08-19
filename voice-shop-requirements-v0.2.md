# Voice Entry Shop Calculator — General Requirements

**Version:** 0.2 (general requirements only)
**Date:** 2026-08-18
**Status:** Draft for review
**Scope note:** This document covers product scope, platform, data model, UI flows, and non-functional requirements. **The speech processing pipeline — normalization, tokenization, number parsing, and fuzzy matching — is deliberately NOT specified here** and will be defined in a separate document (see §13).

---

## 1. Purpose

A shopkeeper serving a customer needs to total up several items quickly, without typing and without looking at the screen for long. This app lets them hold a button, speak the items and quantities in one breath, and see an itemized total.

The app is a **calculator with a voice front-end**. It is not a POS system, not an inventory system, and not an accounting system.

---

## 2. Glossary

| Term | Meaning |
|---|---|
| **Catalog** | The shopkeeper's full list of products with prices. |
| **Product** | One sellable item at one unit and one price. E.g. "Indomie Goreng" (per piece). |
| **Display name** | The written name shown in the UI. |
| **Alias** | A spoken form that should resolve to a product. One product may have many. |
| **Unit** | The measure a product is priced by. Drawn from a fixed system list. |
| **Quantity type** | Whether a unit admits fractional amounts. `DISCRETE` (bottles, packets) or `DECIMAL` (rice, sugar). A property of the unit, not the product. |
| **Utterance** | One recording session — everything said between button press and release. |
| **Item line** | One parsed `(product, quantity)` pair with a computed subtotal. |
| **Bill** | The ordered list of item lines currently on screen, plus a total. |
| **Separator** | A reserved word that divides one item from the next within an utterance. |
| **Reserved word** | A word the parser interprets structurally (separator, number, unit) and which is therefore forbidden inside product names and aliases. |

---

## 3. Users and operating context

- **Primary user:** a single shopkeeper (warung / kios / small retail), operating their own phone.
- **Environment:** noisy — street traffic, other voices, music. Background noise is the normal case, not an edge case.
- **Connectivity:** unreliable. The app must be usable with no signal at all.
- **Device:** mid- to low-end Android, likely 2–4 GB RAM.
- **Posture:** one-handed, often while handling goods or cash. Speed matters more than polish.
- **Literacy/comfort:** assume basic smartphone literacy; avoid dense screens and jargon.

---

## 4. Platform and technology

| Item | Decision |
|---|---|
| Language | Kotlin |
| IDE | Android Studio |
| `minSdk` | 26 (Android 8.0) — covers devices sold from 2020 comfortably |
| `targetSdk` | Latest stable at build time |
| UI | Jetpack Compose |
| Persistence | Room |
| Async | Coroutines + Flow |
| Architecture | MVVM, single-activity |
| Currency | Indonesian Rupiah, integer only (no sub-unit) |
| UI language | Indonesian (`id`), with English (`en`) as a secondary string set |
| Orientation | Portrait **and** landscape. No orientation lock in the manifest |
| Form factors | Phone and tablet, via Compose `WindowSizeClass` |
| Distribution | Prototype: sideloaded APK. Release: Google Play |

**Hard constraints:**
- **No LLM**, on-device or remote.
- **No mandatory backend.** The app must function with zero server infrastructure owned by us.
- **No user account, no login, no sync.**

---

## 5. Speech recognition strategy (general)

The app uses Android's platform `SpeechRecognizer` rather than a bundled model. This keeps APK size near zero and gives access to Google's Indonesian acoustic models.

**Tiered fallback, in order:**

| Tier | Mechanism | Condition |
|---|---|---|
| 1 | `SpeechRecognizer.createOnDeviceSpeechRecognizer()` | API 33+ and `id-ID` on-device pack installed |
| 2 | `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE = true` | API 26–32 |
| 3 | `SpeechRecognizer` online | Tiers 1–2 unavailable **and** network present |
| 4 | Manual entry only | No recognizer and no network |

**REQ-ASR-1** — The app MUST detect which tier is active at startup and on each recognition attempt, and MUST surface the current mode to the user via a small, persistent indicator (e.g. Offline / Online / Manual).

**REQ-ASR-2** — The recognizer MUST be configured with language `id-ID`, free-form language model, partial results enabled, and `EXTRA_MAX_RESULTS = 5`.

**REQ-ASR-3** — All returned N-best alternatives MUST be passed to the parsing stage, not only the top hypothesis.

**REQ-ASR-4** — Audio MUST NOT be written to disk or transmitted anywhere other than the platform recognizer.

**REQ-ASR-5** — The app MUST degrade silently between tiers. A tier change MUST NOT block, prompt, or interrupt the user mid-task.

**REQ-ASR-6** — On first run the app SHOULD detect whether the `id-ID` offline pack is installed and, if not, offer a one-tap deep link to the system speech settings, with a clear "skip" option.

> **Known risk:** offline pack availability varies by OEM and Android version. Some devices report offline support but fail without a network. Tier detection must be based on actual observed behavior, not just API availability.

---

## 6. Functional requirements

### 6.1 Main screen

- **FR-1.1** The main screen MUST show three controls: a large **push-to-talk** button (dominant), a **Price Lookup** button, and a **Catalog** button. Only the PTT button is primary; the other two are secondary in visual weight.
- **FR-1.2** The pattern hint (e.g. *"jumlah + nama barang, pisahkan dengan 'lalu'"*) MUST be visible on the main screen at rest — **not** in a popup shown after the button is pressed.
- **FR-1.3** The current recognition tier indicator MUST be visible.
- **FR-1.4** If the catalog is empty, the PTT button MUST be disabled with a prompt directing the user to add products or import a list.
- **FR-1.5** In landscape and on expanded-width screens, the PTT button MUST remain within one-handed thumb reach — anchored to a screen edge rather than centered.

### 6.2 Voice capture

- **FR-2.1** Recording MUST start on press and stop on release (push-to-talk).
- **FR-2.2** A **slide-to-cancel** gesture MUST discard the utterance without processing.
- **FR-2.3** While recording, the UI MUST show a live audio level indicator and the **partial transcript**.
- **FR-2.4** Recording MUST auto-stop at a maximum utterance length (default 20 s) to protect against a stuck button.
- **FR-2.5** A press shorter than a minimum threshold (default 300 ms) MUST be treated as an accidental tap and discarded with a brief hint.
- **FR-2.6** A settings option SHOULD allow switching to tap-to-start / tap-to-stop mode for users who cannot hold the button.

### 6.3 Parsing and matching

- **FR-3.1** The pipeline is: `raw transcript (N-best) → normalize → tokenize → segment by separator → extract (quantity, product phrase) → match against catalog → item lines`.
- **FR-3.2** One utterance MUST be able to produce multiple item lines.
- **FR-3.3** Each item line MUST carry a **confidence score**, used to decide accept / disambiguate / reject.
- **FR-3.4** Matching MUST be fuzzy, performed against an **in-memory** catalog index. Direct SQL `LIKE` matching is explicitly rejected.
- **FR-3.5** Matching MUST consider display names and all aliases.
- **FR-3.6** Where a spoken phrase matches a *family* of products rather than one product (e.g. "Indomie" → Indomie Goreng, Indomie Kari Ayam), the app MUST present a chooser rather than failing.
- **FR-3.7** Parsing MUST NOT be blocked by an unrecognized fragment; unparseable segments become an "unresolved" line the user can fix, and the rest of the utterance MUST still resolve.
- **FR-3.8** A parsed quantity MUST be validated against the matched product's quantity type. A fractional quantity against a `DISCRETE` unit MUST NOT be silently rounded — the line MUST be flagged for review showing what was heard.

> Algorithms, thresholds, and Indonesian-specific rules are out of scope for this document. See §13.

### 6.4 Separators and reserved words

- **FR-4.1** The default separator set is: `lalu`, `terus`, `terakhir`.
- **FR-4.2** The separator set MUST be editable in settings.
- **FR-4.3** Separators, number words, and unit words are **reserved**. Product names and aliases containing a reserved word MUST be rejected at save time with an explanatory message.
- **FR-4.4** Mixed-language separators (e.g. English `next`, `then`) are **not** enabled by default. They MAY be added by the user.

### 6.5 Bill / result screen

- **FR-5.1** The result MUST be an **editable line-item list**, not a read-only total.
- **FR-5.2** Each line MUST show: product display name, quantity, unit, unit price, subtotal.
- **FR-5.3** Each line MUST support: change quantity, swap product, delete.
- **FR-5.3a** The quantity editor MUST match the quantity type: a stepper (+/−, integers only) for `DISCRETE`, a decimal numeric input for `DECIMAL`.
- **FR-5.4** Lines below the confidence threshold MUST be visually flagged as needing review.
- **FR-5.5** The user MUST be able to add further items **by voice** to the existing bill without clearing it.
- **FR-5.6** The user MUST be able to add an item manually via product search.
- **FR-5.7** The running total MUST update immediately on any edit.
- **FR-5.8** A single, clearly-placed **Clear** action MUST reset the bill. It MUST require confirmation if the bill has 3+ lines.
- **FR-5.9** The bill is transient. It MUST NOT be persisted to the database on completion (see §11).

### 6.6 Ambiguity resolution

- **FR-6.1** When a phrase resolves to multiple candidates above threshold, the app MUST present a chooser listing candidates ordered by score, each with name, unit, and price.
- **FR-6.2** The chooser MUST include a "none of these" option that converts the line to unresolved.
- **FR-6.3** Multiple ambiguities in one utterance MUST be resolved in sequence without losing already-resolved lines.
- **FR-6.4** When the user resolves an ambiguity, the app SHOULD offer to save the spoken phrase as an alias for the chosen product. This is the primary mechanism by which accuracy improves over time.

### 6.7 Catalog CRUD

- **FR-7.1** The user MUST be able to create, read, update, and soft-delete products.
- **FR-7.2** A product record consists of: display name, unit, price per unit, active flag. Quantity type is inherited from the unit and is not separately editable.
- **FR-7.3** **One product = one unit = one price.** Different packagings of the same goods are separate products.
- **FR-7.4** The list MUST be searchable and sortable by name and by recently used.
- **FR-7.5** Deletion MUST be a soft delete (inactive), so historical aliases are not orphaned.
- **FR-7.6** Duplicate display names MUST be rejected.
- **FR-7.7** Price MUST be entered and stored as a whole number of Rupiah.
- **FR-7.8** Unit MUST be chosen from the system unit list, not free text. Each unit carries a fixed quantity type.
- **FR-7.9** The unit picker MUST show the quantity type, so the user understands why "1.5" is accepted for one product and rejected for another.

### 6.11 Price lookup screen

A read-only view for answering "how much is this?" without starting a bill.

- **FR-11.1** The screen MUST provide a searchable, scrollable list of active products showing display name, unit, and price.
- **FR-11.2** Search MUST match display names and aliases.
- **FR-11.3** The screen MUST be strictly read-only. No create, edit, or delete affordances.
- **FR-11.4** Search MUST use the same matching component as FR-5.6 (manual add to bill) and the same catalog index as FR-3.4. Three separate search implementations MUST NOT exist.
- **FR-11.5** Voice search on this screen is **out of scope for v1**; the search is typed.

### 6.8 Aliases

- **FR-8.1** The user MUST be able to add, edit, and delete aliases for any product.
- **FR-8.2** The alias editor MUST be reachable from both the product detail screen and the disambiguation flow.
- **FR-8.3** An alias MUST NOT be assignable to more than one active product. Conflicts MUST be reported at save time.
- **FR-8.4** Aliases MUST be subject to the reserved-word validation in FR-4.3.

### 6.9 Backup and import

- **FR-9.1** The catalog (products + aliases) MUST be exportable to a single CSV or JSON file via the system share sheet.
- **FR-9.2** The catalog MUST be importable from the same format, with a preview and a merge-or-replace choice.
- **FR-9.3** Import MUST validate every row and report failures per-row without aborting the whole import.

*Rationale: these users lose and replace phones. Without export, a full catalog re-entry is a project-killer.*

### 6.10 First run

- **FR-10.1** First run MUST request `RECORD_AUDIO` with an in-context rationale before the system dialog.
- **FR-10.2** If permission is denied, the app MUST remain fully usable in manual-entry mode, with a non-nagging path to re-enable.
- **FR-10.3** First run MUST offer: import a catalog, or add the first product manually.
- **FR-10.4** A short one-screen explanation of the speaking pattern MUST be shown once, and be re-viewable from settings.

---

## 7. Data model (logical)

**Product**
`id` · `displayName` · `unitId` (FK) · `pricePerUnitRupiah` (integer) · `isActive` · `createdAt` · `updatedAt`

**Unit**
`id` · `code` (e.g. `kg`, `ons`, `pcs`, `botol`) · `displayName` · `quantityType` (`DISCRETE` | `DECIMAL`) · `spokenForms` · `isSystem`

**Alias**
`id` · `productId` (FK) · `spokenText` · `normalizedText` (indexed) · `source` (`manual` | `learned` | `imported`) · `createdAt`

**ReservedWord**
`id` · `word` · `type` (`separator` | `number` | `unit`) · `isUserAdded`

**Settings** (key-value)
recognizer tier preference · separator set · PTT mode · confidence thresholds · onboarding flags

Notes:
- **Quantity is stored as a scaled integer, never a float** (e.g. thousandths, so 1.5 kg = `1500`). Floating-point quantities multiplied by prices produce visible rounding errors in currency.
- **Subtotal rounding:** `quantity × pricePerUnit`, rounded half-up to the nearest whole Rupiah, at the line level. The total is the sum of already-rounded lines.
- `quantityType` lives on `Unit`, not `Product`, so it cannot become inconsistent between two products sharing a unit.
- No `Bill` / `Transaction` table in v1.
- No stock, barcode, category, supplier, or cost-price fields in v1.
- `normalizedText` is written by the normalization routine defined in the speech processing spec; the schema reserves the column but does not define its contents.

---

## 8. Error and edge case behavior

| Condition | Required behavior |
|---|---|
| Silence / no speech detected | Toast-level hint, stay on main screen, no dialog |
| Audio captured but nothing parseable | Show the raw transcript + "add manually" affordance |
| Quantity missing for a product | Default to 1, flag the line for review |
| Product phrase matches nothing | Unresolved line showing the heard phrase, tappable to search catalog |
| Multiple candidates | Disambiguation chooser (§6.6) |
| Partial success (3 of 4 items parsed) | Keep the 3, show the 4th as unresolved. Never discard the whole utterance |
| Recognizer unavailable mid-session | Drop to next tier silently, update indicator |
| Network lost during online recognition | Fail that utterance with a clear one-line message; do not retry automatically |
| `RECORD_AUDIO` denied | Manual mode (FR-10.2) |
| Catalog empty | PTT disabled (FR-1.4) |
| Price is zero or unset | Allow, show subtotal 0, flag visually in catalog |
| Utterance exceeds max length | Auto-stop and process what was captured |
| Fractional quantity spoken for a `DISCRETE` product | Flag the line for review with the heard value; do not silently round |
| Quantity of zero or negative | Reject the line, flag for review |
| Device rotated mid-recording | Recording MUST continue uninterrupted; the utterance MUST NOT be lost |
| Device rotated with an unsaved bill or half-filled CRUD form | All state MUST survive the configuration change |

---

## 9. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | **Latency:** ≤ 1.5 s from button release to result screen on the target device tier, when using an on-device recognizer. |
| NFR-2 | **APK size:** ≤ 15 MB download. No bundled acoustic model. |
| NFR-3 | **Offline:** all functionality except Tier-3 recognition MUST work with airplane mode on. |
| NFR-4 | **Catalog scale:** MUST perform acceptably with 1,000 products and 5,000 aliases. |
| NFR-5 | **Cold start:** ≤ 2 s to interactive on target device tier. |
| NFR-6 | **Privacy:** no analytics, no telemetry, no network calls other than the platform recognizer. |
| NFR-7 | **Accessibility:** primary controls usable one-handed; minimum 48 dp touch targets; supports system font scaling to 200%. |
| NFR-8 | **Noise:** recognition must be evaluated under realistic market noise, not quiet-room conditions. |
| NFR-9 | **Battery:** no background listening. Microphone active only while the button is held. |
| NFR-10 | **Adaptive layout:** all screens MUST be usable in portrait and landscape, at compact / medium / expanded width classes. On expanded width, the bill and catalog SHOULD use a two-pane layout rather than stretched single-column. |
| NFR-11 | **State retention:** recognizer session and all screen state MUST be held in `ViewModel` scope and survive configuration changes. No `screenOrientation` lock and no `configChanges` workaround. |
| NFR-12 | **Play Store readiness (release build only):** privacy policy covering microphone use, completed Data Safety declaration, and compliance with the current Play target-API requirement. |

---

## 10. Test and acceptance approach

- **AC-1** A recorded evaluation corpus MUST be assembled before tuning begins: multiple speakers, real product names from a real catalog, recorded in genuine market noise.
- **AC-2** The primary metric is **item-level accuracy** — correct product *and* correct quantity — not word error rate.
- **AC-3** A target accuracy figure MUST be agreed after the first baseline measurement, not guessed beforehand.
- **AC-4** Every row in §8 MUST have a corresponding test case.
- **AC-5** The app MUST be tested on at least one Android 8–10 device without an on-device recognizer, to validate the tier fallback.
- **AC-6** The app MUST be tested on a tablet in both orientations, including rotation during an active recording.

---

## 11. Out of scope for v1

Explicitly excluded, to be revisited later:

- Cash tendered and change calculation
- Saving, listing, or reporting on past bills
- Receipt printing or sharing
- Stock / inventory tracking
- Barcode scanning
- Discounts, promotions, tiered or bulk pricing
- Multiple units or packagings per product
- Multi-user, multi-device, cloud sync
- Any LLM-based component
- Languages other than Indonesian (English UI strings only)

---

## 12. Open questions

1. **The actual unit list.** Fixed-list is now decided; the contents are not. Proposed starting set —
   `DECIMAL`: kg, ons (100 g), gram, liter, ml, meter.
   `DISCRETE`: pcs/biji, bungkus, botol, kaleng, sachet, dus, pak, ikat, lembar, batang.
   Needs review by someone who actually runs a warung.
2. **Can the user add custom units?** If yes, they must supply spoken forms and a quantity type, which is a non-trivial form. Recommend no for v1.
3. **Does the quantity always precede the product name**, or must both orders be accepted? Recommend fixing one order for v1.
4. **Confidence thresholds** — the accept / disambiguate / reject cut-offs cannot be set until baseline measurement (AC-3).
5. **Learned aliases** — should FR-6.4 save aliases automatically, or always ask? Automatic is faster but can silently poison matching.
6. **Seed catalog** — ship a starter list of common Indonesian warung goods, or start empty?
7. **Quantity precision** — thousandths is proposed. Is that enough (10 g granularity within a kg), or is finer needed?

---

## 13. Deferred: speech processing specification

The following belongs in a separate document and is **not** covered here:

- Transcript normalization (casing, punctuation, digit-vs-word forms returned by the recognizer)
- Tokenization rules
- Indonesian number parsing — including `belas` / `puluh` / `ratus` / `ribu` compounding, the `se-` prefix fusion (*sekilo*, *sebungkus*), fractional forms (*setengah*, *seperempat*), and colloquial variants
- Unit vocabulary and unit-specific rules (note: Indonesian *ons* = 100 g, not the English ounce)
- Mapping spoken fractional forms to scaled-integer quantities, and how the parser uses the matched product's quantity type to constrain interpretation
- Segmentation strategy when separators are absent or misheard
- Fuzzy matching algorithm: edit distance, phonetic keying, token-set scoring, and how N-best alternatives are combined
- Scoring and threshold model
- Alias learning rules

---

## Change log

| Version | Date | Notes |
|---|---|---|
| 0.1 | 2026-08-18 | Initial draft. Architecture switched from bundled offline model to platform `SpeechRecognizer` with tiered fallback, following the failed Indonesian offline model spike. |
| 0.2 | 2026-08-18 | Added discrete/decimal quantity types as a property of `Unit`; added the read-only Price Lookup screen (§6.11); added portrait/landscape and tablet support with adaptive layout and state-retention requirements; resolved the unit-list and distribution open questions. |
