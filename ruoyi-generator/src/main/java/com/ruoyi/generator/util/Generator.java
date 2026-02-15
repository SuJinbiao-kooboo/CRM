package com.ruoyi.generator.util;


import com.baomidou.mybatisplus.generator.AutoGenerator;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

/**
 * MyBatis-Plus 3.5.3.1 代码生成器
 * @description:
 * @author: 亦愘
 * @date: 2024/10/31
 * @company: 深圳减字科技有限公司
 */
public class Generator {
    // jdbc:mysql://42.194.240.191:3306/crm_me?useUnicode=true&useSSL=false&characterEncoding=utf8&serverTimezone=GMT%2B8
    // root
    private static final String Url = "jdbc:mysql://42.194.240.191:3306/crm_me?useUnicode=true&useSSL=false&characterEncoding=utf8&serverTimezone=GMT%2B8";
    private static final String DriverName = "com.mysql.cj.jdbc.Driver";
    private static final String Username = "root";
    private static final String Password = "eke123";
    private static final String projectPath = System.getProperty("user.dir");
    private static final String moduleName = "common";
    private static final String parentPackage = "com.ruoyi.buddy";

    public static void main(String[] args) {
        // 1、创建代码生成器
        AutoGenerator autoGenerator = new AutoGenerator(new DataSourceConfig.Builder(Url, Username, Password)
                .build());

        // 2、全局配置
        GlobalConfig globalConfig = new GlobalConfig.Builder()
                .author("亦愘")                                        // 设置作者
                .outputDir(projectPath + "/src/main/java")             // 输出路径
                .disableOpenDir()                                       // 生成后不打开输出目录
                .dateType(DateType.ONLY_DATE)                           // 日期类型
                .commentDate("yyyy-MM-dd")                              // 注释日期格式
                .build();

        // 3、包配置
        PackageConfig packageConfig = new PackageConfig.Builder()
                .parent(parentPackage)                                  // 父包名
                .moduleName(moduleName)                                 // 模块名
                .controller("controller")                               // controller包名
                .entity("bean")                                         // entity包名
                .service("service")                                     // service包名
                .serviceImpl("service.impl")                            // serviceImpl包名
                .mapper("mapper")                                       // mapper包名
                .xml("mapper.xml")                                      // xml包名
                .build();

        // 4、策略配置 - 关键修复点：分开构建，最后组装
        StrategyConfig.Builder strategyBuilder = new StrategyConfig.Builder();

        // 添加表名
        strategyBuilder.addInclude(
                "do_user",
                "do_user_setting",
                "do_plan",
                "do_plan_buddy",
                "do_checkin",
                "do_energy_log",
                "do_achievement",
                "do_user_achievement",
                "do_notification"
        );

        // Entity 策略配置
        strategyBuilder.entityBuilder()
                .naming(NamingStrategy.underline_to_camel)              // 表名转驼峰
                .columnNaming(NamingStrategy.underline_to_camel)        // 列名转驼峰
                .enableLombok()                                          // 启用Lombok
                .idType(com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID) // 主键策略
                .enableChainModel();                                     // 链式模型

        // Controller 策略配置
        strategyBuilder.controllerBuilder()
                .enableRestStyle()                                       // Rest风格
                .enableHyphenStyle();                                    // 连字符风格

        // Service 策略配置
        strategyBuilder.serviceBuilder()
                .formatServiceFileName("%sService")                      // Service接口文件名格式
                .formatServiceImplFileName("%sServiceImpl");             // Service实现类文件名格式

        // Mapper 策略配置
        strategyBuilder.mapperBuilder()
                .enableBaseResultMap()                                   // 生成ResultMap
                .enableBaseColumnList()                                  // 生成ColumnList
                .formatMapperFileName("%sMapper")                        // Mapper文件名格式
                .formatXmlFileName("%sMapper");                          // Xml文件名格式

        StrategyConfig strategyConfig = strategyBuilder.build();

        // 5、模板配置（如果需要自定义模板）
        // TemplateConfig templateConfig = new TemplateConfig.Builder().build();

        // 6、整合配置并执行
        autoGenerator
                .global(globalConfig)
                .packageInfo(packageConfig)
                .strategy(strategyConfig)
                .execute(new FreemarkerTemplateEngine());
    }
}