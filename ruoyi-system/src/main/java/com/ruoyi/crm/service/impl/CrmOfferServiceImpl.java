package com.ruoyi.crm.service.impl;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.ruoyi.crm.mapper.CrmOfferMapper;
import com.ruoyi.crm.service.ICrmOfferService;
import com.ruoyi.common.utils.SecurityUtils;

@Service
public class CrmOfferServiceImpl implements ICrmOfferService {

    @Autowired
    private CrmOfferMapper offerMapper;

    @Autowired
    private DeepSeekAiService deepSeekAiService;

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
        double ratio = profitRatio == null ? 2d : profitRatio.doubleValue();
        // 调用AI把无规则文本整理为结构化物料列表（品牌/料号/型号/规格型号/数量/报价）
        List<Map<String, Object>> items = deepSeekAiService.parseMaterialContent(content);

        String userName = SecurityUtils.getUsername();
        Date now = new Date();
        // 来源表名：AI+时间+供应商名+操作人，便于追溯录入批次
        String sheetName = "AI_" + new SimpleDateFormat("yyyyMMddHHmm").format(now) + "_" + supplierName + "_" + userName;

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

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
