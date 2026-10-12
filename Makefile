.PHONY: clean clean-test clean-pyc clean-build docs mcp-manifest help docker docker-dev docker-multi-platform buildx-setup push-docker integration-test integration-test-mcp build_claude_mcpb helm-package k8s-yml helm-push helm-attach chart-tag
.DEFAULT_GOAL := help
define BROWSER_PYSCRIPT
import os, webbrowser, sys
try:
	from urllib import pathname2url
except:
	from urllib.request import pathname2url

webbrowser.open("file://" + pathname2url(os.path.abspath(sys.argv[1])))
endef
export BROWSER_PYSCRIPT

define PRINT_HELP_PYSCRIPT
import re, sys

for line in sys.stdin:
	match = re.match(r'^([a-zA-Z0-9_-]+):.*?## (.*)$$', line)
	if match:
		target, help = match.groups()
		print("%-20s %s" % (target, help))
endef
export PRINT_HELP_PYSCRIPT
BROWSER := python -c "$$BROWSER_PYSCRIPT"

help:
	@python -c "$$PRINT_HELP_PYSCRIPT" < $(MAKEFILE_LIST)

clean: ## run mvn clean and docs clean
	mvn clean
	$(MAKE) -C docs clean
	/bin/rm -rf dist

lint: ## check style with checkstyle:checkstyle
	mvn checkstyle:checkstyle

# Compiles from clean on purpose. maven-compiler-plugin's staleness detection does not
# reliably notice edited sources in this project: it prints "Nothing to compile - all
# classes are up to date" followed by BUILD SUCCESS, so tests run against stale classes
# and real compilation errors go unreported. -Dmaven.compiler.useIncrementalCompilation
# does not fix that — despite the name it selects a timestamp-based stale-source scanner
# rather than forcing a full compile, and that scanner is what does the skipping. A clean
# build is the only deterministic option, and costs a few seconds.
test: helm-package ## lint and package the Helm chart, then run tests (always recompiles; incremental detection is unreliable here)
	mvn clean test

coverage: ## check code coverage with jacoco
	mvn clean test jacoco:report
	$(BROWSER) target/site/jacoco/index.html

compile: ## compile sources (always from clean, same reason as test)
	mvn clean compile

install: clean ## install the package to local repo
	mvn install

updateversion: ## updates version in pom.xml via maven command
	mvn versions:set

docs: ## generate Sphinx HTML documentation, including API docs
	$(MAKE) -C docs clean
	$(MAKE) -C docs html
	$(BROWSER) docs/_build/html/index.html

servedocs: docs ## compile the docs watching for changes
	watchmedo shell-command -p '*.rst' -c '$(MAKE) -C docs html' -R -D .

mcp-manifest: ## generate McpManifest.md from registered MCP tools
	mvn process-classes
	@echo "McpManifest.md written to target/generated-resources/McpManifest.md"

# Component version overrides — passed as Docker build-args
KEYCLOAK_VERSION  ?= 26.1.0
SOLR_VERSION      ?= 9.6.1
POSTGRES_VERSION  ?= 16
MAILHOG_VERSION   ?= 1.0.1
NDEX_COMMIT_HASH  ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo docker)

DOCKER_BUILD_ARGS = \
	    --build-arg KEYCLOAK_VERSION=$(KEYCLOAK_VERSION) \
	    --build-arg SOLR_VERSION=$(SOLR_VERSION) \
	    --build-arg POSTGRES_VERSION=$(POSTGRES_VERSION) \
	    --build-arg MAILHOG_VERSION=$(MAILHOG_VERSION) \
	    --build-arg NDEX_COMMIT_HASH=$(NDEX_COMMIT_HASH)

# Optional layer-cache flags — override from CI to enable e.g. --cache-from type=gha --cache-to type=gha,mode=max
DOCKER_CACHE_ARGS ?=

# Local build-cache dir for docker-multi-platform (persistent across rebuilds)
MULTIPLATFORM_CACHE_DIR ?= /tmp/ndex-buildcache
# Output path for the multi-platform OCI tar
MULTIPLATFORM_OCI_TAR   ?= /tmp/ndex-multiarch.tar

