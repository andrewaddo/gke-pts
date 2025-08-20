import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1TopologySpreadConstraint;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CreateDeploymentWithTopologySpread {

    public static void main(String[] args) {
        try {
            // Load Kubernetes configuration from default location (~/.kube/config)
            // Ensure you have authenticated to your GKE cluster.
            ApiClient client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);

            // Create an instance of the AppsV1Api client
            AppsV1Api api = new AppsV1Api();

            // Define the pod label selector for the spread constraint
            Map<String, String> matchLabels = new HashMap<>();
            matchLabels.put("app", "my-app-java");
            V1LabelSelector labelSelector = new V1LabelSelector().matchLabels(matchLabels);

            // Define the Topology Spread Constraint
            V1TopologySpreadConstraint topologySpreadConstraint = new V1TopologySpreadConstraint()
                    .maxSkew(1)
                    .topologyKey("topology.kubernetes.io/zone")
                    .whenUnsatisfiable("DoNotSchedule")
                    .labelSelector(labelSelector);

            // Define the container
            V1Container container = new V1Container()
                    .name("my-app-container")
                    .image("nginx:latest")
                    .ports(Collections.singletonList(new V1ContainerPort().containerPort(80)));

            // Define the Pod Template Spec
            V1PodTemplateSpec template = new V1PodTemplateSpec()
                    .metadata(new V1ObjectMeta().labels(matchLabels))
                    .spec(new V1PodSpec()
                            .containers(Collections.singletonList(container))
                            .topologySpreadConstraints(Collections.singletonList(topologySpreadConstraint)));

            // Define the Deployment Spec
            V1DeploymentSpec deploymentSpec = new V1DeploymentSpec()
                    .replicas(6)
                    .template(template)
                    .selector(labelSelector);

            // Define the Deployment object
            V1Deployment deployment = new V1Deployment()
                    .apiVersion("apps/v1")
                    .kind("Deployment")
                    .metadata(new V1ObjectMeta().name("my-app-java"))
                    .spec(deploymentSpec);

            // Create the deployment in the 'default' namespace
            String namespace = "default";
            V1Deployment apiResponse = api.createNamespacedDeployment(namespace, deployment).execute();
            System.out.println(String.format("Deployment '%s' created successfully in namespace '%s'.", apiResponse.getMetadata().getName(), namespace));

        } catch (IOException e) {
            System.err.println("Error loading kube config: " + e.getMessage());
            e.printStackTrace();
        } catch (ApiException e) {
            System.err.println("Error creating deployment: " + e.getResponseBody());
            System.err.println("Please check if a deployment with the same name already exists.");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
