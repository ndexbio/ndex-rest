.PHONY: clean clean-test clean-pyc clean-build docs help
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

clean: ## run mvn clean
	mvn clean
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
