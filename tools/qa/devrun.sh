#!/usr/bin/env bash
# Start or stop dev servers/clients for QA under Xvfb with software rendering.
#   tools/qa/devrun.sh start-server <workspace> <loader>
#   tools/qa/devrun.sh start-client <workspace> <loader>
#   tools/qa/devrun.sh stop-client | stop-server [<workspace>]
# Logs go to $QA_LOGS (default /tmp/remnants-qa). Give each workspace its own QA_LOGS, server-port and rcon.port when
# several dev servers run at once; stop-server <workspace> then stops only that workspace's server.
set -u
LOGS=${QA_LOGS:-/tmp/remnants-qa}
mkdir -p "$LOGS"
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
# Gradle runs on the workspace's Java: 17 for 1.20.1, 21 for 1.21.1, 25 for 26.x (override with QA_JAVA_HOME).
case "${2:-}" in
  1.20.1) default_java=$(ls -d /opt/jdks/jdk-17* 2>/dev/null | head -1) ;;
  1.21.1) default_java=$(ls -d /usr/lib/jvm/java-21-openjdk-amd64 /opt/jdks/jdk-21* 2>/dev/null | head -1) ;;
  *) default_java=$(ls -d /opt/jdks/jdk-25* 2>/dev/null | head -1) ;;
esac
JAVA_HOME=${QA_JAVA_HOME:-$default_java}
export JAVA_HOME
case "$1" in
  start-server)
    cd "$ROOT/$2" && nohup ./gradlew ":$3:runServer" --no-daemon > "$LOGS/$3-server.log" 2>&1 &
    ;;
  start-client)
    # 26.3+ windows through SDL3, which only finds a usable visual on Xvfb through EGL.
    export DISPLAY=${DISPLAY:-:1} LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe SDL_VIDEO_FORCE_EGL=1 ${QA_CLIENT_ENV:-}
    cd "$ROOT/$2" && nohup ./gradlew ":$3:runQaClient" --no-daemon > "$LOGS/$3-client.log" 2>&1 &
    ;;
  stop-client|stop-server)
    # Only java processes are considered, so this never matches the calling shell.
    want=$([ "$1" = stop-client ] && echo 'KnotClient|clientQaClient|runQaClient|--quickPlayMultiplayer' || echo 'KnotServer|runServer|forgeserver|forge_userdev_server|serverData|neoforgeserver')
    for pid in $(pgrep -x java); do
      cmd=$(tr '\0' ' ' < "/proc/$pid/cmdline")
      if echo "$cmd" | grep -qE "$want" && { [ -z "${2:-}" ] || echo "$cmd $(readlink "/proc/$pid/cwd")" | grep -q "/$2/"; }; then kill "$pid"; fi
    done
    ;;
esac
