package com.onextel.CallServiceApplication.common.startup;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

@Getter
@Component
public class AppInstanceIdProvider {

    private static final String APP_INSTANCE_ID_VARIABLE = "APP_INSTANCE_ID";
    private static final String CGROUP_PATH = "/proc/self/cgroup";
    private static final String UNKNOWN_HOST = "unknown-host";
    private final String appInstanceId;

    public AppInstanceIdProvider(@Value("${spring.application.name}") String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = "CallServiceApplication";
        }
        this.appInstanceId = resolveAppInstanceId(serviceName);
    }

    private String resolveAppInstanceId(String serviceName) {
        // 1. Check if APP_INSTANCE_ID is defined in the environment
        String envInstanceId = System.getenv(APP_INSTANCE_ID_VARIABLE);
        if (envInstanceId != null && !envInstanceId.isBlank()) {
            return envInstanceId.trim();
        }

        // 2. Try Docker container ID
        String containerId = getDockerContainerId();
        if (containerId != null) {
            return serviceName + "-" + containerId;
        }

        // 3. Fallback to hostname + uuid
        String hostname = getHostname();
        String uuid = UUID.randomUUID().toString();
        return serviceName + "-" + hostname + "-" + uuid;
    }

    private String getDockerContainerId() {
        try {
            String cgroupContent = Files.readString(Paths.get(CGROUP_PATH));
            return cgroupContent.lines()
                    .map(line -> line.substring(line.lastIndexOf("/") + 1))
                    .filter(id -> id.length() >= 12)
                    .findFirst()
                    .map(id -> id.substring(0, 12)) // short container ID
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return UNKNOWN_HOST;
        }
    }
}
