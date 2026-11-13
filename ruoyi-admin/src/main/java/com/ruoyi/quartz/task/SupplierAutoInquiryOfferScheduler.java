package com.ruoyi.quartz.task;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.mapper.CrmSupplierMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.wexinAuto.service.WeChatOfferTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 供应商自动询Offer定时任务：每周一、周三、周四 10:00 扫描开启开关的供应商，
 * 读取当天日期尾数对应的字典祝福语，为每家供应商创建待审批微信任务。
 */
@Slf4j
@Component("supplierAutoInquiryOfferScheduler")
public class SupplierAutoInquiryOfferScheduler {

    public static final String GREETING_DICT_TYPE = "wechat_auto_inquiry_offer_greeting";
    private static final String QUESTION = "请问今天有什么好的Offer Share吗?";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CrmSupplierMapper supplierMapper;
    private final WeChatOfferTaskService taskService;
    private final ISysDictTypeService dictTypeService;

    public SupplierAutoInquiryOfferScheduler(CrmSupplierMapper supplierMapper,
                                             WeChatOfferTaskService taskService,
                                             ISysDictTypeService dictTypeService) {
        this.supplierMapper = supplierMapper;
        this.taskService = taskService;
        this.dictTypeService = dictTypeService;
    }

    /** Quartz 入口：所有异常按供应商隔离，单家失败不影响其余供应商。 */
    public void createPendingTasks() {
        LocalDate today = LocalDate.now(ZONE);
        String index = String.valueOf(today.getDayOfMonth() % 10);
        String greeting = resolveGreeting(index);
        if (StringUtils.isBlank(greeting)) {
            log.error("自动询Offer终止：字典 {} 未配置 dict_value={} 的祝福语", GREETING_DICT_TYPE, index);
            return;
        }

        String content = greeting + "\n\n" + QUESTION;
        List<CrmSupplier> suppliers = supplierMapper.selectAutoInquiryOfferSuppliers();
        if (suppliers == null || suppliers.isEmpty()) {
            log.info("自动询Offer扫描完成：没有开启自动询Offer且配置销售群的供应商");
            return;
        }

        String dayKey = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        int created = 0;
        int duplicates = 0;
        int failed = 0;
        for (CrmSupplier supplier : suppliers) {
            try {
                Map<String, Object> result = taskService.createAutoInquiryOfferTask(supplier, content, dayKey);
                created += number(result.get("created"));
                duplicates += number(result.get("duplicates"));
                log.info("自动询Offer供应商处理完成: supplierId={}, supplierCode={}, created={}, duplicates={}",
                        supplier.getId(), supplier.getSupplierCode(), result.get("created"), result.get("duplicates"));
            } catch (Exception e) {
                failed++;
                log.error("自动询Offer供应商处理失败: supplierId={}, supplierCode={}",
                        supplier.getId(), supplier.getSupplierCode(), e);
            }
        }
        log.info("自动询Offer扫描完成: date={}, greetingIndex={}, suppliers={}, created={}, duplicates={}, failed={}",
                dayKey, index, suppliers.size(), created, duplicates, failed);
    }

    /** 当天的最后一位数字直接匹配字典值 0-9；读不到时安全终止，不在代码里硬编码兜底。 */
    private String resolveGreeting(String dictValue) {
        List<SysDictData> rows = dictTypeService.selectDictDataByType(GREETING_DICT_TYPE);
        if (rows == null) {
            rows = Collections.emptyList();
        }
        for (SysDictData row : rows) {
            if (row != null && dictValue.equals(StringUtils.trimToEmpty(row.getDictValue()))) {
                return StringUtils.trimToEmpty(row.getDictLabel());
            }
        }
        return null;
    }

    private int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
