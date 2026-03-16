package com.edtronaut.worker.service;

import com.edtronaut.worker.model.Execution;
import com.edtronaut.worker.model.ExecutionStatus;
import com.edtronaut.worker.repository.ExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CodeExecutorService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutorService.class);

    @Value("${app.executor.timeout-seconds}")
    private int timeoutSeconds;

    @Value("${app.executor.memory-limit}")
    private String memoryLimit;

    @Value("${app.executor.workspace-base}")
    private String workspaceBase;

    private final ExecutionRepository executionRepository;

    public CodeExecutorService(@Value("${app.executor.timeout-seconds}") int timeoutSeconds,
            @Value("${app.executor.memory-limit}") String memoryLimit,
            @Value("${app.executor.workspace-base}") String workspaceBase,
            ExecutionRepository executionRepository) {
        this.timeoutSeconds = timeoutSeconds;
        this.memoryLimit = memoryLimit;
        this.workspaceBase = workspaceBase;
        this.executionRepository = executionRepository;
    }

    public void execute(UUID executionId) {

        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution not found: " + executionId));

        if (execution.getStatus() != ExecutionStatus.QUEUED) {
            log.warn("Execution {} is not QUEUED", executionId);
            return;
        }

        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setStartedAt(Instant.now());
        executionRepository.save(execution);

        Path workspace = Path.of(workspaceBase, "execution-" + executionId);

        try {

            Files.createDirectories(workspace);

            LanguageConfig config = LanguageConfig.forLanguage(execution.getLanguage());

            String sourceCode = execution.getSourceCode() != null ? execution.getSourceCode().stripTrailing() : "";
            System.out.println("Source code: " + sourceCode);
            Files.writeString(workspace.resolve(config.fileName()), sourceCode);

            long startTime = System.currentTimeMillis();

            // -------------------
            // COMPILE STEP
            // -------------------

            if (config.compileCommand() != null) {
                Process compile = runDocker(workspace, config.dockerImage(), config.compileCommand());
                boolean completed = compile.waitFor(timeoutSeconds, TimeUnit.SECONDS);

                if (!completed) {
                    compile.destroyForcibly();
                    execution.setStatus(ExecutionStatus.FAILED);
                    execution.setStderr("Compilation timed out after " + timeoutSeconds + " seconds");
                    executionRepository.save(execution);
                    return;
                }

                int compileExit = compile.exitValue();
                if (compileExit != 0) {
                    String err = new String(compile.getErrorStream().readAllBytes());
                    execution.setStatus(ExecutionStatus.FAILED);
                    execution.setStderr(err);
                    executionRepository.save(execution);
                    return;
                }
            }

            // -------------------
            // RUN STEP
            // -------------------

            Process process = runDocker(workspace, config.dockerImage(), config.runCommand());

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            long elapsed = System.currentTimeMillis() - startTime;

            if (!completed) {

                process.destroyForcibly();

                execution.setStatus(ExecutionStatus.TIMEOUT);
                execution.setStderr("Execution timed out after " + timeoutSeconds + " seconds");

            } else {

                String stdout = readWithLimit(process.getInputStream(), 1024 * 100); // 100KB limit
                String stderr = readWithLimit(process.getErrorStream(), 1024 * 100); // 100KB limit

                int exitCode = process.exitValue();

                execution.setStdout(stdout);
                execution.setStderr(stderr);

                execution.setStatus(
                        exitCode == 0
                                ? ExecutionStatus.COMPLETED
                                : ExecutionStatus.FAILED);
            }

            execution.setExecutionTimeMs((int) elapsed);

        } catch (Exception e) {

            log.error("Execution failed", e);

            execution.setStatus(ExecutionStatus.FAILED);
            execution.setStderr(e.getMessage());

        } finally {

            execution.setFinishedAt(Instant.now());
            executionRepository.save(execution);

            cleanupWorkspace(workspace);
        }
    }

    private Process runDocker(Path workspace, String image, List<String> cmd) throws IOException {

        List<String> command = new ArrayList<>();

        command.addAll(List.of(
                "docker", "run", "--rm",
                "-m", memoryLimit,
                "--cpus", "0.5",
                "--pids-limit", "64",
                "--network", "none",
                "--ulimit", "fsize=10000000:10000000", // Limit file size to 10MB
                "-v", workspace.toAbsolutePath().toString().replace("\\", "/") + ":/app",
                "-w", "/app",
                image));

        command.addAll(cmd);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        log.info("Docker command: {}", String.join(" ", command));

        return pb.start();
    }

    private void cleanupWorkspace(Path workspace) {
        try {
            if (Files.exists(workspace)) {
                Files.walk(workspace)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException ignored) {
                            }
                        });
                log.info("Workspace cleaned: {}", workspace);
            }
        } catch (IOException e) {
            log.warn("Failed to clean workspace: {}", workspace, e);
        }
    }
    private String readWithLimit(InputStream is, int limit) throws IOException {
        byte[] buffer = new byte[Math.min(limit, 8192)];
        StringBuilder result = new StringBuilder();
        int totalRead = 0;
        int n;
        while ((n = is.read(buffer)) != -1) {
            if (totalRead + n > limit) {
                result.append(new String(buffer, 0, limit - totalRead));
                result.append("\n[Output truncated due to size limit]");
                break;
            }
            result.append(new String(buffer, 0, n));
            totalRead += n;
        }
        return result.toString();
    }
}
