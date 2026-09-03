# Publishing a release

GitHub is automated. Modrinth and CurseForge are not, yet — this file is the checklist for doing
them by hand, and the plan for automating them.

## Coordinates

| | |
|---|---|
| Modrinth project | `si3AK8q5` — https://modrinth.com/mod/ett-emi-tree-taps |
| CurseForge project | `1677804` — https://www.curseforge.com/minecraft/mc-mods/ett-emi-tree-tabs |
| CurseForge author area | https://legacy.curseforge.com/ → **Dashboard** → **Projects** → **Edit** → **Files** |

> The Modrinth slug is spelled **`ett-emi-tree-taps`** — "taps", not "tabs". Worth correcting in
> project settings while the download count is still low; it changes the public URL.

## 1. GitHub (automated)

```bash
./gradlew clean build -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk
git tag -a vX.Y.Z -m "EMI Tree Tabs X.Y.Z" && git push origin vX.Y.Z
```

`release.yml` builds both jars and opens a **draft** release. Fill in the notes, then
`gh release edit vX.Y.Z --draft=false`. Nothing is public until that command runs.

## 2. Modrinth (manual)

**Versions → Create version.** A three-step wizard: **Files → Metadata → Details**.

Step 1 is a hard gate: Metadata and Details are inert until a file is attached, so there is no way
to fill the form out first and add the jar last.

Attach **both** `emitreetabs-forge-X.Y.Z.jar` and `emitreetabs-fabric-X.Y.Z.jar` — Modrinth takes
several files per version, so one version covers both loaders. Loaders: Forge **and** Fabric. Game
versions: 1.20.1. Release channel: **Release** for a stable build.

## 3. CurseForge (manual)

**Files → Add File.** One file per upload, so Forge and Fabric are two separate uploads.

The form is: Display name · Environment\* · Modloader\* · Java · Minecraft\* · Release Type ·
Changelog (WYSIWYG or markdown).

Get these right — the 2.0.1 upload got all three wrong:

- **Modloader** — the Forge jar is tagged `NeoForge, Forge`. NeoForge does not exist for 1.20.1, so
  that tag matches nobody. Tag the Forge jar `Forge` and the Fabric jar `Fabric`, nothing else.
- **Release Type** — 2.0.1 is marked **Beta**. Launchers set to "release only" will not offer it.
  Use **Release** unless the build really is a beta.
- **Changelog** — 2.0.1 still reads `## Initial release`. Paste the GitHub release notes.
- **Java** is 17 on 1.20.1, and 21 after the 1.21.1 port.

## 4. Automating both

Neither platform needs the browser. Both have upload APIs, and
[`Kir-Antipov/mc-publish`](https://github.com/Kir-Antipov/mc-publish) drives both from the existing
tag workflow. It would need two repository secrets — a Modrinth PAT scoped to *create versions*, and
a CurseForge API token — after which `release.yml` publishes everywhere from one `git push --tags`.

Deliberately not done yet: it puts credentials that can publish under this name into GitHub, and
the loader/version/release-type mapping should be verified by hand once more first. Tokens are the
user's to create; do not generate or store them without being asked.
