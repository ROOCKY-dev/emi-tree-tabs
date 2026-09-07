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

## 4. Automated, in two tiers — as of 2026-09-07

**A `v*` tag does not reach players.** Development moves faster than anyone wants releases, so the
stores are opt in and every ordinary tag stays internal.

| You push | What happens |
|---|---|
| `v2.3.0` | Builds both jars, opens a **draft GitHub release**. Nothing else. |
| `store-v2.3.0` | Builds that same tag and publishes it to **Modrinth and CurseForge**. |

```bash
# every version — cheap, private, no noise
git tag -a v2.3.0 -m "EMI Tree Tabs 2.3.0" && git push origin v2.3.0

# write the notes on the GitHub release, then publish it
gh release edit v2.3.0 --notes-file notes.md --draft=false

# only the ones players should actually get
git tag store-v2.3.0 && git push origin store-v2.3.0
```

Rehearse from the **Actions tab → Publish to stores → Run workflow**, with `dry_run` ticked (it
defaults to ticked). That validates the credentials, the jars and every field against both stores
without publishing.

### Why it is built this way

- **Two tiers, because releasing is not the same as building.** Pushing every tag to the stores
  would spam followers and bury the versions that matter.
- **One version per loader**, `X.Y.Z+forge` and `X.Y.Z+fabric`. Modrinth's download button serves
  only the *primary* file, so a single version carrying both jars hands Fabric users the Forge jar.
- **Nothing about loaders or Minecraft versions is written in the workflow.** Both are read out of
  the tag being published, so a port needs no workflow edit:
  - **loaders** — the modules in `settings.gradle` besides `common`. Today `forge` + `fabric`; after
    the 1.21.1 port that branch will say `neoforge` + `fabric` and publishing follows automatically.
  - **Minecraft versions** — `publish_game_versions` in `gradle.properties`. Comma separated, so a
    build that genuinely covers several (`1.21.1,1.21.2`) says so in one place.

  It is a separate property rather than `minecraft_version_range`, because the range says
  `[1.20.1,1.21)` and auto-detecting from it claims 1.20.1 through 1.20.6 — which is what Modrinth
  guessed, and it is wrong. EMI publishes no Forge build past 1.20.2, so those extra versions
  advertise installs that cannot work. Widen the list only when a build has actually been run on
  each version in it.
- **EMI declared as a required dependency** on both stores.
- **Release type `release`, not beta.** 2.0.1 was marked beta by hand, so launchers set to
  "release only" skipped it.
- **Changelog read back from the GitHub release** for that tag. One source of truth, already
  reviewed by the time you decide to push to stores. A file in the repo cannot serve, because the
  publish job checks out the *release tag* — anything added to `main` afterwards is not there.

### Credentials

Repository secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`; local copies in
`~/.config/ett-publish/*.token`, mode 600.

The Modrinth token is scoped to **read projects** and **create / read / write versions**, nothing
else — no account data, no deletes, no payouts. CurseForge's upload token has no scope selector.

Rotate at [modrinth.com/settings/pats](https://modrinth.com/settings/pats) or CurseForge → account →
API Tokens, then `gh secret set MODRINTH_TOKEN < newfile`.

## Appendix: doing it by hand



Neither platform needs the browser. Both have upload APIs, and
[`Kir-Antipov/mc-publish`](https://github.com/Kir-Antipov/mc-publish) drives both from the existing
tag workflow. It would need two repository secrets — a Modrinth PAT scoped to *create versions*, and
a CurseForge API token — after which `release.yml` publishes everywhere from one `git push --tags`.

Deliberately not done yet: it puts credentials that can publish under this name into GitHub, and
the loader/version/release-type mapping should be verified by hand once more first. Tokens are the
user's to create; do not generate or store them without being asked.
