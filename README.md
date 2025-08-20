# gke-pts

## Summary

1. Why Kyverno?

Kyverno helps define topologySpreadConstraints at the cluster level by acting as
  a dynamic admission controller that can automatically modify resources as they
  are created. Instead of manually adding the constraints to every Deployment or
  StatefulSet, you can create a single Kyverno ClusterPolicy. This policy
  intercepts workloads at creation time and uses a mutate rule to automatically
  inject the desired topologySpreadConstraints block into any pod spec that is
  missing one. This ensures that all applications, by default, adhere to your
  cluster's high-availability spreading rules (like distributing pods across zones
  or nodes) without any effort from individual developers, leading to consistent
  and enforceable best practices across the entire cluster.

1. Why not Gatekeeper?

Gatekeeper's primary purpose is Validation, not Mutation.

   1. Immutable by Design: The core function of open-source OPA Gatekeeper is to act
      as a validating webhook. It inspects resources against policies written in Rego
      and returns a simple "allowed" or "denied" answer. It is intentionally designed
      not to change the content of the resources it inspects.

   2. Enforce vs. Modify: Gatekeeper's goal is to enforce that resources are compliant
      before they are created. It puts the responsibility on the user or CI/CD pipeline
       to create a compliant resource. If you submitted a Deployment without
      topologySpreadConstraints, a Gatekeeper policy would reject the request with an
      error, forcing you to go back and add the required field to your YAML. It will
      not add it for you.

   3. Separation of Concerns: This approach maintains a clear separation of concerns.
      The user defines the exact resource they want, and the policy engine acts as a
      simple guardrail to ensure it meets the rules. It avoids the complexity of the
      cluster silently modifying resources on the fly.

1. Why not KubeSchedulerConfiguration?

The short answer is: `KubeSchedulerConfiguration` doesn't work with GKE because 
  GKE manages the Kubernetes control plane for you, and does not expose the 
  scheduler's underlying configuration files.

## Set up

```bash
# Create a cluster
gcloud container clusters create gke-pts \
   --zone us-central1 

# Create a nodepool that spans across multiple zones
 gcloud container node-pools create np3z \
   --cluster gke-pts \
   --location us-central1 \
   --machine-type n2-standard-8 \
   --num-nodes 3
```

## Test a deployment with  YAML 

```bash
kubectl apply -f deployment-with-pts.yaml
## Validated by inspecting the deployment yaml file
kubectl get deployment my-app-with-pts -o yaml 
## --> sample output
# topologySpreadConstraints:
# - labelSelector:
#       matchLabels:
#       app: my-app-with-pts
#    maxSkew: 1
#    topologyKey: topology.kubernetes.io/zone
#    whenUnsatisfiable: DoNotSchedule
```

## Test with Python client

```bash
python3 create_deployment_with_topology_spread.py
## Validated by inspecting the deployment yaml file
kubectl get deployment my-app-python -o yaml 
## --> sample output
topologySpreadConstraints:
# - labelSelector:
#       matchLabels:
#       app: my-app-python
#    maxSkew: 1
#    topologyKey: topology.kubernetes.io/zone
#    whenUnsatisfiable: DoNotSchedule
```

## Test a deployment without PTS

```bash
kubectl apply -f deployment-without-pts.yaml
## Validated by inspecting the deployment yaml file
kubectl get deployment my-app-without-pts -o yaml | grep topologySpreadConstraints
## --> sample output
None
```

## Test with kyverno ClusterPolicy

```bash
# Install kyverno
kubectl create -f https://github.com/kyverno/kyverno/releases/download/v1.11.1/install.yaml
kubectl apply -f enforce-nodal-spread-policy.yaml
kubectl apply -f deployment-without-pts.yaml
## Validated by inspecting the deployment yaml file
kubectl get deployment my-app-without-pts -o yaml 
## --> sample output
# topologySpreadConstraints:
# - labelSelector:
#       matchLabels:
#       app: my-app-without-pts
#    maxSkew: 1
#    topologyKey: kubernetes.io/hostname
#    whenUnsatisfiable: ScheduleAnyway

# Output: indicating that the nodal (hostname) constraint is applied, and no longer the default zonal placement
POD                                                NODE                           ZONE
my-app-default-9b86fd96b-7cmfv                     gke-gke-pts-np3z-4bb89168-ln9h us-central1-c
my-app-default-9b86fd96b-lkrr9                     gke-gke-pts-np3z-4bb89168-mm98 us-central1-c
my-app-default-9b86fd96b-ppvsh                     gke-gke-pts-np3z-fa3fb573-3mnq us-central1-a
my-app-default-9b86fd96b-prz2s                     gke-gke-pts-np3z-4bb89168-484w us-central1-c
my-app-default-9b86fd96b-vvfsz                     gke-gke-pts-np3z-7577a52e-8bhm us-central1-b
my-app-default-9b86fd96b-wl2qr                     gke-gke-pts-np3z-fa3fb573-f8pm us-central1-a
```

## Logs

