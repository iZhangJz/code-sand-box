package com.zjz.codesandbox.utils;

import com.zjz.codesandbox.constant.CommonConstant;
import com.zjz.codesandbox.constant.FileConstant;
import com.zjz.codesandbox.model.process.ProcessMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.zjz.codesandbox.constant.CmdConstant.*;

/**
 * 进程工具类
 */
@Slf4j
public class ProcessUtils {


    private static long getWinMemoryUsage(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            reader.readLine(); // Skip the header line
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                // 使用正则表达式匹配逗号分隔符，同时忽略内存信息中的逗号
                String[] parts = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                if (parts.length > 4) {
                    try {
                        // Clean non-numeric characters
                        return Long.parseLong(parts[4].trim().replaceAll("[^0-9]", ""));
                    } catch (NumberFormatException e) {
                        log.error("无法解析内存使用情况：{}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
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
                    return Long.parseLong(line.trim());
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
    public static void buildProcessSuccessOutput(
            Process runProcess, StringBuilder SuccessStringBuilder, CountDownLatch latch) {
        Thread outputReaderThread = new Thread(() -> {
            try (BufferedReader reader
                         = new BufferedReader(new InputStreamReader(runProcess.getInputStream(), FileConstant.ENCODING_GBK))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    SuccessStringBuilder.append(line).append("\n");
                }
            } catch (IOException e) {
                log.error("读取进程输出时发生错误：{}", e.getMessage());
            } finally {
                latch.countDown();  // 确保在任务完成后减少计数
            }
        });
        outputReaderThread.start();
    }

    /**
     * 获取进程错误输出信息
     * @param runProcess 进程
     * @return 进程错误输出信息
     */
    public static void buildProcessErrorOutput(
            Process runProcess, StringBuilder ErrorStringBuilder, CountDownLatch latch) {
        Thread errorReaderThread = new Thread(() -> {
            try (BufferedReader reader
                         = new BufferedReader(new InputStreamReader(runProcess.getErrorStream(), FileConstant.ENCODING_GBK))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ErrorStringBuilder.append(line).append("\n");
                    log.error("进程错误输出：{}", line);
                }
            } catch (IOException e) {
                log.error("读取进程错误输出时发生错误：{}", e.getMessage());
            } finally {
                latch.countDown();  // 确保在任务完成后减少计数
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

            // 创建线程池
            CountDownLatch latch = new CountDownLatch(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            // 提交任务获取标准输出
            Future<?> successFuture = executor.submit(() -> buildProcessSuccessOutput(runProcess, SuccessStringBuilder, latch));

            // 提交任务获取错误输出
            Future<?> errorFuture = executor.submit(() -> buildProcessErrorOutput(runProcess, ErrorStringBuilder, latch));
            // 等待任务完成
            latch.await();
            successFuture.get();
            errorFuture.get();

            // 关闭线程池
            executor.shutdown();
            if (exit == 0) {
                log.info(opName + "成功");
                // 获取控制台输出的信息
                processMessage.setSuccessMsg(SuccessStringBuilder.toString());
            }else {
                // 获取控制台输出的错误信息
                // 获取错误信息字符串
                String errorStr = ErrorStringBuilder.toString();
                int index = errorStr.indexOf("Main");
                // 设置错误消息
                processMessage.setErrorMsg(index != -1 ? errorStr.substring(index) : errorStr);
                // 记录日志
                log.error("{} 失败： {} 退出码：{}", opName, ErrorStringBuilder, exit);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return processMessage;
    }

    public static ProcessMessage runProcessAndMessage(Process runProcess, String opName, String input,long memory) {
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
                maxMemoryUsage[0] = Math.max(memoryUsage, maxMemoryUsage[0]);
            } catch (Exception e) {
                log.error("获取内存使用情况失败：{}", e.getMessage());
            }
        };

        try {
            // 每 10 ms 监控一次内存使用情况
            scheduler.scheduleAtFixedRate(memoryMonitor, 10, 10, TimeUnit.MILLISECONDS); // 定期检查内存使用情况

            outputStreamWriter.write(String.join("\n", split) + "\n");
            stopWatch.start(); // 开始计时
            outputStreamWriter.flush();

            // 监控是否超时
            AtomicBoolean isTerminated  = new AtomicBoolean(false);
            monitorProcessTime(runProcess,isTerminated);

            // 异步读取标准输出和标准错误输出，防止阻塞
            StringBuilder SuccessStringBuilder = new StringBuilder();
            StringBuilder ErrorStringBuilder = new StringBuilder();

            // 创建线程池
            CountDownLatch latch = new CountDownLatch(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            // 提交任务获取标准输出
            Future<?> successFuture = executor.submit(() -> buildProcessSuccessOutput(runProcess, SuccessStringBuilder, latch));

            // 提交任务获取错误输出
            Future<?> errorFuture = executor.submit(() -> buildProcessErrorOutput(runProcess, ErrorStringBuilder, latch));
            // 等待任务完成
            latch.await();
            successFuture.get();
            errorFuture.get();

            // 关闭线程池
            executor.shutdown();
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
                if (isTerminated.get()){
                    // 运行超时
                    processMessage.setErrorMsg("process terminated due to timeout");
                    log.info(opName + "失败:"+ "process terminated due to timeout，测试用例为：{}", input);
                } else {
                    processMessage.setErrorMsg(ErrorStringBuilder.toString());
                    log.info(opName + "失败，测试用例为：{}", input);
                }
            }

            if (runProcess.isAlive()){
                runProcess.destroy();
            }
        } catch (IOException | InterruptedException e) {
            processMessage.setSuccessMsg(null);
            processMessage.setErrorMsg(e.getMessage());
            log.error(opName + "失败，测试用例为：{}", input);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
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
            processMessage.setMemoryUsage(maxMemoryUsage[0]);
        }
        return processMessage;
    }

    /**
     * 守护线程，监控进程运行时间，超时则终止进程
     * @param runProcess
     */
    public static void monitorProcessTime(Process runProcess, AtomicBoolean isTerminated){
        new Thread(() -> {
            try {
                Thread.sleep(CommonConstant.TIME_OUT);
                long pid = runProcess.pid();
                if (runProcess.isAlive()) {
                    runProcess.destroy();
                    isTerminated.set(true); // 设置终止标志
                    log.info("进程 {} 运行超时，已终止", pid);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

}
