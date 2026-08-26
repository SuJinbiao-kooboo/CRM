package com.ruoyi.crm.service.impl;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
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
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.mapper.CrmOfferMapper;
import com.ruoyi.crm.service.ICrmOfferService;
import com.ruoyi.crm.service.ICrmSupplierService;
import com.ruoyi.common.utils.SecurityUtils;

@Service
public class CrmOfferServiceImpl implements ICrmOfferService {

    @Autowired
    private CrmOfferMapper offerMapper;

    @Autowired
    private DeepSeekAiService deepSeekAiService;

    @Autowired
    private ICrmSupplierService supplierService;

    @Override
    public List<CrmOffer> selectOfferList(CrmOffer offer) {

        List<CrmOffer> crmOffers = offerMapper.selectOfferList(offer);

        return crmOffers;
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
        if (offer.getPriceOffer() == null && offer.getPriceCost() != null) {
            offer.setPriceOffer(round2(offer.getPriceCost() * 1.03d));
        }
        return offerMapper.insertOffer(offer);
    }

    @Override
    public int updateOffer(CrmOffer offer) {
        return offerMapper.updateOffer(offer);
    }

    @Override
    public int deleteOfferByIds(Long[] ids) {
        return offerMapper.deleteOfferByIds(ids);
    }

    @Override
    public int batchUpdate(List<Long> ids, CrmOffer offer) {
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

    @Override
    @Transactional
    public int aiEntryOffers(String supplierCode, String supplierName, String inqOfferType, Double profitRatio, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ServiceException("粘贴的物料内容不能为空");
        }
        if (supplierCode == null || supplierCode.trim().isEmpty()) {
            throw new ServiceException("供应商信息不能为空");
        }
        if (!"Offer".equals(inqOfferType) && !"Inq".equals(inqOfferType)) {
            throw new ServiceException("类型必须为Inq或Offer");
        }
        // 数据权限：非管理员/超级管理员仅能录入跟进人包含自己的供应商（防止绕过前端直接提交）
        supplierCode = supplierCode.trim();
        CrmSupplier authorizedSupplier = supplierService.selectAuthorizedSupplierByCode(supplierCode);
        // 供应商名称以数据库为准，防止前端传入的名称被篡改
        if (authorizedSupplier.getSupplierName() != null && !authorizedSupplier.getSupplierName().trim().isEmpty()) {
            supplierName = authorizedSupplier.getSupplierName();
        }
        double ratio = profitRatio == null ? 2d : profitRatio.doubleValue();
        // 调用AI把无规则文本整理为结构化物料列表（品牌/料号/型号/规格型号/数量/报价）
        List<Map<String, Object>> items = deepSeekAiService.parseMaterialContent(content);

        String userName = SecurityUtils.getUsername();
        Date now = new Date();
        // 来源表名：AI+时间+供应商名+操作人，便于追溯录入批次
        String sheetName = "AI_" + new SimpleDateFormat("yyyyMMddHHmm").format(now) + "_" + supplierCode + "_" + userName;

        int success = 0;
        for (Map<String, Object> item : items) {
            CrmOffer offer = new CrmOffer();
            offer.setProductCode(str(item.get("partNumber")));
            offer.setProductBrand(str(item.get("brand")));
            offer.setProductDetailCode(str(item.get("model")));
            offer.setProductDetail(str(item.get("spec")));
            offer.setQuantity((Integer) item.get("quantity"));
            Double cost = (Double) item.get("price");
            offer.setPriceCost(cost);
            // OFFER时按利润比例计算报价价：供应商价格×(1+利润比例/100)；INQ仅记录供应商价格
            if ("Offer".equals(inqOfferType) && cost != null) {
                offer.setPriceOffer(round2(cost * (1d + ratio / 100d)));
            }
            offer.setPriceUnit("USD");
            offer.setStockDate(now);
            offer.setSheetName(sheetName);
            offer.setSupplierCode(supplierCode);
            offer.setSupplierName(supplierName);
            offer.setInqOfferType(inqOfferType);
            offer.setStatus(1);
            offer.setCreateBy(userName);
            offer.setUpdateBy(userName);
            success += offerMapper.insertOffer(offer);
        }
        return success;
    }

