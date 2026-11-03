package com.tools_service.k8s;

 
import java.util.List;

import org.springframework.stereotype.Service;

import com.tools_service.config.TargetProfile;
import com.tools_service.config.TargetRegistry;
import com.tools_service.k8s.dto.PodInfo;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.tools_service.k8s.dto.PodReadiness;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class KubernetesService {
    private  final TargetRegistry targetRegistry;
    private  final KubernetesClient kubernetesClient;

    public List<PodInfo> getPods(String targetId){
        TargetProfile target = targetRegistry.getTarget(targetId);
        String namespace = target.getKubernetes().getNamespace();
        return kubernetesClient.pods().inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .map(this::toPodInfo)
                .toList();
    }

    public String getPodLogs(String targetId,String podName,Integer tailLines){
        TargetProfile target = targetRegistry.getTarget(targetId);
        String namespace = target.getKubernetes().getNamespace();
        var podLog =kubernetesClient.pods().inNamespace(namespace).withName(podName);
        if (tailLines != null) {
        return podLog.tailingLines(tailLines).getLog();
    }
        return podLog.getLog();
    }

        public PodReadiness getPodReadiness(
            String targetId,
            String podName) {

        TargetProfile target = targetRegistry.getTarget(targetId);
        String namespace = target.getKubernetes().getNamespace();

        Pod pod = kubernetesClient
                .pods()
                .inNamespace(namespace)
                .withName(podName)
                .get();

        if (pod == null) {
            return new PodReadiness(
                    podName,
                    "NotFound",
                    false,
                    0,
                    0,
                    "Pod does not exist");
        }

        String phase = pod.getStatus() != null
                && pod.getStatus().getPhase() != null
                ? pod.getStatus().getPhase()
                : "Unknown";

        var containerStatuses = pod.getStatus() != null
                ? pod.getStatus().getContainerStatuses()
                : null;

        int totalContainers = containerStatuses == null
                ? 0
                : containerStatuses.size();

        int readyContainers = containerStatuses == null
                ? 0
                : (int) containerStatuses.stream()
                        .filter(status -> Boolean.TRUE.equals(status.getReady()))
                        .count();

        boolean ready = "Running".equals(phase)
                && totalContainers > 0
                && readyContainers == totalContainers;

        String reason = ready
                ? "All containers are ready"
                : "Pod is not running with all containers ready";

        return new PodReadiness(
                podName,
                phase,
                ready,
                readyContainers,
                totalContainers,
                reason);
    }

    private PodInfo toPodInfo(Pod pod){
        String status = "Unknown";
        if(pod.getStatus()!=null && pod.getStatus().getPhase()!=null){
            status=pod.getStatus().getPhase();
        }
        return new PodInfo(pod.getMetadata().getName(), status);
    }
    // @PostConstruct
    // public void test() {
    //     System.out.println(getPods("event-platform"));
    // }
}
