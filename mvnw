#!/usr/bin/env sh
set -e

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

echo "Maven is required to run this project. Install Maven or replace this script with the standard Maven Wrapper." >&2
exit 1
