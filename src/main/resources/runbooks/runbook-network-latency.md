# Runbook: Network Latency Investigation

**System:** any
**Severity:** high
**Team:** operations
**Last updated:** 2024-01

---

## Overview

This runbook guides the investigation of elevated network latency alerts between services
in a microservices architecture. High latency is often a symptom of resource saturation,
misconfigured timeouts, DNS issues or an upstream dependency problem.

---

## Step 1 — Understand the alert scope

From the alert payload, identify:
- **Source service** — caller exhibiting high latency
- **Target service** — upstream dependency
- **Affected percentile** — p99 or p95 latency
- **Environment** — prod / staging

## Step 2 — Verify with live traffic data

Check p99 latency between the source and target in Grafana:

- Navigate to the **Service Map** or **RED metrics** dashboard.
- Confirm the latency is sustained (> 5 minutes) and not a transient spike.

Check HTTP error rates alongside latency — latency + errors together indicates overload.

## Step 3 — Check the target service for overload

High latency in the target service can cascade upstream. Check:

```bash
kubectl top pods -n <namespace> -l app=<target-service> --sort-by=cpu
```

Review the target's own latency metrics. If the target is also slow, the problem is upstream.

## Step 4 — Check network policies and DNS

DNS resolution failures or misconfigured NetworkPolicies can cause intermittent latency:

```bash
# Test DNS from the source pod
kubectl exec -it <source-pod> -n <namespace> -- nslookup <target-service>

# Check round-trip time
kubectl exec -it <source-pod> -n <namespace> -- ping -c 5 <target-service>
```

If DNS is slow (> 5 ms for intra-cluster), check CoreDNS pod health:

```bash
kubectl get pods -n kube-system -l k8s-app=kube-dns
kubectl logs -n kube-system -l k8s-app=kube-dns --tail=50
```

## Step 5 — Check connection pool and timeout configuration

Latency spikes under load often indicate exhausted connection pools:

1. Review the source service's HTTP client timeout and pool settings.
2. Look for `ConnectionTimeoutException` or `SocketTimeoutException` in logs:
   ```bash
   kubectl logs <source-pod> -n <namespace> --tail=200 | grep -i timeout
   ```
3. If the pool is exhausted, consider increasing `max-connections` or reducing connection
   keep-alive duration.

## Step 6 — Check for noisy neighbours on the node

Network I/O saturation from another workload on the same node can cause latency:

```bash
kubectl describe node <node-name> | grep -A 20 "Allocated resources"
```

If the node is heavily loaded, evict or reschedule the noisy pod.

## Step 7 — Temporary mitigation

If the target service is overloaded:
- Scale it out: `kubectl scale deployment/<target> -n <namespace> --replicas=<n+2>`
- Enable circuit breaking in the service mesh (Istio / Linkerd) to fail fast and reduce
  queue build-up.

## Step 8 — Escalation

If the root cause cannot be identified within 30 minutes and customer impact is confirmed:

- Engage the network / infrastructure team.
- Open a P1 incident and attach: alert details, pod metrics, DNS test results, and logs.

---

## Resolution criteria

- p99 latency returns below the SLO threshold for at least 15 minutes.
- Root cause identified and documented.
- Incident ticket updated with mitigation steps and next actions.
