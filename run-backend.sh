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

# Kill any process that is LISTENING on port 5080 (not mere clients/tunnels connected to it)
LISTENER_PID=$(ss -Htlnp 'sport = :5080' | grep -oP 'pid=\K[0-9]+' | head -1)
if [[ -n "$LISTENER_PID" ]]; then
    echo "[run-backend] Killing existing listener on port 5080 (pid $LISTENER_PID)..."
    kill -9 "$LISTENER_PID" 2>/dev/null || true
    # Wait until the port is actually released (up to 10 s)
    for i in $(seq 1 10); do
        ss -Htlnp 'sport = :5080' | grep -q . || break
        sleep 1
    done
fi

# Apply EF Core migrations
echo "[run-backend] Applying database migrations..."
dotnet ef database update \
    --project "$PROJECT" \
    --startup-project "$PROJECT"

# Start the backend
echo "[run-backend] Starting backend..."
echo "[run-backend] Health check: curl https://evil-paws-try.loca.lt/health"
dotnet run --project "$PROJECT" --urls "http://0.0.0.0:5080"
