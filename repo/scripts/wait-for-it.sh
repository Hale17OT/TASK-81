#!/bin/bash
#
# wait-for-it.sh -- Wait for a host:port to become available.
#
# Usage: wait-for-it.sh --host=HOST --port=PORT [--timeout=TIMEOUT]

set -e

HOST=""
PORT=""
TIMEOUT=30

for arg in "$@"; do
    case $arg in
        --host=*)
            HOST="${arg#*=}"
            shift
            ;;
        --port=*)
            PORT="${arg#*=}"
            shift
            ;;
        --timeout=*)
            TIMEOUT="${arg#*=}"
            shift
            ;;
        *)
            ;;
    esac
done

if [ -z "$HOST" ] || [ -z "$PORT" ]; then
    echo "Usage: wait-for-it.sh --host=HOST --port=PORT [--timeout=TIMEOUT]"
    exit 1
fi

echo "Waiting for $HOST:$PORT to be available (timeout: ${TIMEOUT}s)..."

start_time=$(date +%s)

while true; do
    current_time=$(date +%s)
    elapsed=$(( current_time - start_time ))

    if [ "$elapsed" -ge "$TIMEOUT" ]; then
        echo "Timeout reached: $HOST:$PORT is not available after ${TIMEOUT}s."
        exit 1
    fi

    # Try /dev/tcp first, fall back to nc
    if (echo > /dev/tcp/"$HOST"/"$PORT") 2>/dev/null; then
        echo "$HOST:$PORT is available after ${elapsed}s."
        exit 0
    elif command -v nc >/dev/null 2>&1; then
        if nc -z "$HOST" "$PORT" 2>/dev/null; then
            echo "$HOST:$PORT is available after ${elapsed}s."
            exit 0
        fi
    fi

    sleep 1
done
