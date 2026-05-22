package dev.cyberjar.service;

import dev.cyberjar.domain.Difficulty;
import dev.cyberjar.domain.Quest;
import dev.cyberjar.dto.AssignQuestRequest;
import dev.cyberjar.dto.AssignQuestResponse;
import dev.cyberjar.dto.CreateQuestRequest;
import dev.cyberjar.exception.QuestNotFoundException;
import dev.cyberjar.repository.QuestRepository;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

import java.util.List;

public class QuestService {

    private final QuestRepository repository;
    private final EventBus eventBus;

    public QuestService(QuestRepository repository, EventBus eventBus) {
        this.repository = repository;
        this.eventBus = eventBus;
    }

    public Future<Quest> findById(Long id) {
        return repository.findById(id)
                .map(quest -> {
                    if (quest == null) {
                        throw new QuestNotFoundException(id);
                    }
                    return quest;
                });
    }

    public Future<List<Quest>> search(Difficulty difficulty, String requiredClass) {
        return repository.search(difficulty, requiredClass);
    }

    public Future<Quest> create(CreateQuestRequest request) {
        return repository.create(
                request.title(),
                request.difficulty(),
                request.reward(),
                request.requiredClass()
        );
    }

    public Future<Quest> update(Long id, CreateQuestRequest request) {
        return repository.update(
                id,
                request.title(),
                request.difficulty(),
                request.reward(),
                request.requiredClass()
        );
    }

    public Future<Void> delete(Long id) {
        return repository.delete(id);
    }

    public Future<AssignQuestResponse> assign(Long id, AssignQuestRequest request) {
        return repository.assign(id, request.heroName())
                .map(quest -> {
                    eventBus.publish("quest.assigned", new JsonObject()
                            .put("questId", quest.id())
                            .put("heroName", request.heroName())
                            .put("heroClass", request.heroClass()));

                    return new AssignQuestResponse(
                            quest.id(),
                            request.heroName(),
                            quest.status()
                    );
                });
    }
}