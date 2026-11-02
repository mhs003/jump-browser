#!/usr/bin/env bash
# Move the app version forward and keep versionCode in step.
#
#   scripts/bump-version.sh patch     # 1.0.0 -> 1.0.1   (default)
#   scripts/bump-version.sh minor     # 1.0.0 -> 1.1.0
#   scripts/bump-version.sh major     # 1.0.0 -> 2.0.0
#   scripts/bump-version.sh 2.3.1     # explicit
#
# versionCode always increases by one, whatever the name does: Android refuses
# to install an APK whose versionCode is not greater than the installed one.
#
# After bumping, this script opens a new `## [x.y.z] - YYYY-MM-DD` section in
# CHANGELOG.md and moves the entries that were under `## [Unreleased]` into it,
# so the version file and the changelog can never disagree — the CI and release
# workflows both fail when they do.
set -euo pipefail

cd "$(dirname "$0")/.."
file=version.properties
changelog=CHANGELOG.md

[ -f "$file" ] || { echo "no $file in $(pwd)" >&2; exit 1; }
[ -f "$changelog" ] || { echo "no $changelog in $(pwd)" >&2; exit 1; }

current_code=$(sed -n 's/^versionCode=//p' "$file" | tr -d '\r')
current_name=$(sed -n 's/^version=//p' "$file" | tr -d '\r')
[ -n "$current_code" ] && [ -n "$current_name" ] || {
    echo "malformed $file: expected version and versionCode" >&2
    exit 1
}

bump=${1:-patch}
if [[ "$bump" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    next_name=$bump
else
    IFS=. read -r major minor patch <<< "$current_name"
    case "$bump" in
        major) major=$((major + 1)); minor=0; patch=0 ;;
        minor) minor=$((minor + 1)); patch=0 ;;
        patch) patch=$((patch + 1)) ;;
        *) echo "usage: $0 [major|minor|patch|X.Y.Z]" >&2; exit 1 ;;
    esac
    next_name="$major.$minor.$patch"
fi

if [ "$next_name" = "$current_name" ]; then
    echo "$version.properties already says $next_name; pass a higher version" >&2
    exit 1
fi

next_code=$((current_code + 1))

# sed -i is GNU on Termux but BSD on macOS; writing through a temp file works
# on both.
tmp=$(mktemp)
sed -e "s/^versionCode=.*/versionCode=$next_code/" \
    -e "s/^version=.*/version=$next_name/" "$file" > "$tmp"
mv "$tmp" "$file"

# Promote the Unreleased entries into a dated section for the new version.
python3 - "$next_name" "$(date +%F)" <<'PY'
import re
import sys
from pathlib import Path

version, today = sys.argv[1], sys.argv[2]
path = Path("CHANGELOG.md")
text = path.read_text()

heading = f"## [{version}] - {today}"
if re.search(r"^## \[" + re.escape(version) + r"\]", text, re.M):
    sys.exit(0)

start = re.search(r"^## \[Unreleased\][^\n]*\n", text, re.M)
if not start:
    sys.exit(0)

# Everything up to the next `## [` heading belongs to Unreleased.
rest = text[start.end():]
nxt = re.search(r"^## \[", rest, re.M)
body = rest[:nxt.start()] if nxt else rest
tail = rest[nxt.start():] if nxt else ""

moved = body.strip("\n")
# Drop empty subsections so the released section only lists real changes.
sections = re.split(r"^(### .*)$", moved, flags=re.M)
cleaned = []
if sections[0].strip():
    cleaned.append(sections[0].strip("\n"))
for i in range(1, len(sections), 2):
    title = sections[i]
    content = sections[i + 1] if i + 1 < len(sections) else ""
    if content.strip():
        cleaned.append(title + "\n\n" + content.strip("\n"))

new_section = ""
if cleaned:
    new_section = heading + "\n\n" + "\n\n".join(cleaned) + "\n\n"
else:
    # An empty [Unreleased] is normal for a release commit that only carries a
    # workflow fix. The heading must still exist: both the CI and release
    # workflows refuse a version without one.
    new_section = heading + "\n\nNo user-facing changes.\n\n"

text = text[:start.end()] + "\n" + new_section + tail
path.write_text(text)
PY

echo "$current_name ($current_code) -> $next_name ($next_code)"
echo "CHANGELOG.md: entries moved under '## [$next_name] - $(date +%F)'"
