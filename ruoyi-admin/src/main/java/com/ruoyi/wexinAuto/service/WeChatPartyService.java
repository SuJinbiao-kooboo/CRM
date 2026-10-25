package com.ruoyi.wexinAuto.service;

import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.mapper.CrmSupplierMapper;
import com.ruoyi.wexinAuto.config.WeChatAutomationConfig;
import com.ruoyi.wexinAuto.model.PartyMatch;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 供应商群映射、我方成员识别和合作方向收件人筛选。 */
@Service
public class WeChatPartyService {

    private final CrmSupplierMapper supplierMapper;
    private final WeChatAutomationConfig config;

    public WeChatPartyService(CrmSupplierMapper supplierMapper, WeChatAutomationConfig config) {
        this.supplierMapper = supplierMapper;
        this.config = config;
    }

    public PartyMatch resolve(String chatName) {
        String name = StringUtils.trimToEmpty(chatName);
        if (name.isEmpty()) {
            return PartyMatch.of(PartyMatch.UNMAPPED, "群名为空，无法映射交易对手");
        }
        List<CrmSupplier> matched = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (CrmSupplier supplier : supplierMapper.selectAllForWeChat()) {
            if (groups(supplier.getSalesGroup()).contains(name) || groups(supplier.getPurchaseGroup()).contains(name)) {
                if (supplier.getId() != null && seen.add(supplier.getId())) {
                    matched.add(supplier);
                }
            }
        }
        if (matched.isEmpty()) {
            return PartyMatch.of(PartyMatch.UNMAPPED, "群未映射到供应商档案");
        }
        if (matched.size() > 1) {
            return PartyMatch.of(PartyMatch.AMBIGUOUS, "群同时映射到多个供应商档案，已停止自动处理");
        }
        CrmSupplier supplier = matched.get(0);
        if (supplier.getStatus() == null || supplier.getStatus() != 1) {
            return PartyMatch.of(PartyMatch.INACTIVE, "供应商档案已停用");
        }
        if (StringUtils.isBlank(supplier.getSupplierCode())) {
            return PartyMatch.of(PartyMatch.MISSING_CODE, "供应商档案缺少供应商编码");
        }
        return PartyMatch.ok(supplier);
    }

    public boolean isSelf(String sender) {
        String value = StringUtils.trimToEmpty(sender);
        for (String name : config.selfNames()) {
            if (name.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public List<CrmSupplier> directionTargets(String productType, String brand,
                                              String groupField, Long excludeSupplierId) {
        Set<String> wanted = directionTokens(productType, brand);
        List<CrmSupplier> out = new ArrayList<>();
        if (wanted.isEmpty()) {
            return out;
        }
        for (CrmSupplier supplier : supplierMapper.selectAllForWeChat()) {
            if (excludeSupplierId != null && excludeSupplierId.equals(supplier.getId())) {
                continue;
            }
            if (supplier.getStatus() == null || supplier.getStatus() != 1
                    || StringUtils.isBlank(supplier.getSupplierCode())) {
                continue;
            }
            String groups = "sales".equalsIgnoreCase(groupField)
                    ? supplier.getSalesGroup() : supplier.getPurchaseGroup();
            if (groups(groups).isEmpty() || !matchesDirection(supplier.getCooperationDirection(), wanted)) {
                continue;
            }
            out.add(supplier);
        }
        return out;
    }

    public boolean matchesDirection(String supplierDirections, Set<String> wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return false;
        }
        for (String raw : split(supplierDirections)) {
            String direction = normalizeDirection(raw);
            if ("全部产品".equals(direction)) {
                return true;
            }
            if (wanted.contains(direction)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> directionTokens(String productType, String brand) {
        Set<String> tokens = new LinkedHashSet<>();
        String type = StringUtils.trimToEmpty(productType).toUpperCase(Locale.ROOT);
        if (type.isEmpty()) {
            return tokens;
        }
        String normalizedBrand = normalizeBrand(brand);
        boolean recognized = false;
        if (type.contains("SSD")) {
            recognized = true;
            tokens.add("服务器SSD");
            tokens.add("消费级SSD");
            if (!normalizedBrand.isEmpty()) {
                tokens.add("SSD-" + normalizedBrand);
            }
        }
        if (type.contains("CPU")) {
            recognized = true;
            tokens.add("服务器CPU");
            tokens.add("消费级CPU");
        }
        if (type.contains("RAM") || type.contains("MEMORY")) {
            recognized = true;
            tokens.add("消费级RAM");
            if (!normalizedBrand.isEmpty()) {
                tokens.add("服务器RAM-" + normalizedBrand);
            } else {
                tokens.addAll(Arrays.asList("服务器RAM-Samsung", "服务器RAM-Micron", "服务器RAM-SKhynix"));
            }
        }
        if (type.contains("HDD")) {
            recognized = true;
            if (!normalizedBrand.isEmpty()) {
                tokens.add("HDD-" + normalizedBrand);
            } else {
                tokens.addAll(Arrays.asList("HDD-WD", "HDD-Seagate", "HDD-Toshiba"));
            }
        }
        if (type.contains("GPU") || type.contains("显卡")) {
            recognized = true;
            if ("Nvida".equals(normalizedBrand) || "AMD".equals(normalizedBrand)) {
                tokens.add("显卡-" + normalizedBrand);
            } else {
                tokens.add("显卡-Nvida");
                tokens.add("显卡-AMD");
            }
        }
        if (recognized) {
            tokens.add("全部产品");
        }
        return tokens;
    }

    public List<String> groups(String raw) {
        return split(raw);
    }

    private List<String> split(String raw) {
        List<String> values = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return values;
        }
        for (String part : raw.split("[,，]")) {
            String value = StringUtils.trimToEmpty(part);
            if (!value.isEmpty() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private String normalizeDirection(String raw) {
        String value = StringUtils.trimToEmpty(raw);
        return value.startsWith("卖") ? value.substring(1) : value;
    }

    private String normalizeBrand(String raw) {
        String value = StringUtils.trimToEmpty(raw).toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        if (value.contains("SAMSUNG")) return "Samsung";
        if (value.contains("MICRON") || value.equals("MT")) return "Micron";
        if (value.contains("SKHYNIX") || value.contains("HYNIX")) return "SKhynix";
        if (value.contains("KIOXIA")) return "Kioxia";
        if (value.contains("SANDISK")) return "SanDisk";
        if (value.contains("YMTC") || value.contains("长江存储")) return "YMTC";
        if (value.contains("SEAGATE")) return "Seagate";
        if (value.contains("TOSHIBA")) return "Toshiba";
        if (value.contains("WESTERN") || value.equals("WD")) return "WD";
        if (value.contains("NVIDIA") || value.contains("NVIDA")) return "Nvida";
        if (value.contains("AMD")) return "AMD";
        return StringUtils.substring(value, 0, 32);
    }
}
