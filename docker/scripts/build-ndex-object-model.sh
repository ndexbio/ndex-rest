#!/usr/bin/env bash
# Build ndex-object-model from source and install to local Maven repository.
# ndex-object-model:3.0.0-SNAPSHOT is not published to any remote Maven repo;
# it must be built from the ndex3develop branch of its GitHub repository.
set -euo pipefail

REPO=https://github.com/ndexbio/ndex-object-model.git
BRANCH=ndex3develop
WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

git clone --depth 1 --branch "${BRANCH}" "${REPO}" "${WORK_DIR}"
mvn -f "${WORK_DIR}/pom.xml" install -q -DskipTests
