package com.xinbo.cloud.service.merchant.api;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import tk.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(scanBasePackages = "com.xinbo.cloud")
@EnableSwagger2
@EnableDiscoveryClient
@MapperScan(basePackages = "com.xinbo.cloud")
@EnableFeignClients
public class CloudMerchantApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudMerchantApiApplication.class, args);
    }
}