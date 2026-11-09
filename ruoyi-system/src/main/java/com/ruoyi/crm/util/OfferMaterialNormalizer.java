package com.ruoyi.crm.util;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offer 物料解析结果归一化：AI 负责理解文本，本类只处理能确定判断的型号格式。 */
public final class OfferMaterialNormalizer {

    private static final String NVIDIA_RTX_5090_STANDARD = "RTX 5090 32G";
    private static final String NVIDIA_PRO_6000_SE_STANDARD = "PRO 6000 SE 96G";
    private static final String NVIDIA_PRO_6000_WS_STANDARD = "PRO 6000 WS 96G";
    private static final String NVIDIA_PRO_6000_STANDARD = "PRO 6000";

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
    /** 紧凑报价行：6336Y---1480 / 6326--850，前面是 Intel/AMD CPU 短型号，后面是价格。 */
    private static final Pattern CPU_COMPACT_QUOTE = Pattern.compile(
            "^(\\d{4}[A-Z]{0,2})\\s*(?:-{2,}|—{2,}|–{2,}|：|:)\\s*(?:USD|CNY|RMB|\\$|¥|€)?\\s*"
                    + "(\\d+(?:\\.\\d+)?)\\s*(?:USD|CNY|RMB)?$",
            Pattern.CASE_INSENSITIVE);
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

