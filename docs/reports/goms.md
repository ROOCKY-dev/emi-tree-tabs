# GOMS workflow model

See [scope](README.md) and [HTA task hierarchy](hta.md). This is a qualitative GOMS model with
symbolic action sequences. It is not a calibrated Keystroke-Level Model, a timing experiment or a
prediction for beginners learning the interface. No completion-time savings are claimed.

## Model and assumptions

**Goals:** preserve build plans, organize stages, set quantities, understand shortages, reconcile
routes and recover mistakes. **Operators:** M = decide/recall; V = locate/read/verify; P = point;
C = click; D = drag/drop (press, move, release); T = type text; K = a key or explicitly named chord;
W = wait for an observable result. A chord is convenient notation, not one physical keystroke.
Pointer travel, homing, system latency, text length and correction effort remain unmeasured.

**Methods** are alternative sequences achieving the same goal. **Selection rules** specify when to
choose them. The tables omit shared navigation only when the start state is explicitly given.
They compare normal successful paths, not failed actions or discovery time.

## Goals, methods and selection rules

| Goal and start state | Existing method | Selection rule | Interpretation |
|---|---|---|---|
| Configure Crafting; EMI settings open | V/P/C Config Presets → V/P/C EMI Tree Tabs → P/C Done → P/C Done → V result | Use preset when replacing layout is acceptable; otherwise configure a page manually | Four clicks on the documented successful route, excluding search, scrolling and page switching. The challenge is selecting the correct preset and understanding replacement |
| Add a goal and craft it; recipe tree button located | M → P/C open → V/P/C craft marker → V result | Use when opening normally in view mode | Opening and then enabling craft adds a second pointer target |
| Same goal | M → K Ctrl-down → P/C open → K Ctrl-up → V result | Use if the Ctrl-opening convention is known | Removes the later marker acquisition/click, but adds modifier use and recall; not automatically faster for every player |
| Create a named phase; intended tree already selected, sidebar visible | V/P/C New phase → T name → K Enter → V result | Prefer button for discovery; Ctrl+G for a learned shortcut | Existing immediate naming avoids a separate rename step |
| Move a tree to an existing phase | M/V source and destination → P/D → V membership | Use sidebar drag/drop | Existing task has no equivalent exposed non-drag assignment method. A proposed menu would improve access/recovery, not necessarily click count |
| Set batches; sidebar tab visible | V/P row → V/P/C # → T expression → V preview → K Enter → V totals | Use visible # for recognition; Ctrl-click marker if learned | Arithmetic preview already reduces external calculator use and catches invalid expressions before applying |
| Park a phase; header visible | M/V scope → P/C pause → V state/list | Use pause button; right-click is a learned alternative | Visible action reduces gesture recall; no extra confirmation is needed for every reversible toggle |
| Work on one phase; header visible | M/V scope → P/C Focus → V list | Use only when parking other phases matches intent | Ungrouped crafting tabs still contribute. An inaccurate mental model can defeat an otherwise short method |
| Inspect why copper is needed; material visible | P → V attribution → K Shift-down → V breakdown → K Shift-up | Use Shift for deeper explanation | Efficient for learned use; keyboard-only access and discovery remain to be tested |
| Resolve a recipe conflict; sidebar visible | V/P/C choices → M/V alternatives and affected trees → P/C Use → V totals → P/C Done | Apply only when differences should disappear; Ctrl+Shift+R is the learned entry method | Preserve deliberate review and affected-tree counts; do not reduce decisions by silently syncing |
| Recover a closed tab; tree screen open | M/V → P/C Reopen → V result, or K Ctrl+Shift+T → V result | Use footer when visible; shortcut when learned or footer omitted | Recovery is only successful if the saved goal can rebuild; currently a failed attempt consumes history (R5) |

## Predicted effort and error sources

These are source-supported hypotheses to test, not observed rates or formal accessibility failures.

| ID | Friction | Proposed experiment/change | Acceptance evidence |
|---|---|---|---|
| U1 | Two different “sidebars,” two config screens, and page icons versus pagination create selection decisions | Explain Tree Tabs layout versus EMI material pages; identify the named preset and its replacement scope near setup guidance | New player sets up the intended panel, switches list type and reverts an unwanted preset without coaching; record wrong turns |
| U2 | Crafting/viewing, parked/folded and Focus have distinct effects; ungrouped goals remain active under Focus | Add concise state/scope explanations; test empty-list interpretation | Player predicts which trees contribute before toggling, including mixed flags, parked phases and ungrouped trees |
| U3 | Assignment relies on dragging; narrow panels omit actions, and custom-drawn buttons are not native focusable widgets | Provide a non-drag “Move to phase” method and keyboard focus/activation for essential actions; retain drag and shortcuts | Complete create/name/assign/park/unpark without dragging; repeat with shortcuts disabled and document any dependency |
| U4 | Hover # and Enter commit are easy to miss; batch click-away cancels while rename click-away commits | Add explicit Apply/Cancel and clear batch/output semantics while retaining keyboard methods | Apply a multi-output recipe quantity, correct an invalid expression and cancel an edit without accidental changes |
| U5 | Rendered phase order and underlying inventory priority can differ; progress and shared totals answer different questions | Explain priority and pooled availability; test representative two-tree stock examples | Player explains the remaining shortage and correctly predicts the effect of reordering |

