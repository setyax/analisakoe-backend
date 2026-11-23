package id.analisakoe.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UserCreateRequest(
    @NotBlank @Size(min = 3, max = 64) String username,
    @Email String email,
    @Size(max = 200) String fullName,
    @NotBlank @Size(min = 8, max = 100) String password,
    Set<String> roleCodes,
    String status
) {}