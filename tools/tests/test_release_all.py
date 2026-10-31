"""Release gates must stop mistakes before any production tag is pushed."""
import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('release_all', Path(__file__).parents[1] / 'release_all.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseGatesTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        for minecraft in ('1.20.1', '1.21.1'):
            (Path(self.directory.name) / f'5.0.0+{minecraft}.md').write_text('Release notes')
        self.calls = []
        self.props = {
            'origin/mc/1.20.1': {'mod_version': '5.0.0', 'minecraft_version': '1.20.1'},
            'origin/main': {'mod_version': '5.0.0', 'minecraft_version': '1.21.1'},
            'v5.0.0': {'mod_version': '5.0.0', 'minecraft_version': '1.21.1'},
        }
        self.code_changed = False

    def command(self, *args, **kwargs):
        self.calls.append(args)
        status = 1 if args[:3] == ('git', 'diff', '--quiet') and self.code_changed else 0
        return subprocess.CompletedProcess(args, status, stdout='same-commit\n', stderr='')

    def invoke(self, publish=False):
        arguments = ['release_all.py', '5.0.0', '--notes-dir', self.directory.name]
        if publish:
            arguments.append('--publish')
        existing = {'v5.0.0', 'store-v5.0.0'}
        with patch.object(sys, 'argv', arguments), \
             patch.object(release, 'command', side_effect=self.command), \
             patch.object(release, 'properties', side_effect=lambda ref: self.props[ref]), \
             patch.object(release, 'has_tag', side_effect=lambda tag: tag in existing), \
             patch.object(release, 'remote_has_tag', side_effect=lambda tag: tag in existing), \
             patch.object(release, 'wait_for_run') as wait, \
             patch.object(release, 'rehearse') as rehearse:
            release.main()
            return wait, rehearse

    def test_missing_backport_blocks_all_tag_creation(self):
        self.props['origin/mc/1.20.1']['mod_version'] = '4.0.1'
        with self.assertRaisesRegex(RuntimeError, 'must contain version'):
            self.invoke(publish=True)
        self.assertFalse(any(call[:2] in [('git', 'tag'), ('git', 'push')] for call in self.calls))

    def test_code_after_existing_tag_requires_a_new_version(self):
        self.code_changed = True
        with self.assertRaisesRegex(RuntimeError, 'bump the shared version'):
            self.invoke(publish=True)
        self.assertFalse(any(call[:2] == ('git', 'push') for call in self.calls))

    def test_rehearsal_covers_both_lines_without_production_tags(self):
        wait, rehearse = self.invoke()
        self.assertEqual([call.args[0] for call in rehearse.call_args_list],
                         ['v5.0.0+1.20.1', 'v5.0.0'])
        self.assertFalse(any(call[:3] == ('git', 'tag', 'store-v5.0.0+1.20.1') for call in self.calls))
        self.assertFalse(any('--draft=false' in call for call in self.calls))

    def test_publish_adds_only_the_missing_counterpart(self):
        wait, rehearse = self.invoke(publish=True)
        pushes = [call for call in self.calls if call[:3] == ('git', 'push', '--atomic')]
        self.assertEqual(len(pushes), 2)
        self.assertIn('refs/tags/store-v5.0.0+1.20.1', pushes[-1])
        self.assertNotIn('refs/tags/store-v5.0.0', pushes[-1])
        store_waits = [call.args[1] for call in wait.call_args_list if call.args[0] == 'publish-stores.yml']
        self.assertEqual(store_waits, ['store-v5.0.0+1.20.1', 'store-v5.0.0'])


if __name__ == '__main__':
    unittest.main()
