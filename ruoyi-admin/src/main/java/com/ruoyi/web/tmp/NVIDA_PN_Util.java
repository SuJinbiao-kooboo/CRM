package com.ruoyi.web.tmp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NVIDA_PN_Util {

    // 完全使用Postman中的Cookie
    private static final String COOKIE = "_abck=E90767F14B1B8E7A01A10F90BFC1EEE3~-1~YAAQFtgjF7Rm7w6cAQAAA/lRSw+hW8X1wGERwyvShy/+i7GzXG8SYGeYjiGli9TcP8dw0xcNaicqp1vocwCprefmSwPV4owPAd+lnhv6I03lyvl+9obn/T+rbJlQfbjqIiC6wittlSf5fAU69xp4tG0tAX+oGkeCzF6UblmcCqzhg+7qedtUJT7sm4mNHiX1OqnnDlMNAZR5pZqRwi4c2oTVSfvAaltpQSs5qS01fu80FMDhSHK/ObarpcQVw5Phod1IB6dbfZCwWExn0OcPLCd81lPU9aqfr7SjchoaR3Ow4CXiYPtB9JUMCvr8mGXOmtpBIslSPX7BoWCwUVLISnuJW/TvhGLjVnLA3eYYPejPEZMFLD2Woze2BBqsPJ1fy+Vpqd8uNbHFt3ElFYvH5ywtqGbZP5WzV0mFgTCwPEjgX+4geerk2rmNg1ayFDfaLTiqgx57AJIxJhurTI7wzG6RH9shTA==~-1~-1~-1~-1~-1; bm_sz=61D0DF6491C1887A202B29DE05C240C6~YAAQFtgjF7Zm7w6cAQAAA/lRSx5HlazwBmuM/fAo4Ab5gdTJR1xd+DwN8VC0peoRUMsZ/lnVtfIgKrpXg9+aR0nch9fJPceg2u+fvzjdnYmm+8abAoPjoBWM1kVxR0kbfYH7qWqPv3f3OwPMYqjvjtrQQTkW/R1ONLuH1hLZrzSg1PbIt4LjF0QDwbzEA/QqVhqp/0gZA9LXh5wq0lb2rfMboez2CEXDOX1dPk6HivmXMb9IrDq54x8vnssdsujfxc87fjg3r9Y2WdpYk3jy3du7X2C56c4GUXnrjNXcwG6cE/uX6nyXlciRvVvCL2SR1mCMYAaHTsbiRFoWui/npJFOfWDppULvZch+X2i+~3686967~3555889; ak_bmsc=5D98DD78381B0344758EE5F86BEBACA9~000000000000000000000000000000~YAAQKATYF98pOTucAQAAgGEFTB69GGzRD3PeJnqFNpcFS+udP8VlymUmp/qk9W70SMIl1uscEUk0eWm+FTP7o5QG+BXkIXlE6balsvrEcrtLin8uKBcrnkoPnqctbvVJm7H4kXWfx+IdQO6qXsI9/4zcmr5yb+mzUq0hyo/nONL6duFzi/RZFU4I/Q9uj5ToeN8pzQEj0VLyh2+chHIHWrH70z3L8JLjqQJCkBALA9jAtKDCpFfbhE8SqChPfxNgEDBpSKK6lESMB7XszwarCoBxJB5tX5f8KmxwG5NwNnBdNKclMEvjm11XYotFtuGf+7pIHu3Vs+MorUgQQDoqOnHhFp2uWyAUGJuv6npilh8Avp8=";
    private static String decodeWithCharset(byte[] bytes) {
        // 尝试常见的中文编码
        String[] charsets = {"UTF-8", "GBK", "GB2312", "ISO-8859-1", "GB18030"};

        for (String charset : charsets) {
            try {
                String result = new String(bytes, charset);
                // 简单验证是否包含常见中文字符
                if (isLikelyChineseText(result)) {
                    System.out.println("检测到编码: " + charset);
                    return result;
                }
            } catch (Exception e) {
                // 继续尝试下一种编码
            }
        }

        // 如果都不行，回退到UTF-8
        return new String(bytes, StandardCharsets.UTF_8);
    }


    private static boolean isLikelyChineseText(String text) {
        // 检查是否包含常见中文字符
        if (text.length() > 100) {
            text = text.substring(0, 100);
        }
        long chineseChars = text.codePoints()
                .filter(cp -> cp >= 0x4E00 && cp <= 0x9FFF) // 基本汉字范围
                .count();
        return chineseChars > 3; // 包含至少3个中文字符
    }

    // 创建OkHttpClient，模拟Postman的行为
    private static OkHttpClient client = createOkHttpClient();

    public static void main(String[] args) {
//        Map<String, String> inputMap = NVIDAUtil.getTargetMap();
        Map<String, String> inputMap = CompanyCodeMap.getCompanyCodeMap();
        for (Map.Entry<String, String> entry : inputMap.entrySet()) {
            System.out.println(entry.getKey() + "\t" + entry.getValue());
        }
        List<PartnerInfo> partnerInfoList = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        System.out.println("开始获取合作伙伴信息...共" + inputMap.size() + "条记录");

        // 首先测试一个请求
        System.out.println("测试第一个请求...");
        try {
            Request testRequest = createRequest("xamplify-services");
            try (Response testResponse = client.newCall(testRequest).execute()) {
                System.out.println("测试HTTP状态码: " + testResponse.code());
                System.out.println("测试响应头: " + testResponse.headers());
                if (testResponse.isSuccessful() && testResponse.body() != null) {
                    String responseBody = testResponse.body().string();
                    System.out.println("测试成功! 响应长度: " + responseBody.length());
                    if (responseBody.length() > 0) {
                        JsonNode testNode = objectMapper.readTree(responseBody);
                        System.out.println("测试响应示例: " + testNode.toString().substring(0, Math.min(200, testNode.toString().length())) + "...");
                    }
                } else {
                    System.out.println("测试失败!");
                    if (testResponse.body() != null) {
                        System.out.println("错误响应: " + testResponse.body().string());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("测试请求异常: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        int processed = 0;
        for (Map.Entry<String, String> entry : inputMap.entrySet()) {
            processed++;
            String countryCode = entry.getKey();
            String partnerCode = entry.getValue();
//            String partnerCode = "beantech";

            System.out.printf("\n[%d/%d] 处理: %s - %s%n", processed, inputMap.size(), countryCode, partnerCode);

            try {
                // 添加延迟
                if (processed > 1) {
//                    Thread.sleep(1000);
                }

                // 每处理5个休息一下
                if (processed % 5 == 0) {
                    System.out.println("已处理" + processed + "条，等待2秒...");
                    client = createOkHttpClient();
                    Thread.sleep(2000);
                }

                Request request = createRequest(partnerCode);
                int times = 0;
                while (times<6){
                    try{
                        extracted(request, objectMapper, countryCode, partnerCode, partnerInfoList);
                        break;
                    }catch (Exception ex){
                        times++;
                    }
                }


            } catch (Exception e) {
                System.err.println("✗ 处理失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        generateCSV(partnerInfoList);
        System.out.println("\n处理完成! 成功获取 " + partnerInfoList.size() + " 条记录");
    }

    private static void extracted(Request request, ObjectMapper objectMapper, String countryCode, String partnerCode, List<PartnerInfo> partnerInfoList) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            System.out.println("  HTTP状态码: " + response.code());

            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();

                if (responseBody != null && !responseBody.trim().isEmpty()) {
                    JsonNode rootNode = objectMapper.readTree(responseBody);

                    // 检查是否有该国家的数据
                    JsonNode countryNode = rootNode.path(countryCode);
                    if (!countryNode.isMissingNode()) {
                        PartnerInfo info = parsePartnerInfo(countryNode, countryCode, partnerCode);
                        partnerInfoList.add(info);
                        System.out.println("✓ 成功获取: " + info.getPartnerName());
                    } else {
                        // 如果没有找到特定国家，使用第一个国家的数据
                        Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
                        if (fields.hasNext()) {
                            Map.Entry<String, JsonNode> firstField = fields.next();
                            PartnerInfo info = parsePartnerInfo(firstField.getValue(), firstField.getKey(), partnerCode);
                            partnerInfoList.add(info);
                            System.out.println("⚠ 使用 " + firstField.getKey() + " 的数据: " + info.getPartnerName());
                        } else {
                            System.out.println("✗ 返回数据为空");
                        }
                    }

                } else {
                    System.out.println("✗ API返回为空");
                }
            } else {
                System.out.println("✗ HTTP请求失败: " + response.code());
                if (response.body() != null) {
                    System.out.println("错误信息: " + response.body().string());
                }
            }
        }
    }

    private static OkHttpClient createOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                // Postman默认配置
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                // 重要：禁用Cookie自动管理，使用手动设置的Cookie
                .cookieJar(new CookieJar() {
                    private final List<Cookie> cookies = new ArrayList<>();

                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        this.cookies.addAll(cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        return cookies;
                    }
                })
                .build();
    }

    private static Request createRequest(String partnerCode) throws UnsupportedEncodingException {
        String url = "https://api.store.nvidia.com/products/v1/partner-info?pn=" +
                URLEncoder.encode(partnerCode, StandardCharsets.UTF_8.toString());

        return new Request.Builder()
                .url(url)
                // 完全按照Postman的设置
                .header("authority", "api.store.nvidia.com")
                .header("origin", "https://marketplace.nvidia.com")
                .header("referer", "https://marketplace.nvidia.com/")
                .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                .header("Cookie", COOKIE)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Cache-Control", "no-cache")
                // Postman默认会添加这些头
                .header("sec-ch-ua", "\"Chromium\";v=\"144\", \"Google Chrome\";v=\"144\", \"Not-A.Brand\";v=\"24\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-site")
                .build();
    }

    private static PartnerInfo parsePartnerInfo(JsonNode node, String countryCode, String partnerCode) {
        PartnerInfo info = new PartnerInfo();
        info.setCountryCode(countryCode);
        info.setPartnerCode(partnerCode);
        info.setPartnerName(cleanText(node.path("partnerName").asText()));
        info.setCountryName(cleanText(node.path("countryName").asText()));
        info.setPartnerSiteLink(cleanText(node.path("partnerSiteLink").asText()));
        info.setExemplar(node.path("exemplar").asBoolean());
        info.setLogo(node.path("logo").asText());

        List<String> addresses = new ArrayList<>();
        JsonNode addressArray = node.path("address");
        if (addressArray.isArray()) {
            for (JsonNode addrNode : addressArray) {
                addresses.add(cleanText(addrNode.asText()));
            }
        }
        info.setAddresses(addresses);

        Map<String, List<PartnerTypeInfo>> partnerTypes = new HashMap<>();
        JsonNode partnerTypesNode = node.path("partnerTypes");
        if (partnerTypesNode.isObject()) {
            partnerTypesNode.fields().forEachRemaining(field -> {
                String type = field.getKey();
                List<PartnerTypeInfo> typeInfos = new ArrayList<>();

                if (field.getValue().isArray()) {
                    field.getValue().forEach(typeNode -> {
                        PartnerTypeInfo typeInfo = new PartnerTypeInfo();
                        typeInfo.setLevel(cleanText(typeNode.path("level").asText()));
                        typeInfo.setCompetency(cleanText(typeNode.path("competency").asText()));
                        typeInfos.add(typeInfo);
                    });
                }
                partnerTypes.put(type, typeInfos);
            });
        }
        info.setPartnerTypes(partnerTypes);

        return info;
    }

    private static String cleanText(String text) {
        return text == null ? "" : text.replace(",", "，");
    }

    private static void generateCSV(List<PartnerInfo> partnerInfoList) {
        try (FileWriter writer = new FileWriter("partners_info.csv")) {
            writer.write("country_code,partner_code,partner_name,country_name,partner_site,exemplar,logo,address,partner_types\n");

            for (PartnerInfo info : partnerInfoList) {
                String line = String.format("%s,%s,%s,%s,%s,%s,%s,\"%s\",\"%s\"%n",
                        info.getCountryCode(),
                        info.getPartnerCode(),
                        info.getPartnerName(),
                        info.getCountryName(),
                        info.getPartnerSiteLink(),
                        info.isExemplar(),
                        info.getLogo(),
                        formatAddresses(info.getAddresses()),
                        formatPartnerTypes(info.getPartnerTypes())
                );
                writer.write(line);
            }
            System.out.println("CSV文件已生成: partners_info.csv");
        } catch (IOException e) {
            System.err.println("生成CSV失败: " + e.getMessage());
        }
    }

    private static String formatAddresses(List<String> addresses) {
        return addresses == null || addresses.isEmpty() ? "" : String.join(" | ", addresses);
    }

    private static String formatPartnerTypes(Map<String, List<PartnerTypeInfo>> partnerTypes) {
        if (partnerTypes == null || partnerTypes.isEmpty()) return "";

        List<String> types = new ArrayList<>();
        partnerTypes.forEach((type, typeInfos) -> {
            List<String> infos = new ArrayList<>();
            typeInfos.forEach(info -> infos.add(info.getLevel() + ":" + info.getCompetency()));
            types.add(type + "[" + String.join(";", infos) + "]");
        });
        return String.join(" | ", types);
    }

    static class PartnerInfo {
        private String countryCode;
        private String partnerCode;
        private String partnerName;
        private String countryName;
        private String partnerSiteLink;
        private boolean exemplar;
        private String logo;
        private List<String> addresses;
        private Map<String, List<PartnerTypeInfo>> partnerTypes;

        // Getters and Setters
        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

        public String getPartnerCode() { return partnerCode; }
        public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }

        public String getPartnerName() { return partnerName; }
        public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

        public String getCountryName() { return countryName; }
        public void setCountryName(String countryName) { this.countryName = countryName; }

        public String getPartnerSiteLink() { return partnerSiteLink; }
        public void setPartnerSiteLink(String partnerSiteLink) { this.partnerSiteLink = partnerSiteLink; }

        public boolean isExemplar() { return exemplar; }
        public void setExemplar(boolean exemplar) { this.exemplar = exemplar; }

        public String getLogo() { return logo; }
        public void setLogo(String logo) { this.logo = logo; }

        public List<String> getAddresses() { return addresses; }
        public void setAddresses(List<String> addresses) { this.addresses = addresses; }

        public Map<String, List<PartnerTypeInfo>> getPartnerTypes() { return partnerTypes; }
        public void setPartnerTypes(Map<String, List<PartnerTypeInfo>> partnerTypes) { this.partnerTypes = partnerTypes; }
    }

    static class PartnerTypeInfo {
        private String level;
        private String competency;

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }

        public String getCompetency() { return competency; }
        public void setCompetency(String competency) { this.competency = competency; }
    }
}