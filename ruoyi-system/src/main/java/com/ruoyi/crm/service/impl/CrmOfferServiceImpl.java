package com.ruoyi.crm.service.impl;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @Override
    public List<CrmOffer> selectOfferList(CrmOffer offer) {
        return offerMapper.selectOfferList(offer);
    }
    @Override
    public int insertOffer(CrmOffer offer) {
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
                    price = round2(cost * 1.02d);
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
                    return Double.valueOf(s.trim());
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
                    return Integer.valueOf(s.trim());
                }
                case NUMERIC: return (int) Math.round(cell.getNumericCellValue());
                default: return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Double round2(double v) {
        return Math.round(v * 100.0d) / 100.0d;
    }
}
