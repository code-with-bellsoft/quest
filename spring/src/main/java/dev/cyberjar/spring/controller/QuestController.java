package dev.cyberjar.spring.controller;

import dev.cyberjar.spring.dto.AssignQuestRequest;
import dev.cyberjar.spring.dto.AssignQuestResponse;
import dev.cyberjar.spring.dto.CreateQuestRequest;
import dev.cyberjar.spring.service.QuestService;
import dev.cyberjar.spring.domain.Difficulty;
import dev.cyberjar.spring.domain.Quest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/quests")
public class QuestController {

    private final QuestService service;

    public QuestController(QuestService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public Quest findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    public List<Quest> search(
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String requiredClass
    ) {
        return service.search(difficulty, requiredClass);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Quest create(@Valid @RequestBody CreateQuestRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public Quest update(
            @PathVariable Long id,
            @Valid @RequestBody CreateQuestRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign")
    public AssignQuestResponse assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignQuestRequest request
    ) {
        return service.assign(id, request);
    }


}
