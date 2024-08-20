package com.zjz.codesandbox.sandbox.impl.java;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.zjz.codesandbox.constant.DockerConstant;
import com.zjz.codesandbox.model.dto.CompileMessage;
import com.zjz.codesandbox.model.dto.PreExecMessage;
import com.zjz.codesandbox.model.dto.PreRunMessage;
import com.zjz.codesandbox.model.dto.RunCodeMessage;
import com.zjz.codesandbox.model.execute.ExecuteInfo;
import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;
import com.zjz.codesandbox.sandbox.CodeBox;
import com.zjz.codesandbox.sandbox.ExecuteCodeTemplate;
import com.zjz.codesandbox.utils.DockerCommonUtils;
import com.zjz.codesandbox.utils.LanguageCommonUtils;
import com.zjz.codesandbox.utils.VerifyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Java Docker 代码沙箱
 */
@Service
@Slf4j
public class JavaDockerCodeBox extends ExecuteCodeTemplate implements CodeBox {


//    public static void main(String[] args) throws IOException {
//        JavaDockerCodeBox javaDockerCodeBox = new JavaDockerCodeBox();
//        List<String> list = new ArrayList<>();
//        list.add("1 2");
//        list.add("2 2");
//        list.add("2 9");
//        String code = ResourceUtil.readStr("testcode/java/Main.java", StandardCharsets.UTF_8);
//        ExecuteRequest java = ExecuteRequest.builder()
//                .code(code)
//                .inputs(list)
//                .language("java")
//                .build();
//        javaDockerCodeBox.executeCode(java);
//    }

    private final DockerClient dockerClient = DockerClientBuilder.getInstance().build();

    private String containerId;

    /**
     * Docker Java镜像拉取 在类加载时只执行一次
     */
    static {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 检查镜像是否已经存在
        boolean imageExists = dockerClient.listImagesCmd().exec().stream()
                .anyMatch(image -> image.getRepoTags() != null &&
                        Arrays.asList(image.getRepoTags()).contains(DockerConstant.DOCKER_JAVA_IMAGE));
        if (!imageExists){
            // 不存在 Java 环境镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(DockerConstant.DOCKER_JAVA_IMAGE);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    log.info("Download Image：{}", item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }else {
            log.info("Image already exists: {}",DockerConstant.DOCKER_JAVA_IMAGE);
        }
    }


    @Override
    public void deleteContainer() {
        DockerCommonUtils.deleteContainer(containerId,dockerClient);
    }

    @Override
    public CompileMessage compileCode(File file) {
        return LanguageCommonUtils.compileJavaCode(file);
    }

    @Override
    public PreExecMessage preExec(ExecuteRequest executeRequest) {
        String code = executeRequest.getCode();
        // 校验用户代码是否保护敏感或危险代码
        if (VerifyUtils.verifyCodeSecurity(code)){
            return PreExecMessage.builder()
                    .success(false)
                    .reason("Dangerous or sensitive code exists")
                    .build();
        }
        return PreExecMessage.builder()
                .success(true)
                .build();
    }

    @Override
    public PreRunMessage preRunCode(String userCodePath) {
        // 创建 Java 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(DockerConstant.DOCKER_JAVA_IMAGE);
        HostConfig hostConfig = new HostConfig();
        // 将本地文件同步到容器中
        hostConfig.setBinds(new Bind(userCodePath, new Volume(DockerConstant.DOCKER_CODE_PATH)));
        hostConfig.withMemory(DockerConstant.DOCKER_JAVA_MEMORY);
        hostConfig.withMemorySwap(0L);
        CreateContainerResponse response = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)  // 网络限制
                .withReadonlyRootfs(true)   // 限制对 root 目录的写权限
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        containerId = response.getId();
        // 启动容器
        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
        startContainerCmd.exec();
        return PreRunMessage.builder()
                .success(true)
                .build();
    }

    @Override
    public RunCodeMessage runCode(List<String> inputs, String userCodePath) {

        if (ObjectUtil.isNull(inputs)) {
            return RunCodeMessage.builder()
                    .success(false)
                    .exceptionMessage("The test case is null")
                    .build();
        }
        List<String> outputs = new ArrayList<>();
        List<ExecuteInfo> executeInfos = new ArrayList<>();
        for (String input : inputs) {
            // 将输入数据写入到容器内的文件
            String inputFilePath = DockerConstant.DOCKER_CODE_PATH + DockerConstant.DOCKER_TEST_CASE_INPUT;
            String hostInputFilePath = userCodePath + DockerConstant.DOCKER_TEST_CASE_INPUT;
            FileUtil.writeString(input, hostInputFilePath, StandardCharsets.UTF_8);

            // 构建要执行的命令
            String[] cmd = new String[]{
                    "sh", "-c",
                    "java -cp " + DockerConstant.DOCKER_CODE_PATH + " Main < " + inputFilePath
            };

            boolean success = LanguageCommonUtils.runCode(outputs, executeInfos, cmd, containerId, dockerClient);
            if (!success){
                return RunCodeMessage.builder()
                        .success(false)
                        .exceptionMessage("The use case failed to execute")
                        .build();
            }
        }
        return RunCodeMessage.builder()
                .success(true)
                .outputs(outputs)
                .executeInfos(executeInfos)
                .build();
    }

    @Override
    public ExecuteResponse executeCode(ExecuteRequest executeRequest) {
        return exec(executeRequest);
    }

    @Override
    public String getStatus() {
        return null;
    }
}
