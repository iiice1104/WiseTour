package com.wisetour;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.wisetour.mapper")
@SpringBootApplication
public class WiseTourApplication {

    public static void main(String[] args) {
        SpringApplication.run(WiseTourApplication.class, args);
    }

}
