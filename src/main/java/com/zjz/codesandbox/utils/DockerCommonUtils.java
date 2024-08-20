package com.zjz.codesandbox.utils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import lombok.extern.slf4j.Slf4j;

/**
 * Docker 工具类
 */
@Slf4j
public class DockerCommonUtils {

    public static void deleteContainer(String containerId, DockerClient client){
        try {
            client.removeContainerCmd(containerId)
                    .withForce(true)
                    .exec();
            log.info("Container {} deleted successfully.", containerId);
        } catch (DockerException e){
            log.error("Error deleting container: {}", e.getMessage());
        }
    }
}
