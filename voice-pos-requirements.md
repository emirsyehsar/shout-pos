# Voice Price Lookup — Requirements Specification

**Version:** 0.1 (draft for review)
**Date:** 18 August 2026
**Platform:** Android (Kotlin)
**Status:** General requirements. Speech-processing internals are specified at requirement level only; algorithm and pipeline design are deferred to a separate technical design document.

---

## 1. Purpose

A shopkeeper holds a button, speaks a list of products and quantities in Indonesian, and immediately sees an itemised total. The app replaces manual lookup in a paper price list or a mental price table.

The app is a **price calculator with voice input**. It is not a point-of-sale system, an inventory system, or an accounting system.

---

## 2. Scope

### 2.1 In scope (v1)

- Voice capture via push-to-talk
- Indonesian speech recognition, offline-first with online fallback
- Parsing of spoken utterances into (product, quantity) pairs
- Matching parsed product names against a local catalogue
- Disambiguation when a spoken name matches more than one product
- An editable result list with a running total
- Full CRUD over products and prices
- Alias management (alternative spoken forms per product)
- Catalogue backup and restore

### 2.2 Out of scope (v1)

- Cash tendered and change calculation
- Stock levels, inventory tracking, reordering
- Receipt printing
- Transaction history and sales reports
- Barcode scanning
- Discounts, promotions, tax, tiered or bulk pricing
- Multi-user, multi-device sync, cloud accounts
- Any use of an LLM, on-device or remote

### 2.3 Deferred to v2 (recorded here so v1 does not block them)

- Cash and change
- Saving completed baskets to a history log
- Voice-driven product creation

---

## 3. Glossary

| Term | Meaning |
|---|---|
| **PTT** | Push-to-talk. The record button on the main screen. |
| **Utterance** | One continuous recording, from button press to release. |
| **Item** | One (product, quantity) pair extracted from an utterance. |
| **Basket** | The ordered list of items currently displayed, with a total. |
| **Alias** | An alternative spoken form for a product, distinct from its display name. |
| **Separator** | A reserved word that marks the boundary between two items in an utterance. |
| **Canonical unit** | The single unit of sale attached to a product. |

---

## 4. Assumptions and constraints

- **A-1** — Primary language is Indonesian (`id-ID`). English is not required in v1.
- **A-2** — One product has exactly one unit of sale and one price. Variants that differ in packaging or flavour are separate products (e.g. *Indomie Goreng* and *Indomie Kari Ayam* are two products; *rokok per batang* and *rokok per bungkus* are two products).
- **A-3** — Prices are in Indonesian Rupiah, integers only, no sub-unit.
- **A-4** — A single shopkeeper uses a single device. No concurrency.
- **A-5** — The catalogue is small to moderate: design for up to 2,000 products, expect 100–500 in practice.
- **A-6** — The device may have no network connection at any time.
- **A-7** — The operating environment is noisy (market, street frontage) and the user is often one-handed.

---

## 5. Target platform

- **P-1** — `minSdk 26` (Android 8.0). Covers devices sold from 2020 onward with margin.
- **P-2** — `targetSdk` current at time of release.
- **P-3** — Language: Kotlin. UI toolkit to be confirmed (see Open Questions).
- **P-4** — Must remain usable on a low-end device: 2 GB RAM, mid-range ARM SoC.
- **P-5** — Portrait orientation only.
- **P-6** — All user-facing strings in Indonesian, externalised to resources.

---

## 6. Functional requirements

### 6.1 Main screen

- **FR-1.1** — The main screen shows exactly two primary controls: a PTT button and a button to open the catalogue management screen.
- **FR-1.2** — The PTT button is the visually dominant element, large enough to hit reliably one-handed without looking.
- **FR-1.3** — The expected speaking pattern is shown as persistent static text on the main screen, *before* the user presses the button. It is not shown for the first time in a dialog that appears while the user is already speaking.
- **FR-1.4** — When a basket is present, the main screen shows the basket and its total (see 6.6).
- **FR-1.5** — The PTT button is visibly disabled, with an explanatory message, whenever recognition is unavailable (see FR-3.6).

### 6.2 Voice capture

- **FR-2.1** — Recording starts on press and ends on release of the PTT button.
- **FR-2.2** — While recording, the UI shows (a) an unambiguous recording indicator, (b) a live audio level meter, and (c) the partial transcript as it becomes available.
- **FR-2.3** — The user can cancel a recording in progress by sliding away from the button before releasing.
- **FR-2.4** — Recording is capped at 30 seconds; on reaching the cap, recording stops and processing proceeds normally.
- **FR-2.5** — A press shorter than 300 ms is treated as an accidental tap: no recording, and a hint is shown explaining that the button must be held.
- **FR-2.6** — Audio is captured at 16 kHz mono.
- **FR-2.7** — Audio is never written to persistent storage and never transmitted except as required by the online recognition fallback (FR-3.3).
- **FR-2.8** — A configurable option shall provide toggle-to-record instead of hold-to-record, for users who need both hands free. Hold-to-record is the default.

