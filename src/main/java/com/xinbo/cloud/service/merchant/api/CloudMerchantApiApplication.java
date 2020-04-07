package com.xinbo.cloud.service.merchant.api;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import tk.mybatis.spring.annotation.MapperScan;

@EnableSwagger2
@MapperScan(basePackages = "com.xinbo.cloud.common.mapper")
@SpringBootApplication(scanBasePackages = "com.xinbo.cloud", exclude= {DataSourceAutoConfiguration.class})
public class CloudMerchantApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudMerchantApiApplication.class, args);
    }
}
