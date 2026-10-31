#!/usr/bin/env python3
"""Stage, rehearse and optionally publish every supported Minecraft line together."""

import argparse
import json
import re
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def command(*args, check=True):
    result = subprocess.run(args, cwd=ROOT, text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError(f"{' '.join(args)} failed:\n{result.stderr or result.stdout}")
    return result


def properties(ref):
    text = command('git', 'show', f'{ref}:gradle.properties').stdout
    return dict(line.split('=', 1) for line in text.splitlines()
                if '=' in line and not line.startswith('#'))


def has_tag(tag):
    return command('git', 'rev-parse', '--verify', '--quiet', f'refs/tags/{tag}', check=False).returncode == 0


def remote_has_tag(tag):
    return command('git', 'ls-remote', '--exit-code', '--tags', 'origin',
                   f'refs/tags/{tag}', check=False).returncode == 0


def verify_tagged_code(tag, ref):
    paths = ['common/src', 'fabric/src', 'forge/src', 'neoforge/src', 'art',
             'build.gradle', 'common/build.gradle', 'fabric/build.gradle',
             'forge/build.gradle', 'neoforge/build.gradle', 'gradle.properties',
             'settings.gradle', 'gradle']
    if command('git', 'diff', '--quiet', tag, ref, '--', *paths, check=False).returncode:
        raise RuntimeError(f'{ref} has code changes after {tag}; bump the shared version before releasing')


def select_tag(version, minecraft):
    # Preserve releases made before tags included Minecraft, including the original 5.0.0 port.
    legacy = f'v{version}'
    if has_tag(legacy) and properties(legacy)['minecraft_version'] == minecraft:
        return legacy
    return f'v{version}+{minecraft}'


def wait_for_run(workflow, ref, after=0, title=None):
    deadline = time.monotonic() + 1800
    last_status = None
    while time.monotonic() < deadline:
        runs = json.loads(command('gh', 'run', 'list', '--workflow', workflow, '--branch', ref,
                                  '--limit', '20', '--json', 'databaseId,status,conclusion,url,displayTitle').stdout)
        matches = [run for run in runs if run['databaseId'] > after
                   and (title is None or run['displayTitle'] == title)]
        if matches:
            run = max(matches, key=lambda item: item['databaseId'])
            if run['status'] != last_status:
                print(f"{workflow} {ref}: {run['status']} {run['url']}", flush=True)
                last_status = run['status']
            if run['status'] == 'completed':
                if run['conclusion'] != 'success':
                    raise RuntimeError(f"Release gate failed: {run['url']} ({run['conclusion']})")
                return
        time.sleep(5)
    raise RuntimeError(f'Timed out waiting for {workflow} on {ref}; no further publishing attempted')


def rehearse(tag):
    runs = json.loads(command('gh', 'run', 'list', '--workflow', 'publish-stores.yml',
                              '--limit', '1', '--json', 'databaseId').stdout)
    after = runs[0]['databaseId'] if runs else 0
    identifier = tag.removeprefix('v')
    command('gh', 'workflow', 'run', 'publish-stores.yml', '--ref', 'main',
            '-f', f'version={identifier}', '-f', 'dry_run=true')
    wait_for_run('publish-stores.yml', 'main', after, f'Publish {identifier} (rehearsal)')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', help='Shared mod version, e.g. 5.0.1')
    parser.add_argument('--publish', action='store_true', help='Publish only after every line passes rehearsal')
    parser.add_argument('--notes-dir', type=Path, default=ROOT / 'docs/releases')
    args = parser.parse_args()
    if not re.fullmatch(r'\d+\.\d+\.\d+', args.version):
        parser.error('version must be X.Y.Z')
    command('git', 'fetch', 'origin', '--tags')
    lines = json.loads((ROOT / '.github/release-lines.json').read_text())
    new_tags = []
    # Check every line before creating any tag. A forgotten backport blocks the whole release.
    for line in lines:
        ref = 'origin/' + line['branch']
        props = properties(ref)
        if props['mod_version'] != args.version or props['minecraft_version'] != line['minecraft']:
            raise RuntimeError(f"{ref} must contain version {args.version} for Minecraft {line['minecraft']}")
        line['tag'] = select_tag(args.version, line['minecraft'])
        line['notes'] = args.notes_dir / f"{args.version}+{line['minecraft']}.md"
        if not line['notes'].is_file() or not line['notes'].read_text().strip():
            raise RuntimeError(f"Release notes required: {line['notes']}")
        if has_tag(line['tag']):
            tagged = properties(line['tag'])
            if tagged['mod_version'] != args.version or tagged['minecraft_version'] != line['minecraft']:
                raise RuntimeError(f"Existing tag targets a different release: {line['tag']}")
            verify_tagged_code(line['tag'], ref)
        if not remote_has_tag(line['tag']):
            new_tags.append((line['tag'], ref))
        print(f"Minecraft {line['minecraft']}: {ref} -> {line['tag']}", flush=True)
    for tag, ref in new_tags:
        if not has_tag(tag):
            command('git', 'tag', '-a', tag, ref, '-m', f'EMI Tree Tabs {tag}')
    if new_tags:
        command('git', 'push', '--atomic', 'origin', *(f'refs/tags/{tag}' for tag, ref in new_tags))
    for line in lines:
        wait_for_run('release.yml', line['tag'])
        command('gh', 'release', 'edit', line['tag'], '--notes-file', str(line['notes']))
        rehearse(line['tag'])
    if not args.publish:
        print('All Minecraft lines passed rehearsal. Use --publish to upload all missing store releases.')
        return
    for line in lines:
        command('gh', 'release', 'edit', line['tag'], '--draft=false',
                '--latest=true' if line['branch'] == 'main' else '--latest=false',
                '--title', f"EMI Tree Tabs {args.version} (Minecraft {line['minecraft']})")
    new_store_tags = []
    for line in lines:
        store_tag = 'store-' + line['tag']
        if has_tag(store_tag):
            actual = command('git', 'rev-parse', f'{store_tag}^{{}}').stdout
            expected = command('git', 'rev-parse', f"{line['tag']}^{{}}").stdout
            if actual != expected:
                raise RuntimeError(f'Existing store tag targets different code: {store_tag}')
        else:
            command('git', 'tag', store_tag, f"{line['tag']}^{{}}")
        if not remote_has_tag(store_tag):
            new_store_tags.append(store_tag)
    if new_store_tags:
        command('git', 'push', '--atomic', 'origin', *(f'refs/tags/{tag}' for tag in new_store_tags))
    for line in lines:
        wait_for_run('publish-stores.yml', 'store-' + line['tag'])
    print('Both Minecraft lines uploaded successfully. Check store processing/moderation before announcing availability.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, KeyboardInterrupt) as error:
        raise SystemExit(str(error))
