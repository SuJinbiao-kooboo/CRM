package com.ruoyi.crm.util;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offer 物料解析结果归一化：AI 负责理解文本，本类只处理能确定判断的型号格式。 */
public final class OfferMaterialNormalizer {

    private static final Pattern AMD_EPYC_PAIR = Pattern.compile(
            "(?i)\\bEPYC\\s*([0-9]{4}[A-Z0-9]*)\\s*/\\s*(100-[0-9A-Z-]+)\\b");
    private static final Pattern AMD_EPYC_PART = Pattern.compile(
            "(?i)^(?:AMD\\s+)?EPYC\\s*([0-9]{4}[A-Z0-9]*)(?:\\s*/\\s*100-[0-9A-Z-]+)?$");
    private static final Pattern AMD_EPYC_ANY = Pattern.compile(
            "(?i)\\bEPYC\\s*([0-9]{4}[A-Z0-9]*)\\b");
    private static final Pattern AMD_OPN = Pattern.compile("(?i)\\b100-[0-9A-Z-]+\\b");
    private static final Pattern WD_PAIR = Pattern.compile(
            "(?i)\\b(0F[0-9A-Z]{5,})-(W(?:UH|US|D)[A-Z0-9]{8,})\\b");
    private static final Pattern WD_PREFIX = Pattern.compile("(?i)^0F[0-9A-Z]{5,}$");
    private static final Pattern WD_MODEL = Pattern.compile("(?i)^W(?:UH|US|D)[A-Z0-9]{8,}$");
    private static final Pattern SAMSUNG_SERIES = Pattern.compile(
            "(?i)\\bPM\\s*-?\\s*(1653|1655|9A3|9A1|893A?|1743|983|883|863)\\b");
    private static final Pattern CAPACITY_T = Pattern.compile(
            "(?i)(?<![A-Z0-9])(\\d+(?:\\.\\d+)?)\\s*T(?![A-Z0-9])");
    private static final Pattern CAPACITY_G = Pattern.compile(
            "(?i)(?<![A-Z0-9])(\\d+(?:\\.\\d+)?)\\s*G(?![A-Z0-9])");

    private static final Pattern NVIDIA_RTX_5090_TOKEN = Pattern.compile(
            "(?i)^(?:NVIDIA\\s+)?(?:GEFORCE\\s+)?(?:RTX|RXT)[\\s-]*5090(?:\\s*(?:32G|32GB))?$");
    private static final Pattern NVIDIA_5090_BARE_TOKEN = Pattern.compile(
            "(?i)^5090(?:\\s*(?:32G|32GB))?$");
    private static final Pattern NVIDIA_RTX_5090_ANY = Pattern.compile(
            "(?i)(?<![0-9A-Z])(?:RTX|RXT|GEFORCE)?[\\s-]*5090(?![0-9A-Z])");
    private static final Pattern NVIDIA_PRO_6000_ANY = Pattern.compile(
            "(?i)(?<![0-9A-Z])(?:PRO[\\s-]*6000|6000[\\s-]*PRO)(?![0-9A-Z])");
    private static final Pattern NVIDIA_PRO_6000_BARE_TOKEN = Pattern.compile(
            "(?i)^(?:NVIDIA\\s+)?(?:RTX\\s+)?(?:PRO[\\s-]*6000|6000[\\s-]*PRO)(?:\\s*BLACKWELL)?(?:\\s*96G(?:B)?)?$");
    private static final Pattern NVIDIA_PRO_6000_TOKEN = Pattern.compile(
            "(?i)^(?:NVIDIA\\s+)?(?:RTX\\s+)?(?:PRO[\\s-]*6000|6000[\\s-]*PRO)"
                    + "(?:\\s*BLACKWELL)?"
                    + "(?:\\s*(SE|SERVER\\s+EDITION|SERVER\\s+VERSION|SERVER|SEVER(?:\\s+EDITION)?|服务器版本|服务器版?|服务版|WS|WORKSTATION\\s+EDITION|WORKSTATION|工作站版?))?"
                    + "(?:\\s*96G(?:B)?)?$");
    private static final Pattern NVIDIA_PRO_6000_D = Pattern.compile(
            "(?i)(?:PRO[\\s-]*6000[\\s-]*D|6000[\\s-]*PRO[\\s-]*D)(?![A-Z])");
    private static final Pattern NVIDIA_SERVER_MARK = Pattern.compile(
            "(?i)(?<![A-Z0-9])(SE|SERVER(?:\\s+(?:EDITION|VERSION))?|SEVER(?:\\s+EDITION)?|服务器版本|服务器版?|服务版)(?![A-Z0-9])");
    private static final Pattern NVIDIA_WORKSTATION_MARK = Pattern.compile(
            "(?i)(?<![A-Z0-9])(WS|WORKSTATION(?:\\s+EDITION)?|工作站版?)(?![A-Z0-9])");