### 6.3 Speech recognition

- **FR-3.1** — Recognition is **offline-first**. Where the device supports on-device recognition for `id-ID`, it is used.
- **FR-3.2** — On API 33+, on-device recognition uses the platform on-device recogniser. On API 26–32, it requests offline preference from the platform recogniser.
- **FR-3.3** — Where on-device recognition is unavailable or fails, and the device has a network connection, the app falls back to network recognition. The user is informed, once per session, that recognition is using the network.
- **FR-3.4** — Where neither offline nor online recognition is available, the app falls back to **manual entry** (FR-6.6) rather than failing outright.
- **FR-3.5** — The recogniser is requested to return multiple alternative transcripts. All alternatives are made available to parsing and matching, not only the top one.
- **FR-3.6** — On first launch and on each app start, the app detects which recognition modes are available and surfaces the current mode to the user in Settings.
- **FR-3.7** — Where the device supports downloading an offline `id-ID` language pack but has not done so, the app shall detect this and offer to route the user to the relevant system settings.
- **FR-3.8** — The recogniser abstraction shall be behind an interface, so an alternative engine can be substituted without changes to parsing, matching, or UI.

### 6.4 Parsing

Requirement-level only. The parsing strategy is a separate design document.

- **FR-4.1** — Canonical utterance pattern: `<product> <quantity>`, repeated, joined by separators. Example: *"Indomie goreng dua, terus Aqua botol tiga."*
- **FR-4.2** — Recognised separator words: `lalu`, `terus`, `terakhir`, `dan`, `sama`. This set is configurable.
- **FR-4.3** — Separator words are **reserved**. The app shall reject any product name or alias containing a separator word as a standalone token (see FR-7.6).
- **FR-4.4** — The parser shall accept quantity expressed as digits or as Indonesian number words, since the recogniser may return either.
- **FR-4.5** — The parser shall handle Indonesian numeral construction: units, `belas`, `puluh`, `ratus`, `ribu`, and their compounds.
- **FR-4.6** — The parser shall expand the `se-` prefix as an implicit quantity of one where it is fused to a unit or classifier: *sekilo* → 1 kg, *sebungkus* → 1 pack, *sebotol* → 1 bottle, *sebiji* → 1 piece.
- **FR-4.7** — The parser shall handle fractional quantities: `setengah` → 0.5, `seperempat` → 0.25.
- **FR-4.8** — **`ons` shall be interpreted as 100 grams**, per Indonesian usage. It must not be treated as the English ounce.
- **FR-4.9** — Where an item has no explicit quantity, quantity defaults to 1.
- **FR-4.10** — Quantity is stored as a decimal, not an integer, to support FR-4.7.
- **FR-4.11** — Common colloquial number forms shall be normalised (for example *rebu* → *ribu*). The specific set is to be established from field recordings.

### 6.5 Product matching

- **FR-5.1** — Matching is fuzzy, not exact string equality, and not a SQL `LIKE` query. The recogniser will frequently return near-misses.
- **FR-5.2** — Matching considers both the display name and all aliases of every product.
- **FR-5.3** — Matching runs against all recogniser alternatives (FR-3.5) and returns the best-scoring candidate across all of them.
- **FR-5.4** — Matching produces one of four outcomes: **confident match**, **ambiguous** (several candidates above threshold, or scores too close to separate), **weak match** (best candidate below the confidence threshold), or **no match**.
- **FR-5.5** — A confident match is added to the basket directly.
- **FR-5.6** — An ambiguous or weak match presents the user with a ranked shortlist to choose from, plus an option to dismiss the item.
- **FR-5.7** — Where a spoken name matches a *family* of products rather than one product — for example *"Indomie"* where the catalogue holds *Indomie Goreng* and *Indomie Kari Ayam* — the app shall present the family members as a disambiguation shortlist. It shall not reject the utterance for lacking specificity.
- **FR-5.8** — Confidence thresholds shall be tunable without a code change and shall be calibrated against the test corpus (see §10).
- **FR-5.9** — Matching shall complete against a 2,000-product catalogue within the latency budget in NFR-2.1. The catalogue is held in memory for matching.

### 6.6 Basket and result

