package com.community;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 *
 * <p>必须位于根包 com.community 下：@SpringBootApplication 隐含 @ComponentScan，
 * 扫描范围是「本类所在包及其子包」。放到子包里会导致同级模块的 Bean 扫不到。
 */
@SpringBootApplication
public class CommunityApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityApplication.class, args);
    }
}
