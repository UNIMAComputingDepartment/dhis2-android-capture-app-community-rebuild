# Cross-Program Data Mapping — Configuration Guide

For people configuring `dataMappers` in the iCHIS Configuration App.
Design rationale lives in `cross_program_data_mapping_design.md`; this is the practical version.

---

## 1. The idea in one minute

You are describing **facts that two or more programs both record**, so a value entered in one shows up
in the other.

Three pieces:

| Piece | What it is |
|---|---|
| **Concept** | A fact — "haemoglobin", "HIV status", "date BCG was given" |
| **Binding** | Where that fact lives in *one* program, and how that program encodes it |
| **Transfer** | Permission for values to flow from program A to program B, and when |

A concept with bindings for ANC and PNC does **not** move anything on its own. A transfer is what
authorises the flow, because "these fields mean the same thing" is not the same as "a value may be
copied from one to the other" — postnatal data written back into an antenatal record would corrupt
the clinical history.

**Rule of thumb:** the canonical form should be the **most precise** representation of the fact.
A date is more precise than a yes/no; a birth date is more precise than an age. Programs holding the
less precise form can then receive but not send, which the app works out for you.

---

## 2. Building your first mapping

### Step 1 — Define the concept

**Concepts** tab → *Add Concept*.

- **Concept ID** — lowercase, no spaces: `haemoglobin`, `bcg_date_given`. Used in logs; make it
  readable. Avoid `1`, `2`.
- **Display label** — free text, e.g. "Haemoglobin".
- **Canonical type** — the *most precise* form of the fact:

| Fact | Canonical type |
|---|---|
| A measurement | `NUMBER` (set the canonical unit, e.g. `g/dL`) |
| A count | `INTEGER` |
| A coded answer (Yes/No/Unknown, HIV status) | `OPTION` + canonical codes |
| A date, or "did X happen and when" | `DATE` |
| Free text | `TEXT` |

### Step 2 — Add a binding per program

Inside the concept → *Add Binding*, once for each program.

1. **Program** — pick it. Everything below is then scoped to it.
2. **Kind of field** — attribute, data element, event date, enrollment property, or constant.
   Everything except attribute and data element is **source-only**: it can be read but never written
   to.
3. **Program stage**, and **which event** — these appear only for data elements and event dates.
4. **Field** — pick by name. Its **value type and option set are shown** once picked; check they
   make sense against the canonical type.
5. **Unit**, **option code mapping**, **age unit** — each appears only when the canonical type or
   derivation calls for it.

The form only shows the questions that apply to what you have picked so far, so a binding you have not
finished will have fewer fields than one you have. That is expected, not a missing setting.

### Step 3 — Authorise the flow

**Transfers** tab → *Add Transfer*.

- **Source** and **target program**
- **When**: `TARGET_ENROLLMENT_CREATED` (whenever an enrollment in the target program is created —
  automatically *or* by hand) and/or `SOURCE_EVENT_SAVED` (whenever a source event is saved)
- **Concepts to carry** — pick from the list

For both directions, add **two transfers**, one each way.

### Step 4 — Save, then sync on the device

The datastore is only read on the device after a **metadata sync**. Nothing happens until then.

### Which actions actually fire mapping

| What the user does | Trigger |
|---|---|
| Enrolls an existing TEI into another program from the dashboard | `TARGET_ENROLLMENT_CREATED` |
| Enrolls a TEI into a program from search | `TARGET_ENROLLMENT_CREATED` |
| A workflow rule auto-enrolls the TEI | `TARGET_ENROLLMENT_CREATED` |
| Saves an event in the source program | `SOURCE_EVENT_SAVED` |

Enrolling by hand counts. The trigger is *target enrollment created*, not *created by automation* — a
child already in EPI who is enrolled into IMCI at the counter pulls the EPI data across exactly as an
automatic enrollment would, and the enrollment form opens with those values already filled.

---

## 3. Choosing the right event

For data elements and event dates on a repeatable stage, you must say *which* event.

