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

## 4. Automated — as of 2026-09-07

Tagging now publishes to **all three** places. `release.yml` builds both jars, opens a **draft**
GitHub release, then uploads to Modrinth and CurseForge via
[`mc-publish`](https://github.com/Kir-Antipov/mc-publish).

```bash
# 1. Write the notes players will read
$EDITOR .github/release-notes.md

# 2. Bump, commit, tag
./gradlew build -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk
git tag -a vX.Y.Z -m "EMI Tree Tabs X.Y.Z" && git push origin vX.Y.Z
```

**A tag now ships to users.** The GitHub release is still a draft for you to review, but the store
uploads are live the moment the workflow passes. To rehearse without publishing, run the workflow
manually from the Actions tab with **dry_run** ticked.

### What it does and why

- **One version per loader**, `X.Y.Z+forge` and `X.Y.Z+fabric`. Modrinth's download button serves
  only the *primary* file, so a single version carrying both jars hands Fabric users the Forge jar.
- **`game-versions: 1.20.1` only**, not the jar's declared `[1.20.1,1.21)`. EMI publishes no Forge
  build past 1.20.2, so a wider range advertises installs that cannot work.
- **EMI declared as a required dependency** on both stores.
- **Release type `release`**, not beta — the 2.0.1 upload was marked beta by hand and launchers set
  to "release only" skipped it.
- **Changelog from `.github/release-notes.md`.** Generated commit lists make poor player-facing
  notes, and the GitHub release is still a draft at that point so it cannot be read back.

### Credentials

Repository secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`. Local copies live in
`~/.config/ett-publish/*.token`, mode 600.

The Modrinth token is scoped to **read projects, create/read/write versions** and nothing else — no
account data, no delete, no payouts. The CurseForge upload token has no scope selector.

To rotate: revoke at
[modrinth.com/settings/pats](https://modrinth.com/settings/pats) /
CurseForge → account → API Tokens, then `gh secret set MODRINTH_TOKEN < newfile`.

## Appendix: doing it by hand



Neither platform needs the browser. Both have upload APIs, and
[`Kir-Antipov/mc-publish`](https://github.com/Kir-Antipov/mc-publish) drives both from the existing
tag workflow. It would need two repository secrets — a Modrinth PAT scoped to *create versions*, and
a CurseForge API token — after which `release.yml` publishes everywhere from one `git push --tags`.

Deliberately not done yet: it puts credentials that can publish under this name into GitHub, and
the loader/version/release-type mapping should be verified by hand once more first. Tokens are the
user's to create; do not generate or store them without being asked.
