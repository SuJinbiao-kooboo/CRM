package com.ruoyi.feishu.codex;

import com.ruoyi.feishu.config.FeishuProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Codex CLI 桥接器：通过非交互子进程 {@code codex exec} 执行指令，供飞书回调调用。
 * <p>调用参数（bin/工作目录/沙箱/超时）来自配置 {@code feishu.codex.*}。</p>
 */
@Component
public class CodexRunner {

    private static final Logger log = LoggerFactory.getLogger(CodexRunner.class);

    private final FeishuProperties props;

    public CodexRunner(FeishuProperties props) {
        this.props = props;
    }

    /**
     * 运行一次 Codex，返回最终回答。
     * @param prompt 交给 Codex 的指令（非空）
     * @return 运行结果（含最终结论或错误信息）
     */
    public CodexRunResult run(String prompt) {
        CodexRunResult result = new CodexRunResult();
        FeishuProperties.Codex cfg = props.getCodex();
        if (cfg == null || !cfg.isEnabled()) {
            result.setError("Codex 未启用：请在配置中设置 feishu.codex.enabled=true");
            return result;
        }
        String workdir = cfg.getWorkdir();
        if (workdir != null && !workdir.trim().isEmpty() && !Files.isDirectory(Paths.get(workdir))) {
            result.setError("Codex 工作目录不存在: " + workdir);
            return result;
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(cfg.getBin());
        cmd.add("exec");
        if (notBlank(cfg.getSandbox())) {
            cmd.add("-s");
            cmd.add(cfg.getSandbox());
        }
        if (notBlank(workdir)) {
            cmd.add("-C");
            cmd.add(workdir);
        }
        cmd.add("--skip-git-repo-check");
        cmd.add("--color");
        cmd.add("never");

        // 用 -o 直接把“最终回答”写入临时文件，避免从大量进度日志里捞结论
        Path outFile = null;
        try {
            outFile = Files.createTempFile("codex-", ".txt");
            cmd.add("-o");
            cmd.add(outFile.toString());
        } catch (IOException e) {
            log.warn("创建 Codex 输出临时文件失败，改用 stdout 捕获", e);
        }
        StringBuilder merged = new StringBuilder();
        try {
            // 快照产出目录，用于识别本次新产出/被修改的文件
            Map<String, Long> beforeFiles = new HashMap<>();
            Path outPath = null;
            String outDir = cfg.getOutputDirectory();
            if (outDir != null && !outDir.trim().isEmpty()) {
                outPath = Paths.get(outDir.trim());
                if (Files.isDirectory(outPath)) {
                    beforeFiles = snapshot(outPath);
                }
            }
            long runStart = System.currentTimeMillis();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            // 异步读取输出，避免管道缓冲区写满导致子进程阻塞
            Thread reader = new Thread(() -> {
                char[] buf = new char[4096];
                try (Reader r = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
                    int n;
                    while ((n = r.read(buf)) != -1) {
                        merged.append(buf, 0, n);
                    }
                } catch (IOException e) {
                    // ignore: 子进程结束或管道被关闭
                }
            });
            reader.setDaemon(true);
            reader.start();

            // 关键：把提示词写入子进程 stdin 并关闭（发送 EOF）。
            // codex 在非交互(无 TTY)环境下从 stdin 读提示词；若不关闭 stdin，
            // 它会一直等待直到超时。这里主动写入并 close 触发 EOF。
            try (java.io.OutputStream stdin = p.getOutputStream()) {
                stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            }

            long timeoutSeconds = cfg.getTimeoutSeconds() > 0 ? cfg.getTimeoutSeconds() : 300;
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                p.destroy();
                result.setTimedOut(true);
                result.setSuccess(false);
                result.setError("Codex 执行超时（" + timeoutSeconds + " 秒），已终止");
            } else {
                int exit = p.exitValue();
                result.setExitCode(exit);
                result.setSuccess(exit == 0);
                if (exit != 0) {
                    String err = tail(merged.toString(), 2000);
                    result.setError("Codex 退出码 " + exit + (err.isEmpty() ? "" : "：\n" + err));
                }
            }
            // 优先取最终回答文件，否则退回 stdout 尾部
            String msg = readOutFile(outFile);
            if (msg == null || msg.trim().isEmpty()) {
                msg = tail(merged.toString(), 4000);
            }
            result.setMessage(msg);
            // 采集本次产出的文件，供回传飞书使用
            if (result.isSuccess() && outPath != null && Files.isDirectory(outPath)) {
                collectNew(outPath, runStart, beforeFiles, result.getProducedFiles());
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError("调用 Codex 失败: " + e.getMessage());
            log.error("调用 Codex 失败, cmd={}", cmd, e);
        } finally {
            if (outFile != null) {
                try {
                    Files.deleteIfExists(outFile);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
        return result;
    }

    /** 快照目录下所有普通文件的绝对路径 -> 最后修改时间 */
    private Map<String, Long> snapshot(Path dir) {
        Map<String, Long> m = new HashMap<>();
        try (Stream<Path> st = Files.list(dir)) {
            st.filter(Files::isRegularFile).forEach(p -> {
                try {
                    m.put(p.toAbsolutePath().toString(), Files.getLastModifiedTime(p).toMillis());
                } catch (IOException ignored) {
                    // ignore
                }
            });
        } catch (IOException ignored) {
            // ignore
        }
        return m;
    }

    /** 收集本次运行期间新增或修改过的文件（绝对路径）写入 out */
    private void collectNew(Path dir, long start, Map<String, Long> before, List<String> out) {
        try (Stream<Path> st = Files.list(dir)) {
            st.filter(Files::isRegularFile).forEach(p -> {
                try {
                    long mt = Files.getLastModifiedTime(p).toMillis();
                    String key = p.toAbsolutePath().toString();
                    if (mt >= start || !before.containsKey(key) || !before.get(key).equals(mt)) {
                        out.add(key);
                    }
                } catch (IOException ignored) {
                    // ignore
                }
            });
        } catch (IOException ignored) {
            // ignore
        }
    }

    private String readOutFile(Path f) {
        if (f == null) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private String tail(String s, int max) {
        if (s == null) {
            return "";
        }
        int len = s.length();
        return len > max ? s.substring(len - max) : s;
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
