package com.ruoyi.crm.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.email.EmailSender;
import com.ruoyi.crm.domain.CrmOffer;
import com.ruoyi.crm.domain.dto.CrmOfferImportDTO;
import com.ruoyi.crm.domain.dto.SendEmailReq;
import com.ruoyi.crm.mapper.CrmOfferMapper;
import com.ruoyi.crm.service.ICrmOfferService;
import com.ruoyi.crm.service.ICrmSendOfferService;
import com.ruoyi.system.service.ISysDictDataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CrmSendOfferServiceImpl implements ICrmSendOfferService {

    @Autowired
    private CrmOfferMapper offerMapper;


    @Autowired
    private ISysDictDataService dictDataService;


    @Autowired
    private ICrmOfferService offerService;


    @Override
    public AjaxResult sendExcelEmail(SendEmailReq req) {
        List<CrmOfferImportDTO> offers = BeanUtil.copyToList(req.getOffers(), CrmOfferImportDTO.class);
        List<String> emailGroups = req.getEmailGroups();
        if (offers == null) {
            return AjaxResult.error("需要发送的Offer为空");
        }
        if (emailGroups == null) {
            return AjaxResult.error("需要发送的emailGroup为空");
        }

        String emailSign = dictDataService.selectDictLabel("crm_email_template_dict", "email_sign");
        String emailTitle = dictDataService.selectDictLabel("crm_email_template_dict", "email_title");
        String emailBody = dictDataService.selectDictLabel("crm_email_template_dict", "email_body");
        String emailAccount = dictDataService.selectDictLabel("crm_email_template_dict", "email_account");
        String emailPassword = dictDataService.selectDictLabel("crm_email_template_dict", "email_password");
        String emailSmtp = dictDataService.selectDictLabel("crm_email_template_dict", "email_smtp");
        String emailSmtpPort = dictDataService.selectDictLabel("crm_email_template_dict", "email_smtp_port");

        File df = getAttachFile(offers);

        String tableHtml = buildHtmlTable(offers);
        List<Map<String, Object>> details = new java.util.ArrayList<>();
        int success = 0;
        int fail = 0;
        for (int i = 0; i < emailGroups.size(); i++) {
            String group = emailGroups.get(i) == null ? "" : emailGroups.get(i);
            String[] parts = group.split(",");
            java.util.List<String> addrs = new java.util.ArrayList<>();
            for (String p : parts) {
                String s = p == null ? "" : p.trim();
                if (!s.isEmpty()) addrs.add(s);
            }
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("batchIndex", i + 1);
            item.put("recipients", addrs);
            boolean ok = false;
            String err = "";
            int attempts = 0;
            for (int t = 0; t < 3 && !ok; t++) {
                attempts++;
                try {
                    EmailSender.sendEmail(emailSmtp, emailSmtpPort, emailAccount, emailPassword, addrs, emailTitle, combineHtml(emailBody, emailSign, tableHtml), df);
                    log.info("成功发送："+addrs);
                    ok = true;
                } catch (Exception ex) {
                    log.error("发送邮件错误， msg={}", ex.getMessage(), ex);
                    err = ex.getMessage();
                }
            }
            item.put("success", ok);
            item.put("attempts", attempts);
            item.put("error", err);
            if (ok) success++;
            else fail++;
            details.add(item);
        }
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("successCount", success);
        result.put("failCount", fail);
        result.put("details", details);
        result.put("attachmentPath", df.getPath());

        if(df.exists()){
            df.delete();
        }

        return AjaxResult.success(result);
    }

    private File getAttachFile(List<CrmOfferImportDTO> offers) {
        File df = null;
        ExcelWriter writer = null;
        try {
            String excelAddr = dictDataService.selectDictLabel("crm_email_template_dict", "excel_addr");

            df = downloadFile(excelAddr);
            log.info(df.getPath());
            HorizontalCellStyleStrategy styleStrategy = getHorizontalCellStyleStrategy();
            WriteHandler columnWidthHandler = getColumnWidthHandler();

            // 1. 核心修改：使用模板文件模式，而不是直接写入目标文件
            // 将下载的文件作为模板，写入时保留原有内容
            writer = EasyExcel.write(df, CrmOfferImportDTO.class)
                    .withTemplate(df) // 关键：设置模板文件
                    .registerWriteHandler(styleStrategy)
                    .registerWriteHandler(columnWidthHandler)
                    .build();

            WriteSheet writeSheet = EasyExcel.writerSheet(0)
                    .relativeHeadRowIndex(2) // 从模板的第3行开始写入（0-based索引）
                    .build();

            // 2. 写入数据：这会从模板的第三行开始追加，不会清除前两行
            writer.write(BeanUtil.copyToList(offers, CrmOfferImportDTO.class), writeSheet);
            writer.finish();

            log.info("Excel文件已成功更新，保留原前两行内容。");
        } catch (Exception e) {
            log.error("发送失败，msg={}", e.getMessage(), e);
            throw new RuntimeException("发送失败，附件生成失败,msg="+e.getMessage());
        } finally {
            // 确保资源被清理
            if (writer != null) {
                writer.finish();
            }
        }
        return df;
    }

    private static WriteHandler getColumnWidthHandler() {
        // 3. 创建自适应列宽策略（推荐）
        WriteHandler columnWidthHandler = new LongestMatchColumnWidthStyleStrategy() {
            @Override
            protected void setColumnWidth(WriteSheetHolder writeSheetHolder,
                                          List<WriteCellData<?>> cellDataList,
                                          Cell cell, Head head,
                                          Integer relativeRowIndex, Boolean isHead) {
                super.setColumnWidth(writeSheetHolder, cellDataList, cell, head, relativeRowIndex, isHead);

                // 设置最小和最大列宽限制
                int currentWidth = writeSheetHolder.getSheet().getColumnWidth(cell.getColumnIndex());
                int minWidth = 15 * 256;  // 最小15字符
                int maxWidth = 40 * 256;  // 最大40字符

                if (currentWidth < minWidth) {
                    writeSheetHolder.getSheet().setColumnWidth(cell.getColumnIndex(), minWidth);
                } else if (currentWidth > maxWidth) {
                    writeSheetHolder.getSheet().setColumnWidth(cell.getColumnIndex(), maxWidth);
                }
            }
        };
        return columnWidthHandler;
    }

    private static HorizontalCellStyleStrategy getHorizontalCellStyleStrategy() {
        // 1. 创建表头样式（淡蓝色背景，新罗马字体）
        WriteCellStyle headStyle = new WriteCellStyle();
// 设置淡蓝色填充
        headStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        headStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);

// 设置新罗马字体（加粗，白色）
        WriteFont headFont = new WriteFont();
        headFont.setBold(true);
        headFont.setColor(IndexedColors.WHITE.getIndex());
        headFont.setFontName("Times New Roman"); // 新罗马字体
        headFont.setFontHeightInPoints((short) 12); // 稍大字号
        headStyle.setWriteFont(headFont);

// 设置边框
        headStyle.setBorderBottom(BorderStyle.THIN);
        headStyle.setBorderLeft(BorderStyle.THIN);
        headStyle.setBorderRight(BorderStyle.THIN);
        headStyle.setBorderTop(BorderStyle.THIN);

// 居中显示
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);

