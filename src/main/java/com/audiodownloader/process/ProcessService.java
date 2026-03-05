package com.audiodownloader.process;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class ProcessService {
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();

    public int runCommand(String processId,
                          List<String> command,
                          Consumer<String> lineConsumer) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        runningProcesses.put(processId, process);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineConsumer.accept(line);
            }
        }

        int exit = process.waitFor();
        runningProcesses.remove(processId);
        return exit;
    }

    public void cancel(String processId) {
        Process process = runningProcesses.get(processId);
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    public boolean isRunning(String processId) {
        Process process = runningProcesses.get(processId);
        return process != null && process.isAlive();
    }
}
