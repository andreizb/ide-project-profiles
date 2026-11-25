"""Exercise bootstrap transports with a synthetic archive; never run manage.py from the project."""
import io
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

INSTALLER = Path(__file__).resolve().parents[1] / 'install.sh'
SHA = '1234567890abcdef1234567890abcdef12345678'
MOCK = r'''#!/usr/bin/env python3
import os, pathlib, sys
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
base = pathlib.Path(os.environ['MOCK_FIXTURE'])
if name == 'mktemp':
    assert args == ['-d', '/private/tmp/ipp-install.XXXXXXXX'], args
    root = base / 'installer-work'
    root.mkdir()
    print(root)
    sys.exit(0)
if name == 'curl':
    assert len(args) == 10 and args[:8] == [
        '--fail', '--silent', '--location', '--proto', '=https',
        '--proto-redir', '=https', '--output'], args
    assert args[9].startswith('https://api.github.com/'), args
    path = args[9].removeprefix('https://api.github.com/')
elif name == 'gh':
    assert args[0] == 'api' and len(args) == 2, args
    path = args[1]
else:
    raise SystemExit('unexpected mock')
with (base / 'requests').open('a') as handle:
    handle.write(f'{name} {path}\n')
if name == 'curl' and os.environ['MOCK_ACCESS'] == 'private':
    sys.exit(22)
prefix = 'repos/andreizb/ide-project-profiles/'
if path.startswith(prefix + 'commits/'):
    assert path == prefix + 'commits/' + os.environ['MOCK_EXPECTED_REF'], path
    reply = ('{"sha":"' + os.environ['MOCK_RETURNED_SHA'] + '"}').encode()
elif path == prefix + 'tarball/' + os.environ['MOCK_RETURNED_SHA']:
    reply = (base / 'bundle.tar.gz').read_bytes()
else:
    raise SystemExit('unexpected endpoint')
if name == 'curl':
    pathlib.Path(args[8]).write_bytes(reply)
else:
    sys.stdout.buffer.write(reply)
'''

FIXTURE = b'''import os, sys
assert os.environ['IDE_PROFILES_SOURCE_COMMIT'] == os.environ['MOCK_RETURNED_SHA']
assert sys.argv[1:] == ['verify', 'fixture argument']
print('synthetic manage.py reached')
'''


class InstallerTransportTests(unittest.TestCase):
    def exercise(self, *, access='public', ref='main', expected_ref='main',
                 returned_sha=SHA, unsafe_archive=False):
        with tempfile.TemporaryDirectory(prefix='ipp-transport-') as folder:
            root = Path(folder)
            for executable in ('gh', 'curl', 'mktemp'):
                path = root / executable
                path.write_text(MOCK)
                path.chmod(0o755)
            with tarfile.open(root / 'bundle.tar.gz', 'w:gz') as bundle:
                entry = tarfile.TarInfo('project-snapshot/manage.py')
                entry.size = len(FIXTURE)
                bundle.addfile(entry, io.BytesIO(FIXTURE))
                if unsafe_archive:
                    entry = tarfile.TarInfo('project-snapshot/../escape')
                    bundle.addfile(entry, io.BytesIO(b''))
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'],
                       MOCK_FIXTURE=str(root), MOCK_ACCESS=access, IDE_PROFILES_REF=ref,
                       MOCK_EXPECTED_REF=expected_ref, MOCK_RETURNED_SHA=returned_sha)
            result = subprocess.run(['/bin/bash', str(INSTALLER), 'verify', 'fixture argument'],
                                    env=env, capture_output=True, text=True, timeout=20)
            self.assertFalse((root / 'installer-work').exists(), result.stderr)
            self.assertFalse((root / 'escape').exists())
            return result, (root / 'requests').read_text().splitlines()

    def test_public_uses_only_anonymous_transport_with_encoded_ref(self):
        result, requests = self.exercise(ref='feature/branch', expected_ref='feature%2Fbranch')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('synthetic manage.py reached', result.stdout)
        self.assertEqual([r.split()[0] for r in requests], ['curl', 'curl'])
        self.assertTrue(requests[1].endswith('/tarball/' + SHA))

    def test_private_falls_back_to_gh_for_both_requests(self):
        result, requests = self.exercise(access='private', ref=SHA, expected_ref=SHA)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('synthetic manage.py reached', result.stdout)
        self.assertEqual([r.split()[0] for r in requests], ['curl', 'gh', 'curl', 'gh'])
        self.assertTrue(requests[-1].endswith('/tarball/' + SHA))

    def test_invalid_sha_fails_without_archive_request(self):
        result, requests = self.exercise(returned_sha='not a commit')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Invalid GitHub commit', result.stderr)
        self.assertEqual(len(requests), 1)
        self.assertNotIn('synthetic manage.py reached', result.stdout)

    def test_full_requested_sha_must_match(self):
        result, requests = self.exercise(ref='a' * 40, expected_ref='a' * 40)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('different commit', result.stderr)
        self.assertEqual(len(requests), 1)

    def test_archive_traversal_stops_before_synthetic_manager(self):
        result, _ = self.exercise(unsafe_archive=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Unsafe source archive member', result.stderr)
        self.assertNotIn('synthetic manage.py reached', result.stdout)


if __name__ == '__main__':
    unittest.main()
