package com.ruoyi.crm.service.impl;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.crm.domain.CrmSupplierAttachment;
import com.ruoyi.crm.mapper.CrmSupplierAttachmentMapper;
import com.ruoyi.crm.service.ICrmSupplierAttachmentService;

/**
 * 供应商附件 Service 实现（附件内容入库，支持软删除）
 *
 * @author ruoyi
 */
@Service
public class CrmSupplierAttachmentServiceImpl implements ICrmSupplierAttachmentService
{
    @Autowired
    private CrmSupplierAttachmentMapper attachmentMapper;

    /**
     * 上传附件：读取文件内容写入附件表。
     * 新增供应商场景下 supplierId 为空，待供应商保存成功后由供应商服务回填。
     */
    @Override
    @Transactional
    public CrmSupplierAttachment upload(MultipartFile file, Long supplierId)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("上传附件不能为空");
        }
        try
        {
            CrmSupplierAttachment attachment = new CrmSupplierAttachment();
            attachment.setSupplierId(supplierId);
            // 附件名称取原始文件名，为空时兜底
            attachment.setFileName(StringUtils.isEmpty(file.getOriginalFilename()) ? "unnamed" : file.getOriginalFilename());
            // 附件内容直接以二进制形式入库
            attachment.setFileContent(file.getBytes());
            attachmentMapper.insert(attachment);
            return attachment;
        }
        catch (Exception e)
        {
            throw new ServiceException("附件上传失败：" + e.getMessage());
        }
    }

    @Override
    public List<CrmSupplierAttachment> selectListBySupplierId(Long supplierId)
    {
        return attachmentMapper.selectListBySupplierId(supplierId);
    }

    @Override
    public CrmSupplierAttachment selectById(Long id)
    {
        return attachmentMapper.selectById(id);
    }

    /** 预览时每 Sheet 最多渲染的行数，防止超大表格拖慢页面 */
    private static final int PREVIEW_MAX_ROWS = 200;

    /** 预览时每行最多渲染的列数 */
    private static final int PREVIEW_MAX_COLS = 20;

    /**
     * 预览附件：将 Excel（xls/xlsx/csv）和 Word（docx）解析为 HTML 片段返回，
     * 供前端弹窗展示；PDF/图片等由前端浏览器原生预览，不经过此方法。
     */
    @Override
    public String previewHtml(Long id)
    {
        CrmSupplierAttachment attachment = attachmentMapper.selectById(id);
        if (attachment == null || attachment.getFileContent() == null)
        {
            throw new ServiceException("附件不存在或已删除");
        }
        String fileName = attachment.getFileName() == null ? "" : attachment.getFileName().toLowerCase();
        try
        {
            if (fileName.endsWith(".xls"))
            {
                return excelToHtml(attachment.getFileContent(), true);
            }
            else if (fileName.endsWith(".xlsx"))
            {
                return excelToHtml(attachment.getFileContent(), false);
            }
            else if (fileName.endsWith(".csv"))
            {
                return csvToHtml(attachment.getFileContent());
            }
            else if (fileName.endsWith(".docx"))
            {
                return docxToHtml(attachment.getFileContent());
            }
            throw new ServiceException("该格式暂不支持在线预览，请下载查看");
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("附件预览失败：" + e.getMessage());
        }
    }

    /** Excel（xls/xlsx）解析为 HTML 表格，限制行/列数防止页面过大 */
    private String excelToHtml(byte[] content, boolean xls) throws Exception
    {
        Workbook workbook = xls ? new HSSFWorkbook(new ByteArrayInputStream(content))
                                : new XSSFWorkbook(new ByteArrayInputStream(content));
        try
        {
            StringBuilder html = new StringBuilder();
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook)
            {
                html.append("<h4>").append(escapeHtml(sheet.getSheetName())).append("</h4>");
                html.append("<table border='1' cellspacing='0' cellpadding='2'>");
                int rowIdx = 0;
                for (Row row : sheet)
                {
                    if (rowIdx++ >= PREVIEW_MAX_ROWS)
                    {
                        html.append("<tr><td>...（仅预览前 " + PREVIEW_MAX_ROWS + " 行，完整内容请下载查看）</td></tr>");
                        break;
                    }
                    html.append("<tr>");
                    int colIdx = 0;
                    for (Cell cell : row)
                    {
                        if (colIdx++ >= PREVIEW_MAX_COLS)
                        {
                            break;
                        }
                        html.append("<td>").append(escapeHtml(formatter.formatCellValue(cell))).append("</td>");
                    }
                    html.append("</tr>");
                }
                html.append("</table>");
            }
            return html.toString();
        }
        finally
        {
            workbook.close();
        }
    }

    /** CSV 解析为 HTML 表格（优先 UTF-8 解码，出现乱码字符时按 GBK 重解） */
    private String csvToHtml(byte[] content)
    {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0)
        {
            text = new String(content, Charset.forName("GBK"));
        }
        String[] lines = text.split("\r\n|\r|\n");
        StringBuilder html = new StringBuilder("<table border='1' cellspacing='0' cellpadding='2'>");
        int max = Math.min(lines.length, PREVIEW_MAX_ROWS);
        for (int i = 0; i < max; i++)
        {
            if (lines[i].trim().isEmpty())
            {
                continue;
            }
            html.append("<tr>");
            for (String cell : lines[i].split(",", -1))
            {
                html.append("<td>").append(escapeHtml(cell)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</table>");
        return html.toString();
    }

    /** Word（docx）解析为 HTML：按段落与表格输出（文本级预览） */
    private String docxToHtml(byte[] content) throws Exception
    {
        XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
        try
        {
            StringBuilder html = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs())
            {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty())
                {
                    html.append("<p>").append(escapeHtml(text)).append("</p>");
                }
            }
            for (XWPFTable table : document.getTables())
            {
                html.append("<table border='1' cellspacing='0' cellpadding='2'>");
                for (XWPFTableRow row : table.getRows())
                {
                    html.append("<tr>");
                    for (XWPFTableCell cell : row.getTableCells())
                    {
                        html.append("<td>").append(escapeHtml(cell.getText())).append("</td>");
                    }
                    html.append("</tr>");
                }
                html.append("</table>");
            }
            return html.toString();
        }
        finally
        {
            document.close();
        }
    }

    /** HTML 特殊字符转义，防止附件内容破坏页面结构 */
    private String escapeHtml(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * 软删除附件：仅将删除标记置为 1，不物理删除数据库记录。
     */
    @Override
    public int softDelete(Long id)
    {
        return attachmentMapper.softDelete(id);
    }
}