docker-base: ## build the shared runtime-base image (docker/Dockerfile)
	docker buildx build --load -f docker/Dockerfile --target runtime-base $(DOCKER_BUILD_ARGS) $(DOCKER_CACHE_ARGS) -t ndex-runtime-base .

docker: docker-base ## build the deploy image (docker/Dockerfile)
	docker buildx build --load -f docker/Dockerfile --target deploy \
	    $(DOCKER_BUILD_ARGS) $(DOCKER_CACHE_ARGS) -t ndexbio/ndex-rest .

docker-dev: docker-base ## build the devcontainer image (.devcontainer/Dockerfile)
	docker build -f .devcontainer/Dockerfile -t ndexbio/ndex-rest-dev .

# ── Multi-platform build targets ─────────────────────────────────────────────
# Prerequisite: QEMU must be installed once per host:
#   docker run --privileged --rm tonistiigi/binfmt --install all

buildx-setup: ## create the multi-platform builder (docker-container driver) — idempotent
	docker buildx inspect ndex-builder > /dev/null 2>&1 || \
	    docker buildx create --name ndex-builder --driver docker-container --bootstrap

docker-multi-platform: buildx-setup ## build linux/amd64 + linux/arm64 OCI tar locally (no push) → $(MULTIPLATFORM_OCI_TAR)
	docker buildx build --builder ndex-builder \
	    --platform linux/amd64,linux/arm64 \
	    --output type=oci,dest=$(MULTIPLATFORM_OCI_TAR) \
	    --cache-to   type=local,dest=$(MULTIPLATFORM_CACHE_DIR),mode=max \
	    --cache-from type=local,src=$(MULTIPLATFORM_CACHE_DIR) \
	    -f docker/Dockerfile --target deploy \
	    $(DOCKER_BUILD_ARGS) .

push-docker: docker-multi-platform ## push multi-platform manifest to registry via cached rebuild (requires DOCKER_REPO and DOCKER_TAG)
	@[ -n "$(DOCKER_REPO)" ] || { echo "ERROR: DOCKER_REPO is not set"; exit 1; }
	@[ -n "$(DOCKER_TAG)" ]  || { echo "ERROR: DOCKER_TAG is not set"; exit 1; }
	docker buildx build --builder ndex-builder \
	    --platform linux/amd64,linux/arm64 \
	    --push \
	    --cache-from type=local,src=$(MULTIPLATFORM_CACHE_DIR) \
	    -f docker/Dockerfile --target deploy \
	    $(DOCKER_BUILD_ARGS) \
	    -t $(DOCKER_REPO):$(DOCKER_TAG) .

integration-test: ## run integration tests (functional group, then the upgrade group)
	docker/test/integration-test.sh

# Every span a production instance might upgrade from. 3.0.4 predates the membership-archive
# migration and is the only span that can assert it; 3.0.6 is the hop a current deployment takes.
MIGRATION_SPANS ?= 3.0.4 3.0.6

migration-test: ## run the upgrade test for each base version in MIGRATION_SPANS
	@for v in $(MIGRATION_SPANS); do \
		echo "── upgrade span $$v → current ──"; \
		docker/test/migration-test.sh --base $$v || exit 1; \
	done

integration-test-mcp: ## run MCP integration tests (standalone)
	docker/test/integration-mcp-test.sh

build_claude_mcpb: ## package claude-extension/ into build/ndex-mcp.mcpb
	rm -rf build/mcpb-staging build/ndex-mcp.mcpb
	mkdir -p build/mcpb-staging
	cp claude-extension/manifest.json build/mcpb-staging/manifest.json
	cp claude-extension/icon.png build/mcpb-staging/icon.png
	cp -r claude-extension/server build/mcpb-staging/server
	cd build/mcpb-staging && zip -r ../ndex-mcp.mcpb .
	@echo "Built build/ndex-mcp.mcpb"

