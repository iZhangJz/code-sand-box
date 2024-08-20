package com.zjz.codesandbox.sandbox.impl.cpp;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
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
import org.jvnet.hk2.annotations.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class CppDockerCodeBox extends ExecuteCodeTemplate implements CodeBox {


//    public static void main(String[] args) throws IOException {
//        CppDockerCodeBox cppDockerCodeBox = new CppDockerCodeBox();
//        List<String> list = new ArrayList<>();
//        list.add("1 2");
//        list.add("2 2");
//        list.add("2 9");
//        String code = ResourceUtil.readStr("testcode/cpp/Main.cpp", StandardCharsets.UTF_8);
//        ExecuteRequest cpp = ExecuteRequest.builder()
//                .code(code)
//                .inputs(list)
//                .language("cpp")
//                .build();
//        cppDockerCodeBox.executeCode(cpp);
//    }


    private final DockerClient dockerClient = DockerClientBuilder.getInstance().build();

    private String containerId;



    /**
     * gcc 镜像拉取 在类加载时只执行一次
     */
    static {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 检查镜像是否已经存在
        boolean imageExists = dockerClient.listImagesCmd().exec().stream()
                .anyMatch(image -> image.getRepoTags() != null &&
                        Arrays.asList(image.getRepoTags()).contains(DockerConstant.DOCKER_GCC_IMAGE));
        if (!imageExists){
            // 不存在 gcc 环境镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(DockerConstant.DOCKER_GCC_IMAGE);
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
            log.info("Image already exists: {}",DockerConstant.DOCKER_GCC_IMAGE);
        }
    }

    @Override
    public ExecuteResponse executeCode(ExecuteRequest executeRequest) {
        return exec(executeRequest);
    }

    @Override
    public String getStatus() {
        return "";
    }

    /**
     * 删除容器并释放资源
     */
    @Override
    public void deleteContainer() {
        DockerCommonUtils.deleteContainer(containerId,dockerClient);
    }

    /**
     * 编译代码文件
     * @param file 代码文件
     * @return 编辑结果
     */
    @Override
    public CompileMessage compileCode(File file) {
        // 1. 创建 gcc 容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(DockerConstant.DOCKER_GCC_IMAGE);
        // 1.1 同步本地代码文件到容器中
        HostConfig hostConfig = new HostConfig();
        String userCodePath = file.getParentFile().getAbsolutePath(); // 获取代码文件的父目录路径
        hostConfig.setBinds(new Bind(userCodePath, new Volume(DockerConstant.DOCKER_CODE_PATH)));
        // 1.2 执行创建容器
        CreateContainerResponse response = containerCmd.withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        containerId = response.getId();
        // 2.启动容器
        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
        startContainerCmd.exec();
        // 3.构建编译命令
        String[] cmd = new String[]{
                "sh","-c",
                "g++ -o /app/" + DockerConstant.DOCKER_GCC_COMPILE_NAME + " " + DockerConstant.DOCKER_CODE_PATH + "/Main.cpp"
        };

        ExecCreateCmdResponse cmdResponse = dockerClient.execCreateCmd(containerId)
                .withCmd(cmd)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .exec();
        String execId = cmdResponse.getId();
        if (StrUtil.isBlank(execId)){
            return CompileMessage.builder()
                    .success(false)
                    .reason("Failed to compile the cpp file")
                    .build();
        }
        // 4.执行编译命令
        // 4.1 构建执行回调函数
        CompileMessage compileMessage = new CompileMessage();
        ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){
            @Override
            public void onNext(Frame frame) {
                StreamType streamType = frame.getStreamType();
                if (StreamType.STDERR.equals(streamType)){
                    compileMessage.setSuccess(false);
                } else {
                    compileMessage.setSuccess(true);
                }
                String payload = new String(frame.getPayload());
                // 去掉所有换行符
                payload = payload.replaceAll("\\R", "");
                compileMessage.setReason(payload);
                System.out.println("Execution result：" + new String(frame.getPayload()));
                super.onNext(frame);
            }
        };
        // 4.2 执行编译指令
        try{
            dockerClient.execStartCmd(execId)
                    .exec(execStartResultCallback)
                    .awaitCompletion();
        }catch (InterruptedException e){
            log.error("The program execution compile is abnormal，{}", e.getStackTrace());
            throw new RuntimeException(e);
        }

        // 5. 编译成功返回
        compileMessage.setSuccess(true);
        return compileMessage;
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
        return PreRunMessage.builder()
                .success(true)
                .build();
    }

    @Override
    public RunCodeMessage runCode(List<String> inputs, String userCodePath) {
        // 1.运行前准备 输出的收集 执行信息的收集
        if (ObjectUtil.isNull(inputs)) {
            return RunCodeMessage.builder()
                    .success(false)
                    .exceptionMessage("The test case is null")
                    .build();
        }
        List<String> outputs = new ArrayList<>();
        List<ExecuteInfo> executeInfos = new ArrayList<>();
        // 2.开始执行程序
        for (String input : inputs) {

            // 2.1 将输入数据写入到容器内的文件
            String inputFilePath = DockerConstant.DOCKER_CODE_PATH + DockerConstant.DOCKER_TEST_CASE_INPUT;
            String hostInputFilePath = userCodePath + DockerConstant.DOCKER_TEST_CASE_INPUT;
            FileUtil.writeString(input, hostInputFilePath, StandardCharsets.UTF_8);

            // 2.2 构建运行 cpp 文件的命令
            String[] command = new String[]{
                    "sh", "-c","./app/main.exe < "+ inputFilePath
            };

            // 2.3 运行代码
            boolean success = LanguageCommonUtils.runCode(outputs, executeInfos, command, containerId, dockerClient);
            if (!success){
               return RunCodeMessage.builder()
                        .success(false)
                        .exceptionMessage("The use case failed to execute")
                        .build();
            }
        }
        System.out.println(outputs);
        System.out.println(executeInfos);
        return RunCodeMessage.builder()
                .success(true)
                .outputs(outputs)
                .executeInfos(executeInfos)
                .build();
    }
}
