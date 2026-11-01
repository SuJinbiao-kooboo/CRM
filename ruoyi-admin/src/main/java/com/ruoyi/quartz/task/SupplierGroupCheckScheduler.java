package com.ruoyi.quartz.task;

import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.service.ICrmSupplierService;
import com.ruoyi.crm.service.ai.AiChannelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/** 供应商群配置检查：每天扫描启用供应商，采购群和销售群都为空时推送飞书告警。 */
@Slf4j
@Component("supplierGroupCheckScheduler")
public class SupplierGroupCheckScheduler {

    private static final String ALERT_EVENT = "supplier.group.missing";
    private static final int DETAIL_LIMIT = 200;

    private final ICrmSupplierService supplierService;
    private final AiChannelService aiChannelService;

    public SupplierGroupCheckScheduler(ICrmSupplierService supplierService, AiChannelService aiChannelService) {
        this.supplierService = supplierService;
        this.aiChannelService = aiChannelService;
    }

    public void checkMissingGroups() {
        try {
            List<CrmSupplier> suppliers = supplierService.selectSuppliersWithoutGroups(new CrmSupplier());
            if (suppliers == null || suppliers.isEmpty()) {
                log.info("供应商群配置检查通过：启用供应商的采购群和销售群均已配置");
                return;
            }
            String detail = buildDetail(suppliers);
            boolean sent = aiChannelService.alert(ALERT_EVENT,
                    "【供应商群配置预警】采购群和销售群均为空（" + suppliers.size() + " 家）", detail);
            if (!sent) {
                log.warn("供应商群配置飞书告警未发送成功，请检查 sys.ai.alert.webhook 配置或告警冷却；待补供应商 {} 家",
                        suppliers.size());
            }
        } catch (Exception e) {
            log.error("供应商群配置定时检查失败", e);
        }
    }

    private String buildDetail(List<CrmSupplier> suppliers) {
        StringBuilder detail = new StringBuilder("以下启用供应商的采购群和销售群均为空，请补齐 crm_supplier.purchase_group / sales_group：\n");
        int limit = Math.min(suppliers.size(), DETAIL_LIMIT);
        for (int i = 0; i < limit; i++) {
            CrmSupplier supplier = suppliers.get(i);
            detail.append(i + 1).append(". ")
                    .append(defaultString(supplier.getSupplierCode())).append(' ')
                    .append(defaultString(supplier.getSupplierName())).append('\n');
        }
        if (suppliers.size() > limit) {
            detail.append("……其余 ").append(suppliers.size() - limit).append(" 家请到供应商列表使用“检查群配置”查看。");
        }
        return detail.toString();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
