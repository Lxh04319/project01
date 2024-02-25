package com.lxh11111;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.lxh11111.mapper")
@SpringBootApplication
public class DianPingSystemsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DianPingSystemsApplication.class, args);
    }

}
