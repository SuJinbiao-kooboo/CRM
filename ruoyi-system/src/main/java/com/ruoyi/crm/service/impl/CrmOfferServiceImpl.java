package com.ruoyi.crm.service.impl;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import lombok.extern.slf4j.Slf4j;
import com.ruoyi.common.utils.PatternUtil;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.crm.service.ai.DeepSeekAiService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.crm.domain.CrmOffer;
import com.ruoyi.crm.domain.CrmOfferPriceMode;
import com.ruoyi.crm.domain.OfferTextLine;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.mapper.CrmOfferMapper;
import com.ruoyi.crm.mapper.CrmSupplierMapper;
import com.ruoyi.crm.service.ICrmOfferService;
import com.ruoyi.crm.service.ICrmSupplierService;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.crm.util.PnUtils;
import com.ruoyi.crm.util.OfferTextFormatter;
import com.ruoyi.system.service.ISysConfigService;

@Service
@Slf4j
public class CrmOfferServiceImpl implements ICrmOfferService {

    @Autowired
    private CrmOfferMapper offerMapper;

    @Autowired
    private CrmSupplierMapper supplierMapper;

    @Autowired
    private DeepSeekAiService deepSeekAiService;

    @Autowired
    private ICrmSupplierService supplierService;

    @Autowired
    private ISysConfigService sysConfigService;

    @Override
    public List<CrmOffer> selectOfferList(CrmOffer offer) {

        List<CrmOffer> crmOffers = offerMapper.selectOfferList(offer);
        applySupplierNameAccess(crmOffers);

        return crmOffers;
    }

    /**
     * 按数据权限填充供应商真实名称：有全权限（manager/admin）显示供应商表真实名称，无权限时名称统一显示"无"。
     * crm_offer 的 supplier_name 字段存的一直是供应商编码，真实名称需关联供应商表查询。
     */
    private void applySupplierNameAccess(List<CrmOffer> offers) {
        if (offers == null || offers.isEmpty()) {
            return;
        }
        boolean full = supplierService.isFullAccess();
        Map<String, String> nameMap = new HashMap<>();
        if (full) {
            LinkedHashSet<String> codes = new LinkedHashSet<>();
            for (CrmOffer o : offers) {
                if (o.getSupplierCode() != null && !o.getSupplierCode().trim().isEmpty()) {
                    codes.add(o.getSupplierCode().trim());
                }
            }
            if (!codes.isEmpty()) {
                for (CrmSupplier s : supplierMapper.selectNamesByCodes(new ArrayList<>(codes))) {
                    if (s.getSupplierCode() != null) {
                        nameMap.put(s.getSupplierCode().trim().toUpperCase(), s.getSupplierName());
                    }
                }
            }
        }
        for (CrmOffer o : offers) {
            String code = o.getSupplierCode() == null ? null : o.getSupplierCode().trim();
            if (!full) {
                o.setSupplierName("无");
            } else {
                String real = code == null ? null : nameMap.get(code.toUpperCase());
                o.setSupplierName(real == null || real.trim().isEmpty() ? code : real);
            }
        }
    }

    @Override
    public CrmOffer selectOfferById(Long id) {
        return offerMapper.selectOfferById(id);
    }

    @Override
    public int insertOffer(CrmOffer offer) {
        String userName = SecurityUtils.getUsername();
        SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss");
        offer.setSheetName(userName + "_" + sdf.format(new Date()));
        offer.setStatus(1);
        offer.setCreateBy(userName);
        offer.setUpdateBy(userName);
        offer.setPnNormalized(PnUtils.normalize(offer.getProductCode()));
        if (offer.getPriceOffer() == null && offer.getPriceCost() != null) {
            offer.setPriceOffer(round2(offer.getPriceCost() * 1.03d));
        }
        return offerMapper.insertOffer(offer);
    }

    @Override
    public int updateOffer(CrmOffer offer) {
        if (offer.getProductCode() != null) {
            offer.setPnNormalized(PnUtils.normalize(offer.getProductCode()));
        }
        return offerMapper.updateOffer(offer);
    }

    @Override
    public int deleteOfferByIds(Long[] ids) {
        return offerMapper.deleteOfferByIds(ids);
    }

    @Override
    public int batchUpdate(List<Long> ids, CrmOffer offer) {
        if (offer.getProductCode() != null) {
            offer.setPnNormalized(PnUtils.normalize(offer.getProductCode()));
        }
        return offerMapper.batchUpdate(ids, offer);
    }

