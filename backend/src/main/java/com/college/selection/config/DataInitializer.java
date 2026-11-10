package com.college.selection.config;

import com.college.selection.entity.SysUser;
import com.college.selection.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * 启动时把 seed.sql 里的密码占位符换成真实 BCrypt 哈希。
 *
 * 为什么这么做：SQL 里没法生成 BCrypt，所以 seed.sql 只能写 '$2a$10$REPLACE...'。
 * 这里在启动时扫一遍，把占位符统一设成 123456。
 *
 * ⚠️ 上线前必须删掉这段逻辑（或用配置开关关掉），否则任何人都知道默认密码。
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEMO_PASSWORD = "123456";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CommandLineRunner initDemoPasswords(SysUserMapper userMapper, PasswordEncoder encoder) {
        return args -> {
            List<SysUser> placeholders = userMapper.findPlaceholderUsers();
            if (placeholders.isEmpty()) {
                return;
            }
            String hash = encoder.encode(DEMO_PASSWORD);
            for (SysUser u : placeholders) {
                userMapper.updatePassword(u.id, hash);
            }
            log.warn("已为 {} 个演示账号设置初始密码 {}（仅限本地演示）", placeholders.size(), DEMO_PASSWORD);
        };
    }
}
