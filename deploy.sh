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

# The Redis password comes from the environment (CI secret) or from .env (local).
# It is never written into git.
if [ -z "${REDIS_PASSWORD:-}" ]; then
    if [ ! -f .env ]; then
        echo "REDIS_PASSWORD=$(openssl rand -hex 16)" > .env
        chmod 600 .env
        echo "created .env with a new random REDIS_PASSWORD"
    fi
    set -a
    . ./.env
    set +a
fi
export REDIS_PASSWORD

case "${1:-}" in
    build)
        mvn -B -q verify
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
