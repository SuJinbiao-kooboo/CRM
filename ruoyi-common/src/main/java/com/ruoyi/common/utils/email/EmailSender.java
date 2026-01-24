package com.ruoyi.common.utils.email;

import javax.annotation.PreDestroy;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.validation.constraints.Size;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件发送工具类（支持连接复用）
 */

public class EmailSender {

    public static void main(String[] args) {
        // 邮件配置
        String smtp = "smtpv.global-mail.cn";
        String port = "465";
        String account = "eke@meelectronic.cn";
        String password = "emailME123";

        // 邮件内容
        String subject = "测试邮件";
        String html = "<h1>尊敬的客户</h1><p>这是您的月度报告</p>";
        File attachment = new File("D:/report.pdf");

        // 收件人列表
        List<String> recipients = Arrays.asList(
                "may@meelectronic.cn",
                "18959290646@163.com"
        );

        // 循环发送
        for (String recipient : recipients) {
            try {
                System.out.println("正在发送给: " + recipient);

                // 发送邮件（自动复用连接）
                EmailSender.sendEmail(smtp, port, account, password,
                        Collections.singletonList(recipient),
                        subject, html, attachment);

                System.out.println("发送成功: " + recipient);

                // 可以添加延迟，避免发送过快被限制
                Thread.sleep(1000);

            } catch (Exception e) {
                System.err.println("发送失败给 " + recipient + ": " + e.getMessage());
                // 可以记录失败，继续发送下一个
            }
        }

        // 发送完成后，清理过期连接（可选）
        EmailSender.cleanupExpiredConnections(30); // 清理30分钟未使用的连接
    }

    // 缓存Session和Transport（按邮箱账号缓存）
    private static final Map<String, EmailConnection> connectionCache = new ConcurrentHashMap<>();

    /**
     * 发送邮件（自动复用连接）
     */
    public static void sendEmail(String smtp, String port, String account, String password,
                                 List<String> to, String subject, String html, File attachPath)
            throws Exception {
        sendEmail(smtp, port, account, password, to, subject, html,
                attachPath != null ? Collections.singletonList(attachPath) : null);
    }

    /**
     * 发送邮件（支持多个附件）
     */
    public static void sendEmail(String smtp, String port, String account, String password,
                                 List<String> to, String subject, String html,
                                 List<File> attachPaths) throws Exception {

        EmailConnection connection = null;
        try {
            // 1. 获取或创建连接
            connection = createNewSSLConnection(smtp, port, account, password);

            // 2. 创建邮件
            MimeMessage message = createMessage(connection.getSession(), account,
                    to, subject, html, attachPaths);

            // 3. 使用缓存的Transport发送
            connection.getTransport().sendMessage(message, message.getAllRecipients());

        } catch (MessagingException e) {
            // 发送失败，关闭并移除无效连接
            System.out.println("发送邮件异常"+e.getMessage()+"\n");
            e.printStackTrace();
            if (connection != null) {
                closeConnection(connection);
                removeConnection(smtp, port, account);
            }
            throw e;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 获取邮件连接（缓存或新建）
     */
    private static synchronized EmailConnection getConnection(String smtp, String port,
                                                              String account, String password)
            throws MessagingException {
        String key = generateKey(smtp, port, account);
        EmailConnection connection = connectionCache.get(key);

        if (connection == null || !connection.isValid()) {
            // 创建新的连接
            if (connection != null) {
                closeConnection(connection);
            }
            connection = createNewConnection(smtp, port, account, password);
            connectionCache.put(key, connection);
        }

        return connection;
    }

    /**
     * 创建新的SSL连接
     */
    private static EmailConnection createNewSSLConnection(String smtp, String port,
                                                          String account, String password)
            throws MessagingException {
        System.out.println("创建SSL邮件连接: " + account + "@" + smtp + ":" + port);

        // SSL连接配置
        Properties props = new Properties();
        props.put("mail.smtp.host", smtp);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");

        // SSL配置（关键！）
        props.put("mail.smtp.socketFactory.port", port);
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.socketFactory.fallback", "false");

        // 超时设置
        props.put("mail.smtp.connectiontimeout", "30000");
        props.put("mail.smtp.timeout", "60000");
        props.put("mail.smtp.writetimeout", "30000");

        // 调试信息
        props.put("mail.debug", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account, password);
            }
        });

        // 连接
        Transport transport = session.getTransport("smtp");
        System.out.println("连接SSL邮件服务器...");
        transport.connect(smtp, Integer.parseInt(port), account, password);

