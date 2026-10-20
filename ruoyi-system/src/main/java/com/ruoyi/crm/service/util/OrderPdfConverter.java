package com.ruoyi.crm.service.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Excel -> PDF 转换：调用 LibreOffice headless（保真度最高，合并单元格/边框/图片/字体均保留）。
 *
 * 未安装 LibreOffice 时会抛出带提示的异常，由上层转成友好错误信息。
 */
public class OrderPdfConverter {

    private OrderPdfConverter() {
    }

    public static byte[] toPdf(byte[] xlsxBytes, String sofficePath) throws IOException {
        if (sofficePath == null || sofficePath.trim().isEmpty()) {
            throw new IOException("未配置 LibreOffice 路径(order.libreoffice-path)");
        }
        Path dir = Files.createTempDirectory("order-pdf-");
        try {
            Path xlsx = dir.resolve("doc.xlsx");
            Files.write(xlsx, xlsxBytes);

            List<String> cmd = Arrays.asList(
                    sofficePath, "--headless", "--norestore", "--nolockcheck",
                    "--convert-to", "pdf", "--outdir", dir.toString(), xlsx.toString());
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();

            String output;
            try (InputStream in = process.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int len;
                while ((len = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, len);
                }
                output = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("PDF 转换超时");
            }
            Path pdf = dir.resolve("doc.pdf");
            if (!Files.exists(pdf)) {
                throw new IOException("PDF 转换失败，请确认已安装 LibreOffice 且 order.libreoffice-path 配置正确。soffice="
                        + sofficePath + "，输出：" + output);
            }
            return Files.readAllBytes(pdf);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("PDF 转换被中断", e);
        } finally {
            deleteQuietly(dir);
        }
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响主流程
                }
            });
        } catch (IOException ignored) {
            // ignore
        }
    }
}
