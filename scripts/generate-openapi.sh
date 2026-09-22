#!/usr/bin/env bash
# Regenerate committed static OpenAPI specs using a temporary Docker container.
# Host requirements: Docker only (no local JDK/Maven).
# SpringDoc stays test-scoped — apps do not serve Swagger UI.
#
# Usage (from repo root):
#   ./scripts/generate-openapi.sh
#
# Then commit docs/openapi/*.yaml and docs/openapi/*.json.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="${OPENAPI_DOCKER_IMAGE:-eclipse-temurin:26-jdk}"
M2_VOLUME="${OPENAPI_M2_VOLUME:-create-your-pizza-m2}"

if ! command -v docker >/dev/null 2>&1; then
	echo "error: Docker is required to regenerate OpenAPI specs" >&2
	echo "  Install Docker, then re-run: ./scripts/generate-openapi.sh" >&2
	exit 1
fi

if ! docker info >/dev/null 2>&1; then
	echo "error: Docker daemon is not reachable (is Docker Desktop / dockerd running?)" >&2
	exit 1
fi

echo "Using image: ${IMAGE}"
echo "Maven cache volume: ${M2_VOLUME}"
echo "Generating docs/openapi/{auth,catalog}-service.{yaml,json} in a temporary container..."

# Bind-mount the repo so output lands on the host; --rm deletes the container after.
# mvnw matches the service Dockerfiles (Java 26). Named volume caches ~/.m2 across runs.
docker run --rm \
	--name create-your-pizza-openapi-gen \
	-v "${ROOT}:/workspace" \
	-v "${M2_VOLUME}:/root/.m2" \
	-w /workspace \
	"${IMAGE}" \
	bash -lc '
		set -euo pipefail
		chmod +x auth-service/mvnw catalog-service/mvnw

		echo ">>> auth-service OpenAPI export"
		( cd auth-service && ./mvnw -B test \
			-Dtest=OpenApiExportTest#exportStaticOpenApi \
			-Dopenapi.export=true \
			-q )

		echo ">>> catalog-service OpenAPI export"
		( cd catalog-service && ./mvnw -B test \
			-Dtest=OpenApiExportTest#exportStaticOpenApi \
			-Dopenapi.export=true \
			-q )
	'

echo "Done. Commit the updated files under docs/openapi/"
ls -la "${ROOT}"/docs/openapi/*.{yaml,json}
