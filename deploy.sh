#!/usr/bin/env bash
# Repeatable deployment of the project submission tracker.
#   ./deploy.sh build    run tests, build the jar and the Docker image
#   ./deploy.sh up       start (or update) redis + app + nginx and wait until healthy
#   ./deploy.sh test     smoke test through nginx
#   ./deploy.sh status   show containers
#   ./deploy.sh logs     last log lines
#   ./deploy.sh down     stop everything (data volume is kept)
set -euo pipefail
cd "$(dirname "$0")"

export APP_ENV=${APP_ENV:-test}
export IMAGE=${IMAGE:-submission-tracker}
export IMAGE_TAG=${IMAGE_TAG:-1.0.0-$(git rev-parse --short HEAD 2>/dev/null || echo local)}
export PUBLIC_PORT=${PUBLIC_PORT:-8181}

# Secrets (Redis password, faculty token) come from the environment (CI secrets)
# or from .env (local run). They are never written into git.
if [ -f .env ]; then
    set -a
    . ./.env
    set +a
fi
for name in REDIS_PASSWORD FACULTY_TOKEN; do
    if [ -z "${!name:-}" ]; then
        value=$(openssl rand -hex 16)
        echo "$name=$value" >> .env
        chmod 600 .env
        export "$name=$value"
        echo "added a random $name to .env"
    fi
done
export REDIS_PASSWORD FACULTY_TOKEN

case "${1:-}" in
    build)
        mvn -B -ntp verify | grep -E 'Tests run:|BUILD|ERROR'
        docker build --build-arg APP_VERSION="$IMAGE_TAG" -t "$IMAGE:$IMAGE_TAG" .
        docker image ls "$IMAGE:$IMAGE_TAG"
        ;;
    up)
        echo "deploying $IMAGE:$IMAGE_TAG to the $APP_ENV environment"
        docker compose up -d --wait
        docker compose ps
        ;;
    test)
        ./scripts/smoke-test.sh "http://localhost:$PUBLIC_PORT"
        ;;
    status)
        docker compose ps
        ;;
    logs)
        docker compose logs --tail=30
        ;;
    down)
        docker compose down
        ;;
    *)
        echo "usage: $0 build|up|test|status|logs|down"
        exit 1
        ;;
esac
