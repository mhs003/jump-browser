#!/usr/bin/env bash
# Prints the CHANGELOG.md section for a version, e.g. "1.0.0".
# Used by .github/workflows/release.yml to fill the GitHub release body.
set -euo pipefail

version="${1:?usage: release-notes.sh <version>}"
awk -v v="$version" '
  $0 ~ "^## \\[" v "\\]" { found = 1; next }
  found && /^## \[/ { exit }
  found { print }
' CHANGELOG.md | sed -e '1{/^$/d;}' -e '${/^$/d;}'
