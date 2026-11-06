package com.community.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserMapper 的真实读写验证。
 *
 * <p>@Transactional 让每个测试方法结束后回滚，测试数据不会污染开发库。
 */
@SpringBootTest
@Transactional
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    private User sample(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setNickname("测试用户");
        user.setStatus(User.STATUS_ACTIVE);
        return user;
    }

    @Test
    void insertThenReadBack() {
        User user = sample("mapper-test-01");

        int rows = userMapper.insert(user);
        assertThat(rows).isEqualTo(1);
        assertThat(user.getId()).as("主键应被回填").isNotNull();

        Optional<User> found = userMapper.findByUsername("mapper-test-01");
        assertThat(found).isPresent();
        User loaded = found.get();

        System.out.println("插入后回填 id = " + user.getId());
        System.out.println("查询结果 = " + loaded);
        System.out.println("createdAt = " + loaded.getCreatedAt());

        assertThat(loaded.getId()).isEqualTo(user.getId());
        assertThat(loaded.getPasswordHash()).isEqualTo("$2a$10$abcdefghijklmnopqrstuv");
        assertThat(loaded.getNickname()).isEqualTo("测试用户");
        assertThat(loaded.getStatus()).isEqualTo(User.STATUS_ACTIVE);
        assertThat(loaded.getCreatedAt()).as("数据库默认值 CURRENT_TIMESTAMP 应被映射").isNotNull();
        assertThat(loaded.isActive()).isTrue();
    }

    @Test
    void duplicateUsernameRejectedByDatabase() {
        userMapper.insert(sample("mapper-test-dup"));

        assertThatThrownBy(() -> userMapper.insert(sample("mapper-test-dup")))
                .isInstanceOf(DuplicateKeyException.class);

        System.out.println("重复用户名被数据库拒绝：DuplicateKeyException");
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        assertThat(userMapper.findById(-1L)).isEmpty();
    }
}
