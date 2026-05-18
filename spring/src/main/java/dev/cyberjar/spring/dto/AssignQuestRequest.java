package dev.cyberjar.spring.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignQuestRequest(
        @NotBlank String heroName,
        @NotBlank String heroClass
) {
}
