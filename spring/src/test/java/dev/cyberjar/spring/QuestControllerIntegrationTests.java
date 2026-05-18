package dev.cyberjar.spring;

import dev.cyberjar.spring.domain.Difficulty;
import dev.cyberjar.spring.domain.Quest;
import dev.cyberjar.spring.domain.QuestStatus;
import dev.cyberjar.spring.dto.AssignQuestRequest;
import dev.cyberjar.spring.dto.CreateQuestRequest;
import dev.cyberjar.spring.repository.QuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class QuestControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new Quest("Clear rats from the tavern cellar", Difficulty.EASY, 50, "Any"),
                new Quest("Recover the cursed JVM artifact", Difficulty.HARD, 500, "Wizard"),
                new Quest("Defeat the production incident dragon", Difficulty.BOSS, 1000, "Senior Engineer")
        ));
    }

    @Test
    void searchesQuestsByDifficultyAndRequiredClass() throws Exception {
        mockMvc.perform(get("/quests")
                        .param("difficulty", "HARD")
                        .param("requiredClass", "wizard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Recover the cursed JVM artifact"))
                .andExpect(jsonPath("$[0].difficulty").value("HARD"))
                .andExpect(jsonPath("$[0].requiredClass").value("Wizard"));
    }

    @Test
    void createsQuest() throws Exception {
        CreateQuestRequest request = new CreateQuestRequest(
                "Patch the prod goblin portal",
                Difficulty.MEDIUM,
                250,
                "Engineer"
        );

        mockMvc.perform(post("/quests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Patch the prod goblin portal"))
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.reward").value(250))
                .andExpect(jsonPath("$.requiredClass").value("Engineer"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void updatesQuest() throws Exception {
        Quest quest = repository.save(new Quest("Old title", Difficulty.EASY, 10, "Intern"));
        CreateQuestRequest request = new CreateQuestRequest(
                "Refactor the haunted monolith",
                Difficulty.HARD,
                750,
                "Architect"
        );

        mockMvc.perform(put("/quests/{id}", quest.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(quest.getId()))
                .andExpect(jsonPath("$.title").value("Refactor the haunted monolith"))
                .andExpect(jsonPath("$.difficulty").value("HARD"))
                .andExpect(jsonPath("$.reward").value(750))
                .andExpect(jsonPath("$.requiredClass").value("Architect"));
    }

    @Test
    void deletesQuest() throws Exception {
        Quest quest = repository.save(new Quest("Temporary fetch quest", Difficulty.EASY, 25, "Any"));

        mockMvc.perform(delete("/quests/{id}", quest.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/quests/{id}", quest.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Quest not found: " + quest.getId()));
    }

    @Test
    void assignsOpenQuestAndRejectsSecondAssignment() throws Exception {
        Quest quest = repository.save(new Quest("Slay the flaky test dragon", Difficulty.BOSS, 1200, "QA Paladin"));
        AssignQuestRequest request = new AssignQuestRequest("Ada", "QA Paladin");

        mockMvc.perform(post("/quests/{id}/assign", quest.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questId").value(quest.getId()))
                .andExpect(jsonPath("$.heroName").value("Ada"))
                .andExpect(jsonPath("$.status").value(QuestStatus.ASSIGNED.name()));

        mockMvc.perform(post("/quests/{id}/assign", quest.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignQuestRequest("Grace", "QA Paladin"))))
                .andExpect(status().isConflict())
                .andExpect(content().string("Quest is already assigned: " + quest.getId()));
    }

    @Test
    void returnsNotFoundForUnknownQuest() throws Exception {
        mockMvc.perform(get("/quests/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Quest not found: 999999"));
    }

    @Test
    void rejectsInvalidCreateQuestRequest() throws Exception {
        CreateQuestRequest request = new CreateQuestRequest("", null, 0, "");

        mockMvc.perform(post("/quests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(
                        result.getResponse().getErrorMessage(),
                        containsString("Invalid request content")
                ));
    }

}
