#!/bin/bash
set -euo pipefail
REPOSITORY="andreizb/ide-project-profiles"
WORK="$(mktemp -d /private/tmp/ipp-install.XXXXXXXX)"
cleanup() { rm -rf -- "$WORK"; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p "$WORK/tmp"
export TMPDIR="$WORK/tmp"
command -v python3 >/dev/null || { echo 'Python 3.9+ is required.' >&2; exit 1; }
command -v curl >/dev/null || { echo 'curl is required.' >&2; exit 1; }
REF="${IDE_PROFILES_REF:-main}"
curl --fail --silent --show-error --location "https://api.github.com/repos/$REPOSITORY/commits/$REF" -o "$WORK/commit.json"
SHA="$(python3 - "$WORK/commit.json" <<'PY'
import json,re,sys
sha=json.load(open(sys.argv[1]))['sha']
if not re.fullmatch('[0-9a-f]{40}',sha):raise SystemExit('Invalid GitHub commit')
print(sha)
PY
)"
curl --fail --silent --show-error --location "https://codeload.github.com/$REPOSITORY/tar.gz/$SHA" -o "$WORK/source.tar.gz"
python3 - "$WORK" <<'PY'
from pathlib import Path,PurePosixPath
import sys,tarfile,shutil
work=Path(sys.argv[1]);root=work/'source';root.mkdir()
with tarfile.open(work/'source.tar.gz') as archive:
 for item in archive:
  p=PurePosixPath(item.name)
  if p.is_absolute() or '..' in p.parts or item.issym() or item.islnk():raise SystemExit('Unsafe source archive member')
  parts=p.parts[1:]
  if not parts:continue
  target=root.joinpath(*parts)
  if item.isdir():target.mkdir(parents=True,exist_ok=True)
  elif item.isfile():
   target.parent.mkdir(parents=True,exist_ok=True)
   with archive.extractfile(item) as src,target.open('wb') as dst:shutil.copyfileobj(src,dst)
   target.chmod(item.mode&0o777)
  else:raise SystemExit('Unexpected source archive member')
PY
export IDE_PROFILES_SOURCE_COMMIT="$SHA"
echo "Installing IDE Project Profiles from commit $SHA"
(cd "$WORK/source" && python3 manage.py "$@")
