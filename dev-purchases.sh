#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$project_dir/data"

if [[ -f "$project_dir/.env" ]]; then
  set -a
  source "$project_dir/.env"
  set +a
fi

cd "$project_dir/services/purchases-api"
export SPRING_PROFILES_ACTIVE=dev
exec mvn spring-boot:run "$@"
