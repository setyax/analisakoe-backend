package id.analisakoe.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UserUpdateRequest(
    @Email String email,
    @Size(max = 200) String fullName,
    Set<String> roleCodes,
    String status
) {}