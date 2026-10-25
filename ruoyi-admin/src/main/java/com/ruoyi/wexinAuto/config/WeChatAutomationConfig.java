package com.ruoyi.wexinAuto.config;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.utils.DictUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 微信自动化参数读取。所有开关和阈值统一来自数据字典 wechat_analysis_config。 */
@Component
public class WeChatAutomationConfig {

    private static final String DICT_TYPE = "wechat_analysis_config";

    public String str(String label, String def) {
        List<SysDictData> list = DictUtils.getDictCache(DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (label.equals(d.getDictLabel()) && StringUtils.isNotBlank(d.getDictValue())) {
                    return d.getDictValue().trim();
                }
            }
        }
        return def;
    }

    public int integer(String label, int def) {
        try {
            return Integer.parseInt(str(label, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public double decimal(String label, double def) {
        try {
            return Double.parseDouble(str(label, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean bool(String label, boolean def) {
        String value = str(label, String.valueOf(def));
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /** 我方成员始终包含四位内置成员，字典只能补充别名。 */
    public Set<String> selfNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String name : "Eke,May,Jin,Jasmine".split(",")) {
            names.add(name.trim());
        }
        String configured = str("selfNames", "");
        if (StringUtils.isNotBlank(configured)) {
            for (String name : configured.split(",")) {
                if (StringUtils.isNotBlank(name)) {
                    names.add(name.trim());
                }
            }
        }
        return names;
    }

    public int matchDayNo() {
        int days = integer("inqMatchDayNo", 2);
        int retention = Math.max(1, integer("msgRetentionDays", 30));
        return Math.max(1, Math.min(retention, days));
    }
}
