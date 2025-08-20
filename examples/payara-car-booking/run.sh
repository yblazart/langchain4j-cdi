#!/usr/bin/env bash
set -euo pipefail

# Ensure Payara (spawned by the Maven Payara Micro plugin) is terminated when
# the user presses Ctrl+C or when the script receives SIGTERM.
# Strategy:
# - Run Maven in the background and wait in the shell. This way, the shell
#   receives SIGINT (Ctrl+C) and can run the trap handler.
# - In the trap, send SIGTERM to the whole process group (kill 0), which includes
#   the Maven process and any children it spawned (like Payara Micro).
# - Also add a best-effort cleanup with pkill to catch any lingering Payara
#   Micro processes just in case.

cleanup() {
  # Best-effort: kill Payara Micro if still running.
  # Limit the pattern to typical Payara Micro identifiers to avoid overkill.
  pkill -f "payara-micro|payara.*micro" >/dev/null 2>&1 || true
}

terminate() {
  echo "Stopping Payara Micro and Maven..."
  # Send SIGTERM to all processes in the current process group
  # (including backgrounded Maven and its children).
  kill 0 >/dev/null 2>&1 || true
  # Give processes a brief moment to exit gracefully.
  sleep 1
  cleanup
}

trap terminate SIGINT SIGTERM
trap cleanup EXIT

# Run the dev build. Background it so the shell gets the signal on Ctrl+C.
mvn clean install payara-micro:dev &
MVN_PID=$!

# Wait for Maven to finish and propagate its exit status.
wait "$MVN_PID"
STATUS=$?

# Final cleanup in case anything is left.
cleanup

exit "$STATUS"
