# Contributing

Thanks for looking. This is a small client-side mod, so the process is light — but a few things
about it are unusual enough to be worth reading before you start.

## What you need

- **JDK 17** (JDK 21 once the 1.21.1 port lands). ForgeGradle cannot run on newer JDKs.
- Nothing else. The Gradle wrapper is committed.

```bash
./gradlew build
```

The jar lands in `build/libs/`. `./gradlew runClient` starts a development client with EMI already
on the classpath.

If your default JDK is newer than 17, point Gradle at the right one explicitly:

```bash
./gradlew build -Dorg.gradle.java.home=/path/to/jdk17
```

Note that a `gradle.properties` in your Gradle home directory **outranks** the one in this project,
so a global `org.gradle.java.home` there will silently win. Passing it on the command line is the
reliable fix.

## The thing that makes this mod unusual

**It hooks EMI's internals, not a public API.** EMI does not expose one for crafting trees, so the
mixins target `dev.emi.emi.bom.BoM`, `dev.emi.emi.screen.BoMScreen`, `EmiFavorites`, `EmiSidebars`
and `EmiScreenManager$ScreenSpace` directly. One class even adds a constant to EMI's `SidebarType`
enum at runtime so the crafting list can have a real sidebar page.

Two consequences:

- **An EMI update can break this mod**, usually as a visible startup error rather than a silent
  failure. Please state which EMI version you tested against in your pull request.
- **Verify against the real jar, do not guess.** `javap -p -c` on EMI's jar is the fastest way to
  check a field or method still exists and has the signature you expect. Several bugs in this mod's
  history came from assuming an API rather than reading it.

## Testing

There are no automated tests, and there is not much point pretending otherwise: almost everything
here is a GUI that only exists while a world is loaded. **CI proves the mod compiles and packages,
nothing more.** Every change needs to be run.

When you have built a jar:

1. **Quit Minecraft completely** before copying it into `mods/`.
2. Check nothing is still running — replacing a jar under a live game causes
   `ClassNotFoundException` on classes that had not been loaded yet, and the resulting crash looks
   nothing like its cause.
3. Launch, and check `logs/latest.log` for `[emitreetabs]` lines.

If your change touches the sidebar, this line is the signal the runtime enum extension worked:

```
[emitreetabs] added sidebar page type 'tree_tabs_crafting' at ordinal 8
```

## Branches and pull requests

- Branch from `main`, one branch per change: `feat/…`, `fix/…`, `ui/…`, `ci/…`.
- Separate working directories help, because a fresh build directory makes ForgeGradle decompile
  Minecraft again:
  ```bash
  git worktree add ../ett-my-change -b feat/my-change
  ```
- Fill in the pull request template honestly, **including the "did not test in game" box** if that
  is the case. Saying so is far more useful than leaving it ambiguous.

## Style

Match what is already there. In particular: comments explain *why* something is done, especially
where the reason is an EMI or Minecraft quirk that is not obvious from the code. If you worked
something out by reading bytecode, write that down — the next person will not want to do it twice.
