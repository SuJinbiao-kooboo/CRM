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
    private static final String COOKIE = "_abck=E90767F14B1B8E7A01A10F90BFC1EEE3~-1~YAAQNv7DFwEdbQqcAQAAwuIZGQ8e1miWnb0YZB3/nK4ijWZGH1/AsJL7RjoIgIkKwIYb7BU0zKPaPJkk054TJJ0zAucV3ghire8lzp+K7PWf5b4Y8P1JR2wSY7lwP9kIVE2qJdUgOvCdNoOLhcW/KqOsNEhPl+uNGw66+UwlWtnt5HBtkHuG4DNJt+cmQBMVEsQC/Ssdfu6cEOyFyhc+UZECf97j4zE7N2eJvGf+Tnmn1+B+zeRDi/326IfvfJrziarLwJbnJA7cQ4jzsgmXKn3jUtrndWU+/uDOteb9xK44+i9Zu80BsYr1qA2/nHShjRBQVvSuzaECA97E6lNVpM9iFjomiB9UGvLson/mzkoO8TPhSutaAT4W/1+Y2Xw8dL8eVPSyiLs9jXJOQ3w2ofDh4NoqRz2UySLCsSuLl71pvDDNu9b0nUDTe7WafV/vPdQNGlig9DM=~-1~-1~-1~-1~-1; bm_sz=E96784EEC5167E8F3CE4E6137438618A~YAAQNv7DFwMdbQqcAQAAwuIZGR6FDpKFMTDTFdX54GR8nkNB7VId4Ja60MYBm2wn6tIXAMUKXukM4jYvhX7Z4Vo820JQU7iACxIrvlWa/MqA24aR/6a20T0w+fifwGc//WJpnl9wmaNBCmSHtBiTlypjQDq+BK0+rXxHf2POyVdfjPue3SG0Qp1+l8/0NnJAJnzUigrFIkvXPydz3s25UBL8/Ee8m0SJZukX2rzqxhw78WyZZ8u1XBUwzi00Q14Yhn2GUm/f7m8gcm8sFpO0zm0fqvr42JrI+HbcxPy95HG/M/fFhviwt1m/XtYT7csDvlVhHhDyf6EYG8DxUGJHl+2YSO4bf5qc7U7Ua+s=~4276537~4604217; ak_bmsc=F5DE8B4AD3883A03284DF329E4DFAF6D~000000000000000000000000000000~YAAQNv7DF/lIbwqcAQAA2ICKGR7B/Qv9QnShuPygdZtM5eN08vgMj98UyzBH5FDj6+ZTCHz5lHQ9JutJfe1ucpYIEPdRAkwXr2L1q5rbv1nBp5/E8y1iCkbAupwS9mNEgLSGyQbRtQw6llWEWJA26j+WwuiBq7Z4076OnzM9wC6rrh7cYM7FQLbR4ydU5OtcsitTlimovxeYr8CbwLSuk1YNf7pLcfuQW9XxbXsXR81KxFo1XlLHGkfHg5/64o+4fy9Rgb/StKhA/dWjiqvhJs9nPPtA59dWSgvxJKLWaXYrCo9SEbPmTJCajnwKe6ZXniAUbYyn74lvYuG4BQXTQcSTYDeB/Lu9YgLiF1p63yN0; bm_sv=544897E8C21E937E00D040F32E121AA1~YAAQIqTBF87m0debAQAA1SSkGR4lizTTVx85mgvAU/PXGuH98zzAu3myqTLUDfTyqYEYw0vsTgph7d1wVA71IDrB1lBY73MBRsxu98JIyjKWjeKwttoz3FIuovjyG6uYjZqgbbtsq0t3TePrrO10MhqIOIxZ58RAgSn5vw2VhpUL3H0gVAQE2xlCaB3AfSKbNJ8rnDxl4NVbofx9KapGcjJLQl9U+7AzFMJphkmAOJbLGBNy1P1ljEzNYnf/n7F0EKb85+XD~1";

    // 创建OkHttpClient，模拟Postman的行为
    private static final OkHttpClient client = createOkHttpClient();

    public static void main(String[] args) {
        Map<String, String> inputMap = NVIDAUtil.getTargetMap();
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

            System.out.printf("\n[%d/%d] 处理: %s - %s%n", processed, inputMap.size(), countryCode, partnerCode);

            try {
                // 添加延迟
                if (processed > 1) {
                    Thread.sleep(1000);
                }

                // 每处理5个休息一下
                if (processed % 5 == 0) {
                    System.out.println("已处理" + processed + "条，等待2秒...");
                    Thread.sleep(2000);
                }

                Request request = createRequest(partnerCode);
                try (Response response = client.newCall(request).execute()) {
                    System.out.println("  HTTP状态码: " + response.code());

                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();

                        if (responseBody != null && !responseBody.trim().isEmpty()) {
                            try {
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
                            } catch (Exception e) {
                                System.err.println("✗ JSON解析失败: " + e.getMessage());
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

            } catch (Exception e) {
                System.err.println("✗ 处理失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        generateCSV(partnerInfoList);
        System.out.println("\n处理完成! 成功获取 " + partnerInfoList.size() + " 条记录");
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