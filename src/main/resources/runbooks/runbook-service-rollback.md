# Runbook: Service Rollback Procedure

**System:** any
**Severity:** high / critical
**Team:** operations
**Last updated:** 2024-01

---

## Overview

This runbook describes how to safely roll back a Kubernetes Deployment to the previous
stable version when a new release causes degraded service behaviour.

---

## Pre-conditions

- You have `kubectl` access to the target cluster and namespace.
- You know the deployment name and the namespace.
- The previous version image is still available in the container registry.

---

## Step 1 — Confirm rollback is the right action

Before rolling back, verify:

1. The issue began shortly after the latest deployment (`kubectl rollout history`).
2. No configuration change or infrastructure issue is the root cause.
3. The team agrees that rollback is faster than a forward-fix deployment.

If in doubt, coordinate with the service owner before proceeding.

## Step 2 — Check rollout history

```bash
kubectl rollout history deployment/<deployment-name> -n <namespace>
```

Identify the last stable revision (typically `revision - 1`).

## Step 3 — Perform the rollback

To roll back to the immediately previous revision:

```bash
kubectl rollout undo deployment/<deployment-name> -n <namespace>
```

To roll back to a specific revision:

```bash
kubectl rollout undo deployment/<deployment-name> -n <namespace> --to-revision=<revision-number>
```

## Step 4 — Monitor the rollback progress

```bash
kubectl rollout status deployment/<deployment-name> -n <namespace>
```

Watch until you see `deployment "<name>" successfully rolled out`.

Check that the new pods come up healthy:

```bash
kubectl get pods -n <namespace> -l app=<app-label> -w
```

## Step 5 — Verify service health

1. Check the service's health endpoint:
   ```bash
   curl -s https://<service-url>/actuator/health | jq .
   ```
2. Review error rates and latency in the monitoring dashboard.
3. Confirm alert resolution: the triggering alert should auto-resolve within 5–10 minutes.

## Step 6 — Notify stakeholders

- Update the incident ticket with rollback action and current status.
- Notify the on-call channel in Slack: `#ops-incidents`.
- If the rollback resolves the issue, mark the incident as mitigated and begin RCA.

## Step 7 — Post-rollback tasks

- Tag the bad image in the registry as `DO NOT USE — rolled back <date>`.
- Trigger a post-mortem if the incident had customer impact.
- File a bug against the feature team referencing the rolled-back commit SHA.

---

## Resolution criteria

- Old version is running and healthy on all replicas.
- Error rate and latency are back to baseline.
- Incident ticket updated.