    /**
     * AI料号查询：调用AI提取完整料号，查询各料号最近半年内的INQ/OFFER历史记录
     * 返回按料号分组的结果，组内记录已按OFFER价格倒序（无价格的INQ排后）
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
        // 3. 按用户提供的料号前缀模糊查询最近半年内的INQ/OFFER记录（SQL用upper不区分大小写，已按料号、价格倒序排序）
        List<CrmOffer> records = offerMapper.selectHistoryByPartNumbers(new ArrayList<>(partNumbers));
        Map<String, List<CrmOffer>> groupMap = new LinkedHashMap<>();
        for (CrmOffer o : records) {
            String key = str(o.getProductCode()).trim().toUpperCase();
            groupMap.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }
        // 4. 按实际匹配到的完整料号分组返回（一个前缀可能匹配到多个料号）
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<CrmOffer>> entry : groupMap.entrySet()) {
            Map<String, Object> group = new HashMap<>();
            group.put("partNumber", entry.getKey());
            List<Map<String, Object>> offerList = new ArrayList<>();
            for (CrmOffer o : entry.getValue()) {
                Map<String, Object> m = new HashMap<>();
                m.put("supplierName", o.getSupplierName());
                m.put("inqOfferType", o.getInqOfferType());
                m.put("quantity", o.getQuantity());
                m.put("priceOffer", o.getPriceOffer());
                m.put("createTime", o.getCreateTime() == null ? "" : DateUtil.format(o.getCreateTime(), "yyyy-MM-dd HH:mm"));
                m.put("deliveryTime", o.getDeliveryTime());
                m.put("productDetail", o.getProductDetail());
                offerList.add(m);
            }
            group.put("offers", offerList);
            result.add(group);
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
                    .append(o.getPriceOffer() == null ? "" : o.getPriceOffer() + "USD");
        }
        return sb.toString();
    }

    /** 复制去重key：品牌+料号（同品牌下相同料号视为同一物料） */
    private String copyKey(CrmOffer o) {
        return (o.getProductBrand() == null ? "" : o.getProductBrand()) + "\u0001" + (o.getProductCode() == null ? "" : o.getProductCode());
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    /**
     * AI查询复制：按料号集合精确匹配（upper不区分大小写）查询最近days天内（1=当天0点至当前，N=N-1天前0点至当前）
     * 仅Offer记录，每个料号取报价最低的一条（同价取最新；该料号无记录则不输出），
     * 货况按详情/备注/质保详情是否含"拆机"判断（不确定默认全新），
     * 组装为"料号 报价(带USD) 数量(带pcs) 交期 DC 货况"制表符分隔文本，首行为英文表头：Part No.\tPrice\tQty\tDelivery\tDC\tCondition
     */
    @Override
    public String buildAiQueryCopyText(List<String> partNumbers, int days) {
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
        List<CrmOffer> list = offerMapper.selectRecentOffersByPartNumbers(new ArrayList<>(pns), days);
        if (list.isEmpty()) {
            throw new ServiceException("最近" + days + "天内这些物料没有Offer记录");
        }
        // 报价升序 → 创建时间倒序（同价取最新），再按料号去重取第一条，即每个料号报价最低的最新Offer
        list.sort(Comparator.comparing(CrmOffer::getPriceOffer, Comparator.nullsLast(Double::compareTo))
                .thenComparing(CrmOffer::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        Map<String, CrmOffer> dedup = new LinkedHashMap<>();
        for (CrmOffer o : list) {
            dedup.putIfAbsent(str(o.getProductCode()).toUpperCase(), o);
        }
        // 首行英文表头，其后每行一条记录
        StringBuilder sb = new StringBuilder("Part No.\tPrice\tQty\tDelivery\tDC\tCondition");
        for (CrmOffer o : dedup.values()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(nvl(o.getProductCode())).append('\t')
                    .append(o.getPriceOffer() == null ? "" : o.getPriceOffer() + "USD").append('\t')
                    .append(o.getQuantity() == null ? "" : o.getQuantity() + "pcs").append('\t')
                    .append(nvl(o.getDeliveryTime())).append('\t')
                    .append(nvl(o.getDc())).append('\t')
                    .append(huokuang(o));
        }
        return sb.toString();
    }

    /** 货况判断：详情/备注/质保详情任一含"拆机"视为拆机，否则默认全新 */
    private String huokuang(CrmOffer o) {
        String text = nvl(o.getProductDetail()) + " " + nvl(o.getRemark()) + " " + nvl(o.getWarrantyDetail());
        return text.contains("拆机") ? "拆机" : "全新";
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
