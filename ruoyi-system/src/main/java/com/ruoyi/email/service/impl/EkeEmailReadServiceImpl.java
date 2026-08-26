package com.ruoyi.email.service.impl;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import javax.mail.search.AndTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.email.config.EkeMailReadProperties;
import com.ruoyi.email.domain.dto.EkeEmailAttachmentDTO;
import com.ruoyi.email.domain.dto.EkeEmailCategoryDTO;
import com.ruoyi.email.domain.dto.EkeEmailDTO;
import com.ruoyi.email.service.IEkeEmailReadService;
import com.ruoyi.email.service.util.EkeEmailTypeAnalyzer;
import com.ruoyi.email.service.util.EkeHtmlToTextUtil;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysDictDataService;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;

/**
 * 企业邮箱读取 服务层实现
 * <p>
 * 通过IMAP协议连接企业邮箱，读取最近一段时间（默认以参数配置 eke_email_last_read_time 为起点）的邮件，
 * 提取邮件正文、发件人、附件并分析邮件类型；读取成功后把 eke_email_last_read_time 更新为本次接口调用时间。
 *
 * @author ruoyi
 */
@Service
public class EkeEmailReadServiceImpl implements IEkeEmailReadService
{
    private static final Logger log = LoggerFactory.getLogger(EkeEmailReadServiceImpl.class);

    /** 参数配置键名：上次成功读取邮件的时间 */
    public static final String CONFIG_KEY_LAST_READ_TIME = "eke_email_last_read_time";

    /** 时间格式化样式 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** IMAP Session缓存：Session只是连接参数容器、无连接状态且线程安全，按“主机:端口”复用，避免每次调用重复创建 */
    private static final ConcurrentMap<String, Session> SESSION_CACHE = new ConcurrentHashMap<>();

    /** 读取互斥锁：同一时间只允许一个读取任务，防止并发重复建立IMAP连接与参数更新竞态 */
    private final Lock readLock = new ReentrantLock();

    @Autowired
    private EkeMailReadProperties properties;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private ISysDictDataService dictDataService;

    @Override
    public Map<String, Object> readEmails(String startTimeStr)
    {
        // 并发防护：同一时间只允许一个读取任务。
        // 若定时任务与手动调用并发，会同时建立多条IMAP连接（可能触发服务器连接数限制）并产生参数更新竞态
        if (!readLock.tryLock())
        {
            throw new ServiceException("邮件读取任务正在进行中，请稍后再试");
        }
        try
        {
            return doReadEmails(startTimeStr);
        }
        finally
        {
            readLock.unlock();
        }
    }

    /**
     * 邮件读取主流程（已在 readEmails 中加锁，本方法不涉及并发问题）
     */
    private Map<String, Object> doReadEmails(String startTimeStr)
    {
        // 1. 记录接口开始被调用的时间（作为更新 eke_email_last_read_time 的值）
        Date interfaceStartTime = new Date();

        // 2. 确定读取的起始时间：传入入参时以入参为准；否则读取参数配置 eke_email_last_read_time
        boolean autoTime = StringUtils.isEmpty(startTimeStr);
        Date startTime = resolveStartTime(startTimeStr, autoTime);

        // 3. 从字典读取邮件类型匹配规则（主题关键词：value=类型编码、label=类型中文名、remark=逗号分隔的关键词）
        List<EkeEmailTypeAnalyzer.TypeRule> typeRules = buildTypeRules();
        // 发件人邮箱过滤规则（第一优先级：value=类型编码与主题字典一致、remark=逗号拼接的邮箱地址）
        List<EkeEmailTypeAnalyzer.EmailRule> emailRules = buildEmailRules();

        // 4. 连接IMAP读取邮件并解析（正文/发件人/附件/类型）
        FetchResult fetchResult = fetchEmailsByImap(startTime, typeRules, emailRules);
        List<EkeEmailDTO> emails = fetchResult.getEmails();

        // 5. 组装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("startTime", DateFormatUtils.format(startTime, DATE_TIME_PATTERN));
        result.put("interfaceStartTime", DateFormatUtils.format(interfaceStartTime, DATE_TIME_PATTERN));
        result.put("count", emails.size());
        result.put("filteredCount", fetchResult.getFilteredCount());
        result.put("emails", emails);
        // 分类汇总：每个字典类型一个分类（含发件人、主题摘要），未归属分类列在最后
        result.put("categories", buildCategorySummary(typeRules, emails));
        result.put("updatedConfig", false);
        if (fetchResult.isTruncated())
        {
            // 邮件数量达到单次上限：本次只读取了最新部分，参数不更新，剩余邮件下次调用继续读取（保证不丢邮件）
            result.put("truncated", true);
            result.put("truncatedMessage", "本次邮件数量达到单次读取上限（" + properties.getMaxEmailsPerRead()
                    + "封），仅返回最新部分；参数配置未更新，剩余邮件将在下次调用时继续读取");
        }
        else if (autoTime)
        {
            // 6. 自动模式下，读取成功后把参数更新为本次接口调用时间
            String newValue = DateFormatUtils.format(interfaceStartTime, DATE_TIME_PATTERN);
            configService.updateConfigByKey(CONFIG_KEY_LAST_READ_TIME, newValue);
            result.put("updatedConfig", true);
            result.put("configKey", CONFIG_KEY_LAST_READ_TIME);
            result.put("configValue", newValue);
        }
        return result;
    }

