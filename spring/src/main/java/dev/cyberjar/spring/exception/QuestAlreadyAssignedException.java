package dev.cyberjar.spring.exception;

public class QuestAlreadyAssignedException extends RuntimeException {

    public QuestAlreadyAssignedException(Long id) {
        super("Quest is already assigned: " + id);
    }
}