# ── Helm chart (docker/ndex-rest-helm) ───────────────────────────────────────
# helm is fetched, pinned, into .tools/<platform>/ on first use and checked against the sha256
# that helm publishes beside the download, so a broken or truncated download never runs.
HELM_VERSION := 4.3.0
OS := $(shell uname -s | tr '[:upper:]' '[:lower:]')
ARCH := $(shell uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')
PLATFORM := $(OS)-$(ARCH)
TOOLS := .tools/$(PLATFORM)
HELM := $(TOOLS)/helm-$(HELM_VERSION)
SHA256 := $(if $(filter darwin,$(OS)),shasum -a 256,sha256sum)
HELM_URL := https://get.helm.sh/helm-v$(HELM_VERSION)-$(PLATFORM).tar.gz

$(HELM):
	@mkdir -p $(TOOLS)
	curl -fsSL -o $@.download $(HELM_URL)
	@echo "$$(curl -fsSL $(HELM_URL).sha256sum | cut -d' ' -f1)  $@.download" | $(SHA256) -c - > /dev/null \
		|| { rm -f $@.download; echo "$(HELM_URL): sha256 mismatch"; exit 1; }
	tar -xzf $@.download -O $(PLATFORM)/helm > $@ && chmod +x $@ && rm $@.download

CHART := docker/ndex-rest-helm
CHART_VERSION := $(shell sed -n 's/^version: *//p' $(CHART)/Chart.yaml)
APP_VERSION := $(shell sed -n 's/^appVersion: *"\(.*\)"/\1/p' $(CHART)/Chart.yaml)
PACKAGE := dist/ndex-rest-helm-$(CHART_VERSION).tgz
REGISTRY := registry-1.docker.io
# helm push names the namespace only: the chart's name and version make ndexbio/ndex-rest-helm:<v>
CHART_REPO := oci://$(REGISTRY)/ndexbio

helm-package: $(HELM) ## lint the Helm chart, then package it as dist/ndex-rest-helm-<version>.tgz
	$(HELM) lint --strict $(CHART)
	$(HELM) package $(CHART) -d dist

k8s-yml: $(HELM) ## render the Helm chart into a plain manifest, dist/k8s-ndex-deployment.yml (for podman kube play)
	@mkdir -p dist
	$(HELM) template ndex $(CHART) > dist/k8s-ndex-deployment.yml
	@echo "Wrote dist/k8s-ndex-deployment.yml"

# a release's version must be the chart's own: a tag never publishes a chart under another number
chart-tag:
	@test -n "$(CHART_TAG)" || { echo "CHART_TAG is not set: the release's version, e.g. $(CHART_VERSION)"; exit 1; }
	@test "$(CHART_TAG)" = "$(CHART_VERSION)" || \
		{ echo "CHART_TAG $(CHART_TAG) is not the chart's version $(CHART_VERSION) ($(CHART)/Chart.yaml)"; exit 1; }

# a chart is never published before the image it deploys
helm-push: chart-tag helm-package ## push the chart to oci://registry-1.docker.io/ndexbio/ndex-rest-helm (requires CHART_TAG, DOCKER_USERNAME, DOCKER_PASSWORD)
	@docker manifest inspect docker.io/ndexbio/ndex-rest:$(APP_VERSION) > /dev/null || \
		{ echo "ndexbio/ndex-rest:$(APP_VERSION), the chart's appVersion, is not on Docker Hub: release v$(APP_VERSION) first"; exit 1; }
	@test -n "$$DOCKER_USERNAME" -a -n "$$DOCKER_PASSWORD" || \
		{ echo "DOCKER_USERNAME and DOCKER_PASSWORD must be set"; exit 1; }
	@echo "$$DOCKER_PASSWORD" | $(HELM) registry login $(REGISTRY) -u "$$DOCKER_USERNAME" --password-stdin
	$(HELM) push $(PACKAGE) $(CHART_REPO)

helm-attach: chart-tag helm-package ## attach the chart package to the ndex-rest-helm-v<version> GitHub release (requires CHART_TAG)
	gh release upload "ndex-rest-helm-v$(CHART_VERSION)" $(PACKAGE) --clobber