    /**
     * 确定读取邮件的起始时间
     *
     * @param startTimeStr 入参时间字符串（可为空）
     * @param autoTime 是否自动模式（未传入参）
     * @return 起始时间
     */
    private Date resolveStartTime(String startTimeStr, boolean autoTime)
    {
        if (autoTime)
        {
            // 优先使用参数配置中记录的上次读取时间
            String lastReadTime = configService.selectConfigByKey(CONFIG_KEY_LAST_READ_TIME);
            if (StringUtils.isNotEmpty(lastReadTime))
            {
                Date lastTime = DateUtils.parseDate(lastReadTime);
                if (lastTime != null)
                {
                    return lastTime;
                }
                log.warn("参数配置 {} 的值 [{}] 格式不合法，改用默认往前读取 {} 小时",
                        CONFIG_KEY_LAST_READ_TIME, lastReadTime, properties.getDefaultLookbackHours());
            }
            // 参数不存在或格式不合法时，默认读取最近 N 小时内的邮件
            return DateUtils.addHours(new Date(), -properties.getDefaultLookbackHours());
        }
        // 入参时间解析失败时直接报错，避免误读全量邮件
        Date startTime = DateUtils.parseDate(startTimeStr);
        if (startTime == null)
        {
            throw new ServiceException("入参时间格式不正确，请使用 yyyy-MM-dd HH:mm:ss 格式");
        }
        return startTime;
    }

