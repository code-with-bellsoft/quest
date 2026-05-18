package dev.cyberjar.spring.dto;

import dev.cyberjar.spring.domain.QuestStatus;

public record AssignQuestResponse(
        Long questId,
        String heroName,
        QuestStatus status
) {
}
