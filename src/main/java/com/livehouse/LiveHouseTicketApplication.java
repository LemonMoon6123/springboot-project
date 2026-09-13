package com.livehouse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.livehouse.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class LiveHouseTicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiveHouseTicketApplication.class, args);
    }

}