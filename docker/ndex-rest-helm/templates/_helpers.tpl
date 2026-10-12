{{/* Every resource is named after the release: `helm install ndex …` gives deploy/ndex,
svc/ndex, svc/ndex-keycloak and pvc/ndex-pvc. */}}
{{- define "ndex.name" -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "ndex.selectorLabels" -}}
app.kubernetes.io/name: ndex-rest
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "ndex.labels" -}}
{{ include "ndex.selectorLabels" . }}
app.kubernetes.io/version: {{ include "ndex.tag" . | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{- define "ndex.tag" -}}
{{- .Values.image.tag | default .Chart.AppVersion -}}
{{- end -}}

{{- define "ndex.image" -}}
{{- printf "%s:%s" .Values.image.repository (include "ndex.tag" .) -}}
{{- end -}}

{{/* The PVC mounted on /apps: one that already exists, or the chart's own. */}}
{{- define "ndex.claimName" -}}
{{- .Values.storage.existingClaim | default (printf "%s-pvc" (include "ndex.name" .)) -}}
{{- end -}}