// 2. 创建内容样式（浅灰色背景，新罗马字体）
        WriteCellStyle contentStyle = new WriteCellStyle();
// 设置浅灰色填充
        contentStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        contentStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);

// 设置新罗马字体
        WriteFont contentFont = new WriteFont();
        contentFont.setColor(IndexedColors.BLACK.getIndex());
        contentFont.setFontName("Times New Roman"); // 新罗马字体
        contentFont.setFontHeightInPoints((short) 11); // 正文字号
        contentStyle.setWriteFont(contentFont);

// 设置边框
        contentStyle.setBorderBottom(BorderStyle.THIN);
        contentStyle.setBorderLeft(BorderStyle.THIN);
        contentStyle.setBorderRight(BorderStyle.THIN);
        contentStyle.setBorderTop(BorderStyle.THIN);

// 内容对齐方式
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        contentStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);
        // 4. 创建样式策略
        HorizontalCellStyleStrategy styleStrategy = new HorizontalCellStyleStrategy(headStyle, contentStyle);
        return styleStrategy;
    }

    private String combineHtml(String body, String sign, String tableHtml) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div>");
        if (StringUtils.isNotBlank(body)) sb.append(body);
        if (StringUtils.isNotBlank(tableHtml)) sb.append(tableHtml);
        if (StringUtils.isNotBlank(sign)) sb.append(sign);
        sb.append("</div>");
        return sb.toString();
    }

    private String buildHtmlTable(List<CrmOfferImportDTO> offers) {
        java.util.List<java.lang.reflect.Field> fields = new java.util.ArrayList<>();
        for (java.lang.reflect.Field f : offers.get(0).getClass().getDeclaredFields()) {
            fields.add(f);
        }
        java.util.List<String> headers = new java.util.ArrayList<>();
        java.util.List<java.lang.reflect.Field> exportFields = new java.util.ArrayList<>();
        for (java.lang.reflect.Field f : fields) {
            com.ruoyi.common.annotation.Excel ex = f.getAnnotation(com.ruoyi.common.annotation.Excel.class);
            if (ex != null) {
                headers.add(ex.name());
                exportFields.add(f);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\" style=\"border-collapse:collapse;font-size:12px;\">");
        sb.append("<thead><tr>");
        for (String h : headers) {
            sb.append("<th style=\"background:#f5f7fa;\">").append(escapeHtml(h)).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (CrmOfferImportDTO o : offers) {
            sb.append("<tr>");
            for (java.lang.reflect.Field f : exportFields) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    String s;
                    if (v == null) s = "";
                    else if (v instanceof java.util.Date) s = sdf.format((java.util.Date) v);
                    else s = String.valueOf(v);
                    sb.append("<td>").append(escapeHtml(s)).append("</td>");
                } catch (Exception ignored) {
                    sb.append("<td></td>");
                }
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * 下载远程文件到本地临时目录
     *
     * @param remoteUrl 远程文件URL
     * @return 下载到本地的File对象
     * @throws IOException 下载失败时抛出
     */
    public static File downloadFile(String remoteUrl) throws IOException {
        // 创建临时目录（如果不存在）
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "downloads");
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        // 从URL提取文件名
        String fileName = "M&E_Offer_"+ DateUtils.dateTime(new Date())+".xlsx";
        if (fileName == null || fileName.isEmpty()) {
            fileName = "downloaded_file_" + System.currentTimeMillis();
        }

        // 创建临时文件路径
        Path tempFilePath = tempDir.resolve(fileName);

        // 下载文件
        HttpURLConnection connection = null;
        InputStream inputStream = null;

        try {
            URL url = new URL(remoteUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // 30秒连接超时
            connection.setReadTimeout(30000);    // 30秒读取超时

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("下载失败，HTTP状态码: " + responseCode);
            }

            inputStream = connection.getInputStream();

            // 保存文件
            Files.copy(inputStream, tempFilePath, StandardCopyOption.REPLACE_EXISTING);

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // 忽略关闭异常
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }

        return tempFilePath.toFile();
    }

    /**
     * 从URL中提取文件名（带扩展名检测）
     */
    private static String getFileNameFromUrl(String url) {
        try {
            URL urlObj = new URL(url);
            String path = urlObj.getPath();

            if (path == null || path.isEmpty()) {
                return "download_" + System.currentTimeMillis();
            }

            // 获取文件名部分
            String fileName = path.substring(path.lastIndexOf("/") + 1);

            // 如果没有文件名或文件名不合法，生成一个
            if (fileName.isEmpty() || fileName.contains("?")) {
                // 尝试从Content-Disposition获取文件名
                return "download_" + System.currentTimeMillis() + getFileExtension(url);
            }

            return fileName;
        } catch (Exception e) {
            return "download_" + System.currentTimeMillis();
        }
    }

    /**
     * 从URL推断文件扩展名
     */
    private static String getFileExtension(String url) {
        if (url.contains(".")) {
            String extension = url.substring(url.lastIndexOf("."));
            if (extension.length() <= 5) { // 扩展名通常不长
                return extension.split("\\?")[0].split("#")[0]; // 移除查询参数和锚点
            }
        }
        return "";
    }


}