        return new EmailConnection(session, transport, System.currentTimeMillis());
    }

    /**
     * 创建新的邮件连接
     */
    private static EmailConnection createNewConnection(String smtp, String port,
                                                       String account, String password)
            throws MessagingException {
        // 创建Session
        Session session = createSession(smtp, port, account, password);

        // 创建Transport并连接
        Transport transport = session.getTransport("smtp");
        transport.connect(smtp, Integer.parseInt(port), account, password);

        return new EmailConnection(session, transport, System.currentTimeMillis());
    }

    /**
     * 创建邮件消息
     */
    private static MimeMessage createMessage(Session session, String from,
                                             List<String> to, String subject,
                                             String html, List<File> attachPaths)
            throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));

        // 设置收件人
        List<InternetAddress> addrs = new ArrayList<>();
        for (String s : to) {
            if (s != null && !s.trim().isEmpty()) {
                addrs.add(new InternetAddress(s.trim()));
            }
        }

        if (addrs.isEmpty()) {
            throw new IllegalArgumentException("收件人列表不能为空");
        }

        message.setRecipients(Message.RecipientType.TO, addrs.toArray(new InternetAddress[0]));
        message.setSubject(subject, "UTF-8");

        // 创建邮件内容
        Multipart multipart = new MimeMultipart();

        // 文本内容
        MimeBodyPart contentPart = new MimeBodyPart();
        contentPart.setContent(html, "text/html; charset=UTF-8");
        multipart.addBodyPart(contentPart);

        // 添加附件
        if (attachPaths != null && !attachPaths.isEmpty()) {
            for (File attachFile : attachPaths) {
                if (attachFile != null && attachFile.exists() && attachFile.isFile()) {
                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    try {
                        attachmentPart.attachFile(attachFile);
                        attachmentPart.setFileName(
                                MimeUtility.encodeText(attachFile.getName(), "UTF-8", null));
                        multipart.addBodyPart(attachmentPart);
                    } catch (IOException e) {
                        System.err.println("附件处理失败: " + attachFile.getAbsolutePath());
                        e.printStackTrace();
                    }
                }
            }
        }

        message.setContent(multipart);
        message.setSentDate(new Date());

        return message;
    }

    /**
     * 创建Session
     */
    private static Session createSession(String smtp, String port,
                                         String account, String password) {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtp);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        // 超时设置
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        // 连接保活
        props.put("mail.smtp.keepalive", "true");

        return Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account, password);
            }
        });
    }

    /**
     * 关闭连接
     */
    private static void closeConnection(EmailConnection connection) {
        if (connection != null && connection.getTransport() != null) {
            try {
                if (connection.getTransport().isConnected()) {
                    connection.getTransport().close();
                }
            } catch (MessagingException e) {
                // 忽略关闭异常
            }
        }
    }

    /**
     * 从缓存中移除连接
     */
    private static void removeConnection(String smtp, String port, String account) {
        String key = generateKey(smtp, port, account);
        connectionCache.remove(key);
    }

    /**
     * 关闭所有连接（应用关闭时调用）
     */
    public static void shutdown() {
        for (EmailConnection connection : connectionCache.values()) {
            closeConnection(connection);
        }
        connectionCache.clear();
    }

    /**
     * 清理过期连接（长时间未使用）
     */
    public static void cleanupExpiredConnections(long timeoutMinutes) {
        long timeoutMillis = timeoutMinutes * 60 * 1000;
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, EmailConnection>> iterator = connectionCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, EmailConnection> entry = iterator.next();
            EmailConnection connection = entry.getValue();

            if (now - connection.getLastUsedTime() > timeoutMillis) {
                closeConnection(connection);
                iterator.remove();
            }
        }
    }

    /**
     * 生成缓存Key
     */
    private static String generateKey(String smtp, String port, String account) {
        return smtp + ":" + port + ":" + account;
    }

    /**
     * 邮件连接封装类
     */
    private static class EmailConnection {
        private Session session;
        private Transport transport;
        private long lastUsedTime;

        public EmailConnection(Session session, Transport transport, long lastUsedTime) {
            this.session = session;
            this.transport = transport;
            this.lastUsedTime = lastUsedTime;
        }

        public Session getSession() {
            this.lastUsedTime = System.currentTimeMillis();
            return session;
        }

        public Transport getTransport() {
            this.lastUsedTime = System.currentTimeMillis();
            return transport;
        }

        public long getLastUsedTime() {
            return lastUsedTime;
        }

        /**
         * 检查连接是否有效
         */
        public boolean isValid() {
            try {
                return transport != null && transport.isConnected();
            } catch (Exception e) {
                return false;
            }
        }
    }
}