    /** 拆机/拆新/二手关键词：命中即按拆机处理（料号加“拆”）。CP 必须独立成词，避免误伤 CPU/PCS/PCIe。 */
    private static final Pattern PULLED_MARK = Pattern.compile(
            "(?i)拆|(?<![A-Z0-9-])CP(?![A-Z0-9-])|\\bCLEAN\\s+PULL\\b|\\bPULL(?:ED)?\\b"
                    + "|\\bRENEW(?:ED)?\\b|\\bREFURB(?:ISHED)?\\b|\\bUSED\\b|二手");
    /** 明确非拆机的否定词：不拆/未拆/非拆/勿拆/无拆等。 */
    private static final Pattern PULLED_NEGATIVE = Pattern.compile(
            "(?i)不拆|未拆|非拆|勿拆|无拆|没有拆|不是拆"
                    + "|not\\s+(?:pulled|used|renewed|refurbished)");
    /** 全新标记：用于拆/新冲突消歧；“拆新”本身属于拆机，不能让其中的“新”覆盖。 */
    private static final Pattern NEW_MARK = Pattern.compile(
            "(?i)全新|(?<!拆)新(?:货|品)?|brand\\s*new|(?<!拆)\\bnew\\b");

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
        boolean pulled = indicatesPulled(normalized);
        String base = normalizePartNumberBase(stripPulledMarkers(normalized));
        if (base.isEmpty()) {
            return base;
        }
        return pulled && !base.endsWith("拆") ? base + "拆" : base;
    }

    /** 在去除拆机标记后统一料号格式。 */
    private static String normalizePartNumberBase(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return "";
        }
        Matcher amd = AMD_EPYC_PART.matcher(normalized);
        if (amd.matches()) {
            return amd.group(1).toUpperCase(Locale.ROOT);
        }
        Matcher cpuQuote = CPU_COMPACT_QUOTE.matcher(normalized);
        if (cpuQuote.matches()) {
            return cpuQuote.group(1).toUpperCase(Locale.ROOT);
        }
        Matcher wd = WD_PAIR.matcher(normalized);
        if (wd.matches()) {
            return wd.group(2).toUpperCase(Locale.ROOT);
        }
        if (NVIDIA_RTX_5090_TOKEN.matcher(normalized).matches()
                || NVIDIA_5090_BARE_TOKEN.matcher(normalized).matches()) {
            return NVIDIA_RTX_5090_STANDARD;
        }
        Matcher nvidiaPro6000 = NVIDIA_PRO_6000_TOKEN.matcher(normalized);
        if (nvidiaPro6000.matches() && nvidiaPro6000.group(1) != null) {
            return isServerEditionToken(nvidiaPro6000.group(1))
                    ? NVIDIA_PRO_6000_SE_STANDARD : NVIDIA_PRO_6000_WS_STANDARD;
        }
        if (NVIDIA_PRO_6000_BARE_TOKEN.matcher(normalized).matches()) {
            return NVIDIA_PRO_6000_STANDARD;
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

        // 先去掉拆机标记再归一化型号，避免“9015拆”“RTX 5090 32G拆”影响型号识别；最后统一补回“拆”。
        String pn = normalizePartNumberBase(stripPulledMarkers(PnUtils.normalize(originalPn)));
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
            if (NVIDIA_RTX_5090_STANDARD.equals(nvidiaPn)) {
                model = "RTX 5090";
                spec = ensureCapacity(spec, "32");
            } else if (NVIDIA_PRO_6000_SE_STANDARD.equals(nvidiaPn)) {
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
            pn = NVIDIA_PRO_6000_STANDARD;
        }

        String samsungSeries = detectSamsungSeries(combined, pn);
        if (samsungSeries != null) {
            put(item, "productType", "SSD");
            type = "SSD";
            if (model.isEmpty() || model.equalsIgnoreCase(pn) || !containsIgnoreCase(model, samsungSeries)) {
                model = samsungSeries;
            }
        }

        // 拆新兜底：AI 可能漏标，这里按原文/AI详情确定性判断，命中则料号、详情、备注同时加“拆”
        if (isPulledItem(pn, originalPn, detail, spec, model, sourceContent)) {
            if (!pn.endsWith("拆")) {
                pn = pn + "拆";
            }
            detail = appendMissing(detail, "拆");
            put(item, "remark", pn);
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
        String compactPn = pn.replace(" ", "");
        if (compactPn.startsWith("RTX509032G") || NVIDIA_RTX_5090_ANY.matcher(combined).find()) {
            return NVIDIA_RTX_5090_STANDARD;
        }
        if (!compactPn.startsWith("PRO6000") && !NVIDIA_PRO_6000_ANY.matcher(combined).find()) {
            return null;
        }
        String upper = combined.toUpperCase(Locale.ROOT);
        if (upper.contains("MAX-Q") || upper.contains("MAX Q") || upper.contains("MAX~Q")
                || upper.contains("MAXQ")) {
            return null;
        }
        boolean server = compactPn.startsWith("PRO6000SE96G") || NVIDIA_SERVER_MARK.matcher(combined).find();
        boolean workstation = compactPn.startsWith("PRO6000WS96G")
                || NVIDIA_WORKSTATION_MARK.matcher(combined).find();
        if (server && !workstation) {
            return NVIDIA_PRO_6000_SE_STANDARD;
        }
        if (workstation && !server) {
            return NVIDIA_PRO_6000_WS_STANDARD;
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

    /** 判断该行是否拆机：原文明确拆机时优先于AI详情误写的“全新”；原文拆/新混合时按AI当前行详情区分。 */
    private static boolean isPulledItem(String pn, String originalPn, String detail, String spec,
                                        String model, String sourceContent) {
        String own = str(originalPn) + " " + str(detail) + " " + str(spec) + " " + str(model);
        boolean ownPulled = indicatesPulled(own);
        if (sourceContent != null) {
            String upperPn = str(pn).toUpperCase(Locale.ROOT);
            String upperOrig = str(originalPn).toUpperCase(Locale.ROOT);
            boolean pulledOnly = false;
            boolean newOnly = false;
            boolean mixed = false;
            for (String line : sourceContent.split("\\r?\\n")) {
                String upperLine = line.toUpperCase(Locale.ROOT);
                boolean hit = (!upperPn.isEmpty() && upperLine.contains(upperPn))
                        || (!upperOrig.isEmpty() && upperLine.contains(upperOrig));
                if (!hit) {
                    continue;
                }
                boolean pulled = indicatesPulled(line);
                boolean isNew = indicatesNew(line);
                if (pulled && isNew) {
                    mixed = true;
                } else if (pulled) {
                    pulledOnly = true;
                } else if (isNew) {
                    newOnly = true;
                }
            }
            // 原文只有拆机标记：即使AI详情误写成“全新”，也必须按拆机处理
            if (pulledOnly && !newOnly && !mixed) {
                return true;
            }
            // 原文同时出现拆/新（同料号拆新两行）：以AI当前行详情区分，详情必须带“拆”或“新”
            if (mixed || (pulledOnly && newOnly)) {
                return ownPulled;
            }
            if (newOnly) {
                return false;
            }
        }
        return ownPulled;
    }

    /** 文本命中拆机关键词且不含否定词。 */
    private static boolean indicatesPulled(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        if (PULLED_NEGATIVE.matcher(text).find()) {
            return false;
        }
        return PULLED_MARK.matcher(text).find();
    }

    /** 文本是否明确标记为全新。 */
    private static boolean indicatesNew(String text) {
        return text != null && !text.trim().isEmpty() && NEW_MARK.matcher(text).find();
    }

    /** 去掉料号尾部/独立成词的拆机标记，供查询和录入共用。 */
    private static String stripPulledMarkers(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String result = value.replaceAll(
                "(?i)(^|\\s)(?:CLEAN\\s+PULL|PULLED|PULL|RENEWED|RENEW|REFURBISHED|REFURB|USED|CP)(?=\\s|$)", " ");
        result = result.replaceAll("拆(?:机|新|箱|件|封)?|二手", "");
        return result.replaceAll("\\s+", " ").trim();
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
