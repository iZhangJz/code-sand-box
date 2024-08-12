package com.zjz.codesandbox.sandbox.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.zjz.codesandbox.constant.CommonConstant;
import com.zjz.codesandbox.constant.DockerConstant;
import com.zjz.codesandbox.constant.FileConstant;
import com.zjz.codesandbox.model.dto.PreExecMessage;
import com.zjz.codesandbox.model.dto.PreRunMessage;
import com.zjz.codesandbox.model.dto.RunCodeMessage;
import com.zjz.codesandbox.model.execute.ExecuteInfo;
import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;
import com.zjz.codesandbox.sandbox.CodeBox;
import com.zjz.codesandbox.sandbox.ExecuteCodeTemplate;
import com.zjz.codesandbox.utils.VerifyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Java Docker 代码沙箱
 */
@Service
@Slf4j
public class JavaDockerCodeBox extends ExecuteCodeTemplate implements CodeBox {


    private final DockerClient dockerClient = DockerClientBuilder.getInstance().build();


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
        String seccomp = ResourceUtil.readUtf8Str(FileConstant.SECCOMP_PROFILE);
        hostConfig.withSecurityOpts(List.of("seccomp=" + seccomp));
        CreateContainerResponse response = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)  // 网络限制
                .withReadonlyRootfs(true)   // 限制对 root 目录的写权限
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        String containerId = response.getId();
        // 启动容器
        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
        startContainerCmd.exec();
        return PreRunMessage.builder()
                .success(true)
                .data(containerId)
                .build();
    }

    @Override
    public RunCodeMessage runCode(List<String> inputs, String userCodePath,Object data) {
        // 执行 Java 程序
        if (ObjectUtil.isNull(data)) {
            return RunCodeMessage.builder()
                    .success(false)
                    .exceptionMessage("The container ID is empty")
                    .build();
        }
        String containerId = (String) data;
        if (ObjectUtil.isNull(inputs) || inputs.isEmpty()) {
            return RunCodeMessage.builder()
                    .success(false)
                    .exceptionMessage("The test case is empty")
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

            ExecCreateCmdResponse cmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .exec();

            String execId = cmdResponse.getId();
            if (StrUtil.isBlank(execId)){
                return RunCodeMessage.builder()
                        .success(false)
                        .exceptionMessage("The use case failed to execute")
                        .build();
            }

            ExecuteInfo executeInfo = new ExecuteInfo();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)){
                        executeInfo.setMessage("failed");
                    } else {
                        executeInfo.setMessage("success");
                    }
                    String payload = new String(frame.getPayload());
                    // 去掉所有换行符
                    payload = payload.replaceAll("\\R", "");
                    outputs.add(payload);
                    System.out.println("Execution result：" + new String(frame.getPayload()));
                    super.onNext(frame);
                }
            };

            // 获取 docker 容器状态
            final Long[] maxMemoryUsage = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<>() {
                @Override
                public void onStart(Closeable closeable) {}

                @Override
                public void onNext(Statistics statistics) {
                    Long usage = statistics.getMemoryStats().getUsage();
                    if (ObjectUtil.isNotNull(usage)){
                        maxMemoryUsage[0] = Math.max(maxMemoryUsage[0], usage);
                    }
                }

                @Override
                public void onError(Throwable throwable) {}

                @Override
                public void onComplete() {}

                @Override
                public void close() throws IOException {}
            };

            statsCmd.exec(statisticsResultCallback);

            try {
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();
                boolean completionInTime = dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(CommonConstant.TIME_OUT, TimeUnit.MILLISECONDS); // 超时限制
                stopWatch.stop();
                if (!completionInTime){
                    // 超时退出
                    log.info("process terminated due to timeout");
                    outputs.add("process terminated due to timeout");
                }

                // 等待内存统计完成
                Thread.sleep(1000);  // 增加一个短暂的等待时间，确保统计回调能及时处理数据

                executeInfo.setTime(stopWatch.getLastTaskTimeMillis());
                executeInfo.setMemory(maxMemoryUsage[0] / 8 / 1024);
                executeInfos.add(executeInfo);

            } catch (InterruptedException e) {
                log.error("The program execution is abnormal，{}", e.getStackTrace());
                throw new RuntimeException(e);
            } finally {
                statsCmd.close();
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
