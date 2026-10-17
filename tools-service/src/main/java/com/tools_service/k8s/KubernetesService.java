package com.tools_service.k8s;

 
import java.util.List;

import org.springframework.stereotype.Service;

import com.tools_service.config.TargetProfile;
import com.tools_service.config.TargetRegistry;
import com.tools_service.k8s.dto.PodInfo;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
 
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
