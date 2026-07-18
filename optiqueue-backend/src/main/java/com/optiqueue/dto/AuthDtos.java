package com.optiqueue.dto;

import com.optiqueue.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "username may only contain letters, digits, '_', '.', '-'")
            String username,
            @NotBlank @Size(min = 8, max = 100)
            String password,
            // Optional; defaults to CUSTOMER. ADMIN/STAFF self-registration is rejected in the service.
            Role role
    ) {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record AuthResponse(String token, String username, Role role) {}
}