## Preserve the approved layout

A sidebar row action is 10 sidebar units. At the approved 0.65 content scale its nominal target is
about 6.5 logical GUI units before rounding and the game's GUI-to-window scale. This calculation
alone proves neither usability nor an accessibility-standard violation. Inspect target acquisition,
focus and readability at GUI scales 2/3/4, left/right placement, narrow windows and long names.

Keep 25%-window width and 65% contents as the default. Test larger non-overlapping hit areas or an
optional comfortable density before changing the user's approved visual size. Avoid adding a
permanent row of buttons simply because it reduces hover: it also spends label width.

## Evaluation protocol

Run the HTA's core chain: **open two goals → create/name a phase → assign the second goal → set an
expression → enable crafting → inspect a shared shortage → park/unpark → close/reopen**.
Run setup and recipe reconciliation as separate tasks so prior setup knowledge does not contaminate
first-use measurements. Repeat learned tasks after a short familiarization, distinguishing first-use
and experienced behavior.

Record task success, unprompted recovery, wrong selections, unintended mode changes, requests for
help and completion time from a defined start to a verified end. Capture actual operator sequences;
do not time only clicks while excluding reading. Compare alternatives on the same scenario and
counterbalance their order where possible. A small formative study can find friction; it cannot
establish population-wide speed or reliability.

Run the functional scenario on all four supported Minecraft/loader combinations. For visual/user
measurements, record resolution, GUI scale, input device, language, tab count and modpack context.
No fresh player measurements or screenshots were collected for this report.

## Complete-task supplement

The main table models frequent paths in detail. This supplement covers the remaining HTA task
families so navigation, configuration, session work and author operations are not omitted. Use the
[complete control/configuration inventory](coverage.md) for every alternative and boundary.

| HTA goal | Method / operator sequence | Selection rule and effort to evaluate |
|---|---|---|
| 7: recover and resume | M classify close versus reload failure → P/C Reopen or K chord → V restored state; for file failure V message/log → restore copied backup → W reload → V | Reopen only for session history; backup recovery for persistent failure. A lost snapshot cannot be fixed by reducing clicks |
| 8: navigate/view/reorder | V/P/C tab or K cycle/jump → V active tree; P/D reorder → V position/membership/priority; EMI pan/zoom → switch → V retained view | Click visible goal, cycle adjacent underlying tabs, numeric jump to known position, scroll/unfold hidden target. Compare wrong-target rate and reacquisition, not just action count |
| 9: rename/fork/close | P/C pencil or K F2 → T → K Enter; K Ctrl+D or P/C strip + → V independent copy; P/C ×, middle-click or K Ctrl+W → V remaining tree | Rename to distinguish, fork to compare, close to discard. Test whether + is mistaken for “new blank plan”; no count advantage justifies losing a goal |
| 10: scoped participation | M choose one/phase/all → V/P/C corresponding marker or K shortcut → V affected flags and effective list | Use the narrowest scope. Mixed-state bulk semantics and parked flags require interpretation; counts must not imply participation they do not measure |
| 11: layout | P/C settings → V/P/C orientation/edge → V preview → P/C save → V actual layout | Auto for adaptation, explicit for stable choice; verify returned screen and hidden-control access. Choose JSON if no native screen exists |
| 12: accounting/presentation | M intended consequence → V/P/C option → P/C save → V totals/presentation; P/C section header → V fold | Distinguish accounting switches from decoration and folding. Do not measure a faster path that changes the meaning of shortages |
| 13: preferences | Native: V category/description → P/C edit → V preview → P/C save/cancel. JSON: back up → T edit → save → W reload → V effect | Native method when available; file method for pack authors/absent YACL. Include editor switching, validation and recovery in effort; do not count only in-game actions |
| 14: stock/location | M source trust → enable stock if desired → V/P material → V source/location → K Shift if breakdown needed → V shortage | Counting stock is opt-in; locating a shortage is a separate provider. No-source, stale-source and failed-provider cases must have understandable results |
| 15: lifecycle/migration | V record known plan → close/reload/disconnect/restart → W ready → V restored fields; migration: copy backups → W target ready → V recipe validity | Select the transition actually being tested; restart success cannot substitute for reload/disconnect checks. Count restoration failures separately from normal completion time |
| 16: defaults/diagnosis | M default versus per-tree change → EMI route edit → V all affected trees; diagnosis: V versions/logs → isolate copied config → W reload → V fallback | Use defaults for shared defaults, task 6 for explicit disagreements. Preserve the plan while diagnosing; avoid repeated blind setting toggles |
| 17: author integration | V API contract/version → T registration/callback → build/run → V callback trace and totals → unregister/test failure → V isolation | Choose stock/locate/listener independently, never rely on internal EMI APIs from the consumer. This is a developer method, not a player click model; runtime evidence matters more than keystroke count |

For each of the 19 configuration keys, apply the task-11/12/13 method to its specific consequence
and reversal described in the coverage table. For every C/L inventory row, record actual selection,
execution and verification during testing. This closes analysis coverage, not empirical coverage.