    private OfferMaterialNormalizer() {
    }

    /** 归一化 AI 返回的物料列表；sourceContent 用于从“内部编码-正式型号”中找回料号。 */
    public static void normalizeItems(java.util.List<Map<String, Object>> items, String sourceContent) {
        if (items == null) {
            return;
        }
        for (Map<String, Object> item : items) {
            normalizeItem(item, sourceContent);
        }
    }

    /** AI 查询纯料号快速路径的格式转换。 */
    public static String normalizePartNumberToken(String value) {
        String normalized = PnUtils.normalize(value);
        if (normalized.isEmpty()) {
            return normalized;
        }
        Matcher amd = AMD_EPYC_PART.matcher(normalized);
        if (amd.matches()) {
            return amd.group(1).toUpperCase(Locale.ROOT);
        }
        Matcher wd = WD_PAIR.matcher(normalized);
        if (wd.matches()) {
            return wd.group(2).toUpperCase(Locale.ROOT);
        }
        if (NVIDIA_RTX_5090_TOKEN.matcher(normalized).matches()
                || NVIDIA_5090_BARE_TOKEN.matcher(normalized).matches()) {
            return "RTX509032G";
        }
        Matcher nvidiaPro6000 = NVIDIA_PRO_6000_TOKEN.matcher(normalized);
        if (nvidiaPro6000.matches() && nvidiaPro6000.group(1) != null) {
            return isServerEditionToken(nvidiaPro6000.group(1)) ? "PRO6000SE96G" : "PRO6000WS96G";
        }
        if (NVIDIA_PRO_6000_BARE_TOKEN.matcher(normalized).matches()) {
            return "PRO6000";
        }
        return normalized;
    }

    private static void normalizeItem(Map<String, Object> item, String sourceContent) {
        if (item == null) {
            return;
        }
        String originalPn = str(item.get("partNumber"));
        String model = str(item.get("model"));
        String detail = str(item.get("productDetail"));
        String spec = str(item.get("spec"));
        String type = str(item.get("productType"));
        String combined = originalPn + " " + model + " " + detail + " " + spec;

        String pn = normalizePartNumberToken(originalPn);
        String amdModel = resolveAmdModel(pn, model, combined, sourceContent);
        if (amdModel != null) {
            String opn = findAmdOpn(originalPn, combined, sourceContent);
            pn = amdModel;
            model = "EPYC " + amdModel;
            put(item, "brand", "AMD");
            put(item, "productType", "CPU");
            if (!opn.isEmpty()) {
                detail = appendMissing(detail, "OPN " + opn);
            }
        } else {
            String wdModel = resolveWdModel(pn, model, combined, sourceContent);
            if (wdModel != null) {
                pn = wdModel;
                if (model.isEmpty() || WD_PREFIX.matcher(model).matches() || WD_PAIR.matcher(model).matches()) {
                    model = wdModel;
                }
                put(item, "brand", "Western Digital");
                put(item, "productType", "HDD");
                type = "HDD";
            }
        }

        String nvidiaPn = resolveNvidiaModel(pn, combined, type);
        if (nvidiaPn != null) {
            String aiModel = model;
            pn = nvidiaPn;
            if ("RTX509032G".equals(nvidiaPn)) {
                model = "RTX 5090";
                spec = ensureCapacity(spec, "32");
            } else if ("PRO6000SE96G".equals(nvidiaPn)) {
                model = "RTX PRO 6000 SE";
                spec = ensureCapacity(spec, "96");
            } else {
                model = "RTX PRO 6000 WS";
                spec = ensureCapacity(spec, "96");
            }
            put(item, "brand", "NVIDIA");
            put(item, "productType", "GPU");
            type = "GPU";
            if (detail.isEmpty() && !aiModel.isEmpty() && !aiModel.equalsIgnoreCase(model)) {
                detail = aiModel;
            }
            if (!originalPn.isEmpty() && originalPn.contains("-")
                    && !originalPn.equalsIgnoreCase(nvidiaPn)) {
                detail = appendMissing(detail, "原PN " + originalPn);
            }
        } else if (isNvidiaContext(combined, type)
                && NVIDIA_PRO_6000_BARE_TOKEN.matcher(pn).matches()) {
            pn = "PRO6000";
        }

        String samsungSeries = detectSamsungSeries(combined, pn);
        if (samsungSeries != null) {
            put(item, "productType", "SSD");
            type = "SSD";
            if (model.isEmpty() || model.equalsIgnoreCase(pn) || !containsIgnoreCase(model, samsungSeries)) {
                model = samsungSeries;
            }
        }

        put(item, "partNumber", pn);
        put(item, "model", model);
        spec = enrichSpec(spec, combined, pn, type, samsungSeries);
        put(item, "spec", spec);
        if (detail.isEmpty()) {
            detail = spec;
        }
        put(item, "productDetail", detail);
    }

