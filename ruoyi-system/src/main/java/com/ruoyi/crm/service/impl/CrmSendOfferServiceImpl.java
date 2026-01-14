package com.ruoyi.crm.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.crm.domain.CrmOffer;
import com.ruoyi.crm.domain.dto.SendEmailReq;
import com.ruoyi.crm.mapper.CrmOfferMapper;
import com.ruoyi.crm.service.ICrmOfferService;
import com.ruoyi.crm.service.ICrmSendOfferService;
import com.ruoyi.system.service.ISysDictDataService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;


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
        List<CrmOffer> offers = req.getOffers();
        List<String> emailGroups = req.getEmailGroups();
        if (offers == null) offers = java.util.Collections.emptyList();
        if (emailGroups == null) emailGroups = java.util.Collections.emptyList();
        String excelAddr = dictDataService.selectDictLabel("crm_email_template_dict", "excel_addr");
        String emailSign = dictDataService.selectDictLabel("crm_email_template_dict", "email_sign");
        String emailTitle = dictDataService.selectDictLabel("crm_email_template_dict", "email_title");
        String emailBody = dictDataService.selectDictLabel("crm_email_template_dict", "email_body");
        String emailAccount = dictDataService.selectDictLabel("crm_email_template_dict", "email_account");
        String emailPassword = dictDataService.selectDictLabel("crm_email_template_dict", "email_password");
        String emailSmtp = dictDataService.selectDictLabel("crm_email_template_dict", "email_smtp");
        String emailSmtpPort = dictDataService.selectDictLabel("crm_email_template_dict", "email_smtp_port");
        String dest = com.ruoyi.common.config.RuoYiConfig.getDownloadPath() + "offer_" + System.currentTimeMillis() + ".xlsx";
        try {
            File df = new File(dest);
            if (df.getParentFile() != null && !df.getParentFile().exists()) df.getParentFile().mkdirs();
            Files.copy(Paths.get(excelAddr), Paths.get(dest), StandardCopyOption.REPLACE_EXISTING);
            WriteCellStyle headStyle = new WriteCellStyle();
            WriteCellStyle contentStyle = new WriteCellStyle();
            HorizontalCellStyleStrategy styleStrategy = new HorizontalCellStyleStrategy(headStyle, contentStyle);
            com.alibaba.excel.ExcelWriter writer = EasyExcel.write(dest, CrmOffer.class).registerWriteHandler(styleStrategy).build();
            WriteSheet writeSheet = EasyExcel.writerSheet(0).relativeHeadRowIndex(1).build();
            writer.write(offers, writeSheet);
            writer.finish();
        } catch (Exception e) {
            return AjaxResult.error("生成附件失败: " + e.getMessage());
        }
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
                    sendEmail(emailSmtp, emailSmtpPort, emailAccount, emailPassword, addrs, emailTitle, combineHtml(emailBody, emailSign, tableHtml), dest);
                    ok = true;
                } catch (Exception ex) {
                    err = ex.getMessage();
                }
            }
            item.put("success", ok);
            item.put("attempts", attempts);
            item.put("error", err);
            if (ok) success++; else fail++;
            details.add(item);
        }
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("successCount", success);
        result.put("failCount", fail);
        result.put("details", details);
        result.put("attachmentPath", dest);
        return AjaxResult.success(result);
    }

    private String combineHtml(String body, String sign, String tableHtml) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div>");
        if (StringUtils.isNotBlank(sign)) sb.append(sign);
        if (StringUtils.isNotBlank(body)) sb.append(body);
        if (StringUtils.isNotBlank(tableHtml)) sb.append(tableHtml);
        sb.append("</div>");
        return sb.toString();
    }

    private String buildHtmlTable(List<CrmOffer> offers) {
        java.util.List<java.lang.reflect.Field> fields = new java.util.ArrayList<>();
        for (java.lang.reflect.Field f : CrmOffer.class.getDeclaredFields()) { fields.add(f); }
        java.util.List<String> headers = new java.util.ArrayList<>();
        java.util.List<java.lang.reflect.Field> exportFields = new java.util.ArrayList<>();
        for (java.lang.reflect.Field f : fields) {
            com.ruoyi.common.annotation.Excel ex = f.getAnnotation(com.ruoyi.common.annotation.Excel.class);
            if (ex != null) { headers.add(ex.name()); exportFields.add(f); }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\" style=\"border-collapse:collapse;font-size:12px;\">");
        sb.append("<thead><tr>");
        for (String h : headers) { sb.append("<th style=\"background:#f5f7fa;\">").append(escapeHtml(h)).append("</th>"); }
        sb.append("</tr></thead><tbody>");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (CrmOffer o : offers) {
            sb.append("<tr>");
            for (java.lang.reflect.Field f : exportFields) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    String s;
                    if (v == null) s = ""; else if (v instanceof java.util.Date) s = sdf.format((java.util.Date) v); else s = String.valueOf(v);
                    sb.append("<td>").append(escapeHtml(s)).append("</td>");
                } catch (Exception ignored) { sb.append("<td></td>"); }
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

    private void sendEmail(String smtp, String port, String account, String password, List<String> to, String subject, String html, String attachPath) throws Exception {
        java.util.Properties props = new java.util.Properties();
        props.put("mail.smtp.host", smtp);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() { return new PasswordAuthentication(account, password); }
        });
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(account));
        java.util.List<InternetAddress> addrs = new java.util.ArrayList<>();
        for (String s : to) { if (StringUtils.isNotBlank(s)) addrs.add(new InternetAddress(s.trim())); }
        message.setRecipients(Message.RecipientType.TO, addrs.toArray(new InternetAddress[0]));
        message.setSubject(subject, "UTF-8");
        MimeBodyPart contentPart = new MimeBodyPart();
        contentPart.setContent(html, "text/html; charset=UTF-8");
        Multipart mp = new MimeMultipart();
        mp.addBodyPart(contentPart);
        if (attachPath != null && !attachPath.isEmpty()) {
            MimeBodyPart attach = new MimeBodyPart();
            attach.attachFile(new File(attachPath));
            mp.addBodyPart(attach);
        }
        message.setContent(mp);
        Transport.send(message);
    }


}
