# Publishing a release

The GitHub Actions workflows build releases and upload to Modrinth and CurseForge. Store publishing
is opt-in: a normal release tag creates a draft on GitHub, while a separate store tag publishes it.

## Release lines

| Branch | Minecraft | Loaders | Mod versions | JDK |
|---|---|---|---|---|
| `main` | 1.21.1 | NeoForge, Fabric | 5.x | 21 |
| `mc/1.20.1` | 1.20.1 | Forge, Fabric | 5.x (same version as main) | 17 |

Project IDs: Modrinth `si3AK8q5`, CurseForge `1677804`.

## Coordinated releases are required

Read [RELEASES.md](RELEASES.md). Shared changes and releases cover **both** supported Minecraft
lines at the same mod version. CI builds both; passing CI does not copy code between branches.

After updating both branches and writing the two version-specific notes in `docs/releases/`:

```bash
python3 tools/release_all.py X.Y.Z
python3 tools/release_all.py X.Y.Z --publish
```

The first command stages missing source tags and draft GitHub releases, then rehearses both lines.
It does not upload to stores. The second repeats the gates and uploads only missing store releases.
Existing successful releases are verified, not duplicated. A missing backport, mismatched version,
failed build or failed rehearsal stops publication. Rehearsals never invoke `mc-publish` itself.

Tags include Minecraft: `vX.Y.Z+1.20.1`, `vX.Y.Z+1.21.1`, with matching `store-` tags. Separate
GitHub releases prevent the two identically named Fabric jars from overwriting each other.
The original `v5.0.0` / `store-v5.0.0` still identify 1.21.1 and are never overwritten.

Jars are `forge/build/libs/emitreetabs-forge-X.Y.Z.jar` on 1.20.1,
`neoforge/build/libs/emitreetabs-neoforge-X.Y.Z.jar` on 1.21.1, and
`fabric/build/libs/emitreetabs-fabric-X.Y.Z.jar` on each branch. Never upload `-sources` or
`-dev-shadow` jars. The workflows read Minecraft, loaders and Java from the checked-out source.

Verify both loader pairs and public store visibility after uploading. CurseForge may queue
files for moderation, and a withheld Modrinth project is separate from successful version uploads.

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
