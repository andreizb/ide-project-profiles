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
github_get() {
  local endpoint="$1" output="$2"
  if command -v curl >/dev/null &&
     curl --fail --silent --location --proto '=https' --proto-redir '=https' \
       --output "$output" "https://api.github.com/$endpoint"; then
    return
  fi
  if command -v gh >/dev/null && gh api "$endpoint" > "$output"; then
    return
  fi
  echo 'Could not download from GitHub. For private access or an API rate limit, try gh auth login.' >&2
  return 1
}
REF="$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "${IDE_PROFILES_REF:-main}")"
github_get "repos/$REPOSITORY/commits/$REF" "$WORK/commit.json"
SHA="$(python3 - "$WORK/commit.json" "$REF" <<'PY_SHA'
import json,re,sys
with open(sys.argv[1]) as stream:
 sha=json.load(stream).get('sha')
if not isinstance(sha,str) or not re.fullmatch('[0-9a-f]{40}',sha):raise SystemExit('Invalid GitHub commit')
if re.fullmatch('[0-9a-fA-F]{40}',sys.argv[2]) and sha != sys.argv[2].lower():
 raise SystemExit('GitHub resolved a different commit than requested')
print(sha)
PY_SHA
)"
github_get "repos/$REPOSITORY/tarball/$SHA" "$WORK/source.tar.gz"
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
