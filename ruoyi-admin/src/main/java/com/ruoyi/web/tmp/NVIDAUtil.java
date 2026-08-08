package com.ruoyi.web.tmp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NVIDAUtil {

    public static Map<String, String> getTargetMap() {
        // 定义文件路径
        String[] filePaths = {
                "C:\\Users\\亦钟人\\Downloads\\page1.txt",
                "C:\\Users\\亦钟人\\Downloads\\page2.txt",
                "C:\\Users\\亦钟人\\Downloads\\page3.txt",
                "C:\\Users\\亦钟人\\Downloads\\page4.txt",
                "C:\\Users\\亦钟人\\Downloads\\page5.txt",
                "C:\\Users\\亦钟人\\Downloads\\page6.txt",
                "C:\\Users\\亦钟人\\Downloads\\page7.txt",
                "C:\\Users\\亦钟人\\Downloads\\page8.txt",
                "C:\\Users\\亦钟人\\Downloads\\page9.txt",
                "C:\\Users\\亦钟人\\Downloads\\page10.txt",
                "C:\\Users\\亦钟人\\Downloads\\page11.txt",
                "C:\\Users\\亦钟人\\Downloads\\page12.txt"
        };

        Map<String, String> csvLines = new HashMap<>();
        // 添加CSV表头

        ObjectMapper objectMapper = new ObjectMapper();

        for (String filePath : filePaths) {
            try {
                // 读取文件内容
                String content = new String(Files.readAllBytes(Paths.get(filePath)));

                // 解析JSON
                JsonNode rootNode = objectMapper.readTree(content);
                JsonNode partnersNode = rootNode.path("partners");

                // 遍历partners数组
                if (partnersNode.isArray()) {
                    for (JsonNode partnerNode : partnersNode) {
                        String name = partnerNode.path("name").asText("");
                        String code = partnerNode.path("code").asText("");

                        // 转义CSV中的特殊字符（引号和逗号）
                        name = escapeCsv(name);
                        code = escapeCsv(code);

                        csvLines.put(name, code);
                    }
                }

            } catch (IOException e) {
                System.err.println("Error processing file: " + filePath);
                e.printStackTrace();
            }
        }

        return csvLines;
    }

    private static String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        // 如果包含逗号、双引号或换行符，用双引号包裹
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            // 转义双引号
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }
        return value;
    }


}
