package com.crm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CRM System Application
 * 
 * @author CRM System
 */
@SpringBootApplication
@MapperScan("com.crm.**.mapper")
public class CrmSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmSystemApplication.class, args);
        System.out.println("CRM System Started Successfully!");
    }
}
