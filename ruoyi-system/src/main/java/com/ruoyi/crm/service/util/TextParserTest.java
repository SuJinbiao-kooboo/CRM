package com.ruoyi.crm.service.util;

import com.ruoyi.crm.domain.CrmOffer;

import java.util.*;

public class TextParserTest {

    private SimpleTextParser parser = new SimpleTextParser();

    public static void main(String[] args) {
        TextParserTest test = new TextParserTest();

        System.out.println("=== 开始测试 ===");

        // 测试1：基本场景（原示例）
        test.testBasicScenario();

        // 测试2：包含更多字段
        test.testWithMoreFields();

        // 测试3：连续分隔符
        test.testWithMultipleSeparators();

        // 测试4：不规则空格
        test.testWithIrregularSpaces();

        // 测试5：包含空字段
        test.testWithEmptyFields();

        // 测试6：复杂价格和数量格式
        test.testComplexFormats();

        // 测试7：多个记录混合
        test.testMixedRecords();

        // 测试8：特殊字符处理
        test.testSpecialCharacters();

        System.out.println("\n=== 所有测试完成 ===");
    }

    /**
     * 测试1：基本场景
     */
    private void testBasicScenario() {
        System.out.println("\n--- 测试1：基本场景 ---");

        Map<String, Object> params = new HashMap<>();
        params.put("text", "WD WD001 500pcs 这个是详情，这个也是 \n200USD 25+\nWD WD002 500pcs 这个是详情，这个也是\n200USD 25+");

        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(createRule("field", "品牌"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品编码"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "数量"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品详情"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("sep", "\\n"));
        sequence.add(createRule("field", "单价"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "DC"));

        params.put("sequence", sequence);

        List<CrmOffer> offers = parser.parseTextToCrmOffers(params);
        printResults("基本场景", offers);
    }

    /**
     * 测试2：包含更多字段
     */
    private void testWithMoreFields() {
        System.out.println("\n--- 测试2：包含更多字段 ---");

        Map<String, Object> params = new HashMap<>();
        params.put("text",
                "三星 Samsung-Galaxy-23 100台 智能手机 Galaxy S23 Ultra 2023年新款 \n$999.99 15+ 7天 三星电子\n" +
                        "苹果 iPhone-15-Pro 50部 旗舰手机 iPhone 15 Pro Max 钛金属 \n$1299.99 10+ 14天 苹果公司"
        );

        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(createRule("field", "品牌"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品编码"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "数量"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品类型"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品详情"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("sep", "\\n"));
        sequence.add(createRule("field", "单价"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "DC"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "交货时间"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "供应商名称"));

        params.put("sequence", sequence);

        List<CrmOffer> offers = parser.parseTextToCrmOffers(params);
        printResults("更多字段", offers);
    }

    /**
     * 测试3：连续分隔符
     */
    private void testWithMultipleSeparators() {
        System.out.println("\n--- 测试3：连续分隔符 ---");

        Map<String, Object> params = new HashMap<>();
        // 注意：这里有多个连续空格
        params.put("text",
                "联想  ThinkPad-X1  250台     商务笔记本  ThinkPad X1 Carbon Gen 11    \n" +
                        "¥8999   5+   联想集团\n" +
                        "戴尔  XPS-13  150部      超极本  XPS 13 Plus 9320   \n" +
                        "$1099.99  3+  戴尔公司"
        );

        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(createRule("field", "品牌"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品编码"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "数量"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品类型"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品详情"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("sep", "\\n"));
        sequence.add(createRule("field", "单价"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "DC"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "供应商名称"));

        params.put("sequence", sequence);

        List<CrmOffer> offers = parser.parseTextToCrmOffers(params);
        printResults("连续分隔符", offers);
    }

    /**
     * 测试4：不规则空格
     */
    private void testWithIrregularSpaces() {
        System.out.println("\n--- 测试4：不规则空格 ---");

        Map<String, Object> params = new HashMap<>();
        // 混合使用空格和制表符
        params.put("text",
                "华为\tMate60-Pro\t100台\t5G手机\t华为Mate 60 Pro 卫星通话\t\n" +
                        "¥6999\t20+\t华为技术\n" +
                        "小米\t14-Pro\t200部\t旗舰手机\t小米14 Pro 徕卡影像\t\n" +
                        "¥4999\t30+\t小米科技"
        );

        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(createRule("field", "品牌"));
        sequence.add(createRule("sep", "\\t"));
        sequence.add(createRule("field", "产品编码"));
        sequence.add(createRule("sep", "\\t"));
        sequence.add(createRule("field", "数量"));
        sequence.add(createRule("sep", "\\t"));
        sequence.add(createRule("field", "产品类型"));
        sequence.add(createRule("sep", "\\t"));
        sequence.add(createRule("field", "产品详情"));
        sequence.add(createRule("sep", "\\t"));
        sequence.add(createRule("sep", "\\n"));
        sequence.add(createRule("field", "单价"));
        sequence.add(createRule("sep", "\\t"));
        sequence.add(createRule("field", "DC"));
        sequence.add(createRule("sep", "\\t"));
        sequence.add(createRule("field", "供应商名称"));

        params.put("sequence", sequence);

        List<CrmOffer> offers = parser.parseTextToCrmOffers(params);
        printResults("不规则空格", offers);
    }

    /**
     * 测试5：包含空字段
     */
    private void testWithEmptyFields() {
        System.out.println("\n--- 测试5：包含空字段 ---");

        Map<String, Object> params = new HashMap<>();
        // 某些字段可能为空
        params.put("text",
                "索尼  PlayStation-5  50台  游戏主机  PlayStation 5 光驱版  \n" +
                        "$499.99  2+  \n" +  // 供应商名称为空
                        "任天堂  Switch-OLED  100部  掌机  Nintendo Switch OLED 白色  \n" +
                        "$349.99  5+  任天堂"
        );

        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(createRule("field", "品牌"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品编码"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "数量"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品类型"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品详情"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("sep", "\\n"));
        sequence.add(createRule("field", "单价"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "DC"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "供应商名称"));

        params.put("sequence", sequence);

        List<CrmOffer> offers = parser.parseTextToCrmOffers(params);
        printResults("包含空字段", offers);
    }

    /**
     * 测试6：复杂价格和数量格式
     */
    private void testComplexFormats() {
        System.out.println("\n--- 测试6：复杂价格和数量格式 ---");

        Map<String, Object> params = new HashMap<>();
        // 复杂的价格和数量表示
        params.put("text",
                "佳能  EOS-R5-M2  30套  全画幅微单  佳能EOS R5 Mark II 4500万像素  \n" +
                        "USD 3,899.00  1+  佳能中国\n" +
                        "尼康  Z9-II  25台  旗舰微单  尼康Z9 II 8K视频拍摄  \n" +
                        "¥25,999元  2+  尼康映像"
        );

        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(createRule("field", "品牌"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品编码"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "数量"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品类型"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品详情"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("sep", "\\n"));
        sequence.add(createRule("field", "单价"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "DC"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "供应商名称"));

        params.put("sequence", sequence);

        List<CrmOffer> offers = parser.parseTextToCrmOffers(params);
        printResults("复杂格式", offers);
    }

    /**
     * 测试7：多个记录混合
     */
    private void testMixedRecords() {
        System.out.println("\n--- 测试7：多个记录混合 ---");

        Map<String, Object> params = new HashMap<>();
        // 混合不同类型的产品
        params.put("text",
                "英特尔  i9-14900K  500颗  CPU  Intel Core i9-14900K 24核心  \n" +
                        "$589.99  100+  英特尔公司\n" +
                        "AMD  Ryzen-9-7950X  400颗  CPU  AMD Ryzen 9 7950X 16核心  \n" +
                        "$549.99  50+  AMD公司\n" +
                        "英伟达  RTX-4090  200张  显卡  NVIDIA GeForce RTX 4090 24GB  \n" +
                        "$1599.99  10+  英伟达\n" +
                        "金士顿  Fury-32GB  1000条  内存  Kingston FURY Beast 32GB DDR5  \n" +
                        "$89.99  500+  金士顿科技"
        );

        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(createRule("field", "品牌"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品编码"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "数量"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品类型"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品详情"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("sep", "\\n"));
        sequence.add(createRule("field", "单价"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "DC"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "供应商名称"));

        params.put("sequence", sequence);

        List<CrmOffer> offers = parser.parseTextToCrmOffers(params);
        printResults("多个记录", offers);
    }

    /**
     * 测试8：特殊字符处理
     */
    private void testSpecialCharacters() {
        System.out.println("\n--- 测试8：特殊字符处理 ---");

        Map<String, Object> params = new HashMap<>();
        // 包含特殊字符的产品详情
        params.put("text",
                "微软  Surface-Pro-9  150台  二合一笔记本  Surface Pro 9 (13\"/Intel) i7/16GB/512GB  \n" +
                        "$1299.99  20+  微软中国\n" +
                        "谷歌  Pixel-8-Pro  80部  智能手机  Google Pixel 8 Pro (黑曜石黑, 128GB) - AI手机  \n" +
                        "$999.00  15+  Google"
        );

        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(createRule("field", "品牌"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品编码"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "数量"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品类型"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "产品详情"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("sep", "\\n"));
        sequence.add(createRule("field", "单价"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "DC"));
        sequence.add(createRule("sep", "空格"));
        sequence.add(createRule("field", "供应商名称"));

        params.put("sequence", sequence);

        List<CrmOffer> offers = parser.parseTextToCrmOffers(params);
        printResults("特殊字符", offers);
    }

    /**
     * 创建规则项
     */
    private Map<String, Object> createRule(String type, String value) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("type", type);
        rule.put("value", value);
        return rule;
    }

    /**
     * 打印结果
     */
    private void printResults(String testName, List<CrmOffer> offers) {
        System.out.println("\n[" + testName + "] 解析到 " + offers.size() + " 个记录:");

        for (int i = 0; i < offers.size(); i++) {
            CrmOffer offer = offers.get(i);
            System.out.println("\n记录 " + (i + 1) + ":");
            System.out.println("  品牌: " + offer.getProductBrand());
            System.out.println("  产品编码: " + offer.getProductCode());
            System.out.println("  数量: " + offer.getQuantity());
            System.out.println("  产品类型: " + offer.getProductType());
            System.out.println("  产品详情: " + offer.getProductDetail());
            System.out.println("  单价: " + offer.getPriceOffer());
            System.out.println("  DC: " + offer.getDc());
            System.out.println("  交货时间: " + offer.getDeliveryTime());
            System.out.println("  供应商名称: " + offer.getSupplierName());
            System.out.println("  MOQ: " + offer.getMoqQuantity());
        }

        System.out.println("-------------------------------------------------------------------");
    }
}