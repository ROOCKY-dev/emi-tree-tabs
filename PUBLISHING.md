# Publishing a release

The GitHub Actions workflows build releases and upload to Modrinth and CurseForge. Store publishing
is opt-in: a normal release tag creates a draft on GitHub, while a separate store tag publishes it.

## Release lines

| Branch | Minecraft | Loaders | Mod versions | JDK |
|---|---|---|---|---|
| `main` | 1.21.1 | NeoForge, Fabric | 5.x | 21 |
| `mc/1.20.1` | 1.20.1 | Forge, Fabric | 4.x | 17 |

Project IDs: Modrinth `si3AK8q5`, CurseForge `1677804`.

## Verify and stage

Run from a clean checkout of the branch being released. Finish the release checks in `ROADMAP.md`,
update `mod_version` in `gradle.properties`, and commit and push the changes.

```bash
./gradlew clean build -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk
git tag -a vX.Y.Z -m "EMI Tree Tabs X.Y.Z"
git push origin vX.Y.Z
```

Use JDK 17 for the maintenance branch. `release.yml` builds both loader jars and opens a draft
GitHub release. Supply player-facing notes and publish that draft:

```bash
gh release edit vX.Y.Z --notes-file /path/to/notes.md --draft=false
```

Jars on the 1.21.1 line are `neoforge/build/libs/emitreetabs-neoforge-X.Y.Z.jar` and
`fabric/build/libs/emitreetabs-fabric-X.Y.Z.jar`. Never upload `-sources` or `-dev-shadow` jars.

## Rehearse and publish to stores

Run **Publish to stores** in Actions with version `X.Y.Z` and `dry_run=true`. The rehearsal builds
the release tag, checks credentials against both stores and prints the exact upload labels.
It does not call `mc-publish`: that action's own dry-run previously uploaded files anyway.

When the rehearsal passes:

```bash
git tag store-vX.Y.Z vX.Y.Z
git push origin store-vX.Y.Z
```

The store workflow checks out `vX.Y.Z`, reads the loaders from `settings.gradle`, the Java version
from `java_version`, and Minecraft versions from `publish_game_versions`. Do not infer supported
Minecraft versions from a dependency range. Verify both loader jobs and the resulting store files.
CurseForge may queue uploads for moderation before they become public.

## Upload conventions

- One version per loader and Minecraft line, e.g. `5.0.0+1.21.1-neoforge` and
  `5.0.0+1.21.1-fabric`. Modrinth serves only the primary file of a version; combining loader jars
  in one version can hand a Fabric player the NeoForge jar.
- Release type is `release`. Each jar is tagged with only its own loader and tested Minecraft
  version. The required EMI dependency is declared on both stores; YACL remains optional.
- Notes come from the corresponding GitHub release. Check they are present before the store tag.
- Store version numbers before the 1.21.1 port used `X.Y.Z+loader`.

## Credentials

Repository secrets are `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`. Local copies, when needed, live in
`~/.config/ett-publish/*.token` with mode 600. Never print, commit or put them in release notes.
The Modrinth token is scoped to reading projects and creating/reading/writing versions.
CurseForge upload tokens have no scope selector.

Rotate tokens through the stores' own account settings, then update the corresponding repository
secret with `gh secret set NAME < /path/to/token`.
