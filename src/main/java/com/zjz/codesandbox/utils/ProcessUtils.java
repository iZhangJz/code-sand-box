package com.zjz.codesandbox.utils;

import com.zjz.codesandbox.model.process.ProcessMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.zjz.codesandbox.constant.CmdConstant.*;

/**
 * 进程工具类
 */
@Slf4j
public class ProcessUtils {


    private static long getWinMemoryUsage(String command) {
        try{
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            reader.readLine(); // Skip the header line
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                String[] parts = line.split(",");
                if (parts.length > 4) {
                    try {
                        // Clean non-numeric characters
                        return Long.parseLong(parts[4].trim().replaceAll("[^0-9]", ""));
                    } catch (NumberFormatException e) {
                        log.error("无法解析内存使用情况：{}", e.getMessage());
                    }
                }
            }
        }catch (IOException e){
            log.error("获取内存使用情况失败：{}", e.getMessage());
            throw new RuntimeException();
        }
        return -1;
    }

    private static long getLinuxMemoryUsage(String command) {
        try{
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            reader.readLine(); // Skip the header line
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                try {
                    return Long.parseLong(line.trim()) * 1024; // Convert KB to bytes
                } catch (NumberFormatException e) {
                    log.error("无法解析内存使用情况：{}", e.getMessage());
                }
            }
        }catch (IOException e) {
            log.error("获取内存使用情况失败：{}", e.getMessage());
            throw new RuntimeException();
        }
        return -1;
    }

    /**
     * 获取 Windows 内存使用情况
     * @param pid 进程号
     * @return 内存使用量，单位为字节
     */
    private static long getMemoryUsage(long pid){
        String command;
        String os = System.getProperty("os.name").toLowerCase();
        long res = -1;
        if (os.contains("win")) {
            command = WINDOWS_TASKLIST_CMD_PREFIX + pid + WINDOWS_TASKLIST_CMD_SUFFIX;
            res = getWinMemoryUsage(command);
        }else {
            command = LINUX_TASKLIST_CMD_PREFIX + pid + LINUX_TASKLIST_CMD_SUFFIX;;
            res = getLinuxMemoryUsage(command);
        }
        return res;
    }


    /**
     * 获取进程输出信息 try-with-resources会自动关闭流，无论是正常结束还是抛出异常
     * @param runProcess 进程
     * @return 进程输出信息
     */
    public static void buildProcessSuccessOutput(Process runProcess, StringBuilder SuccessStringBuilder) {
        Thread outputReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    SuccessStringBuilder.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                log.error("读取进程输出时发生错误：{}", e.getMessage());
            }
        });
        outputReaderThread.start();
    }

    /**
     * 获取进程错误输出信息
     * @param runProcess 进程
     * @return 进程错误输出信息
     */
    public static void buildProcessErrorOutput(Process runProcess, StringBuilder ErrorStringBuilder) {
        Thread errorReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ErrorStringBuilder.append(line).append(System.lineSeparator());
                    log.error("进程错误输出：{}", line);
                }
            } catch (IOException e) {
                log.error("读取进程错误输出时发生错误：{}", e.getMessage());
            }
        });
        errorReaderThread.start();
    }




    /**
     * 运行进程并返回信息
     * @param runProcess 进程
     * @param opName 操作名称
     * @return ProcessMessage
     */
    public static ProcessMessage runProcessAndMessage(Process runProcess,String opName){
        ProcessMessage processMessage = new ProcessMessage();
        try {
            int exit = runProcess.waitFor();
            processMessage.setExitCode(exit);
            StringBuilder SuccessStringBuilder = new StringBuilder();
            StringBuilder ErrorStringBuilder = new StringBuilder();

            buildProcessSuccessOutput(runProcess, SuccessStringBuilder);
            buildProcessErrorOutput(runProcess, ErrorStringBuilder);
            if (exit == 0) {
                log.info(opName + "成功");
                // 获取控制台输出的信息
                processMessage.setSuccessMsg(SuccessStringBuilder.toString());
            }else {
                // 获取控制台输出的错误信息
                processMessage.setErrorMsg(ErrorStringBuilder.toString());
                log.error(opName + "失败： {} 退出码：{}",ErrorStringBuilder,exit);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return processMessage;
    }

    public static ProcessMessage runProcessAndMessage(Process runProcess, String opName, String input) {
        ProcessMessage processMessage = new ProcessMessage();
        StopWatch stopWatch = new StopWatch();
        OutputStream outputStream = runProcess.getOutputStream();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);

        String[] split = input.split(" ");
        long pid = runProcess.pid();
        final long[] maxMemoryUsage = {0};

        // 创建一个定时执行器，用于定时监控进程内存使用情况
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        // 定义一个用于监控内存的任务
        Runnable memoryMonitor = () -> {
            try {
                long memoryUsage = getMemoryUsage(pid); // 获取当前进程内存使用情况
                if (memoryUsage > maxMemoryUsage[0]) {
                    maxMemoryUsage[0] = memoryUsage; // 更新最大内存使用情况
                }
            } catch (Exception e) {
                log.error("获取内存使用情况失败：{}", e.getMessage());
            }
        };

        try {
            // 每 10 ms 监控一次内存使用情况
            scheduler.scheduleAtFixedRate(memoryMonitor, 0, 10, TimeUnit.MILLISECONDS); // 定期检查内存使用情况

            outputStreamWriter.write(String.join("\n", split) + "\n");
            stopWatch.start(); // 开始计时
            outputStreamWriter.flush();

            // 异步读取标准输出和标准错误输出，防止阻塞
            StringBuilder SuccessStringBuilder = new StringBuilder();
            StringBuilder ErrorStringBuilder = new StringBuilder();

            buildProcessSuccessOutput(runProcess, SuccessStringBuilder);
            buildProcessErrorOutput(runProcess, ErrorStringBuilder);

            // 检查进程是否存活并等待进程结束
            if (runProcess.isAlive()) {
                runProcess.waitFor();
            }
            stopWatch.stop(); // 停止计时

            outputStreamWriter.close();
            outputStream.close();

            int exitCode = runProcess.exitValue();
            processMessage.setExitCode(exitCode);
            processMessage.setExecuteTime(stopWatch.getLastTaskTimeMillis());
            if (exitCode == 0){
                processMessage.setSuccessMsg(SuccessStringBuilder.toString());
                log.info(opName + "成功，测试用例为：{}", input);
            }else {
                processMessage.setErrorMsg(ErrorStringBuilder.toString());
                log.info(opName + "失败，测试用例为：{}", input);
            }

            runProcess.destroy();
        } catch (IOException | InterruptedException e) {
            processMessage.setSuccessMsg(null);
            processMessage.setErrorMsg(e.getMessage());
            log.error(opName + "失败，测试用例为：{}", input);
        } finally {
            scheduler.shutdown(); // 停止内存监控
            try {
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }

            try {
                outputStreamWriter.close();
                outputStream.close();
            } catch (IOException e) {
                log.error("关闭输出流时发生错误：{}", e.getMessage());
            }
            processMessage.setMemoryUsage(maxMemoryUsage[0]); // 记录最大内存使用情况
        }
        return processMessage;
    }

}
