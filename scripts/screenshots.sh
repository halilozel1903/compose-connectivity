#!/usr/bin/env bash
# Captures README screenshots of the sample app on a running emulator.
# The emulator's network can't be toggled reliably, so the sample fakes the status via `fakeStatus`.
set -euo pipefail
source "$(dirname "$0")/screenshot-lib.sh"

install_sample
for mode in light dark; do
  set_night_mode "$mode"
  for scene in offline reconnected metered; do
    fresh_launch --es fakeStatus "$scene"
    capture "$scene-$mode"
  done
done
