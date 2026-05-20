package dev.cyberjar.controller;

import dev.cyberjar.domain.Difficulty;
import dev.cyberjar.domain.Quest;
import dev.cyberjar.domain.QuestStatus;
import dev.cyberjar.dto.AssignQuestRequest;
import dev.cyberjar.dto.CreateQuestRequest;
import dev.cyberjar.repository.QuestRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class QuestResourceTest {

    @Inject
    QuestRepository repository;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            repository.deleteAll();
            repository.persist(List.of(
                    new Quest("Clear rats from the tavern cellar", Difficulty.EASY, 50, "Any"),
                    new Quest("Recover the cursed JVM artifact", Difficulty.HARD, 500, "Wizard"),
                    new Quest("Defeat the production incident dragon", Difficulty.BOSS, 1000, "Senior Engineer")
            ));
        });
    }

    @Test
    void searchesQuestsByDifficultyAndRequiredClass() {
        given()
                .queryParam("difficulty", "HARD")
                .queryParam("requiredClass", "wizard")
                .when()
                .get("/quests")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].title", equalTo("Recover the cursed JVM artifact"))
                .body("[0].difficulty", equalTo("HARD"))
                .body("[0].requiredClass", equalTo("Wizard"));
    }

    @Test
    void createsQuest() {
        CreateQuestRequest request = new CreateQuestRequest(
                "Patch the prod goblin portal",
                Difficulty.MEDIUM,
                250,
                "Engineer"
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/quests")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("title", equalTo("Patch the prod goblin portal"))
                .body("difficulty", equalTo("MEDIUM"))
                .body("reward", equalTo(250))
                .body("requiredClass", equalTo("Engineer"))
                .body("status", equalTo("OPEN"));
    }


    @Test
    void updatesQuest() {
        Long questId = persistQuest(new Quest("Old title", Difficulty.EASY, 10, "Intern"));

        CreateQuestRequest request = new CreateQuestRequest(
                "Refactor the haunted monolith",
                Difficulty.HARD,
                750,
                "Architect"
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .put("/quests/{id}", questId)
                .then()
                .statusCode(200)
                .body("id", equalTo(questId.intValue()))
                .body("title", equalTo("Refactor the haunted monolith"))
                .body("difficulty", equalTo("HARD"))
                .body("reward", equalTo(750))
                .body("requiredClass", equalTo("Architect"));
    }


    @Test
    void deletesQuest() {
        Long questId = persistQuest(new Quest("Temporary fetch quest", Difficulty.EASY, 25, "Any"));

        given()
                .when()
                .delete("/quests/{id}", questId)
                .then()
                .statusCode(204);

        given()
                .when()
                .get("/quests/{id}", questId)
                .then()
                .statusCode(404)
                .body(equalTo("Quest not found: " + questId));
    }

    @Test
    void assignsOpenQuestAndRejectsSecondAssignment() {
        Long questId = persistQuest(new Quest(
                "Slay the flaky test dragon",
                Difficulty.BOSS,
                1200,
                "QA Paladin"
        ));

        AssignQuestRequest request = new AssignQuestRequest("Ada", "QA Paladin");

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/quests/{id}/assign", questId)
                .then()
                .statusCode(200)
                .body("questId", equalTo(questId.intValue()))
                .body("heroName", equalTo("Ada"))
                .body("status", equalTo(QuestStatus.ASSIGNED.name()));

        given()
                .contentType(ContentType.JSON)
                .body(new AssignQuestRequest("Grace", "QA Paladin"))
                .when()
                .post("/quests/{id}/assign", questId)
                .then()
                .statusCode(409)
                .body(equalTo("Quest is already assigned: " + questId));
    }


    private Long persistQuest(Quest quest) {
        return QuarkusTransaction.requiringNew().call(() -> {
            repository.persist(quest);
            repository.flush();
            return quest.getId();
        });
    }

}