| Setting | Use when |
|---|---|
| **Latest with a value** (default) | Normal case. Skips a newer, half-filled event that would otherwise carry a blank forward |
| Latest | You want the newest event even if blank |
| First / First with a value | The earliest record is the true one (e.g. date first administered) |
| The triggering event | Only what was just saved |
| Nth by date | Fixed position on a repeatable stage |

**Prefer a condition over a position.** "Only consider events where…" lets you say *the visit where
BCG was marked given* rather than *the second visit*, which survives messy data entry.

Use **is true** rather than *equals `true`* for yes/no fields — DHIS2 stores booleans in more than one
encoding, and a strict text comparison can match nothing and fail silently.

---

## 4. Coded fields, when the programs disagree

The common case, and the one worth reading carefully. Say three programs record HIV status:

| Program | How it stores the result |
|---|---|
| A | option set: `P`, `N`, `U` |
| B | a plain yes/no field |
| C | option set: `POS`, `NEG` — no code for unknown |

All three are mappable. Here is how.

### Step 1 — Canonical codes are the shared vocabulary

Canonical type **OPTION**, codes `POSITIVE, NEGATIVE, UNKNOWN`. Use the **fullest** vocabulary of any
program — it is the interchange language, not a compromise. Narrowing it now would throw away detail
that program A genuinely records.

### Step 2 — The read map: what this program's codes mean

Each binding gets an **option code mapping** — its own code on the left, canonical on the right:

| Program | Read map |
|---|---|
| A | `P → POSITIVE`, `N → NEGATIVE`, `U → UNKNOWN` |
| B | `true → POSITIVE`, `false → NEGATIVE` |
| C | `POS → POSITIVE`, `NEG → NEGATIVE` |

**A yes/no field maps like any other.** Its vocabulary is exactly two codes, `true` and `false`, and
the editor offers them as a dropdown. This is what lets a boolean join an option concept.

An unmapped code **fails rather than transfers** — deliberately, because `1` meaning *Male* in one
program and *Positive* in another is exactly how silent corruption happens.

### Step 3 — Only where the two disagree

**Most bindings need only the table above.** Writing is simply reading in reverse, so a second table
appears only when reversing cannot answer the question — and when it isn't needed the app says so and
gets out of the way.

There are exactly two cases where it cannot:

1. **The concept has a value this programme has no code for** — B and C have nowhere to put
   `UNKNOWN`.
2. **Two of this programme's codes mean the same thing** — reversing is then ambiguous, and you have
   to say which one to write.

Case 1 applies to B and C. Nothing is wrong with the config — the gap is per *value*, and would
surface weeks later the first time an unknown result came through. So the app asks:

> This programme has no code for **unknown**. Say what should happen when the source reads that value
> — record it as another code, record nothing, or remove whatever the field holds.

and the transfer-level check warns:

> Concept 'hiv' binding for Program C has no code for UNKNOWN; values of that code will fail rather
> than transfer into HIV result. Use the write map to record them as another code

You answer per value. For B and C that is one decision each:

| Program | Write map |
|---|---|
| B | `POSITIVE → true`, `NEGATIVE → false`, `UNKNOWN → false` |
| C | `POSITIVE → POS`, `NEGATIVE → NEG`, `UNKNOWN → NEG` |

Three canonical codes onto two. That is allowed, and the editor says plainly that
`NEGATIVE + UNKNOWN both recorded as NEG — detail is lost here`.

**The engine will never infer that collapse.** Two distinct clinical findings becoming one is not a
decision a program may take on your behalf, so it has to be typed out — the same bargain as a
derivation. Leave the write map empty and a many-to-one read map makes the binding source-only.

### Per value: record it as something else, record nothing, or report it

Recording *unknown* as *negative* asserts something that was never established. Often the honest
answer is to write nothing at all — and that is a decision **per value**, not one setting for the
whole binding.

So the right-hand side of the write map offers three kinds of answer:

