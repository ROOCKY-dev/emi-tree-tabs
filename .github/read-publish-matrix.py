#!/usr/bin/env python3
"""Work out what a store-v* tag publishes: every Minecraft version x loader pair.

This repository ships two Minecraft versions side by side - 1.20.1 on Forge and Fabric, 1.21.1 on
NeoForge and Fabric - and both are published from the same tag. A store takes one version id per
upload, so the Minecraft version is part of that id: 4.0.0+1.20.1-forge and 4.0.0+1.21.1-neoforge,
never two things called 4.0.0+fabric.

What gets published is read out of the modules rather than written down in the workflow, so adding
a Minecraft version or a loader needs no edit here:

  * a module whose own gradle.properties says `publish_loader` is published as that loader, for
    `publish_game_version` when it says one, else its own `minecraft_version`, else the root's;
  * a module that says neither is shared code - common, or the common half of a tree - and is
    skipped. The 1.20.1 modules say neither, so they keep publishing exactly as they always have.

The jar name is the module's archivesName plus the mod version, which the root build script
assembles as `emitreetabs` or `emitreetabs-<minecraft version>` for a module whose Minecraft
version differs from the root's, then `-<module>`. Rather than reimplement that here, the matrix
carries a glob and the publish job resolves it against what the build actually produced - a name
that does not exist is caught by the glob finding nothing, not by this script guessing.

Usage: read-publish-matrix.py <mod-version>   (JSON on stdout, the plan on stderr)
"""

import json
import os
import re
import sys


def props(path):
    """A gradle.properties file as a dict. Comments and blank lines are skipped."""
    out = {}
    if not os.path.exists(path):
        return out
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            key, _, value = line.partition('=')
            out[key.strip()] = value.strip()
    return out


def main():
    if len(sys.argv) != 2 or not re.match(r'^\d+\.\d+\.\d+$', sys.argv[1]):
        print('usage: read-publish-matrix.py <mod-version>', file=sys.stderr)
        return 2
    version = sys.argv[1]

    root = props('gradle.properties')
    built = root.get('mod_version')
    if built != version:
        # The publish job checks this too, but failing here says which file disagrees.
        print('::error::tag says %s but gradle.properties says mod_version=%s' % (version, built),
              file=sys.stderr)
        return 1
    root_mc = root.get('minecraft_version')

    settings = open('settings.gradle', encoding='utf-8').read()
    modules = re.findall(r"include '([^']+)'", settings)
    # A module can live in a subdirectory, and then settings.gradle says where.
    dirs = dict(re.findall(r"project\(':([^']+)'\)\.projectDir = file\('([^']+)'\)", settings))

    targets, skipped = [], []
    for name in modules:
        directory = dirs.get(name, name)
        p = props(os.path.join(directory, 'gradle.properties'))
        loader = p.get('publish_loader')
        if not loader:
            skipped.append(name)
            continue

        # Never minecraft_version_range: the range spans versions EMI has no build for.
        game = p.get('publish_game_version') or p.get('minecraft_version') or root_mc
        if not game:
            print('::error::no Minecraft version for module %s' % name, file=sys.stderr)
            return 1

        # A module on the root's Minecraft version keeps the untagged jar name, because that is
        # what the root build script has always produced for it.
        tag = '' if game == root_mc else '-' + game
        targets.append({
            'module': name,
            'dir': directory,
            'loader': loader,
            'game_version': game,
            'java': p.get('java_version', root.get('java_version', '17')),
            'store_version': '%s+%s-%s' % (version, game, loader),
            'jar': '%s/build/libs/emitreetabs%s-%s-%s.jar' % (directory, tag, name.rpartition('-')[2]
                                                             or name, version),
        })

    if not targets:
        print('::error::no module declares publish_loader; nothing to publish', file=sys.stderr)
        return 1

    seen = {}
    for t in targets:
        clash = seen.get(t['store_version'])
        if clash:
            print('::error::%s and %s both want store version %s'
                  % (clash, t['module'], t['store_version']), file=sys.stderr)
            return 1
        seen[t['store_version']] = t['module']

    print('Publishing %s' % version, file=sys.stderr)
    for t in targets:
        print('  %-28s %-9s Minecraft %-7s java %-3s %s'
              % (t['store_version'], t['loader'], t['game_version'], t['java'], t['jar']),
              file=sys.stderr)
    if skipped:
        print('  not published: %s' % ', '.join(skipped), file=sys.stderr)

    print(json.dumps(targets, separators=(',', ':')))
    return 0


if __name__ == '__main__':
    sys.exit(main())
