# ndex-rest-helm

This chart runs NDEx on Kubernetes using the image `ndexbio/ndex-rest`. The NDEx REST server, PostgreSQL, Keycloak, Solr and MailHog all run together in one pod. For a step-by-step deployment, see the
[runbook](../RUNBOOK.md#ndex-on-kubernetes-with-helm). This page lists the chart's values and explains how to release a new version.

```bash
helm install ndex oci://registry-1.docker.io/ndexbio/ndex-rest-helm --version <version> \
  -n ndex --create-namespace -f values.yaml
```

- **Versions.** The chart's `version` is its own version number. Its `appVersion` is the ndex-rest
  release it deploys, which is also the image's default tag. Each chart version is published as
  `oci://registry-1.docker.io/ndexbio/ndex-rest-helm:<version>`, and also as
  `ndex-rest-helm-<version>.tgz` on the GitHub release `ndex-rest-helm-v<version>`. The chart works
  with Helm 3.8 or later, and with Helm 4. You don't need `helm repo add`.
- **One values file per install.** Pass the same values file, with `--version`, to every `helm install`
  and `helm upgrade`.
- **Resources.** Every resource is named after the release (`ndex` below):
  - a Deployment `ndex` with one replica, using the `Recreate` strategy;
  - a ReadWriteOnce PersistentVolumeClaim `ndex-pvc`, mounted on `/apps`;
  - a Service `ndex` for the REST API;
  - a Service `ndex-keycloak` for Keycloak.
- **Logins.** Keycloak issues its tokens as `http://localhost:8085`, so anything logging in has to
  reach Keycloak at `localhost:8085`. With the default ClusterIP Services, that means running
  `kubectl port-forward svc/ndex-keycloak 8085:8085` alongside `svc/ndex 8080:8080`.
- **A plain manifest.** Run `make k8s-yml` from the repository root. It renders this chart with its
  defaults into `dist/k8s-ndex-deployment.yml`, which works with `podman kube play` or `kubectl apply`.

## Values

| Value | Default | Meaning |
|---|---|---|
| `image.repository` | `docker.io/ndexbio/ndex-rest` | The image. |
| `image.tag` | `""` | Empty means the chart's `appVersion`. Never set it older than the image the volume last ran: the schema upgrade only moves forward. |
| `image.pullPolicy` | `IfNotPresent` | Use `Always` together with `image.tag=latest`. |
| `storage.size` | `512Gi` | |
| `storage.className` | `""` | Empty means the cluster's default StorageClass. |
| `storage.keep` | `true` | `helm uninstall` keeps the volume and its data (`helm.sh/resource-policy: keep`). |
| `storage.existingClaim` | `""` | A PVC that already exists in the release's namespace, such as `ndex-pvc` from a `kubectl apply` install. The chart mounts it and creates no PVC of its own. |
| `service.type` | `ClusterIP` | The type of both Services. |
| `service.port` | `8080` | The REST API's Service port. |
| `keycloakService.port` | `8085` | Keycloak's Service port. |
| `resources` | `{}` | The container's resource requests and limits. |

## Releasing the chart

The release workflow (`.github/workflows/release.yml`) decides what to build by looking at the release's tag prefix.

1. If the chart should deploy a new ndex-rest image, release that image first, with a GitHub release
   tagged `vX.Y.Z`.
2. In `Chart.yaml`, bump `version` and set `appVersion` to the image version. Run `make test`, which
   lints and packages the chart.
3. Add a `[NDEx REST Helm <version>]` entry to `CHANGELOG.md`, then merge.
4. Publish a GitHub release tagged **`ndex-rest-helm-v<version>`**, where `<version>` is exactly the
   `version` in `Chart.yaml`. Only this tag prefix starts the workflow's `push-chart` job. That job
   pushes the chart to `oci://registry-1.docker.io/ndexbio/ndex-rest-helm:<version>` and attaches
   `ndex-rest-helm-<version>.tgz` to the release.

The job refuses to publish if the tag's version isn't the chart's version, or if
`ndexbio/ndex-rest:<appVersion>` isn't on Docker Hub yet.
