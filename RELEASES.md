# Supported-version release policy

Every shared change must be implemented and tested on **both Minecraft 1.20.1 and 1.21.1**.
Both lines use the same mod version. A port does not retire the older Minecraft line.

The supported branch, loader and Java matrix is `.github/release-lines.json`. CI builds both lines
on each push or pull request, using the proposed commit for its own line and the supported branch
for the other. Passing that matrix does not backport changes automatically: update both branches.

## Release both lines together

1. Backport shared behavior to both branches; keep Minecraft/loader API differences local.
2. Set the same `mod_version` on both branches. Test both loader pairs in game after relevant changes.
3. Add notes at `docs/releases/VERSION+1.20.1.md` and `docs/releases/VERSION+1.21.1.md`.
4. Push both branches, then rehearse and publish:

```bash
python3 tools/release_all.py X.Y.Z
python3 tools/release_all.py X.Y.Z --publish
```

The command refuses mismatched versions and code changes after an existing release tag. It stages
both GitHub releases and waits for both release builds and both store rehearsals before creating
any missing production store tag. A failed gate stops the release. Already successful store tags
are verified and reused so retries do not duplicate uploads.

Source tags are `vX.Y.Z+1.20.1` and `vX.Y.Z+1.21.1`; store tags add `store-`. Existing legacy tags
such as the original `v5.0.0` for 1.21.1 remain valid and are never moved or overwritten. GitHub
releases are separate so identically named Fabric jars cannot overwrite each other. Store version
numbers include Minecraft and loader, e.g. `5.0.0+1.20.1-forge`.

A Minecraft-specific fix may differ internally, but each normal release still covers both supported
lines. Any intentional exception needs the owner's explicit agreement and a recorded reason.
Store moderation is separate from a successful upload; verify visibility before announcing it.
