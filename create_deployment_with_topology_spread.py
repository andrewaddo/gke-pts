
import os
from kubernetes import client, config
from kubernetes.client.rest import ApiException

def main():
    """
    This script connects to a Kubernetes cluster and creates a Deployment
    with Pod Topology Spread constraints to distribute pods across zones.
    """
    # Load Kubernetes configuration from default location (~/.kube/config)
    # Ensure you have authenticated to your GKE cluster.
    try:
        config.load_kube_config()
    except config.ConfigException:
        raise Exception("Could not load kube config. Is it configured correctly?")

    # Create an instance of the AppsV1Api client
    apps_v1 = client.AppsV1Api()

    # Define the pod label selector for the spread constraint
    label_selector = client.V1LabelSelector(
        match_labels={"app": "my-app-python"}
    )

    # Define the Topology Spread Constraint
    topology_spread_constraint = client.V1TopologySpreadConstraint(
        max_skew=1,
        topology_key="topology.kubernetes.io/zone",
        when_unsatisfiable="DoNotSchedule",
        label_selector=label_selector
    )

    # Define the container
    container = client.V1Container(
        name="my-app-container",
        image="nginx:latest",
        ports=[client.V1ContainerPort(container_port=80)]
    )

    # Define the Pod Template Spec
    template = client.V1PodTemplateSpec(
        metadata=client.V1ObjectMeta(labels={"app": "my-app-python"}),
        spec=client.V1PodSpec(
            containers=[container],
            topology_spread_constraints=[topology_spread_constraint]
        )
    )

    # Define the Deployment Spec
    deployment_spec = client.V1DeploymentSpec(
        replicas=6,
        template=template,
        selector=label_selector
    )

    # Define the Deployment object
    deployment = client.V1Deployment(
        api_version="apps/v1",
        kind="Deployment",
        metadata=client.V1ObjectMeta(name="my-app-python"),
        spec=deployment_spec
    )

    # Create the deployment in the 'default' namespace
    namespace = "default"
    try:
        api_response = apps_v1.create_namespaced_deployment(
            body=deployment,
            namespace=namespace
        )
        print(f"Deployment 'my-app-python' created successfully in namespace '{namespace}'.")
        # print(f"API Response: {api_response.status}")
    except ApiException as e:
        print(f"Error creating deployment: {e.body}")
        print("Please check if a deployment with the same name already exists.")

if __name__ == '__main__':
    main()