```bash
## Clean up
kubectl delete ClusterPolicy --all
kubectl delete deployment --all
# View nodes and zones
kubectl get nodes -L topology.kubernetes.io/zone
## sample output
NAME                                     STATUS   ROLES    AGE   VERSION               ZONE
gke-gke-pts-default-pool-1bb5e1a0-bmhc   Ready    <none>   85m   v1.33.2-gke.1240000   us-central1-a
gke-gke-pts-default-pool-1bb5e1a0-rs9q   Ready    <none>   85m   v1.33.2-gke.1240000   us-central1-a
gke-gke-pts-default-pool-1bb5e1a0-xs2z   Ready    <none>   85m   v1.33.2-gke.1240000   us-central1-a
gke-gke-pts-default-pool-700cfe48-882g   Ready    <none>   85m   v1.33.2-gke.1240000   us-central1-c
gke-gke-pts-default-pool-700cfe48-h51q   Ready    <none>   85m   v1.33.2-gke.1240000   us-central1-c
gke-gke-pts-default-pool-700cfe48-rsqv   Ready    <none>   85m   v1.33.2-gke.1240000   us-central1-c
gke-gke-pts-default-pool-b37f1eed-7z68   Ready    <none>   85m   v1.33.2-gke.1240000   us-central1-b
gke-gke-pts-default-pool-b37f1eed-hzw2   Ready    <none>   85m   v1.33.2-gke.1240000   us-central1-b
gke-gke-pts-default-pool-b37f1eed-vwvh   Ready    <none>   85m   v1.33.2-gke.1240000   us-central1-b
gke-gke-pts-np3z-4bb89168-484w           Ready    <none>   83m   v1.33.2-gke.1240000   us-central1-c
gke-gke-pts-np3z-4bb89168-ln9h           Ready    <none>   83m   v1.33.2-gke.1240000   us-central1-c
gke-gke-pts-np3z-4bb89168-mm98           Ready    <none>   83m   v1.33.2-gke.1240000   us-central1-c
gke-gke-pts-np3z-7577a52e-09lx           Ready    <none>   83m   v1.33.2-gke.1240000   us-central1-b
gke-gke-pts-np3z-7577a52e-8bhm           Ready    <none>   83m   v1.33.2-gke.1240000   us-central1-b
gke-gke-pts-np3z-7577a52e-dzk9           Ready    <none>   83m   v1.33.2-gke.1240000   us-central1-b
gke-gke-pts-np3z-fa3fb573-3mnq           Ready    <none>   83m   v1.33.2-gke.1240000   us-central1-a
gke-gke-pts-np3z-fa3fb573-f8pm           Ready    <none>   83m   v1.33.2-gke.1240000   us-central1-a
gke-gke-pts-np3z-fa3fb573-rlmd           Ready    <none>   83m   v1.33.2-gke.1240000   us-central1-a

# View pods on nodes and zones distribution
printf "%-50s %-30s %s\n" "POD" "NODE" "ZONE" && \
kubectl get pods -o=custom-columns='POD:.metadata.name,NODE:.spec.nodeName' --no-headers | \
while read -r pod_name node_name; do \
   if [ -n "$node_name" ] && [ "$node_name" != "<none>" ]; then \
      zone=$(kubectl get node "$node_name" -o=jsonpath='{.metadata.labels.topology\.kubernetes\.io/zone}'); \
      printf "%-50s %-30s %s\n" "$pod_name" "$node_name" "$zone"; \
   else \
      printf "%-50s %-30s %s\n" "$pod_name" "$node_name" "<unknown>"; \
   fi; \
done
## sample output
POD                                                NODE                           ZONE
my-app-5f94f7dd49-8wjrk                            gke-gke-pts-np3z-7577a52e-8bhm us-central1-b
my-app-5f94f7dd49-bwtzl                            gke-gke-pts-np3z-fa3fb573-3mnq us-central1-a
my-app-5f94f7dd49-kmvrb                            gke-gke-pts-np3z-7577a52e-dzk9 us-central1-b
my-app-5f94f7dd49-mp4b4                            gke-gke-pts-np3z-4bb89168-ln9h us-central1-c
my-app-5f94f7dd49-n4s5x                            gke-gke-pts-np3z-4bb89168-484w us-central1-c
my-app-5f94f7dd49-znsfn                            gke-gke-pts-np3z-fa3fb573-rlmd us-central1-a
my-app-python-675c5f7649-588xx                     gke-gke-pts-np3z-fa3fb573-rlmd us-central1-a
my-app-python-675c5f7649-dd88r                     gke-gke-pts-np3z-4bb89168-484w us-central1-c
my-app-python-675c5f7649-dhtws                     gke-gke-pts-np3z-fa3fb573-3mnq us-central1-a
my-app-python-675c5f7649-hvcwh                     gke-gke-pts-np3z-4bb89168-mm98 us-central1-c
my-app-python-675c5f7649-krnpk                     gke-gke-pts-np3z-7577a52e-8bhm us-central1-b
my-app-python-675c5f7649-x25lx                     gke-gke-pts-np3z-7577a52e-dzk9 us-central1-b
```
