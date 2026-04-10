# Runbook: High CPU Investigation

**System:** any
**Severity:** high / critical
**Team:** operations
**Last updated:** 2024-01

---

## Overview

This runbook describes how to investigate and mitigate a high CPU alert on a Java-based microservice
running in a Kubernetes cluster.

---

## Step 1 — Verify the alert

1. Open the monitoring dashboard (Grafana or Datadog) and confirm the CPU alert is active.
2. Check the alert labels: `service`, `namespace`, `pod`, `environment`.
3. Confirm the alert has been firing for at least 5 minutes to rule out transient spikes.

## Step 2 — Identify the affected pod(s)

```bash
kubectl top pods -n <namespace> --sort-by=cpu
```

Note the pod(s) with the highest CPU usage. If multiple pods are affected, the issue is likely
in the application logic or a shared dependency, not a single instance.

## Step 3 — Collect a thread dump

For Java services, collect a JVM thread dump to identify CPU-intensive threads:

```bash
kubectl exec -it <pod-name> -n <namespace> -- jstack <PID>
```

To find the PID inside the container:
```bash
kubectl exec -it <pod-name> -n <namespace> -- ps aux | grep java
```

Look for threads in RUNNABLE state, especially those consuming CPU in tight loops or in GC.

## Step 4 — Check GC activity

High CPU is often caused by excessive garbage collection. Check GC logs or use:

```bash
kubectl exec -it <pod-name> -n <namespace> -- jstat -gcutil <PID> 1000 10
```

If the `FGC` (full GC) counter is increasing rapidly, the JVM is under memory pressure.
Proceed to the Heap Dump runbook.

## Step 5 — Check recent deployments

A CPU spike after a deployment is often caused by a regression in the new version:

```bash
kubectl rollout history deployment/<deployment-name> -n <namespace>
```

If the spike correlates with a recent deployment, consider rollback:

```bash
kubectl rollout undo deployment/<deployment-name> -n <namespace>
```

See the **Service Rollback Runbook** for the full rollback procedure.

## Step 6 — Scale horizontally (short-term mitigation)

While investigating the root cause, scale out to distribute the load:

```bash
kubectl scale deployment/<deployment-name> -n <namespace> --replicas=<current+2>
```

Verify the CPU drops after new pods are Ready.

## Step 7 — Escalation

If CPU remains above threshold after scaling and no root cause has been identified:

- Engage the service owner (see CODEOWNERS file).
- Open a P1 incident ticket.
- Attach: alert details, thread dump, GC stats, and deployment history.

---

## Resolution criteria

- CPU returns below the alert threshold for at least 15 minutes.
- Root cause identified and documented.
- Incident ticket updated with RCA.
