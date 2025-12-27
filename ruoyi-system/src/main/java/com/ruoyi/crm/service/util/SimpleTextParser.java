package com.ruoyi.crm.service.util;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.crm.domain.CrmOffer;
import java.util.*;
import java.util.regex.Pattern;

public class SimpleTextParser {

    /**
     * 解析文本生成CrmOffer列表
     */
    public List<CrmOffer> parseTextToCrmOffers(Map<String, Object> params) {
        List<CrmOffer> offerList = new ArrayList<>();

        if (params == null || params.isEmpty()) {
            return offerList;
        }

        String text = (String) params.get("text");
        if (text == null || text.trim().isEmpty()) {
            return offerList;
        }

        List<Map<String, Object>> sequence = (List<Map<String, Object>>) params.get("sequence");
        if (sequence == null || sequence.isEmpty()) {
            return offerList;
        }

        // 按换行符分割文本
        String[] lines = text.split("\n");

        // 遍历每一行，每行都是独立的记录
        while (!text.isEmpty()){

            text = text.trim();
            if (text.isEmpty()) {
                continue;
            }
            CrmOffer offer = new CrmOffer();
            text = parseText(text, sequence, offer);
            if (offer != null && StringUtils.isNotEmpty(offer.getProductCode())) {
                offerList.add(offer);
            }
        }

        return offerList;
    }

    /**
     * 解析单行文本
     */
    private String parseText(String text, List<Map<String, Object>> sequence, CrmOffer offer ) {
        List<String> productDetails = new ArrayList<>();

        // 遍历解析规则
        for (int ruleIndex = 0; ruleIndex < sequence.size(); ruleIndex++) {

            Map<String, Object> rule = sequence.get(ruleIndex);
            String type = (String) rule.get("type");
            String value = (String) rule.get("value");
            if(value.contains("空格")){
                value = " ";
            }
            if ("sep".equals(type)) {
                // 分隔符：跳过开头的所有该分隔符
                String separator = convertSeparator(value);
                while (!text.isEmpty() && text.startsWith(separator)) {
                    text = text.substring(separator.length()).trim();
                }
            } else if ("field".equals(type)) {
                // 字段：读取内容直到下一个分隔符
                String fieldName = value;

                // 查找下一个分隔符
                String nextSeparator = "\n";
                for (int i = ruleIndex + 1; i < sequence.size(); i++) {
                    Map<String, Object> nextRule = sequence.get(i);
                    if ("sep".equals(nextRule.get("type"))) {
                        nextSeparator = convertSeparator((String) nextRule.get("value"));
                        if(nextSeparator.contains("空格")){
                            nextSeparator = " ";
                        }
                        break;
                    }
                }

                String fieldValue;
                if (nextSeparator != null) {
                    // 找到下一个分隔符的位置
                    int sepIndex = text.indexOf(nextSeparator);
                    int huanhang = text.indexOf("\n")  < 0 ? 99999999 : text.indexOf("\n");
                    int sepIndexLast = sepIndex > huanhang ? huanhang : sepIndex;
                    if (sepIndexLast >= 0) {
                        fieldValue = text.substring(0, sepIndexLast).trim();
                        text = text.replaceFirst(Pattern.quote(fieldValue), "");
                        while (!text.isEmpty() && text.startsWith(" ")) {
                            text = text.replaceFirst(" ", "");
                        }
                    } else {
                        // 没有找到分隔符，使用剩余所有文本
                        fieldValue = text.trim();
                        text = "";
                    }
                } else {
                    // 没有下一个分隔符，这是最后一个字段
                    fieldValue = text.trim();
                    text = "";
                }

                // 映射字段
                mapField(fieldName, fieldValue, offer, productDetails);
            }

        }
        // 正确跳过换行符的方法
        int newlinePos = text.indexOf("\n");
        if (newlinePos >= 0) {
            // 从换行符的下一个字符开始截取
            text = text.substring(newlinePos + 1);
            // 如果需要，可以去掉开头的空格
            text = text.trim();
        }

        // 处理产品详情多选
        if (!productDetails.isEmpty()) {
            offer.setProductDetail(String.join(", ", productDetails));
        }

        // 设置默认值
        offer.setStatus(1);
        offer.setCreateTime(new Date());
        offer.setUpdateTime(new Date());

        return text;
    }

    /**
     * 映射字段值
     */
    private void mapField(String fieldName, String fieldValue, CrmOffer offer, List<String> productDetails) {
        if (fieldValue == null || fieldValue.isEmpty()) {
            return;
        }

        switch (fieldName) {
            case "品牌":
                offer.setProductBrand(fieldValue);
                break;
            case "产品编码":
                offer.setProductCode(fieldValue);
                break;
            case "数量":
                offer.setQuantity(parseInteger(fieldValue));
                break;
            case "产品详情":
                productDetails.add(fieldValue);
                break;
            case "单价":
                offer.setPriceCost(parseDouble(fieldValue));
                break;
            case "DC":
                offer.setDc(fieldValue);
                break;
            case "供应商名称":
                offer.setSupplierName(fieldValue);
                break;
            case "交货时间":
                offer.setDeliveryTime(fieldValue);
                break;
            case "产品类型":
                offer.setProductType(fieldValue);
                break;
            case "产品明细编号":
                offer.setProductDetailCode(fieldValue);
                break;
            case "备注":
                offer.setRemark(fieldValue);
                break;
            case "质保详情":
                offer.setWarrantyDetail(fieldValue);
                break;
            case "MOQ":
                offer.setMoqQuantity(parseInteger(fieldValue));
                break;
            case "价格单位":
                offer.setPriceUnit(fieldValue);
                break;
            case "标签1":
                offer.setTagsFirst(fieldValue);
                break;
            case "标签2":
                offer.setTagsSecond(fieldValue);
                break;
            case "标签3":
                offer.setTagsThird(fieldValue);
                break;
            case "标签4":
                offer.setTagsSi(fieldValue);
                break;
        }
    }

