package com.community;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证数据层的两件事：
 * 1. 应用的数据源真的连上了 community 库，且 3 张表存在；
 * 2. MyBatis 的自动装配生效（SqlSessionFactory 已被创建）。
 */
@SpringBootTest
class SchemaExistsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void threeTablesExistInApplicationView() {
        String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name",
                String.class);

        System.out.println("应用连上的库: " + schema);
        System.out.println("库中的表: " + tables);

        assertThat(tables).contains("user", "content", "content_like");
    }

    @Test
    void mybatisIsAutoConfigured() {
        assertThat(sqlSessionFactory).isNotNull();
        assertThat(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource()).isNotNull();

        System.out.println("MyBatis 环境: "
                + sqlSessionFactory.getConfiguration().getEnvironment().getId());
        System.out.println("数据库产品: "
                + sqlSessionFactory.getConfiguration().getDatabaseId());
    }
}
