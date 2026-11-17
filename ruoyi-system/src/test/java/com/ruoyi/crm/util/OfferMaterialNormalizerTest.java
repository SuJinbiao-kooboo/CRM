package com.ruoyi.crm.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OfferMaterialNormalizer 的独立自检，不依赖 JUnit，可直接运行 main。 */
public class OfferMaterialNormalizerTest {

    public static void main(String[] args) {
        testWechat640392();
        testAllProductTypes();
        testNegativeMarkers();
        testQueryToken();
        testMixedNewAndPulled();
        testIntelCpuSspec();
        System.out.println("OfferMaterialNormalizerTest PASSED");
    }

    private static void testWechat640392() {
        Map<String, Object> item = item("6760P", "Intel Xeon 6760P",
                "Intel Xeon 6760P 全新", "CPU");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item);
        String source = "Inq:\n\n6737P 16pcs 4400订货两周\n\n6760P 46pcs 7900拆机\n\n6517P 26pcs2550";
        OfferMaterialNormalizer.normalizeItems(items, source);
        eq("6760P拆", item.get("partNumber"), "640392 拆机料号");
        eq("6760P拆", item.get("remark"), "640392 拆机备注");
        assertContains(String.valueOf(item.get("productDetail")), "拆", "640392 详情含拆");
    }

    private static void testAllProductTypes() {
        assertPulled("9015", "EPYC 9015", "EPYC9015/100-000001553 100pcs 拆", "9015拆", "CPU");
        assertPulled("RTX 5090 32G", "RTX 5090 32G", "RTX 5090 32G 5pcs CP", "RTX 5090 32G拆", "GPU");
        assertPulled("M393A8G40CB4-CWEGY", "Samsung 64GB DDR4",
                "M393A8G40CB4-CWEGY 64GB 10pcs Pulled", "M393A8G40CB4-CWEGY拆", "RAM");
        assertPulled("MZQL2960HCJR-00A07", "Samsung PM9A3 960GB",
                "MZQL2960HCJR-00A07 960G 10pcs Renew", "MZQL2960HCJR-00A07拆", "SSD");
        assertPulled("WUH722424ALE6L4", "Western Digital 24TB",
                "WUH722424ALE6L4 24T 9pcs Refurbished", "WUH722424ALE6L4拆", "HDD");
        assertPulled("X13SEI-F", "Supermicro X13SEI-F",
                "X13SEI-F 2pcs Used", "X13SEI-F拆", "Motherboard");
        assertPulled("K4A8G165WC-BCTD", "Samsung DRAM",
                "K4A8G165WC-BCTD 100pcs 二手", "K4A8G165WC-BCTD拆", "颗粒");
    }

    private static void testNegativeMarkers() {
        assertNotPulled("6760P", "Intel Xeon 6760P", "6760P 46pcs 7900 不拆");
        assertNotPulled("6760P", "Intel Xeon 6760P", "6760P 46pcs 7900 未拆");
        assertNotPulled("6760P", "Intel Xeon 6760P", "6760P 46pcs 7900 非拆");
    }

    private static void testQueryToken() {
        eq("6760P拆", OfferMaterialNormalizer.normalizePartNumberToken("6760P拆机"), "查询-拆机");
        eq("6760P拆", OfferMaterialNormalizer.normalizePartNumberToken("6760P CP"), "查询-CP");
        eq("6760P拆", OfferMaterialNormalizer.normalizePartNumberToken("6760P pulled"), "查询-Pulled");
        eq("6760P拆", OfferMaterialNormalizer.normalizePartNumberToken("6760P renew"), "查询-Renew");
        eq("6760P拆", OfferMaterialNormalizer.normalizePartNumberToken("6760P refurbished"), "查询-Refurbished");
        eq("6760P拆", OfferMaterialNormalizer.normalizePartNumberToken("6760P used"), "查询-Used");
        eq("6760P", OfferMaterialNormalizer.normalizePartNumberToken("6760P"), "查询-普通料号");
        eq("CPU", OfferMaterialNormalizer.normalizePartNumberToken("CPU"), "查询-CPU不能误判");
        assertNotContains(OfferMaterialNormalizer.normalizePartNumberToken("6760P PCS"), "拆", "查询-PCS不能误判");
    }

    private static void testMixedNewAndPulled() {
        Map<String, Object> pulled = item("4510", "Intel Xeon 4510", "Intel Xeon 4510 拆", "CPU");
        Map<String, Object> brandNew = item("4510", "Intel Xeon 4510", "Intel Xeon 4510 新", "CPU");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(pulled);
        items.add(brandNew);
        OfferMaterialNormalizer.normalizeItems(items,
                "4510 71pcs 拆 800USD 94pcs 新 830USD");
        eq("4510拆", pulled.get("partNumber"), "拆/新混合-拆机行");
        eq("4510", brandNew.get("partNumber"), "拆/新混合-全新行");
    }

    private static void testIntelCpuSspec() {
        assertIntelPart("U7-366H/SA4R9", "U7-366H", "U7-366H/SA4R9\n20 pcs");
        assertIntelPart("I7-1365U/SRMM0", "I7-1365U",
                "I7-1365U/SRMM0\nFJ8071505259101\n514 pcs");
        assertIntelPart("I7-4700EQ/SR17L", "I7-4700EQ",
                "I7-4700EQ/SR17L\nCL8064701483802\n63 pcs");
        assertIntelPart("I7-6820EQ/SR2DT", "I7-6820EQ",
                "I7-6820EQ/SR2DT\n300 to 1k+ pcs");
        assertIntelPart("I7-6822EQ/SR2DW", "I7-6822EQ",
                "I7-6822EQ/SR2DW\nCL8066202302204\n190 pcs");
        assertIntelPart("W3-2423/SRM9L", "W3-2423", "W3-2423/SRM9L\n15 pcs");
        assertIntelPart("I7-6600U/SR2F1", "I7-6600U",
                "I7-6600U/SR2F1\nFJ8066201924950\n100 pcs");
        assertIntelPart("I5-1145G7E/SRK0Z", "I5-1145G7E",
                "I5-1145G7E/SRK0Z\nFH8069004542000\n180 pcs");
        assertIntelPart("CORE5-120U/SRN7P", "CORE5-120U", "CORE5-120U/SRN7P\n3480 pcs");
        assertIntelPart("X6425E/SRKUE", "X6425E", "X6425E/SRKUE\n1k to 3k pcs");
        assertIntelPart("J6413/SRKL7", "J6413", "J6413/SRKL7\n2475 pcs");
        assertIntelPart("9470/SRMJC", "9470", "9470/SRMJC\nPK8071305223200\n70 pcs");
        assertIntelPart("I7-1365U SRMM0 FJ8071505259101", "I7-1365U",
                "I7-1365U/SRMM0\nFJ8071505259101\n514 pcs");
        assertIntelPart("FJ8071505426801SRN7N", "I7-1365U",
                "I7-1365U\nFJ8071505426801SRN7N\n514 pcs");
        assertIntelPart("I7-1365U/SRMM0FJ8071505259101", "I7-1365U",
                "I7-1365U/SRMM0\nFJ8071505259101\n514 pcs");

        eq("U7-366H", OfferMaterialNormalizer.normalizePartNumberToken("U7-366H/SA4R9"),
                "查询-Intel S-Spec");
        eq("I7-1365U", OfferMaterialNormalizer.normalizePartNumberToken("I7-1365U/SRMM0"),
                "查询-Intel S-Spec 2");
        eq("SNV3S/4000G", OfferMaterialNormalizer.normalizePartNumberToken("SNV3S/4000G"),
                "查询-Kingston斜杠料号不受影响");
        eq("SA400S37/960G", OfferMaterialNormalizer.normalizePartNumberToken("SA400S37/960G"),
                "查询-Kingston SA400不受影响");
        eq("KVR32N22S8/16", OfferMaterialNormalizer.normalizePartNumberToken("KVR32N22S8/16"),
                "查询-Kingston内存不受影响");
        eq("MZ-77E1T0B/EU", OfferMaterialNormalizer.normalizePartNumberToken("MZ-77E1T0B/EU"),
                "查询-Samsung斜杠料号不受影响");

        Map<String, Object> mmOnly = item("CL8066202302204", "", "CL8066202302204", "CPU");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(mmOnly);
        OfferMaterialNormalizer.normalizeItems(items,
                "Any support for these ?\n\nI7-6822EQ/SR2DW\nCL8066202302204\n190 pcs");
        eq("I7-6822EQ", mmOnly.get("partNumber"), "MM#单独出现时回找CPU型号");
    }

    private static void assertIntelPart(String pn, String expected, String source) {
        Map<String, Object> item = item(pn, pn, pn, "CPU");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item);
        OfferMaterialNormalizer.normalizeItems(items, source);
        eq(expected, item.get("partNumber"), "Intel CPU料号-" + pn);
        if (pn.contains("/")) {
            assertContains(String.valueOf(item.get("productDetail")),
                    pn.substring(pn.indexOf('/') + 1), "Intel CPU详情保留辅助码-" + pn);
        }
    }

    private static void assertPulled(String pn, String model, String source, String expected, String type) {
        Map<String, Object> item = item(pn, model, model, type);
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item);
        OfferMaterialNormalizer.normalizeItems(items, source);
        eq(expected, item.get("partNumber"), type + " 拆机料号");
        eq(expected, item.get("remark"), type + " 拆机备注");
    }

    private static void assertNotPulled(String pn, String model, String source) {
        Map<String, Object> item = item(pn, model, model, "CPU");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item);
        OfferMaterialNormalizer.normalizeItems(items, source);
        eq(pn, item.get("partNumber"), "否定词不拆-" + source);
    }

    private static Map<String, Object> item(String pn, String model, String detail, String type) {
        Map<String, Object> item = new HashMap<>();
        item.put("brand", "Intel");
        item.put("partNumber", pn);
        item.put("model", model);
        item.put("productDetail", detail);
        item.put("productType", type);
        return item;
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + "：expected=" + expected + "，actual=" + actual);
        }
    }

    private static void assertContains(String value, String search, String message) {
        if (value == null || !value.contains(search)) {
            throw new AssertionError(message + "：value=" + value);
        }
    }

    private static void assertNotContains(String value, String search, String message) {
        if (value != null && value.contains(search)) {
            throw new AssertionError(message + "：value=" + value);
        }
    }
}
