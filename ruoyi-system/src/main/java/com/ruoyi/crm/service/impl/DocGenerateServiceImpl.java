package com.ruoyi.crm.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.crm.domain.CrmOrderItem;
import com.ruoyi.crm.domain.dto.DocGenerateReq;
import com.ruoyi.crm.domain.dto.DocTypeDef;
import com.ruoyi.crm.service.IDocGenerateService;
import com.ruoyi.crm.service.util.DocItemParser;
import com.ruoyi.crm.service.util.DocTypeRegistry;
import com.ruoyi.crm.service.util.EntrustFiller;
import com.ruoyi.crm.service.util.OrderDocumentFiller;
import com.ruoyi.crm.service.util.PackingListFiller;
import com.ruoyi.crm.service.util.SnListFiller;

@Service
public class DocGenerateServiceImpl implements IDocGenerateService {

    private static final String TPL_SN = "order-template/SN List.xlsx";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    @Override
    public List<DocTypeDef> listTypes() {
        return DocTypeRegistry.list();
    }

    @Override
    public List<Map<String, Object>> parseItems(DocGenerateReq req, Map<String, MultipartFile> files) throws IOException {
        MultipartFile poFile = file(files, "poFile");
        MultipartFile plFile = file(files, "plFile");
        if (poFile == null && plFile == null) {
            throw new ServiceException("请至少上传 PO 文件或 PL 文件");
        }
        try (InputStream po = poFile == null ? null : poFile.getInputStream();
             InputStream pl = plFile == null ? null : plFile.getInputStream()) {
            return DocItemParser.parse(po, pl);
        }
    }

    @Override
    public byte[] generateExcel(DocGenerateReq req, Map<String, MultipartFile> files) throws IOException {
        String type = req == null ? null : req.getDocType();
        if (StringUtils.isEmpty(type)) {
            throw new ServiceException("请选择单据类型");
        }
        switch (type.toUpperCase()) {
            case DocTypeRegistry.PO:
                return fillInvoiceTpl(DocTypeRegistry.TPL_PO, req, null, req.getSellerText(), null);
            case DocTypeRegistry.PI:
                return fillInvoiceTpl(DocTypeRegistry.TPL_PI, req, req.getBuyerText(), null, req.getShipTo());
            case DocTypeRegistry.CI:
                return fillInvoiceTpl(DocTypeRegistry.TPL_CI, req, req.getBuyerText(), null, req.getShipTo());
            case DocTypeRegistry.SN:
                return fillSn(req);
            case DocTypeRegistry.PL:
                return fillPl(req, file(files, "file"));
            case DocTypeRegistry.ENTRUST:
                return fillEntrust(req);
            default:
                throw new ServiceException("暂不支持的单据类型：" + type);
        }
    }

    @Override
    public String fileName(DocGenerateReq req, String ext) {
        String suffix = "." + ext;
        String type = req == null || req.getDocType() == null ? "" : req.getDocType().toUpperCase();
        switch (type) {
            case DocTypeRegistry.PL:
                return "PL-Packing List " + nvl(req.getCustomerPoNo()) + suffix;
            case DocTypeRegistry.SN:
                return "SN List" + suffix;
            case DocTypeRegistry.ENTRUST:
                return "Copy of QR-KF-005-A2香港仓库交货委托单" + nvl(extra(req, "warehouseNo")) + suffix;
            default:
                return nvl(req == null ? null : req.getOrderNo()) + suffix;
        }
    }

    private byte[] fillInvoiceTpl(String tpl, DocGenerateReq req, String buyerText, String sellerText, String shipTo) throws IOException {
        try (InputStream in = new ClassPathResource(tpl).getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            OrderDocumentFiller.fillInvoice(in, out, req.getOrderNo(), parseDate(req.getDateText()),
                    buyerText, sellerText, shipTo, toItems(req.getItems()));
            return out.toByteArray();
        }
    }

    private byte[] fillSn(DocGenerateReq req) throws IOException {
        try (InputStream in = new ClassPathResource(TPL_SN).getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SnListFiller.fill(in, out, req.getSnText());
            return out.toByteArray();
        }
    }

    private byte[] fillPl(DocGenerateReq req, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请上传供应商提供的 PL 文件");
        }
        try (InputStream in = new ClassPathResource(DocTypeRegistry.TPL_PL).getInputStream();
             InputStream supplier = file.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PackingListFiller.fill(in, out, req.getDateText(), req.getShipTo(), supplier);
            return out.toByteArray();
        }
    }

    private byte[] fillEntrust(DocGenerateReq req) throws IOException {
        Map<String, String> extras = req.getExtras() == null ? new HashMap<>() : req.getExtras();
        try (InputStream in = new ClassPathResource(DocTypeRegistry.TPL_ENTRUST).getInputStream()) {
            return EntrustFiller.fill(in, req.getDateText(), extras.get("receiverInfo"),
                    req.getItems() == null ? new ArrayList<>() : req.getItems(), extras);
        }
    }

    private MultipartFile file(Map<String, MultipartFile> files, String key) {
        if (files == null) {
            return null;
        }
        MultipartFile f = files.get(key);
        return (f == null || f.isEmpty()) ? null : f;
    }

    private List<CrmOrderItem> toItems(List<Map<String, Object>> rows) {
        List<CrmOrderItem> items = new ArrayList<>();
        if (rows == null) {
            return items;
        }
        for (Map<String, Object> row : rows) {
            CrmOrderItem item = new CrmOrderItem();
            item.setBrand(str(row.get("brand")));
            item.setItemName(str(row.get("itemName")));
            item.setSpec(str(row.get("spec")));
            item.setQty(decimal(row.get("qty")));
            item.setWarranty(str(row.get("warranty")));
            item.setUnitPrice(decimal(row.get("unitPrice")));
            item.setLeadTime(str(row.get("leadTime")));
            items.add(item);
        }
        return items;
    }

    private Date parseDate(String text) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        try {
            return new SimpleDateFormat(DATE_PATTERN).parse(text);
        } catch (ParseException e) {
            return null;
        }
    }

    private BigDecimal decimal(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String extra(DocGenerateReq req, String key) {
        if (req == null || req.getExtras() == null) {
            return null;
        }
        return req.getExtras().get(key);
    }
}
