package com.ruoyi.framework.config.properties;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * druid 配置属性
 * 
 * @author ruoyi
 */
@Component
@ConfigurationProperties("crm")
@Data
public class CRMProperties
{
    private List<String> whiteList = Arrays.asList("/login", "/register", "/captchaImage");

}
