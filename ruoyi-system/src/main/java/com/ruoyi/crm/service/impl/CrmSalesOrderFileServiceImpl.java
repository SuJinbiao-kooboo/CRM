package com.ruoyi.crm.service.impl;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.crm.domain.CrmSalesOrderFile;
import com.ruoyi.crm.mapper.CrmSalesOrderFileMapper;
import com.ruoyi.crm.mapper.CrmSalesOrderMapper;
import com.ruoyi.crm.service.ICrmSalesOrderFileService;

/**
 * 销售订单附件 Service 实现：文件内容直接入库，上传成功即持久化。
 */
@Service
public class CrmSalesOrderFileServiceImpl implements ICrmSalesOrderFileService {

    /** 单个附件上限 50MB（需与 application.yml multipart 限制保持一致） */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 订单上的 12 个附件栏位 */
    private static final Set<String> FIELD_KEYS = new HashSet<>(Arrays.asList(
        "signReceiptFile", "photoFile", "poCustomerFile", "piOurFile", "poOurFile", "piSupplierFile",
        "plSupplierFile", "plOurFile", "ciSupplierFile", "ciOurFile", "entrustFile", "snFile"
    ));

    private static final int PREVIEW_MAX_ROWS = 200;
    private static final int PREVIEW_MAX_COLS = 20;

    @Autowired
    private CrmSalesOrderFileMapper fileMapper;

    @Autowired
    private CrmSalesOrderMapper salesOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmSalesOrderFile upload(MultipartFile file, Long orderId, String fieldKey) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传附件不能为空");
        }
        if (orderId == null) {
            throw new ServiceException("订单ID不能为空");
        }
        if (StringUtils.isEmpty(fieldKey) || !FIELD_KEYS.contains(fieldKey)) {
            throw new ServiceException("附件栏位不正确");
        }
        if (salesOrderMapper.selectSalesOrderById(orderId) == null) {
            throw new ServiceException("订单不存在或已删除");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException("单个附件不能超过 50MB");
        }
        try {
            String username = SecurityUtils.getUsername();
            CrmSalesOrderFile attachment = new CrmSalesOrderFile();
            attachment.setOrderId(orderId);
            attachment.setFieldKey(fieldKey);
            attachment.setFileName(StringUtils.isEmpty(file.getOriginalFilename()) ? "unnamed" : file.getOriginalFilename());
            attachment.setContentType(StringUtils.isEmpty(file.getContentType())
                ? "application/octet-stream" : file.getContentType());
            attachment.setFileSize(file.getSize());
            attachment.setFileContent(file.getBytes());
            attachment.setCreateTime(new Date());
            attachment.setCreateBy(username);
            attachment.setUpdateBy(username);
            fileMapper.insert(attachment);
            return attachment;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("附件上传失败：" + e.getMessage());
        }
    }

    @Override
    public List<CrmSalesOrderFile> selectListByOrderId(Long orderId) {
        return fileMapper.selectListByOrderId(orderId);
    }

    @Override
    public CrmSalesOrderFile selectById(Long id) {
        return fileMapper.selectById(id);
    }

    @Override
    public String previewHtml(Long id) {
        CrmSalesOrderFile attachment = fileMapper.selectById(id);
        if (attachment == null || attachment.getFileContent() == null) {
            throw new ServiceException("附件不存在或已删除");
        }
        String fileName = attachment.getFileName() == null ? "" : attachment.getFileName().toLowerCase();
        try {
            if (fileName.endsWith(".xls")) {
                return excelToHtml(attachment.getFileContent(), true);
            } else if (fileName.endsWith(".xlsx")) {
                return excelToHtml(attachment.getFileContent(), false);
            } else if (fileName.endsWith(".csv")) {
                return csvToHtml(attachment.getFileContent());
            } else if (fileName.endsWith(".docx")) {
                return docxToHtml(attachment.getFileContent());
            }
            throw new ServiceException("该格式暂不支持在线预览，请下载查看");
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("附件预览失败：" + e.getMessage());
        }
    }

    private String excelToHtml(byte[] content, boolean xls) throws Exception {
        Workbook workbook = xls ? new HSSFWorkbook(new ByteArrayInputStream(content))
                                : new XSSFWorkbook(new ByteArrayInputStream(content));
        try {
            StringBuilder html = new StringBuilder();
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                html.append("<h4>").append(escapeHtml(sheet.getSheetName())).append("</h4>");
                html.append("<table border='1' cellspacing='0' cellpadding='2'>");
                int rowIdx = 0;
                for (Row row : sheet) {
                    if (rowIdx++ >= PREVIEW_MAX_ROWS) {
                        html.append("<tr><td>...（仅预览前 ").append(PREVIEW_MAX_ROWS).append(" 行，完整内容请下载查看）</td></tr>");
                        break;
                    }
                    html.append("<tr>");
                    int colIdx = 0;
                    for (Cell cell : row) {
                        if (colIdx++ >= PREVIEW_MAX_COLS) {
                            break;
                        }
                        html.append("<td>").append(escapeHtml(formatter.formatCellValue(cell))).append("</td>");
                    }
                    html.append("</tr>");
                }
                html.append("</table>");
            }
            return html.toString();
        } finally {
            workbook.close();
        }
    }

    private String csvToHtml(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0) {
            text = new String(content, Charset.forName("GBK"));
        }
        String[] lines = text.split("\r\n|\r|\n");
        StringBuilder html = new StringBuilder("<table border='1' cellspacing='0' cellpadding='2'>");
        int max = Math.min(lines.length, PREVIEW_MAX_ROWS);
        for (int i = 0; i < max; i++) {
            if (lines[i].trim().isEmpty()) {
                continue;
            }
            html.append("<tr>");
            for (String cell : lines[i].split(",", -1)) {
                html.append("<td>").append(escapeHtml(cell)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</table>");
        return html.toString();
    }

    private String docxToHtml(byte[] content) throws Exception {
        XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
        try {
            StringBuilder html = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    html.append("<p>").append(escapeHtml(text)).append("</p>");
                }
            }
            for (XWPFTable table : document.getTables()) {
                html.append("<table border='1' cellspacing='0' cellpadding='2'>");
                for (XWPFTableRow row : table.getRows()) {
                    html.append("<tr>");
                    for (XWPFTableCell cell : row.getTableCells()) {
                        html.append("<td>").append(escapeHtml(cell.getText())).append("</td>");
                    }
                    html.append("</tr>");
                }
                html.append("</table>");
            }
            return html.toString();
        } finally {
            document.close();
        }
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Override
    public int softDelete(Long id) {
        return fileMapper.softDelete(id, SecurityUtils.getUsername());
    }
}
