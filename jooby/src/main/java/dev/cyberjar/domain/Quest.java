package dev.cyberjar.domain;

public record Quest(
        Long id,
        String title,
        Difficulty difficulty,
        int reward,
        String requiredClass,
        QuestStatus status,
        String assignedHero
) {
}
