#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=server/deploy/blue-green/common.sh
source "$SCRIPT_DIR/common.sh"
load_config
preflight
log "$SETTY_DEPLOY_ENVIRONMENT slot deployment prerequisites passed."