    /** EPYC9015/100-000001553 → 9015。 */
    private static String resolveAmdModel(String pn, String model, String combined, String sourceContent) {
        Matcher part = AMD_EPYC_ANY.matcher(combined);
        if (part.find()) {
            return part.group(1).toUpperCase(Locale.ROOT);
        }
        Matcher opn = AMD_OPN.matcher(pn + " " + model);
        String candidate = opn.find() ? opn.group().toUpperCase(Locale.ROOT) : "";
        if (!candidate.isEmpty() && sourceContent != null) {
            Matcher pair = AMD_EPYC_PAIR.matcher(sourceContent);
            while (pair.find()) {
                if (candidate.equalsIgnoreCase(pair.group(2))) {
                    return pair.group(1).toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private static String findAmdOpn(String pn, String combined, String sourceContent) {
        Matcher m = AMD_OPN.matcher(pn + " " + combined);
        if (m.find()) {
            return m.group().toUpperCase(Locale.ROOT);
        }
        if (sourceContent != null) {
            Matcher pair = AMD_EPYC_PAIR.matcher(sourceContent);
            String model = resolveAmdModel(pn, combined, combined, null);
            while (pair.find()) {
                if (model != null && model.equalsIgnoreCase(pair.group(1))) {
                    return pair.group(2).toUpperCase(Locale.ROOT);
                }
            }
        }
        return "";
    }

    /** 西部数据内部编码 0Fxxxxx-WUH... 的正式料号取连字符后的型号。 */
    private static String resolveWdModel(String pn, String model, String combined, String sourceContent) {
        Matcher direct = WD_PAIR.matcher(pn);
        if (direct.find()) {
            return direct.group(2).toUpperCase(Locale.ROOT);
        }
        if (WD_MODEL.matcher(model).matches()) {
            return model.toUpperCase(Locale.ROOT);
        }
        Matcher pair = WD_PAIR.matcher(combined);
        if (pair.find()) {
            return pair.group(2).toUpperCase(Locale.ROOT);
        }
        if (!WD_PREFIX.matcher(pn).matches() || sourceContent == null) {
            return null;
        }
        Matcher sourcePair = WD_PAIR.matcher(sourceContent);
        while (sourcePair.find()) {
            if (pn.equalsIgnoreCase(sourcePair.group(1))) {
                return sourcePair.group(2).toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** NVIDIA RTX 5090 / RTX PRO 6000 的“系列+型号+版本+显存”归一化。 */
    private static String resolveNvidiaModel(String pn, String combined, String type) {
        if (!isNvidiaContext(combined, type)) {
            return null;
        }
        if (pn.startsWith("RTX509032G") || NVIDIA_RTX_5090_ANY.matcher(combined).find()) {
            return "RTX509032G";
        }
        if (!pn.startsWith("PRO6000") && !NVIDIA_PRO_6000_ANY.matcher(combined).find()) {
            return null;
        }
        String upper = combined.toUpperCase(Locale.ROOT);
        if (upper.contains("MAX-Q") || upper.contains("MAX Q") || upper.contains("MAX~Q")
                || upper.contains("MAXQ")) {
            return null;
        }
        boolean server = pn.startsWith("PRO6000SE96G") || NVIDIA_SERVER_MARK.matcher(combined).find();
        boolean workstation = pn.startsWith("PRO6000WS96G")
                || NVIDIA_WORKSTATION_MARK.matcher(combined).find();
        if (server && !workstation) {
            return "PRO6000SE96G";
        }
        if (workstation && !server) {
            return "PRO6000WS96G";
        }
        return null;
    }

    private static boolean isNvidiaContext(String combined, String type) {
        String upper = combined.toUpperCase(Locale.ROOT);
        if (upper.contains("AMD") || upper.contains("RADEON")) {
            return false;
        }
        if (upper.contains("整机") || upper.contains("机箱") || upper.contains("准系统")
                || upper.contains("CHASSIS")
                || NVIDIA_PRO_6000_D.matcher(combined).find()) {
            return false;
        }
        return type.isEmpty() || "GPU".equalsIgnoreCase(type);
    }

    private static boolean isServerEditionToken(String token) {
        String value = token.toUpperCase(Locale.ROOT);
        return "SE".equals(value) || value.startsWith("SERVER") || value.startsWith("SEVER")
                || value.startsWith("服务");
    }

    private static String ensureCapacity(String spec, String gb) {
        if (spec != null && Pattern.compile("(?i)(?<![0-9A-Z])" + gb + "\\s*G(?:B)?(?![0-9A-Z])")
                .matcher(spec).find()) {
            return spec;
        }
        return appendMissing(spec, gb + "GB");
    }

    /** 只对可确认的三星 SSD 系列补充系列名；MZILG 按容量代号区分 PM1653/PM1655。 */
    private static String detectSamsungSeries(String combined, String pn) {
        String value = PnUtils.normalize(combined + " " + pn);
        if (value.contains("MZILG1T6") || value.contains("MZILG3T2")) {
            return "PM1655";
        }
        if (value.matches(".*MZILG(?:960|1T9|3T8|7T6|15T|30T).*")) {
            return "PM1653";
        }
        Matcher explicit = SAMSUNG_SERIES.matcher(combined);
        if (explicit.find()) {
            String seriesValue = explicit.group(1).toUpperCase(Locale.ROOT);
            return "PM" + (seriesValue.endsWith("A")
                    ? seriesValue.substring(0, seriesValue.length() - 1) : seriesValue);
        }
        if (value.contains("MZQL2") || value.contains("MZ1L2")) {
            return "PM9A3";
        }
        if (value.contains("MZ7L3")) {
            return "PM893";
        }
        if (value.contains("MZVL2")) {
            return "PM9A1";
        }
        if (value.contains("MZ3LO") || value.contains("MZWLO")) {
            return "PM1743";
        }
        return null;
    }

    private static String enrichSpec(String spec, String combined, String pn, String type, String samsungSeries) {
        String value = normalizeCapacityUnits(spec);
        if ("HDD".equalsIgnoreCase(type) || "SSD".equalsIgnoreCase(type)) {
            value = appendInterfaceFromSource(value, combined);
        } else if ("RAM".equalsIgnoreCase(type)) {
            value = appendRamType(value, combined);
        }
        if (samsungSeries != null) {
            value = appendMissing(value, samsungSeries);
            if ("PM1653".equals(samsungSeries) || "PM1655".equals(samsungSeries)) {
                value = appendMissing(value, "SAS 24Gb/s");
                value = appendMissing(value, "2.5-inch");
            } else if ("PM893".equals(samsungSeries)) {
                value = appendMissing(value, "SATA 6Gb/s");
                value = appendMissing(value, "2.5-inch");
            } else if ("PM9A3".equals(samsungSeries)) {
                value = appendMissing(value, "PCIe 4.0 NVMe");
                if (PnUtils.normalize(pn).contains("MZQL2")) {
                    value = appendMissing(value, "U.2");
                } else if (PnUtils.normalize(pn).contains("MZ1L2")) {
                    value = appendMissing(value, "M.2");
                }
            } else if ("PM9A1".equals(samsungSeries)) {
                value = appendMissing(value, "PCIe 4.0 NVMe");
                value = appendMissing(value, "M.2");
            } else if ("PM1743".equals(samsungSeries)) {
                value = appendMissing(value, "PCIe 5.0 NVMe");
            }
        }
        String upperPn = PnUtils.normalize(pn);
        if (upperPn.endsWith("ALE6L4")) {
            value = appendMissing(value, "SATA 6Gb/s");
            value = appendMissing(value, "7200rpm");
            value = appendMissing(value, "3.5-inch");
        } else if (upperPn.endsWith("AL5204")) {
            value = appendMissing(value, "SAS 12Gb/s");
            value = appendMissing(value, "7200rpm");
            value = appendMissing(value, "3.5-inch");
        }
        return value.trim();
    }

    private static String appendInterfaceFromSource(String spec, String combined) {
        String upper = combined.toUpperCase(Locale.ROOT);
        if (upper.contains("SAS")) {
            if (upper.contains("24G") || upper.contains("24 G")) {
                spec = appendMissing(spec, "SAS 24Gb/s");
            } else if (upper.contains("12G") || upper.contains("12 G")) {
                spec = appendMissing(spec, "SAS 12Gb/s");
            } else {
                spec = appendMissing(spec, "SAS");
            }
        } else if (upper.contains("SATA")) {
            spec = appendMissing(spec, upper.contains("3.0") ? "SATA 6Gb/s" : "SATA");
        }
        if (upper.contains("NVME") || upper.contains("NVM EXPRESS")) {
            spec = appendMissing(spec, "NVMe");
        }
        if (upper.contains("PCIE 4") || upper.contains("PCIE4")) {
            spec = appendMissing(spec, "PCIe 4.0");
        } else if (upper.contains("PCIE 5") || upper.contains("PCIE5")) {
            spec = appendMissing(spec, "PCIe 5.0");
        }
        if (upper.contains("U.2")) {
            spec = appendMissing(spec, "U.2");
        }
        if (upper.contains("M.2")) {
            spec = appendMissing(spec, "M.2");
        }
        if (upper.contains("E3.S")) {
            spec = appendMissing(spec, "E3.S");
        } else if (upper.contains("E1.S")) {
            spec = appendMissing(spec, "E1.S");
        }
        return spec;
    }

    private static String appendRamType(String spec, String combined) {
        String upper = combined.toUpperCase(Locale.ROOT);
        if (upper.contains("DDR5")) {
            spec = appendMissing(spec, "DDR5");
        } else if (upper.contains("DDR4")) {
            spec = appendMissing(spec, "DDR4");
        } else if (upper.contains("DDR3")) {
            spec = appendMissing(spec, "DDR3");
        }
        if (upper.contains("LRDIMM")) {
            spec = appendMissing(spec, "LRDIMM");
        } else if (upper.contains("RDIMM")) {
            spec = appendMissing(spec, "RDIMM");
        } else if (upper.contains("UDIMM")) {
            spec = appendMissing(spec, "UDIMM");
        }
        if (upper.contains("ECC")) {
            spec = appendMissing(spec, "ECC");
        }
        return spec;
    }

    private static String normalizeCapacityUnits(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        Matcher t = CAPACITY_T.matcher(value);
        value = t.replaceAll("$1TB");
        Matcher g = CAPACITY_G.matcher(value);
        return g.replaceAll("$1GB");
    }

    private static String appendMissing(String source, String token) {
        if (token == null || token.trim().isEmpty()) {
            return source == null ? "" : source;
        }
        String base = source == null ? "" : source.trim();
        if (base.isEmpty()) {
            return token;
        }
        if (containsIgnoreCase(base, token)) {
            return base;
        }
        String upperBase = base.toUpperCase(Locale.ROOT);
        String upperToken = token.toUpperCase(Locale.ROOT);
        if ((upperToken.startsWith("SATA") && upperBase.contains("SATA"))
                || (upperToken.startsWith("SAS") && upperBase.contains("SAS"))
                || (upperToken.startsWith("PCIE") && upperBase.contains("PCIE"))
                || ("NVME".equals(upperToken) && upperBase.contains("NVME"))
                || (upperToken.startsWith("DDR") && upperBase.contains("DDR"))) {
            return base;
        }
        return base + " " + token;
    }

    private static boolean containsIgnoreCase(String value, String search) {
        return value != null && search != null
                && value.toUpperCase(Locale.ROOT).contains(search.toUpperCase(Locale.ROOT));
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void put(Map<String, Object> item, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            item.put(key, value.trim());
        }
    }
}
