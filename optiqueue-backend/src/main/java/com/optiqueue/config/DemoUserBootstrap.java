package com.optiqueue.config;

import com.optiqueue.entity.Role;
import com.optiqueue.entity.User;
import com.optiqueue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Public registration only creates CUSTOMER accounts, so the ADMIN and STAFF
 * accounts are provisioned at startup from environment variables (idempotent:
 * skipped if the username already exists).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DemoUserBootstrap {

    @Value("${optiqueue.bootstrap.admin-username:admin}")
    private String adminUsername;
    @Value("${optiqueue.bootstrap.admin-password:admin12345}")
    private String adminPassword;
    @Value("${optiqueue.bootstrap.staff-username:staff}")
    private String staffUsername;
    @Value("${optiqueue.bootstrap.staff-password:staff12345}")
    private String staffPassword;

    @Bean
    public CommandLineRunner bootstrapUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            createIfMissing(userRepository, passwordEncoder, adminUsername, adminPassword, Role.ADMIN);
            createIfMissing(userRepository, passwordEncoder, staffUsername, staffPassword, Role.STAFF);
        };
    }

    private void createIfMissing(UserRepository repo, PasswordEncoder encoder,
                                 String username, String password, Role role) {
        if (!repo.existsByUsername(username)) {
            repo.save(User.builder()
                    .username(username)
                    .passwordHash(encoder.encode(password))
                    .role(role)
                    .build());
            log.info("Bootstrapped {} user '{}'", role, username);
        }
    }
}