    /**
     * 通过IMAP协议连接邮箱服务器，读取接收时间在起始时间与当前时间之间的邮件
     * <p>
     * IMAP连接可能因服务器空闲超时等原因被关闭（FolderClosedException），
     * 这里采用“预取消息头 + UID去重 + 自动重连重试”的方式保证读取完整不重复；
     * 同时限制单次读取数量上限，超限时只读取最新部分，剩余邮件下次调用继续读取。
     *
     * @param startTime 起始时间
     * @param typeRules 邮件类型匹配规则（从字典读取）
     * @param emailRules 发件人邮箱过滤规则（从字典读取，第一优先级）
     * @return 解析结果（邮件列表 + 是否被截断）
     */
    private FetchResult fetchEmailsByImap(Date startTime, List<EkeEmailTypeAnalyzer.TypeRule> typeRules,
            List<EkeEmailTypeAnalyzer.EmailRule> emailRules)
    {
        List<EkeEmailDTO> result = new ArrayList<>();
        // 已成功解析的邮件UID：连接中断重试时跳过，避免重复解析
        Set<Long> parsedUids = new HashSet<>();
        // 是否因单次读取上限被截断（true时调用方不更新参数，剩余邮件下次继续读取）
        boolean truncated = false;
        // 被发件人过滤规则排除的邮件数量（不读取、不返回）
        int filteredCount = 0;
        // 最大尝试次数：连接中断后自动重连继续解析剩余邮件
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++)
        {
            Store store = null;
            Folder folder = null;
            try
            {
                // 1. 连接邮箱服务器并打开收件箱（只读，不影响邮件已读状态）
                store = connectStore();
                folder = openFolder(store);

                // 2. 搜索接收时间在 [起始时间, 当前时间] 之间的邮件
                //    加上限时间可避免服务器时间异常时读到“未来”的邮件
                javax.mail.search.SearchTerm searchTerm = new AndTerm(
                        new ReceivedDateTerm(ComparisonTerm.GE, startTime),
                        new ReceivedDateTerm(ComparisonTerm.LE, new Date()));
                Message[] messages = folder.search(searchTerm);
                log.info("查询到 {} 封接收时间晚于 {} 的邮件",
                        messages.length, DateFormatUtils.format(startTime, DATE_TIME_PATTERN));

                // 3. 预取UID与邮件头信息，减少逐封解析时的网络往返，降低连接被关闭的概率
                FetchProfile fetchProfile = new FetchProfile();
                fetchProfile.add(UIDFolder.FetchProfileItem.UID);
                fetchProfile.add(FetchProfile.Item.ENVELOPE);
                folder.fetch(messages, fetchProfile);

                // 4. 按接收时间倒序排列（最新邮件在前），并跳过已解析成功的邮件
                List<Message> messageList = new ArrayList<>(Arrays.asList(messages));
                messageList.sort(this::compareByReceivedDateDesc);

                // 5. 单次读取数量上限：超限时只解析最新的N封，剩余邮件保留给下次调用（此时调用方不更新参数，保证不丢邮件）
                int maxPerRead = properties.getMaxEmailsPerRead();
                if (messageList.size() > maxPerRead)
                {
                    log.warn("邮件数量 {} 超过单次读取上限 {}，本次仅读取最新的 {} 封，剩余邮件下次继续读取",
                            messageList.size(), maxPerRead, maxPerRead);
                    messageList = new ArrayList<>(messageList.subList(0, maxPerRead));
                    truncated = true;
                }

                // 6. 逐封解析：正文、发件人、附件、类型；连接中断时重连后继续
                boolean interrupted = false;
                for (Message message : messageList)
                {
                    // 发件人过滤：发件人地址模糊匹配过滤规则（如内部域名）时跳过该邮件，不读取正文/附件、不返回
                    if (isSenderFiltered(message))
                    {
                        filteredCount++;
                        continue;
                    }
                    long uid = getUid(folder, message);
                    if (uid != -1 && parsedUids.contains(uid))
                    {
                        continue;
                    }
                    try
                    {
                        result.add(parseMessage(message, typeRules, emailRules));
                        if (uid != -1)
                        {
                            parsedUids.add(uid);
                        }
                    }
                    catch (FolderClosedException e)
                    {
                        // 服务器关闭了连接：记录现场，退出本轮循环，重连后从未解析的邮件继续
                        log.warn("第 {} 次尝试：IMAP连接被服务器关闭，重新连接后继续解析（subject={}）",
                                attempt, safeSubject(message));
                        interrupted = true;
                        break;
                    }
                    catch (Exception e)
                    {
                        // 单封邮件解析失败不影响整体读取
                        log.error("解析邮件失败，subject={}", safeSubject(message), e);
                    }
                }
                if (!interrupted)
                {
                    // 全部解析完成
                    if (filteredCount > 0)
                    {
                        log.info("发件人过滤规则 [{}]，共过滤 {} 封邮件", properties.getSenderBlackFilter(), filteredCount);
                    }
                    return new FetchResult(result, truncated, filteredCount);
                }
            }
            catch (MessagingException e)
            {
                log.error("连接IMAP服务器失败：{}:{}", properties.getImapHost(), properties.getImapPort(), e);
                if (attempt >= maxAttempts)
                {
                    throw new ServiceException("连接邮箱服务器失败：" + e.getMessage());
                }
            }
            finally
            {
                closeQuietly(folder, store);
            }
        }
        throw new ServiceException("读取邮件失败：IMAP连接多次中断，仍有邮件未完成解析");
    }

    /**
     * 创建IMAP连接（SSL），连接复用缓存的Session
     */
    private Store connectStore() throws MessagingException
    {
        Session session = getSession();
        Store store = session.getStore("imap");
        store.connect(properties.getImapHost(), properties.getImapPort(),
                properties.getUsername(), properties.getPassword());
        return store;
    }

    /**
     * 获取（或创建并缓存）IMAP Session
     * <p>
     * Session 只是连接参数的容器，本身无连接状态且线程安全；
     * 同一邮箱配置按“主机:端口”缓存复用，避免每次调用重复创建。
     */
    private Session getSession()
    {
        String key = properties.getImapHost() + ":" + properties.getImapPort();
        Session session = SESSION_CACHE.get(key);
        if (session == null)
        {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imap");
            props.put("mail.imap.host", properties.getImapHost());
            props.put("mail.imap.port", String.valueOf(properties.getImapPort()));
            // 企业邮箱一般要求SSL加密连接（imapv.global-mail.cn 993端口）
            props.put("mail.imap.ssl.enable", "true");
            props.put("mail.imap.connectiontimeout", "30000");
            props.put("mail.imap.timeout", "300000");
            props.put("mail.imap.writetimeout", "300000");
            // 空闲保活：每120秒发送NOOP，防止服务器因空闲超时关闭连接
            props.put("mail.imap.keepalive", "120");
            Session newSession = Session.getInstance(props);
            Session existing = SESSION_CACHE.putIfAbsent(key, newSession);
            session = existing == null ? newSession : existing;
        }
        return session;
    }

    /**
     * 打开收件箱文件夹（只读）
     */
    private Folder openFolder(Store store) throws MessagingException
    {
        Folder folder = store.getFolder(properties.getFolder());
        if (folder == null || !folder.exists())
        {
            throw new ServiceException("邮件文件夹不存在：" + properties.getFolder());
        }
        folder.open(Folder.READ_ONLY);
        return folder;
    }

    /**
     * 获取邮件UID（用于去重），不支持时返回-1
     */
    private long getUid(Folder folder, Message message)
    {
        if (folder instanceof UIDFolder)
        {
            try
            {
                return ((UIDFolder) folder).getUID(message);
            }
            catch (MessagingException e)
            {
                return -1;
            }
        }
        return -1;
    }

    /**
     * 发件人过滤：发件人地址模糊匹配过滤规则（如内部域名 meelectronic.cn）时返回true（跳过该邮件）
     * <p>
     * 过滤规则为空时不过滤；过滤发生在解析正文/附件之前，被过滤的邮件不读取、不返回。
     */
    private boolean isSenderFiltered(Message message)
    {
        String filter = StringUtils.trimToNull(properties.getSenderBlackFilter());
        if (filter == null)
        {
            return false;
        }
        String lowerFilter = filter.toLowerCase();
        try
        {
            Address[] fromAddresses = message.getFrom();
            if (fromAddresses != null)
            {
                for (Address address : fromAddresses)
                {
                    if (address instanceof InternetAddress)
                    {
                        String sender = ((InternetAddress) address).getAddress();
                        // 邮箱地址不区分大小写，统一转小写后模糊匹配
                        if (StringUtils.isNotEmpty(sender) && sender.toLowerCase().contains(lowerFilter))
                        {
                            return true;
                        }
                    }
                }
            }
        }
        catch (MessagingException e)
        {
            // 获取发件人失败时不过滤，避免误丢邮件
            log.warn("获取发件人失败，跳过发件人过滤：{}", e.getMessage());
        }
        return false;
    }

    /**
     * 解析单封邮件：发件人、主题、正文、附件、类型
     */
    private EkeEmailDTO parseMessage(Message message, List<EkeEmailTypeAnalyzer.TypeRule> typeRules,
            List<EkeEmailTypeAnalyzer.EmailRule> emailRules) throws Exception
    {
        EkeEmailDTO dto = new EkeEmailDTO();

        // 邮件唯一标识
        if (message instanceof MimeMessage)
        {
            dto.setMessageId(((MimeMessage) message).getMessageID());
        }
        // 发件人
        Address[] fromAddresses = message.getFrom();
        if (fromAddresses != null && fromAddresses.length > 0 && fromAddresses[0] instanceof InternetAddress)
        {
            InternetAddress from = (InternetAddress) fromAddresses[0];
            dto.setSender(from.getAddress());
            // 发件人名称可能使用MIME编码（如 =?UTF-8?B?...?=），需要解码
            if (StringUtils.isNotEmpty(from.getPersonal()))
            {
                dto.setSenderName(MimeUtility.decodeText(from.getPersonal()));
            }
        }
        // 收件人
        Address[] toAddresses = message.getRecipients(Message.RecipientType.TO);
        if (toAddresses != null)
        {
            for (Address address : toAddresses)
            {
                dto.getRecipients().add(address.toString());
            }
        }
        // 主题（同样需要MIME解码）
        dto.setSubject(MimeUtility.decodeText(StringUtils.trimToEmpty(message.getSubject())));
        // 时间
        dto.setSentDate(message.getSentDate());
        dto.setReceivedDate(message.getReceivedDate());
        // 格式化时间，便于直接展示
        dto.setSentTimeStr(formatDate(dto.getSentDate()));
        dto.setReceivedTimeStr(formatDate(dto.getReceivedDate()));

        // 正文与附件（递归解析）
        parsePart(message, dto);

        // 邮件类型分析：发件人邮箱过滤（第一优先级）优先于主题关键词匹配，均未命中时归为“未归属”
        EkeEmailTypeAnalyzer.TypeResult typeResult =
                EkeEmailTypeAnalyzer.analyze(dto.getSubject(), dto.getSender(), typeRules, emailRules);
        dto.setType(typeResult.getTypeName());
        dto.setTypeCode(typeResult.getTypeCode());
        return dto;
    }

    /**
     * 递归解析邮件内容：提取纯文本正文与附件，HTML正文转为纯文本
     */
    private void parsePart(Part part, EkeEmailDTO dto) throws Exception
    {
        // 纯文本正文（基础清理：统一换行符、去除引用标记与多余空白，便于阅读）
        if (part.isMimeType("text/plain"))
        {
            Object content = part.getContent();
            if (content != null && StringUtils.isEmpty(dto.getContent()))
            {
                dto.setContent(EkeHtmlToTextUtil.cleanPlainText(content.toString()));
            }
        }
        // HTML正文：去除标签并还原表格为制表符分隔的纯文本（仅在无纯文本正文时使用）
        else if (part.isMimeType("text/html"))
        {
            if (StringUtils.isEmpty(dto.getContent()))
            {
                // 读取原始字节并按UTF-8优先解码，避免Content-Type未声明/声明错误导致的乱码（如欧元符号显示为�?）
                try (InputStream in = part.getInputStream())
                {
                    byte[] bytes = IoUtil.readBytes(in);
                    dto.setContent(EkeHtmlToTextUtil.toText(EkeHtmlToTextUtil.decode(bytes, part.getContentType())));
                }
            }
        }
        // 复合内容：递归解析每个子部分
        else if (part.isMimeType("multipart/*"))
        {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++)
            {
                parsePart(multipart.getBodyPart(i), dto);
            }
        }
        // 转发邮件（message/rfc822）：递归解析内嵌邮件
        else if (part.isMimeType("message/rfc822"))
        {
            parsePart((Part) part.getContent(), dto);
        }
        // 附件：显式标记为附件，或带文件名且不是内嵌图片
        else
        {
            String disposition = part.getDisposition();
            String fileName = part.getFileName();
            if (Part.ATTACHMENT.equalsIgnoreCase(disposition)
                    || (StringUtils.isNotEmpty(fileName) && !Part.INLINE.equalsIgnoreCase(disposition)))
            {
                saveAttachment(part, dto);
            }
        }
    }

    /**
     * 保存邮件附件到本地磁盘，并记录附件信息
     * <p>
     * 附件保存失败（目录权限、文件名非法等）只记录日志，不影响整封邮件的解析结果
     */
    private void saveAttachment(Part part, EkeEmailDTO dto)
    {
        try
        {
            // 文件名解码（可能为MIME编码），过滤Windows文件名非法字符（/ \ : * ? " < > | 及控制字符），
            // 既防止路径穿越，也避免 createNewFile 因非法字符报“系统找不到指定的路径”
            String fileName = MimeUtility.decodeText(StringUtils.defaultString(safeFileName(part)));
            fileName = fileName.replaceAll("[/\\\\:*?\"<>|\\p{Cntrl}]", "_");
            if (StringUtils.isEmpty(fileName))
            {
                fileName = "attachment_" + System.currentTimeMillis();
            }
            // 按日期分子目录，UUID前缀避免重名覆盖；目录创建失败时跳过该附件
            String dateDir = DateFormatUtils.format(new Date(), "yyyyMMdd");
            File dir = new File(properties.getAttachmentPath(), dateDir);
            if (!dir.exists() && !dir.mkdirs())
            {
                log.error("创建附件目录失败，跳过附件保存：{}", dir.getAbsolutePath());
                return;
            }
            String uniqueName = UUID.randomUUID().toString().replace("-", "") + "_" + fileName;
            File target = new File(dir, uniqueName);
            try (InputStream in = part.getInputStream())
            {
                FileUtil.writeFromStream(in, target);
            }

            // 记录附件信息
            EkeEmailAttachmentDTO attach = new EkeEmailAttachmentDTO();
            attach.setFileName(fileName);
            attach.setFilePath(target.getAbsolutePath());
            attach.setFileSize(target.length());
            attach.setContentType(part.getContentType());
            dto.getAttachments().add(attach);
        }
        catch (Exception e)
        {
            // 单封附件保存失败不影响整封邮件解析
            log.error("附件保存失败，跳过该附件：{}", e.getMessage());
        }
    }

    /**
     * 安全获取附件文件名（获取失败时返回null）
     */
    private String safeFileName(Part part)
    {
        try
        {
            return part.getFileName();
        }
        catch (MessagingException e)
        {
            return null;
        }
    }

    /**
     * 从字典读取邮件类型匹配规则
     * <p>
     * 字典 value = 邮件类型编码，label = 邮件类型中文名称，remark = 逗号分隔的匹配关键词；
     * 按字典排序（dict_sort）作为匹配优先级，只取正常状态的字典项；
     * 未配置关键词的字典项不会命中任何邮件，但分类汇总中仍会列出。
     *
     * @return 匹配规则列表
     */
    private List<EkeEmailTypeAnalyzer.TypeRule> buildTypeRules()
    {
        // 查询正常状态（status=0）的字典数据，按 dict_sort 升序返回
        SysDictData query = new SysDictData();
        query.setDictType(properties.getTypeDictType());
        query.setStatus(UserConstants.NORMAL);
        List<SysDictData> dictDatas = dictDataService.selectDictDataList(query);

        List<EkeEmailTypeAnalyzer.TypeRule> rules = new ArrayList<>();
        if (dictDatas != null)
        {
            for (SysDictData dict : dictDatas)
            {
                // remark 中按逗号（兼容中英文逗号）分割关键词，自动去除空白与空项
                List<String> keywords = new ArrayList<>();
                for (String keyword : StringUtils.trimToEmpty(dict.getRemark()).split("[,，]"))
                {
                    String k = StringUtils.trim(keyword);
                    if (StringUtils.isNotEmpty(k))
                    {
                        keywords.add(k);
                    }
                }
                // 关键词为空时该类型不参与匹配（analyze 永远不会命中），但分类汇总中仍会列出
                rules.add(new EkeEmailTypeAnalyzer.TypeRule(dict.getDictValue(), dict.getDictLabel(), keywords));
            }
        }
        return rules;
    }

    /**
     * 从字典读取发件人邮箱过滤规则（第一优先级）
     * <p>
     * 字典类型见 EkeMailReadProperties.filterEmailDictType（默认 eke_email_filter_email_special）：
     * 字典 value = 类型编码（与主题字典 value 一致，如 OFFER/INQ），label = 类型中文名称，
     * remark = 逗号拼接的邮箱地址；发件人模糊匹配命中时直接归类，优先于主题匹配。
     *
     * @return 邮箱过滤规则列表
     */
    private List<EkeEmailTypeAnalyzer.EmailRule> buildEmailRules()
    {
        // 查询正常状态（status=0）的字典数据，按 dict_sort 升序返回
        SysDictData query = new SysDictData();
        query.setDictType(properties.getFilterEmailDictType());
        query.setStatus(UserConstants.NORMAL);
        List<SysDictData> dictDatas = dictDataService.selectDictDataList(query);

        List<EkeEmailTypeAnalyzer.EmailRule> rules = new ArrayList<>();
        if (dictDatas != null)
        {
            for (SysDictData dict : dictDatas)
            {
                // remark 中按逗号（兼容中英文逗号）分割邮箱地址，统一转小写便于不区分大小写匹配
                List<String> emails = new ArrayList<>();
                for (String email : StringUtils.trimToEmpty(dict.getRemark()).split("[,，]"))
                {
                    String e = StringUtils.trim(email).toLowerCase();
                    if (StringUtils.isNotEmpty(e))
                    {
                        emails.add(e);
                    }
                }
                if (emails.isEmpty())
                {
                    continue;
                }
                rules.add(new EkeEmailTypeAnalyzer.EmailRule(dict.getDictValue(), dict.getDictLabel(), emails));
            }
        }
        return rules;
    }

    /**
     * 按类型汇总邮件，生成分类数组
     * <p>
     * 每个字典类型一个分类（按 dict_sort 顺序），未匹配任何字典的邮件归入“未归属”分类并排在最后；
     * 分类下只包含邮件发件人与主题的摘要信息。
     *
     * @param typeRules 字典匹配规则（顺序即分类顺序）
     * @param emails 已解析的邮件列表
     * @return 分类汇总列表
     */
    private List<EkeEmailCategoryDTO> buildCategorySummary(List<EkeEmailTypeAnalyzer.TypeRule> typeRules,
            List<EkeEmailDTO> emails)
    {
        // 先按类型编码分组邮件（LinkedHashMap 保持插入顺序）
        Map<String, List<EkeEmailDTO>> grouped = new LinkedHashMap<>();
        for (EkeEmailDTO email : emails)
        {
            grouped.computeIfAbsent(email.getTypeCode(), k -> new ArrayList<>()).add(email);
        }
        List<EkeEmailCategoryDTO> categories = new ArrayList<>();
        // 字典类型按 dict_sort 顺序全部列出（即使该类型没有邮件也列出，count=0）
        if (typeRules != null)
        {
            for (EkeEmailTypeAnalyzer.TypeRule rule : typeRules)
            {
                categories.add(buildCategory(rule.getTypeCode(), rule.getTypeName(), grouped.get(rule.getTypeCode())));
            }
        }
        // 未归属分类始终列出，排在最后
        categories.add(buildCategory(EkeEmailTypeAnalyzer.DEFAULT_TYPE_CODE, EkeEmailTypeAnalyzer.DEFAULT_TYPE_NAME,
                grouped.get(EkeEmailTypeAnalyzer.DEFAULT_TYPE_CODE)));
        return categories;
    }

    /**
     * 构建单个分类：类型编码、类型名称、邮件数、邮件明细（发件人、主题）
     */
    private EkeEmailCategoryDTO buildCategory(String typeCode, String typeName, List<EkeEmailDTO> emails)
    {
        EkeEmailCategoryDTO category = new EkeEmailCategoryDTO();
        category.setTypeCode(typeCode);
        category.setTypeName(typeName);
        List<Map<String, Object>> mails = new ArrayList<>();
        if (emails != null)
        {
            for (EkeEmailDTO email : emails)
            {
                Map<String, Object> mail = new HashMap<>();
                mail.put("sender", email.getSender());
                mail.put("senderName", email.getSenderName());
                mail.put("subject", email.getSubject());
                mails.add(mail);
            }
        }
        category.setCount(mails.size());
        category.setMails(mails);
        return category;
    }

    /**
     * 格式化时间为 yyyy-MM-dd HH:mm:ss，时间为空时返回 null
     */
    private String formatDate(Date date)
    {
        return date == null ? null : DateFormatUtils.format(date, DATE_TIME_PATTERN);
    }

    /**
     * 按接收时间倒序比较两封邮件（接收时间为空的排最后）
     */
    private int compareByReceivedDateDesc(Message m1, Message m2)
    {
        Date d1 = safeReceivedDate(m1);
        Date d2 = safeReceivedDate(m2);
        if (d1 == null && d2 == null)
        {
            return 0;
        }
        if (d1 == null)
        {
            return 1;
        }
        if (d2 == null)
        {
            return -1;
        }
        return d2.compareTo(d1);
    }

    private Date safeReceivedDate(Message message)
    {
        try
        {
            return message.getReceivedDate();
        }
        catch (MessagingException e)
        {
            return null;
        }
    }

    private String safeSubject(Message message)
    {
        try
        {
            return message.getSubject();
        }
        catch (MessagingException e)
        {
            return null;
        }
    }

    /**
     * 关闭文件夹与连接（忽略关闭异常）
     */
    private void closeQuietly(Folder folder, Store store)
    {
        if (folder != null)
        {
            try
            {
                folder.close(false);
            }
            catch (MessagingException ignored)
            {
            }
        }
        if (store != null)
        {
            try
            {
                store.close();
            }
            catch (MessagingException ignored)
            {
            }
        }
    }

    /**
     * IMAP读取结果：邮件列表 + 是否因单次数量上限被截断
     */
    private static class FetchResult
    {
        private final List<EkeEmailDTO> emails;
        private final boolean truncated;
        private final int filteredCount;

        FetchResult(List<EkeEmailDTO> emails, boolean truncated, int filteredCount)
        {
            this.emails = emails;
            this.truncated = truncated;
            this.filteredCount = filteredCount;
        }

        public List<EkeEmailDTO> getEmails()
        {
            return emails;
        }

        public boolean isTruncated()
        {
            return truncated;
        }

        public int getFilteredCount()
        {
            return filteredCount;
        }
    }
}
