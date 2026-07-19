package com.nylg.zziiaicodemother;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.nylg.zziiaicodemother.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class ZziiAiCodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZziiAiCodeMotherApplication.class, args);
    }

}
