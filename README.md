ndex-rest
=========

NDEx Rest Server

## Container Deployments

A production-oriented Docker image bundles the full NDEx stack — PostgreSQL, Keycloak, Apache Solr, MailHog, and the NDEx REST API (Tomcat) — into a single self-contained image.

The `runtime-base` stage is also used by the devcontainer image, ensuring both environments run identical service installations.

Key features:
- **Monolithic or distributed**: run all services in one container, or split them across containers using command-line flags (`--ndex`, `--postgres`, `--solr`, `--keycloak`, `--mailhog`)
- **Flag-driven startup**: enable only the services you need — no environment variables required
- **Ephemeral by default**: all state lives in the container layer; bind-mount host paths to persist config or data across restarts
- **Auto-provisioned credentials**: in monolithic mode, randomized credentials are written to `/etc/<svc>.otp` on first boot and deleted after 2 hours
- **Integration tested**: a live integration test script (`docker/test/integration-test.sh`) validates the ndex API is working in container through full API lifecycle including CX1/CX2 network upload, summary polling, CX2 retrieval, and Solr search

For complete build, run, configuration, and testing instructions, see **[docker/README.md](docker/README.md)**.

---

## Development Environment

A fully self-contained devcontainer bundle is provided under [.devcontainer](.devcontainer). It provides PostgreSQL, Keycloak, Solr, MailHog, and the NDEx API (Jetty) built from current source code of ndex-rest into a single container — no external service installation needed.

For complete setup, configuration, testing, and persistence instructions, see **[.devcontainer/README.md](.devcontainer/README.md)**.
