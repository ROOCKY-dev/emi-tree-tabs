# Workflow and reliability review — 2026-11-01

Scope: EMI Tree Tabs 5.0.0 on **both** supported lines: Minecraft 1.20.1
(Forge/Fabric, Java 17) and 1.21.1 (NeoForge/Fabric, Java 21). Reviewed source:
`main` at `47a3573` and `mc/1.20.1` at `3a31086`.

This is a source-based task analysis and reliability review, not a fresh visual audit or a
measured usability study. No Minecraft client was exercised for this report. Earlier screenshots
and the owner's reported preset/layout difficulties provide context, not new experimental results.
Source-confirmed findings, predicted UX friction and proposed validation are distinguished below.
No application behavior is changed by these reports.

| Report | Purpose |
|---|---|
| [HTA](hta.md) | Tasks, subtasks, execution plans, exceptions and recovery |
| [GOMS](goms.md) | Goals, operators, methods, selection rules and unnecessary effort |
| [Complete coverage](coverage.md) | Every exposed control, configuration key, lifecycle and API operation |
| [Reliability and delivery](reliability.md) | Source findings, priorities, coverage gaps and acceptance checks |

## Recommended order

The HTA covers all 17 task families, with 40 player-action entries, all 19 configuration keys
and 12 lifecycle/integration boundaries mapped in the coverage inventory. These are analyzed
workflows, not 17 completed playtests.

1. Protect saves and restore valid tabs despite a malformed neighbor (R1–R3).
2. Make tab-limit replacement recoverable; preserve failed reopen attempts (R4–R5).
3. Correct progress invalidation and publish list-change notifications after the completed result
   (R6–R8).
4. Improve discoverability, mouse/keyboard task completion and state explanations (U1–U5).
5. Measure the core workflows in game on all four Minecraft/loader combinations before selecting
   further features or changing the approved layout defaults.

These are proposed work packages, not shipped fixes. Keep the attached panel at 25% of window
width and its contents at 65% GUI scale unless a new playtest justifies another choice. Preserve
EMI's solver, the public API signature, existing save compatibility and optional YACL isolation.
26.x remains deferred until a compatible EMI build exists; it does not replace either supported line.

## Evidence and limits

The reviewed `TreeTabs` implementations differ only in the Minecraft toast enum spelling.
`CraftingFavorites`, `RecipeChoices`, `TreeTabsConfig`, `TabUi` and `SidebarLayout` are identical
between these revisions. `TabCodec`, input widgets and mixins have Minecraft API differences;
the restore and input paths cited here were checked on both branches. Thus the shared findings
apply to both lines, without assuming their jars or Minecraft APIs are interchangeable.

Existing automated tests concentrate on pure geometry, drag mapping, formulas and the API version
gate. They do not establish persistence fault tolerance, correct EMI totals or comfortable in-game
interaction. See the reliability report for this run's verification and the proposed runtime matrix.

The lead agent performed source interpretation, HTA/GOMS modeling, prioritization and report writing.
One Gemini Flash inventory attempt through `agy` produced no inventory because its headless command
permission was denied. No permission bypass was used; inventory was completed locally.

## Verification during this review

- Minecraft 1.21.1: `./gradlew :common:test -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk`
  succeeded; JUnit XML reports **111 tests, 0 failures, 0 errors, 0 skipped**.
- Minecraft 1.20.1: `./gradlew :common:test -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk`
  succeeded; JUnit XML reports **111 tests, 0 failures, 0 errors, 0 skipped**.
- Coordinated-release unit suite: `python3 -m unittest discover -s tools/tests -v` passed
  **4 tests** on each branch. These are mocked release-gate tests, not live uploads.
- All relative report links were resolved against local files; `git diff --check` passed.
- No fresh in-game, accessibility, performance, fault-injection or user-timing claims are made.