- **FR-6.1** — The result is an **editable list of line items**, not a read-only total. This is the primary recovery mechanism for every recognition and parsing failure.
- **FR-6.2** — Each line shows: product display name, quantity, unit, unit price, line subtotal.
- **FR-6.3** — The user can edit the quantity of any line inline.
- **FR-6.4** — The user can replace the product on any line, via a searchable picker.
- **FR-6.5** — The user can delete any line.
- **FR-6.6** — The user can add a line manually, via a searchable picker, without using voice.
- **FR-6.7** — A further PTT utterance **appends** to the current basket rather than replacing it.
- **FR-6.8** — A clear action empties the basket. It requires confirmation when the basket is non-empty.
- **FR-6.9** — The total is displayed prominently and updates immediately on any edit.
- **FR-6.10** — Where an utterance is partially understood — three of four items parsed — the app shall add the items it understood and report the portion it could not, rather than discarding the whole utterance.
- **FR-6.11** — Lines added from a weak match are visually flagged, so the user can spot a wrong guess quickly.
- **FR-6.12** — The basket survives configuration change and process death.

### 6.7 Catalogue management

- **FR-7.1** — Create, read, update, delete products.
- **FR-7.2** — Product fields: display name, unit, price, active flag, aliases.
- **FR-7.3** — The list is searchable and sorted alphabetically by default.
- **FR-7.4** — Deleting a product requires confirmation. A product referenced by the current basket cannot be hard-deleted; it is deactivated instead.
- **FR-7.5** — Price must be a positive integer. Display name must be non-empty and unique.
- **FR-7.6** — On save, the app validates the display name and every alias against the reserved separator list (FR-4.3) and rejects the save with an explanatory message on conflict.
- **FR-7.7** — The app warns, without blocking, when a new product's name or alias is confusingly similar to an existing one, since this will produce chronic ambiguity at match time.

### 6.8 Aliases

- **FR-8.1** — A product may have zero or more aliases. An alias is an alternative *spoken* form, distinct from the display name.
- **FR-8.2** — Aliases are managed by the shopkeeper from the product edit screen. They know what they actually say; the app must not guess.
- **FR-8.3** — Aliases participate in matching identically to display names (FR-5.2).
- **FR-8.4** — An alias must be unique across the catalogue. The app rejects a duplicate and identifies the conflicting product.
- **FR-8.5** — The product edit screen shall offer a way to test an alias: record a phrase and show what the recogniser and matcher produce.

### 6.9 Backup and restore

- **FR-9.1** — Export the full catalogue, including aliases, to a CSV file via the system share sheet.
- **FR-9.2** — Import a catalogue from CSV, with a preview and an explicit merge-or-replace choice.
- **FR-9.3** — Import validates every row and reports failures per row without aborting the whole import.
- **FR-9.4** — The app prompts the user to export a backup periodically once the catalogue exceeds a threshold size.

### 6.10 First run

- **FR-10.1** — On first launch the app requests the microphone permission, with a plain-language rationale shown beforehand.
- **FR-10.2** — Where permission is denied, the app remains usable via manual entry (FR-6.6), and offers a route to system settings to grant it.
- **FR-10.3** — Where permission is permanently denied, the app does not re-prompt; it shows a persistent, dismissible route to settings.
- **FR-10.4** — With an empty catalogue, the main screen shows an onboarding state directing the user to add products or import a CSV. The PTT button is disabled with an explanation, since it cannot match anything.
- **FR-10.5** — First run checks offline `id-ID` availability and follows FR-3.7 where a language pack can be installed.

### 6.11 Settings

- **FR-11.1** — Show current recognition mode (on-device, network, unavailable).
- **FR-11.2** — Toggle whether network fallback is permitted at all.
- **FR-11.3** — Toggle hold-to-record versus toggle-to-record (FR-2.8).
- **FR-11.4** — Edit the separator word list.
- **FR-11.5** — Export and import catalogue.

---

## 7. Data model

Persistence: local relational database. No remote store.

**Product**

| Field | Type | Notes |
|---|---|---|
| `id` | Long | Primary key |
| `displayName` | String | Unique, non-empty |
| `unit` | String | Canonical unit of sale: kg, gram, pcs, bungkus, botol, liter, … |
| `price` | Long | Rupiah per one `unit`, positive integer |
| `isActive` | Boolean | Soft delete |
| `createdAt` / `updatedAt` | Timestamp | |

**Alias**

| Field | Type | Notes |
|---|---|---|
| `id` | Long | Primary key |
| `productId` | Long | Foreign key, cascade delete |
| `spokenForm` | String | Unique across the table |

