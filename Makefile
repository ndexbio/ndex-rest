.PHONY: clean clean-test clean-pyc clean-build docs help docker docker-dev push-docker integration-test
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
	match = re.match(r'^([a-zA-Z_-]+):.*?## (.*)$$', line)
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

test: ## run tests with mvn test
	mvn test

coverage: ## check code coverage with jacoco
	mvn test jacoco:report
	$(BROWSER) target/site/jacoco/index.html

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

docker-base: ## build the shared runtime-base image (docker/Dockerfile)
	docker buildx build --load --platform linux/amd64 -f docker/Dockerfile --target runtime-base $(DOCKER_BUILD_ARGS) $(DOCKER_CACHE_ARGS) -t ndex-runtime-base .

docker: docker-base ## build the deploy image (docker/Dockerfile_deploy)
	docker buildx build --load --platform linux/amd64 -f docker/Dockerfile_deploy $(DOCKER_BUILD_ARGS) $(DOCKER_CACHE_ARGS) -t ndexbio/ndex-rest .

docker-dev: docker-base ## build the devcontainer image (.devcontainer/Dockerfile)
	docker build --platform linux/amd64 -f .devcontainer/Dockerfile -t ndexbio/ndex-rest-dev .

push-docker: docker ## push deploy image to registry (requires DOCKER_REPO and DOCKER_TAG)
	@[ -n "$(DOCKER_REPO)" ] || { echo "ERROR: DOCKER_REPO is not set"; exit 1; }
	@[ -n "$(DOCKER_TAG)" ]  || { echo "ERROR: DOCKER_TAG is not set"; exit 1; }
	docker tag ndexbio/ndex-rest $(DOCKER_REPO):$(DOCKER_TAG)
	docker push $(DOCKER_REPO):$(DOCKER_TAG)

integration-test: ## run integration tests
	docker/test/integration-test.sh
