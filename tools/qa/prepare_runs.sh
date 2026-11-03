#!/usr/bin/env bash
# Prepares dev run directories of a workspace for QA: server.properties (RCON on 25575), eula, the QA player as op,
# and client options that skip first-run screens.
#   tools/qa/prepare_runs.sh <workspace> <loader> [options.txt to copy]
set -eu
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SERVER="$ROOT/$1/$2/runs/server"
CLIENT="$ROOT/$1/$2/runs/client"
mkdir -p "$SERVER" "$CLIENT"
cat > "$SERVER/server.properties" <<'PROPS'
broadcast-rcon-to-ops=false
difficulty=normal
enable-rcon=true
enforce-whitelist=false
force-gamemode=false
function-permission-level=2
gamemode=survival
level-name=qa_world
level-seed=remnants-qa
max-tick-time=60000
motd=Remnants QA
online-mode=false
op-permission-level=4
rcon.password=qa
rcon.port=25575
simulation-distance=6
spawn-protection=0
view-distance=6
white-list=false
PROPS
echo "eula=true" > "$SERVER/eula.txt"
cat > "$SERVER/ops.json" <<'OPS'
[{"uuid": "35ddc670-85bb-3ccd-9416-7e1a75745779", "name": "RemnantHunter", "level": 4, "bypassesPlayerLimit": false}]
OPS
# Forge 47 dev servers crash advertising themselves to LAN (the pinger class is client-only in dev).
if [ "$2" = forge ]; then
  mkdir -p "$SERVER/defaultconfigs"
  printf '[server]\n\tadvertiseDedicatedServerToLan = false\n' > "$SERVER/defaultconfigs/forge-server.toml"
fi
# Plates stay visible with the HUD hidden, so the HUD-less client shots show them. Other keys are filled in on start.
if [ ! -f "$CLIENT/config/remnant_mobs/client.json" ]; then
  mkdir -p "$CLIENT/config/remnant_mobs"
  echo '{"nameplates": {"hide_with_hud": false}}' > "$CLIENT/config/remnant_mobs/client.json"
fi
if [ -n "${3:-}" ] && [ -f "$3" ]; then cp "$3" "$CLIENT/options.txt"; fi
# Without an options.txt to copy, write the keys that skip the first-run screens: the accessibility prompt otherwise
# stops the QA client's --quickPlayMultiplayer join on a fresh run directory.
if [ ! -f "$CLIENT/options.txt" ]; then
  printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\ntutorialStep:none\npauseOnLostFocus:false\n' \
    > "$CLIENT/options.txt"
fi