**Unit** is a controlled vocabulary, not free text, so that FR-4.6 and FR-4.8 can map spoken units onto it reliably.

---

## 8. Failure modes

Every row requires defined behaviour. The editable basket (FR-6.1) is the shared recovery path.

| Condition | Behaviour |
|---|---|
| Microphone permission denied | Manual entry available; route to settings offered |
| No recognition available at all | Manual entry; PTT disabled with explanation |
| Recording cancelled by user | Silent discard, no state change |
| Press shorter than 300 ms | Hint shown, no recording |
| Silence or no speech detected | Message prompting the user to try again; basket unchanged |
| Transcript returned but unparseable | Show the transcript so the user sees what was heard; offer manual entry |
| Item parsed, no product match | Report the unmatched phrase; offer a searchable picker and the option to create the product |
| Item parsed, ambiguous match | Disambiguation shortlist (FR-5.6, FR-5.7) |
| Item parsed, weak match | Added but flagged (FR-6.11) |
| Product matched, quantity missing | Default to 1 (FR-4.9), line flagged for review |
| Utterance partially parsed | Add what was understood, report the rest (FR-6.10) |
| Network fallback times out | Report timeout; offer retry or manual entry |
| Catalogue empty | Onboarding state (FR-10.4) |

---

## 9. Non-functional requirements

### 9.1 Performance

- **NFR-1.1** — Cold start to interactive main screen: under 2 seconds on the reference low-end device.
- **NFR-1.2** — Button release to displayed result, on-device recognition: under 1.5 seconds for a four-item utterance.
- **NFR-1.3** — Parsing and matching, excluding recognition: under 150 ms for a 2,000-product catalogue.
- **NFR-1.4** — The UI thread is never blocked by recognition, parsing, matching, or database access.

### 9.2 Size and resources

- **NFR-2.1** — Installed APK under 15 MB. The platform recogniser adds no model weight to the app; this budget must be defended if an engine is ever bundled.
- **NFR-2.2** — Idle memory under 100 MB.
- **NFR-2.3** — No background service, no wake word, no always-on listening. Audio capture occurs only between press and release.

### 9.3 Reliability and privacy

- **NFR-3.1** — All application functions except network recognition fallback work with no network.
- **NFR-3.2** — No analytics, telemetry, or crash reporting that transmits user data in v1.
- **NFR-3.3** — No audio is persisted (FR-2.7).
- **NFR-3.4** — The catalogue is the user's data; export is always available (FR-9.1).

### 9.4 Usability

- **NFR-4.1** — Every primary action reachable one-handed.
- **NFR-4.2** — Minimum touch target 48 dp; body text minimum 16 sp; respects system font scaling.
- **NFR-4.3** — Readable in direct sunlight: high contrast, no low-contrast greys for essential information.
- **NFR-4.4** — No feature requires reading English.

---

## 10. Verification

- **V-1** — A recorded test corpus is required before accuracy can be claimed. Minimum: 5 speakers, 200 utterances, recorded in a real market environment, using real product names from a real catalogue.
- **V-2** — The primary metric is **item-level accuracy**: proportion of spoken items resolved to the correct product *and* the correct quantity. Word error rate is not the metric of interest.
- **V-3** — Secondary metrics: false-confident rate (wrong product added without prompting the user — the most damaging failure), disambiguation rate, and end-to-end latency.
- **V-4** — Confidence thresholds (FR-5.8) are tuned against this corpus, prioritising a low false-confident rate over a low disambiguation rate. A prompt costs a tap; a silent wrong price costs money.
- **V-5** — Device matrix: at least one API 33+ device with offline `id-ID`, one API 33+ device without it, and one API 26–29 device.

---

## 11. Open questions

1. **Word order.** This document specifies `<product> <quantity>` (FR-4.1). Should `<quantity> <product>` also be accepted? Accepting both is more forgiving but materially increases ambiguity, particularly where a product name begins with a number word.
2. **UI toolkit.** Jetpack Compose or Views with XML? Compose is assumed unless stated otherwise.
3. **Unit vocabulary.** The controlled list in §7 needs to be fixed from the real catalogue before FR-4.6 and FR-4.8 can be implemented.
4. **Colloquial number forms.** FR-4.11 needs a concrete list, which should come from the V-1 corpus rather than from guesswork.
5. **Regional numerals.** Do the target users mix Javanese or Sundanese number words into Indonesian? If so, FR-4.5 expands significantly.
6. **Seed catalogue.** Should the app ship with a starter catalogue of common warung products, or start empty?

---

## 12. Change log

| Version | Date | Notes |
|---|---|---|
| 0.1 | 18 Aug 2026 | Initial draft |
