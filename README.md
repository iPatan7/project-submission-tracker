# Project Submission Tracker

Small Java service where students submit their project (UID, name, title, GitHub link) and faculty
move it through review: SUBMITTED -> UNDER_REVIEW -> APPROVED or REJECTED (rejected work can be
submitted again). Made for the Technical Training-II (25CAP-707) final practical, questions 3 and 4.

Stack: Java 21 (no framework, JDK HTTP server), Maven, Redis, nginx, Docker, Docker Compose, GitHub Actions.

## Run it

```
./deploy.sh build   # unit tests, jar, Docker image
./deploy.sh up      # redis + app + nginx on http://localhost:8181
./deploy.sh test    # smoke test through nginx
./deploy.sh down
```

`deploy.sh` creates `.env` with a random Redis password and faculty token the first time.
`.env` is not committed.

## API

| Method | Path | Notes |
|---|---|---|
| GET | /health | app and Redis status |
| GET | /api/submissions | all submissions |
| POST | /api/submissions | `{uid, studentName, title, repoUrl}` |
| GET | /api/submissions/{id} | one submission |
| PUT | /api/submissions/{id}/status | `{status}`, needs `Authorization: Bearer <FACULTY_TOKEN>` |

## Pipeline

`.github/workflows/ci-cd.yml`: Maven build and tests, hadolint, Docker build, container health check,
tags `1.0.<run number>` and `sha-<commit>`, push to GHCR, then deploy that tag to the `test`
environment with Docker Compose and run the smoke test.
