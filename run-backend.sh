#!/usr/bin/env bash
set -euo pipefail

# Resolve script directory so the script works from any working directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/backend/.env.local"
PROJECT="$SCRIPT_DIR/backend/HelpId.Api/HelpId.Api.csproj"

# Load secrets from backend/.env.local if present
if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "$ENV_FILE"
    set +a
else
    cat >&2 <<'MSG'
[run-backend] backend/.env.local not found.
Copy backend/.env.local.example to backend/.env.local and fill in the values:

  cp backend/.env.local.example backend/.env.local

MSG
fi

# Ensure App_Data directory exists and resolve absolute path for SQLite.
# EF Core resolves relative paths from the project directory, not the repo root,
# so we always use an absolute path here regardless of what .env.local says.
DB_DIR="$SCRIPT_DIR/backend/HelpId.Api/App_Data"
mkdir -p "$DB_DIR"
export ConnectionStrings__HelpIdDb="Data Source=$DB_DIR/helpid-dev.db"

# Validate required env vars
MISSING=()
[[ -z "${HELPID_AUTH_JWT_SIGNING_KEY:-}" ]]    && MISSING+=(HELPID_AUTH_JWT_SIGNING_KEY)
[[ -z "${HELPID_PROFILE_JWT_SIGNING_KEY:-}" ]] && MISSING+=(HELPID_PROFILE_JWT_SIGNING_KEY)
[[ -z "${PublicWeb__BaseUrl:-}" ]]             && MISSING+=(PublicWeb__BaseUrl)

if [[ ${#MISSING[@]} -gt 0 ]]; then
    echo "[run-backend] Missing required env vars:" >&2
    printf '  %s\n' "${MISSING[@]}" >&2
    exit 1
fi

# Apply EF Core migrations
echo "[run-backend] Applying database migrations..."
dotnet ef database update \
    --project "$PROJECT" \
    --startup-project "$PROJECT"

# Start the backend
echo "[run-backend] Starting backend..."
echo "[run-backend] Health check: curl http://127.0.0.1:5080/health"
dotnet run --project "$PROJECT" --urls "http://0.0.0.0:5080"
