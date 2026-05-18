package dev.cyberjar.spring.event;

public record QuestAssignedEvent(
        Long questId,
        String heroName,
        String heroClass
) {
}
