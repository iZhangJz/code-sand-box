package com.zjz.codesandbox.utils;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.zjz.codesandbox.constant.CmdConstant;
import com.zjz.codesandbox.constant.CommonConstant;
import com.zjz.codesandbox.model.dto.CompileMessage;
import com.zjz.codesandbox.model.execute.ExecuteInfo;
import com.zjz.codesandbox.model.process.ProcessMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LanguageCommonUtils {

    /**
     * 编译 Java 代码
     * @param file
     * @return
     */
    public static CompileMessage compileJavaCode(File file){
        String responseMessage = "";
        String compileCommand = String.format(CmdConstant.JAVA_COMPILE_CMD, file.getAbsolutePath());

        try {
            Process exec = Runtime.getRuntime().exec(compileCommand);
            ProcessMessage compileMessage = ProcessUtils.runProcessAndMessage(exec, CmdConstant.COMPILE_OPERATION_NAME);

            if (compileMessage.getExitCode() == 0) {
                // 编译成功
                System.out.println(compileMessage.getSuccessMsg());
            } else {
                responseMessage = compileMessage.getErrorMsg();
                return CompileMessage.builder()
                        .success(false)
                        .reason(responseMessage)
                        .build();
            }
        } catch (IOException e) {
            throw new RuntimeException("Process Error", e);
        }
        return CompileMessage.builder()
                .success(true)
                .build();
    }

    /**
     * 运行代码，并收集结果
     */
    public static boolean runCode(List<String> outputs,List<ExecuteInfo> executeInfos,
                                  String[] cmd, String containerId, DockerClient dockerClient){

        // 1.1 创建执行命令的ExecCreateCmd
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withCmd(cmd)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .exec();
        String execId = execCreateCmdResponse.getId();
        if (StrUtil.isBlank(execId)){
            return false;
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

        // 1.2 使用ExecStartCmd执行命令
        try{
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            boolean completionInTime = dockerClient.execStartCmd(execId)
                    .exec(execStartResultCallback)
                    .awaitCompletion(CommonConstant.TIME_OUT, TimeUnit.MILLISECONDS);
            stopWatch.stop();
            if (!completionInTime){
                // 超时退出
                log.info("process terminated due to timeout");
                outputs.add("process terminated due to timeout");
            }
            // 1.3 设置执行信息
            Thread.sleep(1000);  // 增加一个短暂的等待时间，确保统计回调能及时处理数据
            executeInfo.setTime(stopWatch.getLastTaskTimeMillis());
            executeInfo.setMemory(maxMemoryUsage[0] / 8 / 1024);
            executeInfos.add(executeInfo);

        } catch (InterruptedException e){
            log.error("The program execution run is abnormal，{}", e.getStackTrace());
            throw new RuntimeException(e);
        }finally {
            statsCmd.close();
        }
        return true;
    }

}
