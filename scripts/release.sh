#!/usr/bin/env bash
# Cut a release: bump the version, commit it and push a matching tag.
#
#   scripts/release.sh            # patch bump  (1.0.0 -> 1.0.1)
#   scripts/release.sh minor
#   scripts/release.sh 1.2.0
#   scripts/release.sh --dry-run patch
#
# The tag is what triggers .github/workflows/release.yml, which builds the APK
# and publishes the GitHub release. Pushing to master alone never releases.
set -euo pipefail

cd "$(dirname "$0")/.."

dry_run=0
if [ "${1:-}" = "--dry-run" ]; then
    dry_run=1
    shift
fi

branch=$(git rev-parse --abbrev-ref HEAD)
[ "$branch" = master ] || { echo "refusing to release from '$branch' (expected master)" >&2; exit 1; }

if [ -n "$(git status --porcelain)" ]; then
    echo "working tree is dirty; commit or stash first" >&2
    git status --short >&2
    exit 1
fi

scripts/bump-version.sh "${1:-patch}"

version=$(sed -n 's/^version=//p' version.properties | tr -d '\r')
tag="v$version"

if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    echo "tag $tag already exists" >&2
    exit 1
fi

echo "-> $tag"
git diff --stat

if [ "$dry_run" = 1 ]; then
    echo "(dry run: not committing)"
    git checkout -- version.properties CHANGELOG.md
    exit 0
fi

git add version.properties CHANGELOG.md
git commit -q -m "chore(release): $version"
git tag -a "$tag" -m "Jump $version"
git push origin master "$tag"

echo "Pushed $tag; watch the Release workflow on GitHub."
