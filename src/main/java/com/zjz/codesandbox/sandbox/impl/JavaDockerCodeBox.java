package com.zjz.codesandbox.sandbox.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.zjz.codesandbox.constant.CmdConstant;
import com.zjz.codesandbox.constant.CommonConstant;
import com.zjz.codesandbox.constant.DockerConstant;
import com.zjz.codesandbox.constant.FileConstant;
import com.zjz.codesandbox.model.enums.CodeBoxExecuteEnum;
import com.zjz.codesandbox.model.execute.ExecuteInfo;
import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;
import com.zjz.codesandbox.model.process.ProcessMessage;
import com.zjz.codesandbox.sandbox.CodeBox;
import com.zjz.codesandbox.utils.ProcessUtils;
import com.zjz.codesandbox.utils.VerifyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class JavaDockerCodeBox implements CodeBox {


    private final DockerClient dockerClient = DockerClientBuilder.getInstance().build();

    public static void main(String[] args) throws IOException {
        JavaDockerCodeBox javaNativeCodeBox = new JavaDockerCodeBox();
        List<String> list = new ArrayList<>();
        list.add("1 2");
        list.add("2 2");
        list.add("2 9");
        String code = ResourceUtil.readStr("testcode/TimeErrorCode/Main.java",StandardCharsets.UTF_8);
        ExecuteRequest java = ExecuteRequest.builder()
                .code(code)
                .inputs(list)
                .language("java")
                .build();
        javaNativeCodeBox.executeCode(java);
    }

    /**
     * Docker Java镜像拉取 在类加载时只执行一次
     */
    static {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // Check if the image already exists
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

    /**
     * 删除文件
     * @param file 文件
     */
    private void deleteFile(File file){
        if (file.getParentFile() != null){
            String parentName = file.getParentFile().getName();
            boolean del = FileUtil.del(file.getParentFile());
            if (del){
                log.info("删除文件 {} 成功",parentName);
            }else {
                log.error("删除文件 {} 失败",parentName);
            }
        }
    }

    @Override
    public ExecuteResponse executeCode(ExecuteRequest executeRequest) {
        List<String> inputs = executeRequest.getInputs();
        String code = executeRequest.getCode();
        String language = executeRequest.getLanguage();

        // 校验用户代码是否保护敏感或危险代码
        if (VerifyUtils.verifyCodeSecurity(code)) {
            return ExecuteResponse.builder()
                    .status(CodeBoxExecuteEnum.FAILED.getValue())
                    .message("用户代码存在敏感或危险代码")
                    .build();
        }

        // 1.获取项目路径
        String projectPath = System.getProperty("user.dir");
        String globalCodePath
                = projectPath + File.separator + FileConstant.GLOBAL_CODE_DIR_NAME + File.separator  + language;
        if (!FileUtil.exist(globalCodePath)) {
            // 创建目录
            FileUtil.mkdir(globalCodePath);
        }
        // 2.为用户代码生成一个唯一的目录
        Snowflake snowflake = IdUtil.getSnowflake();
        String userCodePath = globalCodePath + File.separator + snowflake.nextId();
        String codeFilePath = userCodePath +File.separator + FileConstant.JAVA_FILE_NAME;
        // 3.将用户代码写入文件
        File file = FileUtil.writeString(code, codeFilePath, StandardCharsets.UTF_8);

        // 4.编译 Java 代码
        String responseMessage = "";
        String responseStatus = "";
        String compileCommand = String.format(CmdConstant.JAVA_COMPILE_CMD, file.getAbsolutePath());

        try {
            Process exec = Runtime.getRuntime().exec(compileCommand);
            ProcessMessage compileMessage = ProcessUtils.runProcessAndMessage(exec, CmdConstant.COMPILE_OPERATION_NAME);

            if (compileMessage.getExitCode() == 0) {
                // 编译成功
                System.out.println(compileMessage.getSuccessMsg());
            } else {
                // 编译失败 封装返回消息
                deleteFile(file);
                responseStatus = CodeBoxExecuteEnum.COMPILE_FAILED.getValue();
                responseMessage = compileMessage.getErrorMsg();
                return ExecuteResponse.builder()
                        .status(responseStatus)
                        .message(responseMessage)
                        .build();
            }
        } catch (IOException e) {
            throw new RuntimeException("Process Error", e);
        }

        // 5. 创建 Java 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(DockerConstant.DOCKER_JAVA_IMAGE);
        HostConfig hostConfig = new HostConfig();
        // 5.1 将本地文件同步到容器中
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
        // 5.2 启动容器
        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
        startContainerCmd.exec();

        // 6.执行 Java 程序
        if (ObjectUtil.isNull(inputs) || inputs.isEmpty()) {
            return ExecuteResponse.builder()
                    .status(CodeBoxExecuteEnum.FAILED.getValue())
                    .message("测试用例为空")
                    .build();
        }
        List<String> outputs = new ArrayList<>();
        List<ExecuteInfo> executeInfos = new ArrayList<>();
        for (String input : inputs) {
            // 将输入数据写入到容器内的文件
            String inputFilePath = DockerConstant.DOCKER_CODE_PATH + "/input.txt";
            String hostInputFilePath = userCodePath + "/input.txt";
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
                return ExecuteResponse.builder()
                        .status(CodeBoxExecuteEnum.FAILED.getValue())
                        .message("用例执行失败")
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
                    System.out.println("执行结果：" + new String(frame.getPayload()));
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


        // 6.删除文件
        deleteFile(file);
        System.out.println(executeInfos);
        System.out.println(outputs);
        return ExecuteResponse.builder()
                .status(CodeBoxExecuteEnum.SUCCESS.getValue())
                .outputs(outputs)
                .executeInfos(executeInfos)
                .message(CodeBoxExecuteEnum.SUCCESS.getText())
                .build();
    }

    @Override
    public String getStatus() {
        return null;
    }
}
