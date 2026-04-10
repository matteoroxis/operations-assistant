# Runbook: Disk Space Alert

**System:** any
**Severity:** high / critical
**Team:** operations
**Last updated:** 2024-01

---

## Overview

This runbook describes how to investigate and remediate a low-disk-space alert on a Linux host
or a Kubernetes node. Disk saturation can cause application failures, log loss and data corruption.

---

## Step 1 — Identify the host or node

From the alert payload, note:
- `host` or `node` label
- `mount_point` — which filesystem is full
- `disk_used_percent` — current utilisation

## Step 2 — Check disk usage

SSH into the affected host, or use `kubectl debug node/<node-name>`:

```bash
df -h
```

Identify the mount point with high usage. Commonly affected paths:
- `/` — root filesystem
- `/var/log` — application and system logs
- `/var/lib/docker` — container images and volumes
- `/data` — persistent data volumes

## Step 3 — Find the large files or directories

```bash
du -sh /* 2>/dev/null | sort -rh | head -20
```

Drill into the top directory:

```bash
du -sh /var/log/* 2>/dev/null | sort -rh | head -20
```

## Step 4 — Safe cleanup options

### Clean up old logs

Check for rotated or uncompressed log files:

```bash
find /var/log -name "*.log.*" -mtime +7 -delete
journalctl --vacuum-size=500M
```

### Clean up Docker artefacts

Remove unused images, stopped containers and dangling volumes:

```bash
docker system prune -f
docker volume prune -f
```

### Clean up large temporary files

```bash
find /tmp -type f -atime +1 -delete
```

> ⚠ **Never delete files under `/data` or application data directories without confirming
> with the service owner first.**

## Step 5 — Expand storage (if cleanup is insufficient)

If the disk will fill again quickly, request a storage increase:

1. For cloud-managed nodes, expand the EBS/PD volume via the cloud console.
2. For on-prem, open a storage request with the infrastructure team.
3. If using a Kubernetes PersistentVolumeClaim, resize the PVC (requires a storage class
   that supports `allowVolumeExpansion: true`):
   ```bash
   kubectl patch pvc <pvc-name> -n <namespace> -p '{"spec":{"resources":{"requests":{"storage":"<new-size>Gi"}}}}'
   ```

## Step 6 — Verify resolution

```bash
df -h <mount-point>
```

Confirm the usage is below the alert threshold (typically < 80%).

## Step 7 — Prevent recurrence

- Review log rotation settings (`/etc/logrotate.d/`).
- Add a disk capacity alert at 70% to provide earlier warning.
- Consider enabling automatic log cleanup via a CronJob or systemd timer.

---

## Resolution criteria

- Disk usage below 80% on the affected mount point.
- No application errors caused by disk saturation.
- Alert resolved and root cause documented in the incident ticket.
