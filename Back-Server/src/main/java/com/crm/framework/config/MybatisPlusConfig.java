package com.crm.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus配置类
 * 
 * @author CRM System
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件配置
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充处理器
     */
    @Component
    public static class MyMetaObjectHandler implements MetaObjectHandler {

        @Override
        public void insertFill(MetaObject metaObject) {
            // 创建时间自动填充
            this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
            this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            
            // 创建人和更新人需要从当前登录用户获取，这里先设置为1L（管理员）
            // 实际项目中应该从SecurityContext中获取当前用户ID
            this.strictInsertFill(metaObject, "createBy", Long.class, 1L);
            this.strictInsertFill(metaObject, "updateBy", Long.class, 1L);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            // 更新时间自动填充
            this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            
            // 更新人需要从当前登录用户获取，这里先设置为1L（管理员）
            // 实际项目中应该从SecurityContext中获取当前用户ID
            this.strictUpdateFill(metaObject, "updateBy", Long.class, 1L);
        }
    }
}