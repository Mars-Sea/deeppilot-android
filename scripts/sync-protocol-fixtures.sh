#!/bin/sh
# Refreshes the copies of the cross-client protocol fixtures from a
# dsh-deeppilot checkout. The plugin repository is the only place these files
# are edited; this repository vendors them so the tests run without it.
#
#   scripts/sync-protocol-fixtures.sh            # copy from ../dsh-deeppilot
#   DSH_DEEPPILOT_DIR=/path scripts/sync-protocol-fixtures.sh
set -eu

here=$(cd "$(dirname "$0")/.." && pwd)
plugin=${DSH_DEEPPILOT_DIR:-"$here/../dsh-deeppilot"}
source_dir="$plugin/tests/fixtures"
target_dir="$here/core/src/test/resources/protocol-fixtures"

# Only the fixtures this client implements. Add a file here together with the
# test that reads it.
fixtures="auth-vectors.json session-goal.json"

[ -d "$source_dir" ] || { echo "no dsh-deeppilot checkout at $plugin" >&2; exit 1; }

mkdir -p "$target_dir"
for name in $fixtures; do
  cp "$source_dir/$name" "$target_dir/$name"
  echo "synced $name"
done