| Choice | What happens when the source reads that value |
|---|---|
| a code (`NEG`) | written as that code |
| *record nothing, leave the field as it is* | nothing is written; the field keeps whatever it had |
| *remove whatever the field holds* | the target's value is deleted |
| *report that it could not be carried* | nothing is written, and the log says so |

So you can mix them freely:

```json
"writeOptions": {
  "POSITIVE": "POS",
  "NEGATIVE": "NEG",
  "UNKNOWN":  "@BLANK"
}
```

*Positive* and *negative* land as codes; *unknown* leaves the field untouched, because this program
has no way to say it. The editor shows `UNKNOWN recorded as nothing — the field is left as it was`.

**"Record nothing" and "remove" are different.** The first leaves whatever is there; the second
deletes it. Use *record nothing* when this program simply has no way to say it, and *remove* when the
absence of a value is itself the answer — see below.

For canonical codes you do not list at all, the binding-wide **"when a value has no code here"**
setting applies (report by default, or leave blank). Use the write map for decisions you want to be
explicit about, and that setting as the catch-all.

These outcomes only make sense in the write direction. Putting one on the *read* map is rejected —
reading turns a program's code into a canonical value, and every canonical value is a real finding:

> read map sends 'U' to @BLANK, which is an outcome rather than a canonical code; outcomes belong on
> the write map

### Yes-only fields, and why removing matters

A DHIS2 **TRUE_ONLY** field holds a tick or nothing at all. There is no `false`. So if a yes/no field
in one program feeds a yes-only field in another, absence *is* the negative — and the only way to
record "no" is to take the tick away.

The engine does this for you: a `BOOLEAN` concept written into a TRUE_ONLY field writes the tick on
true and **removes it on false**. Nothing to configure.

It matters because without it a box ticked from a source that later reads false would stay ticked for
ever — the mapping could not correct data it had itself written.

**One setting you must get right.** Removing a value only matters when the field holds one, and the
default conflict policy *Skip if present* never touches a field that holds one. Together they do
nothing at all, so the app warns:

> …removes the value in HIV positive (tick) when the source says no, but its conflict policy is Skip
> if present, which never touches a target that holds a value — so the field would stay set. Use
> Overwrite on this binding for the removal to take effect

Set that binding's conflict policy to **Overwrite**.

Removal is never easier than an ordinary write: a value someone edited by hand is still never touched,
and a policy that would not overwrite will not remove either.

### Two of your codes meaning the same thing

If a read map sends two codes to one canonical value (`U → NEGATIVE` as well as `N → NEGATIVE`), the
reverse is ambiguous and the binding becomes source-only — unless you declare a write map saying
which one to use.

---

## 5. When the two programs store the fact differently

Common in practice: one program records **"BCG given?"** as a yes/no on a visit, another records
**"BCG date given"** as a date.

These are *not* interchangeable. A date implies it happened; a tick does not tell you when. So:

- **Canonical = `DATE`** (the more precise form).
- The program with the **date field** binds to it normally.
- The program with the **yes/no** binds in one of two ways:
  - as a **source**: use *event date* as the kind, with the condition "flag is true" — meaning "the
    date of the visit where it was marked given";
  - as a **target**: set **Derivation = "Yes/no from a date"**, which writes the tick whenever a date
    exists.

A derived binding is **write-only** by default, and the app will refuse to read it. That is the point:
deriving in reverse would invent a date that was never recorded.

> Today a binding has a single field, so covering both directions for such a pair needs **two
> concepts** — one per direction. Slightly verbose; a known limitation.

---

## 6. Settings

| Setting | Meaning |
|---|---|
| **Default conflict policy** | What to do when the target already has a value |
| **Target event policy** | `REQUIRE_EXISTING` skips when the target stage has no event; `CREATE_IF_MISSING` creates one |
| **Fail fast** | Stop after the first failure instead of continuing |

Conflict policies:

- **Skip if present** (default, safest) — never touches an existing value
- **Overwrite** — replaces it
- **Overwrite if source is newer** — only if the source changed since *our own* last write. **On a
  target that already has a value and was never written by mapping, this does nothing** — there is no
  earlier write to compare against. Use *Overwrite* if you want it to win.