    @Override
    @Transactional
    public Map<String, Object> importOffers(MultipartFile file, String supplierCode, String supplierName, String inqOfferType, Map<String, String> colMap, Double profitRatio) {
        // 数据权限：非管理员/超级管理员仅能导入跟进人包含自己的供应商（防止绕过前端直接提交）
        CrmSupplier authorizedSupplier = supplierService.selectAuthorizedSupplierByCode(supplierCode == null ? "" : supplierCode.trim());
        // 供应商名称以数据库为准，防止前端传入的名称被篡改
        if (authorizedSupplier.getSupplierName() != null && !authorizedSupplier.getSupplierName().trim().isEmpty()) {
            supplierName = authorizedSupplier.getSupplierName();
        }
        int success = 0;
        List<Map<String, Object>> fails = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return summary(success, fails);
            String sheetName = (file.getOriginalFilename() == null ? "" : file.getOriginalFilename()) + "_" + sheet.getSheetName();
            int lastRow = sheet.getLastRowNum();
            int idxProduct = letterToIndex(colMap.getOrDefault("productCode", ""));
            if (idxProduct < 0) return summary(success, fails);
            String productDetailLetters = colMap.get("productDetail");
            Integer idxBrand = optIndex(colMap.get("productBrand"));
            Integer idxType = optIndex(colMap.get("productType"));
            Integer idxPriceCost = optIndex(colMap.get("priceCost"));
            Integer idxPriceOffer = optIndex(colMap.get("priceOffer"));
            Integer idxQuantity = optIndex(colMap.get("quantity"));
            Integer idxDeliveryTime = optIndex(colMap.get("deliveryTime"));
            Integer idxRemark = optIndex(colMap.get("remark"));
            Integer idxMoq = optIndex(colMap.get("moqQuantity"));
            Integer idxWarranty = optIndex(colMap.get("warrantyDetail"));
            Integer idxDc = optIndex(colMap.get("dc"));
            Date now = new Date();

            for (int r = sheet.getFirstRowNum(); r <= lastRow; r++) {
                if(r==0){
                    continue;
                }

                Row row = sheet.getRow(r);
                if (row == null) continue;
                String productCode = getString(row.getCell(idxProduct));
                if (productCode == null || productCode.trim().isEmpty()) { fails.add(fail(r+1, "产品编码为空")); continue; }
                CrmOffer offer = new CrmOffer();
                offer.setProductCode(productCode.trim());
                offer.setPnNormalized(PnUtils.normalize(productCode));
                offer.setSupplierCode(supplierCode);
                offer.setSupplierName(supplierName);
                offer.setInqOfferType(inqOfferType);
                offer.setStockDate(now);
                offer.setSheetName(sheetName);
                String mergedDetail = mergeColumns(row, productDetailLetters);
                if (mergedDetail != null) offer.setProductDetail(mergedDetail);
                if (idxBrand != null) offer.setProductBrand(getString(row.getCell(idxBrand)));
                Double cost = idxPriceCost != null ? getDouble(row.getCell(idxPriceCost)) : null;
                offer.setPriceCost(cost);
                Double price = idxPriceOffer != null ? getDouble(row.getCell(idxPriceOffer)) : null;
                if (price == null && cost != null) {
                    double ratio = profitRatio == null ? 2d : profitRatio.doubleValue();
                    price = round2(cost * (1d + ratio / 100d));
                }
                offer.setPriceOffer(price);
                offer.setPriceUnit("USD");
                if (idxType != null) offer.setProductType(getString(row.getCell(idxType)));
                Integer qty = idxQuantity != null ? getInteger(row.getCell(idxQuantity)) : null;
                offer.setQuantity(qty);
                if (idxDeliveryTime != null) offer.setDeliveryTime(getString(row.getCell(idxDeliveryTime)));
                if (idxRemark != null) offer.setRemark(getString(row.getCell(idxRemark)));
                if (idxMoq != null) offer.setMoqQuantity(getInteger(row.getCell(idxMoq)));
                if (idxWarranty != null) offer.setWarrantyDetail(getString(row.getCell(idxWarranty)));
                if (idxDc != null) {
                    String dcVal = getString(row.getCell(idxDc));
                    if (dcVal != null) {
                        dcVal = dcVal.trim();
                        offer.setDc(dcVal.length() > 32 ? dcVal.substring(0, 32) : dcVal);
                    }
                }
                offer.setStatus(1);
                try {
                    success += offerMapper.insertOffer(offer);
                } catch (Exception e) {
                    fails.add(fail(r+1, e.getMessage()));
                }
            }
        } catch (Exception e) {
            fails.add(fail(0, e.getMessage()));
        }
        return summary(success, fails);
    }

    private String mergeColumns(Row row, String lettersCsv) {
        if (lettersCsv == null || lettersCsv.trim().isEmpty()) return null;
        String[] parts = lettersCsv.split(",");
        List<String> vals = new ArrayList<>();
        for (String p : parts) {
            int idx = letterToIndex(p);
            if (idx >= 0) {
                String v = getString(row.getCell(idx));
                if (v != null && !v.trim().isEmpty()) vals.add(v.trim());
            }
        }
        if (vals.isEmpty()) return null;
        return String.join(" ", vals);
    }

    private Map<String, Object> summary(int success, List<Map<String, Object>> fails) {
        Map<String, Object> map = new HashMap<>();
        map.put("successCount", success);
        map.put("failCount", fails.size());
        map.put("failDetails", fails);
        return map;
    }

    private Map<String, Object> fail(int rowIndex, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("row", rowIndex);
        m.put("reason", reason);
        return m;
    }

    private Integer optIndex(String letter) {
        int idx = letterToIndex(letter);
        return idx < 0 ? null : idx;
    }

    private int letterToIndex(String letter) {
        if (letter == null || letter.trim().isEmpty()) return -1;
        String s = letter.trim().toUpperCase();
        int result = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 'A' || c > 'Z') return -1;
            result = result * 26 + (c - 'A' + 1);
        }
        return result - 1;
    }

    private String getString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return null;
        }
    }

    private Double getDouble(Cell cell) {
        if (cell == null) return null;
        try {
            switch (cell.getCellType()) {
                case STRING: {
                    String s = cell.getStringCellValue();
                    if (s == null || s.trim().isEmpty()) return null;
                    return Double.valueOf(PatternUtil.extractNumbersAndFirstDot(s));
                }
                case NUMERIC: return cell.getNumericCellValue();
                default: return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getInteger(Cell cell) {
        if (cell == null) return null;
        try {
            switch (cell.getCellType()) {
                case STRING: {
                    String s = cell.getStringCellValue();
                    if (s == null || s.trim().isEmpty()) return null;
                    return Integer.valueOf(PatternUtil.extractNumbersAndFirstDot(s));
                }
                case NUMERIC: return (int) Math.round(cell.getNumericCellValue());
                default: return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Double round2(double v) {
        return NumberUtil.round(Math.round(v * 100.0d) / 100.0d, 0).doubleValue();
    }

    /** 报价向上取整到5的倍数（以0或5结尾，保留整数，不留小数） */
    private Double ceilTo5(double v) {
        return Math.ceil(v / 5.0d) * 5.0d;
    }

    /** 颗粒类产品判定：AI识别产品类型为“颗粒”（eMMC/UFS/NAND/DRAM颗粒等，利润空间小）时走精确百分比报价 */
    private boolean isGranularProduct(String productType) {
        return productType != null && productType.trim().contains("颗粒");
    }

    /** 按利润比例精确计算报价：严格百分比、保留2位小数，不取整到5 */
    private Double priceWithPercent(double cost, double ratio) {
        return new BigDecimal(String.valueOf(cost * (1d + ratio / 100d))).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** 原文无币种按 USD；无法归属的币种保留原值但不参与报价和好价格。 */
    private String normalizeCurrency(String value) {
        String c = value == null ? "" : value.trim().toUpperCase();
        if (c.isEmpty() || "USD".equals(c) || "$".equals(c) || "US$".equals(c) || "美元".equals(c)) {
            return "USD";
        }
        return c.length() > 32 ? c.substring(0, 32) : c;
    }

    @Override
    @Transactional
    public int aiEntryOffers(String supplierCode, String supplierName, String inqOfferType, Double profitRatio, String content) {
        // 兼容原调用方：只关心成功条数
        return aiEntryOffersReturning(supplierCode, supplierName, inqOfferType, profitRatio, content).size();
    }

    /**
     * AI智能录入（返回入库明细）：逻辑同 aiEntryOffers，同时收集本次成功入库的Offer（供录入后近1个月同料号比价使用）
     */
    @Override
    @Transactional
    public List<CrmOffer> aiEntryOffersReturning(String supplierCode, String supplierName, String inqOfferType, Double profitRatio, String content) {
        return aiEntryOffersReturningInternal(supplierCode, supplierName, inqOfferType, profitRatio, content,
                CrmOfferPriceMode.COST_PLUS_PROFIT, SecurityUtils.getUsername(), true, null, null, 0L, null);
    }

    @Override
    @Transactional
    public List<CrmOffer> aiEntryOffersReturningAsSystem(String supplierCode, String supplierName,
                                                          String inqOfferType, Double profitRatio, String content,
                                                          String operator, String wechatServerId,
                                                          String wechatUsername, long wechatLocalId, String remark) {
        return aiEntryOffersReturningAsSystem(supplierCode, supplierName, inqOfferType, profitRatio, content,
                CrmOfferPriceMode.COST_PLUS_PROFIT, operator, wechatServerId, wechatUsername, wechatLocalId, remark);
    }

    @Override
    @Transactional
    public List<CrmOffer> aiEntryOffersReturningAsSystem(String supplierCode, String supplierName,
                                                          String inqOfferType, Double profitRatio, String content,
                                                          CrmOfferPriceMode priceMode, String operator,
                                                          String wechatServerId, String wechatUsername,
                                                          long wechatLocalId, String remark) {
        String userName = operator == null || operator.trim().isEmpty() ? "wechat_auto" : operator.trim();
        return aiEntryOffersReturningInternal(supplierCode, supplierName, inqOfferType, profitRatio, content,
                priceMode == null ? CrmOfferPriceMode.COST_PLUS_PROFIT : priceMode,
                userName, false, wechatServerId, wechatUsername, wechatLocalId, remark);
    }

    /**
     * AI录入按钮与微信自动录入共用核心逻辑。自动任务只改变操作人来源和供应商权限校验方式，
     * 不复制一套解析/计算/入库代码。
     */
    private List<CrmOffer> aiEntryOffersReturningInternal(String supplierCode, String supplierName,
                                                           String inqOfferType, Double profitRatio, String content,
                                                           CrmOfferPriceMode priceMode,
                                                           String userName, boolean authorizeSupplier,
                                                           String wechatServerId, String wechatUsername,
                                                           long wechatLocalId, String sourceRemark) {
        if (content == null || content.trim().isEmpty()) {
            throw new ServiceException("粘贴的物料内容不能为空");
        }
        if (supplierCode == null || supplierCode.trim().isEmpty()) {
            throw new ServiceException("供应商信息不能为空");
        }
        if (!"Offer".equals(inqOfferType) && !"Inq".equals(inqOfferType)) {
            throw new ServiceException("类型必须为Inq或Offer");
        }
        supplierCode = supplierCode.trim();
        CrmSupplier authorizedSupplier = authorizeSupplier
                ? supplierService.selectAuthorizedSupplierByCode(supplierCode)
                : supplierMapper.selectSupplierByCode(supplierCode);
        if (authorizedSupplier == null) {
            throw new ServiceException("供应商不存在：" + supplierCode);
        }
        // 供应商名称以数据库为准，防止前端传入的名称被篡改
        if (authorizedSupplier.getSupplierName() != null && !authorizedSupplier.getSupplierName().trim().isEmpty()) {
            supplierName = authorizedSupplier.getSupplierName();
        }
        double ratio = profitRatio == null ? 2d : profitRatio.doubleValue();
        if (authorizedSupplier.getProfitRatio() != null) {
            ratio = authorizedSupplier.getProfitRatio();
        }
        // 调用AI把无规则文本整理为结构化物料列表（品牌/料号/型号/规格型号/数量/报价）
        List<Map<String, Object>> items = deepSeekAiService.parseMaterialContent(content);

        Date now = new Date();
        // 来源表名：AI+时间+供应商名+操作人，便于追溯录入批次
        String sheetName = "AI_" + new SimpleDateFormat("yyyyMMddHHmm").format(now) + "_" + supplierCode + "_" + userName;

        List<CrmOffer> saved = new ArrayList<>();
        for (Map<String, Object> item : items) {
            CrmOffer offer = new CrmOffer();
            offer.setProductCode(str(item.get("partNumber")));
            offer.setProductBrand(str(item.get("brand")));
            offer.setProductDetailCode(str(item.get("model")));
            String aiDetail = str(item.get("productDetail"));
            // 详情优先取AI补充的汇总详情，为空时回退原文规格细节
            offer.setProductDetail(aiDetail.isEmpty() ? str(item.get("spec")) : aiDetail);
            offer.setSpec(str(item.get("spec")));
            offer.setDc(str(item.get("dc")));
            offer.setCoo(str(item.get("coo")));
            offer.setDeliveryTime(str(item.get("deliveryTime")));
            offer.setProductType(str(item.get("productType")));
            Object qty = item.get("quantity");
            offer.setQuantity(qty instanceof Number ? ((Number) qty).intValue() : null);
            Object price = item.get("price");
            Double cost = price instanceof Number ? ((Number) price).doubleValue() : null;
            String currency = normalizeCurrency(str(item.get("currency")));
            boolean usd = "USD".equals(currency);
            if (priceMode == CrmOfferPriceMode.AS_IS) {
                offer.setPriceCost(null);
                offer.setPriceOffer(usd && cost != null ? cost : null);
            } else if (priceMode == CrmOfferPriceMode.NONE) {
                offer.setPriceCost(cost);
                offer.setPriceOffer(null);
            } else {
                offer.setPriceCost(cost);
                if (usd && cost != null && cost > 0) {
                    offer.setPriceOffer(isGranularProduct(str(item.get("productType")))
                            ? priceWithPercent(cost, ratio)
                            : ceilTo5(cost * (1d + ratio / 100d)));
                }
            }
            offer.setPriceUnit(currency);
            offer.setStockDate(now);
            offer.setSheetName(sheetName);
            offer.setSupplierCode(supplierCode);
            offer.setSupplierName(supplierName);
            offer.setInqOfferType(inqOfferType);
            offer.setPnNormalized(PnUtils.normalize(offer.getProductCode()));
            offer.setStatus(1);
            offer.setCreateBy(userName);
            offer.setUpdateBy(userName);
            if (wechatServerId != null) {
                offer.setWechatServerId(wechatServerId);
                offer.setWechatUsername(wechatUsername);
                offer.setWechatLocalId(wechatLocalId);
            }
            if (sourceRemark != null && !sourceRemark.trim().isEmpty()) {
                offer.setRemark(sourceRemark.length() > 500 ? sourceRemark.substring(0, 500) : sourceRemark);
            }
            if (offerMapper.insertOffer(offer) > 0) {
                saved.add(offer);
            }
        }
        return saved;
    }

    /**
     * AI料号查询：调用AI提取完整料号，查询各料号最近1个月内的INQ/OFFER历史记录
     * 返回按料号分组的结果，组内记录已按Offer日期（库存日期优先、为空回退创建时间）倒排
     */
    @Override
    public List<Map<String, Object>> aiQueryHistory(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ServiceException("粘贴的物料内容不能为空");
        }
        // 1. 提取料号：每一行都是纯料号（无空格/逗号/制表符的单个词）时直接提取，跳过AI解析；
        //    否则（多字段物料描述文本）调用AI提取结构化物料信息
        List<Map<String, Object>> items;
        List<String> simpleParts = extractSimplePartNumbers(content);
        if (simpleParts != null && !simpleParts.isEmpty()) {
            items = new ArrayList<>();
            for (String p : simpleParts) {
                Map<String, Object> m = new HashMap<>();
                m.put("partNumber", p);
                items.add(m);
            }
        } else {
            items = deepSeekAiService.parseMaterialContent(content);
        }
        // 2. 提取料号（转大写去重、保持原文顺序；入库均大写，但兼容历史数据大小写，查询用upper不区分大小写）
        LinkedHashSet<String> partNumbers = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            String pn = str(item.get("partNumber")).trim().toUpperCase();
            if (!pn.isEmpty()) {
                partNumbers.add(pn);
            }
        }
        if (partNumbers.isEmpty()) {
            throw new ServiceException("AI未能从内容中识别出料号，请检查粘贴内容");
        }
        // 3. 按料号集合查询近1个月历史并装配为分组结构（AI查询、AI录入比价、复制Offer共用）
        return buildPartNumberGroups(new ArrayList<>(partNumbers));
    }

    /**
     * 按料号集合查询最近1个月内（Offer日期口径：库存日期优先、为空回退创建时间）的INQ/OFFER记录，
     * 按实际匹配到的完整料号分组返回（组内按Offer日期倒排），供应商名称已按数据权限处理；
     * 供AI料号查询/复制Offer（当日料号比价）共用
     */
    private List<Map<String, Object>> buildPartNumberGroups(List<String> partNumbers) {
        List<CrmOffer> records = offerMapper.selectHistoryByPartNumbers(partNumbers);
        applySupplierNameAccess(records);
        Map<String, List<CrmOffer>> groupMap = new LinkedHashMap<>();
        for (CrmOffer o : records) {
            String key = str(o.getProductCode()).trim().toUpperCase();
            groupMap.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<CrmOffer>> entry : groupMap.entrySet()) {
            Map<String, Object> group = new HashMap<>();
            group.put("partNumber", entry.getKey());
            List<Map<String, Object>> offerList = new ArrayList<>();
            for (CrmOffer o : entry.getValue()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", o.getId());
                m.put("status", o.getStatus());
                m.put("supplierName", o.getSupplierName());
                m.put("supplierCode", o.getSupplierCode());
                m.put("productType", o.getProductType());
                m.put("dc", o.getDc());
                m.put("coo", o.getCoo());
                m.put("priceCost", o.getPriceCost());
                m.put("inqOfferType", o.getInqOfferType());
                m.put("quantity", o.getQuantity());
                m.put("priceOffer", o.getPriceOffer());
                m.put("createTime", o.getCreateTime() == null ? "" : DateUtil.format(o.getCreateTime(), "yyyy-MM-dd HH:mm"));
                // Offer日期：库存日期优先、为空回退创建时间（与组内排序口径一致，前端按此列展示）
                Date offerDate = o.getStockDate() != null ? o.getStockDate() : o.getCreateTime();
                m.put("offerDate", offerDate == null ? "" : DateUtil.format(offerDate, "yyyy-MM-dd HH:mm"));
                m.put("deliveryTime", o.getDeliveryTime());
                m.put("spec", o.getSpec());
                m.put("productDetail", o.getProductDetail());
                offerList.add(m);
            }
            group.put("offers", offerList);
            result.add(group);
        }
        return result;
    }

    /**
     * 复制Offer（最近N天料号比价）：取最近N天（N=1当天0点起、N=2昨天0点起、N=3前天0点起，按创建时间口径）
     * 录入的全部Offer去重料号，按料号查询最近1个月历史，返回与AI料号查询相同的分组结构（前端按价格优势阈值自动勾选）
     */
    @Override
    public List<Map<String, Object>> todayCopyGroups(int days) {
        if (days < 1 || days > 365) {
            throw new ServiceException("最近天数范围应为1-365天");
        }
        List<String> partNumbers = offerMapper.selectTodayOfferedPartNumbers(days);
        if (partNumbers == null || partNumbers.isEmpty()) {
            return new ArrayList<>();
        }
        return buildPartNumberGroups(partNumbers);
    }

        /**
     * 供应商名称详情（点击列表供应商名称时实时查询）：先判数据权限，
     * 有权限（admin/manager角色）返回供应商表真实名称（查不到返回"未找到名称"），
     * 无权限返回"您无权查看"（不返回编码，不泄露任何供应商信息）
     */
    @Override
    public String supplierNameDetail(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "";
        }
        String c = code.trim();
        // 无权限：仅返回编码
        if (!supplierService.isFullAccess()) {
            return "您无权查看";
        }
        // 有权限：查供应商表真实名称（upper 不区分大小写）；无记录或名称为空回退编码
        List<CrmSupplier> list = supplierMapper.selectNamesByCodes(Collections.singletonList(c));
        for (CrmSupplier s : list) {
            if (s.getSupplierName() != null && !s.getSupplierName().trim().isEmpty()) {
                return s.getSupplierName().trim();
            }
        }
        return "未找到名称";
    }

    /**
     * AI录入后比价：查询料号集合最近1个月内（Offer日期口径：库存日期优先、为空回退创建时间）的INQ/OFFER记录，按Offer日期倒序返回
     * 字段：partNumber(物料编号)、supplierCode(供应商编号)、priceCost(成本价格)、priceOffer(Offer价格)、
     *       offerDate(Offer日期)、productDetail(详情)、quantity(数量)、deliveryTime(交期)、dc、inqOfferType(类型)
     * 说明：料号为空/无记录时返回空列表，不抛异常（录入成功后比价失败不应影响录入结果）
     */
    @Override
    public List<Map<String, Object>> compareRecentOffers(List<String> partNumbers) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (partNumbers == null || partNumbers.isEmpty()) {
            return result;
        }
        // 料号转大写去重（入库均大写，upper匹配兼容历史数据大小写），保持传入顺序
        LinkedHashSet<String> pns = new LinkedHashSet<>();
        for (String p : partNumbers) {
            String v = str(p).trim().toUpperCase();
            if (!v.isEmpty()) {
                pns.add(v);
            }
        }
        if (pns.isEmpty()) {
            return result;
        }
        // SQL已限定近1个月并已按Offer日期（库存日期优先、为空回退创建时间）倒序排序
        List<CrmOffer> list = offerMapper.selectRecentByPartNumbers(new ArrayList<>(pns));
        applySupplierNameAccess(list);
        for (CrmOffer o : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("status", o.getStatus());
            m.put("partNumber", o.getProductCode());
            m.put("supplierCode", o.getSupplierCode());
            m.put("supplierName", o.getSupplierName());
            m.put("priceCost", o.getPriceCost());
            m.put("priceOffer", o.getPriceOffer());
            Date od = o.getStockDate() != null ? o.getStockDate() : o.getCreateTime();
            m.put("offerDate", od == null ? "" : DateUtil.format(od, "yyyy-MM-dd"));
            m.put("productDetail", o.getProductDetail());
            m.put("quantity", o.getQuantity());
            m.put("deliveryTime", o.getDeliveryTime());
            m.put("productType", o.getProductType());
            m.put("coo", o.getCoo());
            m.put("spec", o.getSpec());
            m.put("dc", o.getDc());
            m.put("inqOfferType", o.getInqOfferType());
            result.add(m);
        }
        return result;
    }

    /**
     * 复制Offer：按库存日期筛选，days=1取今天0点至当前时间、days=N取N-1天前0点至当前时间，仅Offer记录，
     * 按品牌升序排列，相同料号（品牌+料号）取成本最低的一条
     * （无价格记录也保留：同料号都无价格时取最新一条），组装为"料号 数量(带pcs) 品牌 DC 交期 详情 报价(带USD)"制表符分隔文本，
     * 首行为英文表头：Part No.\tQty\tBrand\tDC\tDelivery\tDetail\tPrice
     * 注意：不输出成本字段（复制内容会发给客户，成本为内部信息）
     */
    @Override
    public String buildCopyOfferText(int days) {
        if (days < 1 || days > 365) {
            throw new ServiceException("最近天数范围应为1-365天");
        }
        List<CrmOffer> list = offerMapper.selectCopyOffers(days);
        if (list.isEmpty()) {
            throw new ServiceException("最近" + days + "天内没有Offer记录");
        }
        // 品牌升序 → 料号升序 → 成本升序（无价格排最后）→ 创建时间倒序（同料号都无价格时取最新）
        list.sort(Comparator.comparing(CrmOffer::getProductBrand, Comparator.nullsLast(String::compareTo))
                .thenComparing(CrmOffer::getProductCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(CrmOffer::getPriceCost, Comparator.nullsLast(Double::compareTo))
                .thenComparing(CrmOffer::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        // 相同料号（品牌+料号）去重取第一条，即成本最低的一条（无价格记录也会保留）
        Map<String, CrmOffer> dedup = new LinkedHashMap<>();
        for (CrmOffer o : list) {
            dedup.putIfAbsent(copyKey(o), o);
        }
        // 首行英文表头，其后每行一条记录（表头便于客户识别列含义）
        StringBuilder sb = new StringBuilder("Part No.\tQty\tBrand\tDC\tDelivery\tDetail\tPrice");
        for (CrmOffer o : dedup.values()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(nvl(o.getProductCode())).append('\t')
                    .append(o.getQuantity() == null ? "" : o.getQuantity() + "pcs").append('\t')
                    .append(nvl(o.getProductBrand())).append('\t')
                    .append(nvl(o.getDc())).append('\t')
                    .append(nvl(o.getDeliveryTime())).append('\t')
                    .append(nvl(o.getProductDetail())).append('\t')
                    .append(o.getPriceOffer() == null ? "" : fmtPrice(o.getPriceOffer()) + "USD");
        }
        return sb.toString();
    }

    /**
     * 批量判断料号价格优势，Offer 页面与微信任务共用。
     * 窗口为最近 N 个自然日；窗口内最低 price_offer 比窗口开始前最近两条有效报价的最低价低至少阈值时命中。
     */
    @Override
    public Map<String, Boolean> priceAdvantageBatch(List<String> partNumbers, int days) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (partNumbers == null || partNumbers.isEmpty()) {
            return result;
        }
        int windowDays = Math.max(1, Math.min(999, days));
        double threshold = 15d;
        try {
            String configured = sysConfigService.selectConfigByKey("offer.price.advantage.threshold");
            if (configured != null && !configured.trim().isEmpty()) {
                threshold = Double.parseDouble(configured.trim());
            }
        } catch (Exception e) {
            log.warn("读取价格优势阈值失败，使用默认值15: {}", e.getMessage());
        }
        String todayKey = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, -(windowDays - 1));
        Date windowStart = calendar.getTime();
        SimpleDateFormat dayOf = new SimpleDateFormat("yyyy-MM-dd");
        for (String rawPn : partNumbers) {
            String pn = PnUtils.normalize(rawPn);
            if (pn.isEmpty()) {
                continue;
            }
            List<CrmOffer> history = offerMapper.selectPriceHistoryByPn(pn);
            List<Double> windowPrices = new ArrayList<>();
            List<CrmOffer> beforeWindow = new ArrayList<>();
            if (history != null) {
                for (CrmOffer offer : history) {
                    if (offer.getPriceOffer() == null || offer.getPriceOffer() <= 0) {
                        continue;
                    }
                    Date offerDate = offer.getStockDate() == null ? offer.getCreateTime() : offer.getStockDate();
                    if (offerDate == null) {
                        continue;
                    }
                    String dateKey = dayOf.format(offerDate);
                    if (dateKey.compareTo(dayOf.format(windowStart)) >= 0 && dateKey.compareTo(todayKey) <= 0) {
                        windowPrices.add(offer.getPriceOffer());
                    } else if (offerDate.before(windowStart)) {
                        beforeWindow.add(offer);
                    }
                }
            }
            boolean advantage = false;
            if (!windowPrices.isEmpty() && beforeWindow.size() >= 2) {
                double windowBest = Collections.min(windowPrices);
                double baseline = Math.min(beforeWindow.get(0).getPriceOffer(), beforeWindow.get(1).getPriceOffer());
                advantage = baseline - windowBest >= threshold;
            }
            result.put(pn, advantage);
        }
        return result;
    }

    /** 复制去重key：品牌+料号（同品牌下相同料号视为同一物料） */
    private String copyKey(CrmOffer o) {
        return (o.getProductBrand() == null ? "" : o.getProductBrand()) + "\u0001" + (o.getProductCode() == null ? "" : o.getProductCode());
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 复制时价格文本：去尾零小数；整数金额(如5.0/5.00)输出为无小数(5)，有非零小数的保留小数(如5.5、5.25) */
    private String fmtPrice(Double price) {
        if (price == null) {
            return "";
        }
        return new BigDecimal(String.valueOf(price)).stripTrailingZeros().toPlainString();
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
    /** 品牌分组值：为空返回空串（无品牌归为最后一组且不输出标题） */
    private String brandOf(CrmOffer o) {
        return str(o.getProductBrand());
    }

    /**
     * 一键复制Offer（AI录入比价/AI查询共用）：按料号集合精确匹配（upper不区分大小写）查询最近days天内
     * （1=当天0点至当前，N=N-1天前0点至当前；Offer日期口径：库存日期优先、为空回退创建时间）仅Offer记录，
     * 每个料号取Offer价格最低的一条（同价取日期最新；该料号无记录则不输出）；
     * 默认输出 料号/数量(带pcs)/Offer价格(带USD)，extraFields 勾选后按固定顺序追加对应列，
     * 组装为制表符分隔文本，首行为英文表头（随勾选字段变化）：Part No.\tQty\tPrice[\tSupplier][\tDetail][\tDelivery][\tDC]
     */
    /**
     * 批量失效：把指定id的Offer置为无效（status=0），返回影响行数
     */
    @Override
    public int invalidateOffers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return offerMapper.invalidateOfferByIds(ids);
    }

    @Override
    public String buildAiQueryCopyText(List<String> partNumbers, int days, List<String> extraFields) {
        if (days < 1 || days > 999) {
            throw new ServiceException("最近天数范围应为1-999天");
        }
        if (partNumbers == null || partNumbers.isEmpty()) {
            throw new ServiceException("没有可复制的物料，请先执行AI查询");
        }
        // 料号转大写去重（入库均大写，upper匹配兼容历史数据大小写），保持传入顺序
        LinkedHashSet<String> pns = new LinkedHashSet<>();
        for (String p : partNumbers) {
            String v = str(p).toUpperCase();
            if (!v.isEmpty()) {
                pns.add(v);
            }
        }
        if (pns.isEmpty()) {
            throw new ServiceException("没有可复制的物料，请先执行AI查询");
        }
        // 额外字段白名单校验并按固定顺序排列（供应商编号→详情→交期→DC），前端勾选先后不影响输出列顺序
        LinkedHashSet<String> extras = new LinkedHashSet<>();
        for (String f : new String[]{"supplierCode", "productDetail", "deliveryTime", "dc", "spec"}) {
            if (extraFields != null && extraFields.contains(f)) {
                extras.add(f);
            }
        }
        List<CrmOffer> list = offerMapper.selectRecentOffersByPartNumbers(new ArrayList<>(pns), days);
        if (list.isEmpty()) {
            throw new ServiceException("最近" + days + "天内这些物料没有Offer记录");
        }
        // 报价升序 → Offer日期倒序（同价取日期最新），再按料号去重取第一条，即每个料号报价最低的最新Offer
        list.sort(Comparator.comparing(CrmOffer::getPriceOffer, Comparator.nullsLast(Double::compareTo))
                .thenComparing((CrmOffer o) -> o.getStockDate() == null ? o.getCreateTime() : o.getStockDate(),
                        Comparator.nullsLast(Comparator.reverseOrder())));
        Map<String, CrmOffer> dedup = new LinkedHashMap<>();
        for (CrmOffer o : list) {
            dedup.putIfAbsent(str(o.getProductCode()).toUpperCase(), o);
        }
        List<CrmOffer> rows = new ArrayList<>(dedup.values());
        List<OfferTextLine> textRows = new ArrayList<>();
        for (CrmOffer o : rows) {
            OfferTextLine line = new OfferTextLine();
            line.setPartNumber(o.getProductCode());
            line.setQuantity(o.getQuantity());
            line.setBrand(o.getProductBrand());
            line.setPrice(o.getPriceOffer());
            line.setSupplierCode(o.getSupplierCode());
            line.setProductDetail(o.getProductDetail());
            line.setDeliveryTime(o.getDeliveryTime());
            line.setDc(o.getDc());
            line.setSpec(o.getSpec());
            textRows.add(line);
        }
        return OfferTextFormatter.formatSendText(textRows, true, new ArrayList<>(extras));
    }

    /**
     * 简单料号快速提取：内容每一行都是纯料号（无空格/逗号/制表符的单个词）时直接提取，跳过AI解析
     * 避免输入"MFG-PART"这类料号前缀时AI误判为非法物料信息；任意一行不满足则返回null，走AI解析
     */
    private List<String> extractSimplePartNumbers(String content) {
        if (content == null) {
            return null;
        }
        String[] lines = content.split("\\R");
        List<String> parts = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            // 含空格/逗号/制表符或过长，说明是描述性文本而非纯料号，交给AI解析
            if (t.length() > 120 || t.contains(",") || t.contains("\t") || t.matches(".*\\s+.*")) {
                return null;
            }
            parts.add(t);
        }
        return parts;
    }
}
