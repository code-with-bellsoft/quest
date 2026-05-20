package dev.cyberjar.service;

import dev.cyberjar.domain.Difficulty;
import dev.cyberjar.domain.Quest;
import dev.cyberjar.dto.AssignQuestRequest;
import dev.cyberjar.dto.AssignQuestResponse;
import dev.cyberjar.dto.CreateQuestRequest;
import dev.cyberjar.exception.QuestNotFoundException;
import dev.cyberjar.repository.QuestRepository;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class QuestService {

    private final QuestRepository repository;
    private final Logger log;

    public QuestService(QuestRepository repository, Logger log) {
        this.repository = repository;
        this.log = log;
    }

    public Quest findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new QuestNotFoundException(id));
    }

    public List<Quest> search(Difficulty difficulty, String requiredClass) {
        return repository.search(difficulty, requiredClass);
    }

    public Quest create(CreateQuestRequest request) {
        return repository.create(
                request.title(),
                request.difficulty(),
                request.reward(),
                request.requiredClass()
        );
    }

    public Quest update(Long id, CreateQuestRequest request) {
        return repository.update(
                id,
                request.title(),
                request.difficulty(),
                request.reward(),
                request.requiredClass()
        );
    }

    public void delete(Long id) {
        repository.delete(id);
    }

    public AssignQuestResponse assign(Long id, AssignQuestRequest request) {
        Quest quest = repository.assign(id, request.heroName());

        CompletableFuture.runAsync(() -> log.info(
                "Quest {} assigned to {} ({})",
                quest.id(),
                request.heroName(),
                request.heroClass()
        ));

        return new AssignQuestResponse(
                quest.id(),
                request.heroName(),
                quest.status()
        );
    }

}