    /**
     * 转换分隔符
     */
    private String convertSeparator(String sepValue) {
        if (sepValue == null) return " ";

        switch (sepValue) {
            case "空格": return " ";
            case "\\n": return "\n";
            case "\\t": return "\t";
            case "逗号": return ",";
            case "分号": return ";";
            case "竖线": return "|";
            case "冒号": return ":";
            case "顿号": return "、";
            default: return sepValue;
        }
    }

    /**
     * 解析整数
     */
    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            String numberStr = value.replaceAll("[^0-9]", "");
            if (numberStr.isEmpty()) {
                return null;
            }
            return Integer.parseInt(numberStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析浮点数
     */
    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            String numberStr = value.replaceAll("[^0-9.]", "");
            if (numberStr.isEmpty()) {
                return null;
            }
            return Double.parseDouble(numberStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        SimpleTextParser parser = new SimpleTextParser();

        // 测试1：原示例
        System.out.println("=== 测试1：原示例 ===");
        Map<String, Object> params1 = new HashMap<>();
        params1.put("text", "WD WD001 500pcs 这个是详情，这个也是 \n200USD 25+\nWD WD002 500pcs 这个是详情，这个也是\n200USD 25+");

        List<Map<String, Object>> sequence1 = new ArrayList<>();
        sequence1.add(createRule("field", "品牌"));
        sequence1.add(createRule("sep", "空格"));
        sequence1.add(createRule("field", "产品编码"));
        sequence1.add(createRule("sep", "空格"));
        sequence1.add(createRule("field", "数量"));
        sequence1.add(createRule("sep", "空格"));
        sequence1.add(createRule("field", "产品详情"));
        sequence1.add(createRule("sep", "空格"));
        sequence1.add(createRule("sep", "\\n"));
        sequence1.add(createRule("field", "单价"));
        sequence1.add(createRule("sep", "空格"));
        sequence1.add(createRule("field", "DC"));

        params1.put("sequence", sequence1);

        List<CrmOffer> offers1 = parser.parseTextToCrmOffers(params1);
        printOffers(offers1);

        // 测试2：产品详情多选
        System.out.println("\n=== 测试2：产品详情多选 ===");
        Map<String, Object> params2 = new HashMap<>();
        params2.put("text", "苹果 iPhone-15 100部 智能手机 6.1英寸 OLED A16芯片 \n三星 Galaxy-S24 80台 旗舰手机 6.7英寸 Dynamic-AMOLED 骁龙8");

        List<Map<String, Object>> sequence2 = new ArrayList<>();
        sequence2.add(createRule("field", "品牌"));
        sequence2.add(createRule("sep", "空格"));
        sequence2.add(createRule("field", "产品编码"));
        sequence2.add(createRule("sep", "空格"));
        sequence2.add(createRule("field", "数量"));
        sequence2.add(createRule("sep", "空格"));
        sequence2.add(createRule("field", "产品类型"));
        sequence2.add(createRule("sep", "空格"));
        sequence2.add(createRule("field", "产品详情"));
        sequence2.add(createRule("sep", "空格"));
        sequence2.add(createRule("field", "产品详情"));
        sequence2.add(createRule("sep", "空格"));
        sequence2.add(createRule("field", "产品详情"));

        params2.put("sequence", sequence2);

        List<CrmOffer> offers2 = parser.parseTextToCrmOffers(params2);
        printOffers(offers2);

        // 测试3：连续分隔符
        System.out.println("\n=== 测试3：连续分隔符 ===");
        Map<String, Object> params3 = new HashMap<>();
        params3.put("text", "华为   Mate-60   50台   5G手机   卫星通话   麒麟芯片\n小米   14-Pro   100部   旗舰手机   徕卡影像   骁龙8");

        List<Map<String, Object>> sequence3 = new ArrayList<>();
        sequence3.add(createRule("field", "品牌"));
        sequence3.add(createRule("sep", "空格"));
        sequence3.add(createRule("field", "产品编码"));
        sequence3.add(createRule("sep", "空格"));
        sequence3.add(createRule("field", "数量"));
        sequence3.add(createRule("sep", "空格"));
        sequence3.add(createRule("field", "产品类型"));
        sequence3.add(createRule("sep", "空格"));
        sequence3.add(createRule("field", "产品详情"));
        sequence3.add(createRule("sep", "空格"));
        sequence3.add(createRule("field", "产品详情"));

        params3.put("sequence", sequence3);

        List<CrmOffer> offers3 = parser.parseTextToCrmOffers(params3);
        printOffers(offers3);
    }

    private static Map<String, Object> createRule(String type, String value) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", type);
        rule.put("value", value);
        return rule;
    }

    private static void printOffers(List<CrmOffer> offers) {
        System.out.println("解析到 " + offers.size() + " 个记录:");
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
            System.out.println("  供应商: " + offer.getSupplierName());
        }
    }
}