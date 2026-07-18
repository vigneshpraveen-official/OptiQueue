package com.optiqueue.service;

import com.optiqueue.dto.AuthDtos.AuthResponse;
import com.optiqueue.dto.AuthDtos.LoginRequest;
import com.optiqueue.dto.AuthDtos.RegisterRequest;
import com.optiqueue.entity.Role;
import com.optiqueue.entity.User;
import com.optiqueue.exception.ApiException;
import com.optiqueue.repository.UserRepository;
import com.optiqueue.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN",
                    "Username '%s' is already taken".formatted(request.username()));
        }
        // Public registration only creates CUSTOMER accounts. ADMIN/STAFF are
        // provisioned by an existing admin (or seeded), never self-registered.
        Role role = request.role() == null ? Role.CUSTOMER : request.role();
        if (role != Role.CUSTOMER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_NOT_ALLOWED",
                    "Self-registration is only allowed for CUSTOMER accounts");
        }
        User user = User.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .build();
        userRepository.save(user);
        return new AuthResponse(jwtUtil.generateToken(user.getUsername(), user.getRole()),
                user.getUsername(), user.getRole());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("bad credentials");
        }
        return new AuthResponse(jwtUtil.generateToken(user.getUsername(), user.getRole()),
                user.getUsername(), user.getRole());
    }
}