- **Fail on conflict** — writes nothing and reports

It is set in two places, most specific first:

1. **The target binding's own policy** — applies whenever that binding receives a value.
2. **The global default in Settings** — everything else.

Two rules apply under **every** policy: a blank never overwrites a real value, and a value a human
edited by hand is never silently reverted.

---

## 7. Checking it worked

### In the config app, before you save

The **Preflight** panel at the top of the page runs the same checks the device runs, against the same
metadata. If it says *"Every mapping checks out"*, the config will compile on the handset.

- An **error** means that mapping will not run at all. It is dropped, and nothing transfers.
- A **warning** means it runs, but probably not the way you intended.

Errors are also shown on the card that can fix them, so a collapsed binding with a red edge and a
"1 error" badge tells you where to look without expanding all of them.

Saving is **not blocked** by errors — the browser's metadata can lag the handset's, and a false alarm
that stopped you working would be worse than a missed warning. But anything reported as an error here
will be refused on the device too.

### On the device

The engine is the authority, and it re-checks everything. Its verdicts go to the **device log**.

```
adb logcat | grep -i "Data mapping"
```

On startup you get:

```
Data mapping config loaded: 2 concept(s), 1 transfer(s), 1 mapping(s), 0 error(s), 0 warning(s)
```

- **`0 mapping(s)` with errors** → your config was rejected. The next line says exactly why.
- **`N mapping(s)`, `0 error(s)`** → compiled and live.

After a save:

```
Data mapping after saving in <program>: 1 applied, 0 skipped, 0 failed
```

### Common messages

| Message | Meaning |
|---|---|
| `X is DATE; cannot store a BOOLEAN concept` | Value types incompatible — see §5 |
| `no enrollment in target program` | The TEI is not enrolled in the target |
| `stage X has no event and targetEventPolicy is REQUIRE_EXISTING` | No target event; switch to `CREATE_IF_MISSING` |
| `Option code 'X' has no mapping` | Add it to the read map (§4) |
| `option map is many-to-one … Declare writeOptions` | Two codes read as one value; add a write map (§4) |
| `has no code for UNKNOWN` | The target cannot store that value; give it a code or an outcome in the write map (§4) |
| `is deliberately not carried into this programme` | That value is mapped to *report it* — working as configured (§4) |
| `the field would stay set` | A removal that cannot fire; set that binding's policy to Overwrite (§4) |
| `was not ours to remove` | The value was edited by hand, so it was left alone (§4) |
| `cannot convert unit 'X' to 'Y'` | Unregistered unit — pick one from the list |
| `binding is derived … and cannot be read as a source` | You are trying to read a derived (write-only) binding |
| `maps attribute X onto itself` | Both programs share one attribute; the value is already shared |
| `has a binding for … that no transfer references` | The binding will never transfer — add a transfer |
| `0 applied, 0 skipped, 0 failed` | No transfer matched — usually you saved in the *target* program, not the source |

---

## 8. If something is not transferring

1. **Does Preflight show errors?** Start there — it names the field and the reason.
2. **Did you sync metadata after saving the config?** Most common cause.
3. **Did you save in the *source* program?** A save in the target matches no transfer.
4. **Is the TEI enrolled in both programs?**
5. **Does the target event exist?** See `targetEventPolicy`.
6. **Check the log** — if the mapping was rejected, the reason is there verbatim.

---

## 9. Known limitations

- **Enrollments created from a relationship do not trigger mapping.** Promoting a member to a new
  household creates an enrollment for a *different* tracked entity, and mapping only ever moves values
  within one.
- **`TARGET_ENROLLMENT_FORM_OPEN` is not wired.** Selecting it warns and does nothing.
- **Mapping is within one TEI.** Values cannot be carried across a relationship to a different
  tracked entity.
- **One field per program per concept**, so a two-way date/flag pair needs two concepts (§5).
- **Only attributes and data elements can be written to.** Event dates, enrollment properties and
  constants can be read but never targeted; the config app no longer offers them as a target.
