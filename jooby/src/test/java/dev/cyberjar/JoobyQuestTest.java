package dev.cyberjar;

import dev.cyberjar.domain.Difficulty;
import dev.cyberjar.domain.Quest;
import dev.cyberjar.domain.QuestStatus;
import dev.cyberjar.dto.AssignQuestRequest;
import dev.cyberjar.dto.CreateQuestRequest;
import dev.cyberjar.exception.QuestAlreadyAssignedException;
import dev.cyberjar.exception.QuestNotFoundException;
import dev.cyberjar.repository.QuestRepository;
import dev.cyberjar.service.QuestService;
import io.jooby.Jooby;
import io.jooby.MediaType;
import io.jooby.StatusCode;
import io.jooby.hibernate.validator.HibernateValidatorModule;
import io.jooby.jackson3.Jackson3Module;
import io.jooby.test.JoobyTest;
import io.jooby.validation.BeanValidator;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@JoobyTest(
        value = JoobyQuestTest.TestApp.class,
        port = 0
)
public class JoobyQuestTest {

    private static final InMemoryQuestRepository repository = new InMemoryQuestRepository();

    @BeforeEach
    void setUp() {
        repository.reset();
    }

    @Test
    void returnsHealth(String serverPath) {
        given()
                .baseUri(serverPath)
                .when()
                .get("/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void searchesQuestsByDifficultyAndRequiredClass(String serverPath) {
        given()
                .baseUri(serverPath)
                .queryParam("difficulty", "HARD")
                .queryParam("requiredClass", "wizard")
                .when()
                .get("/quests")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].title", equalTo("Recover the cursed JVM artifact"))
                .body("[0].difficulty", equalTo("HARD"))
                .body("[0].reward", equalTo(500))
                .body("[0].requiredClass", equalTo("Wizard"))
                .body("[0].status", equalTo("OPEN"));
    }

    @Test
    void createsQuest(String serverPath) {
        given()
                .baseUri(serverPath)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "title", "Patch the prod goblin portal",
                        "difficulty", "MEDIUM",
                        "reward", 250,
                        "requiredClass", "Engineer"
                ))
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
    void updatesQuest(String serverPath) {
        long questId = repository.create(
                "Old title",
                Difficulty.EASY,
                10,
                "Intern"
        ).id();

        given()
                .baseUri(serverPath)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "title", "Refactor the haunted monolith",
                        "difficulty", "HARD",
                        "reward", 750,
                        "requiredClass", "Architect"
                ))
                .when()
                .put("/quests/{id}", questId)
                .then()
                .statusCode(200)
                .body("id", equalTo((int) questId))
                .body("title", equalTo("Refactor the haunted monolith"))
                .body("difficulty", equalTo("HARD"))
                .body("reward", equalTo(750))
                .body("requiredClass", equalTo("Architect"))
                .body("status", equalTo("OPEN"));
    }

    @Test
    void deletesQuest(String serverPath) {
        long questId = repository.create(
                "Temporary fetch quest",
                Difficulty.EASY,
                25,
                "Any"
        ).id();

        given()
                .baseUri(serverPath)
                .when()
                .delete("/quests/{id}", questId)
                .then()
                .statusCode(204);

        given()
                .baseUri(serverPath)
                .when()
                .get("/quests/{id}", questId)
                .then()
                .statusCode(404)
                .body(equalTo("Quest not found: " + questId));
    }

    @Test
    void assignsOpenQuestAndRejectsSecondAssignment(String serverPath) {
        long questId = repository.create(
                "Slay the flaky test dragon",
                Difficulty.BOSS,
                1200,
                "QA Paladin"
        ).id();

        given()
                .baseUri(serverPath)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "heroName", "Ada",
                        "heroClass", "QA Paladin"
                ))
                .when()
                .post("/quests/{id}/assign", questId)
                .then()
                .statusCode(200)
                .body("questId", equalTo((int) questId))
                .body("heroName", equalTo("Ada"))
                .body("status", equalTo("ASSIGNED"));

        given()
                .baseUri(serverPath)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "heroName", "Grace",
                        "heroClass", "QA Paladin"
                ))
                .when()
                .post("/quests/{id}/assign", questId)
                .then()
                .statusCode(409)
                .body(equalTo("Quest is already assigned: " + questId));
    }

    @Test
    void returnsNotFoundForUnknownQuest(String serverPath) {
        given()
                .baseUri(serverPath)
                .when()
                .get("/quests/{id}", 999_999L)
                .then()
                .statusCode(404)
                .body(equalTo("Quest not found: 999999"));
    }

    @Test
    void rejectsInvalidCreateQuestRequest(String serverPath) {
        given()
                .baseUri(serverPath)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "title", "",
                        "reward", 0,
                        "requiredClass", ""
                ))
                .when()
                .post("/quests")
                .then()
                .statusCode(400)
                .body(containsString("must"));
    }

    public static class TestApp extends Jooby {

        {
            install(new Jackson3Module());
            install(new HibernateValidatorModule().statusCode(StatusCode.BAD_REQUEST));

            errorCode(QuestNotFoundException.class, StatusCode.NOT_FOUND);
            errorCode(QuestAlreadyAssignedException.class, StatusCode.CONFLICT);

            error(QuestNotFoundException.class, (ctx, cause, statusCode) -> {
                ctx.setResponseCode(statusCode);
                ctx.setResponseType(MediaType.TEXT);
                ctx.send(cause.getMessage());
            });

            error(QuestAlreadyAssignedException.class, (ctx, cause, statusCode) -> {
                ctx.setResponseCode(statusCode);
                ctx.setResponseType(MediaType.TEXT);
                ctx.send(cause.getMessage());
            });

            use(BeanValidator.validate());

            var service = new QuestService(repository, getLog());

            get("/health", ctx -> Map.of("status", "UP"));

            get("/quests/{id}", ctx -> service.findById(ctx.path("id").longValue()));

            get("/quests", ctx -> service.search(
                    ctx.query("difficulty").toOptional(Difficulty.class).orElse(null),
                    ctx.query("requiredClass").toOptional().orElse(null)
            ));

            post("/quests", ctx -> {
                var request = ctx.body(CreateQuestRequest.class);
                ctx.setResponseCode(StatusCode.CREATED);
                return service.create(request);
            });

            put("/quests/{id}", ctx -> service.update(
                    ctx.path("id").longValue(),
                    ctx.body(CreateQuestRequest.class)
            ));

            delete("/quests/{id}", ctx -> {
                service.delete(ctx.path("id").longValue());
                ctx.setResponseCode(StatusCode.NO_CONTENT);
                return ctx;
            });

            post("/quests/{id}/assign", ctx -> service.assign(
                    ctx.path("id").longValue(),
                    ctx.body(AssignQuestRequest.class)
            ));
        }
    }

    private static final class InMemoryQuestRepository extends QuestRepository {

        private final Map<Long, Quest> quests = new LinkedHashMap<>();
        private long nextId = 1;

        private InMemoryQuestRepository() {
            super(null);
        }

        void reset() {
            quests.clear();
            nextId = 1;

            create("Clear rats from the tavern cellar", Difficulty.EASY, 50, "Any");
            create("Recover the cursed JVM artifact", Difficulty.HARD, 500, "Wizard");
            create("Defeat the production incident dragon", Difficulty.BOSS, 1000, "Senior Engineer");
        }

        @Override
        public Optional<Quest> findById(Long id) {
            return Optional.ofNullable(quests.get(id));
        }

        @Override
        public List<Quest> search(Difficulty difficulty, String requiredClass) {
            return quests.values()
                    .stream()
                    .filter(quest -> difficulty == null || quest.difficulty() == difficulty)
                    .filter(quest -> requiredClass == null
                            || quest.requiredClass().equalsIgnoreCase(requiredClass))
                    .sorted(Comparator.comparingLong(Quest::id))
                    .toList();
        }

        @Override
        public Quest create(String title, Difficulty difficulty, int reward, String requiredClass) {
            Quest quest = new Quest(
                    nextId++,
                    title,
                    difficulty,
                    reward,
                    requiredClass,
                    QuestStatus.OPEN,
                    null
            );

            quests.put(quest.id(), quest);
            return quest;
        }

        @Override
        public Quest update(Long id, String title, Difficulty difficulty, int reward, String requiredClass) {
            Quest current = findById(id)
                    .orElseThrow(() -> new QuestNotFoundException(id));

            Quest updated = new Quest(
                    current.id(),
                    title,
                    difficulty,
                    reward,
                    requiredClass,
                    current.status(),
                    current.assignedHero()
            );

            quests.put(id, updated);
            return updated;
        }

        @Override
        public void delete(Long id) {
            Quest removed = quests.remove(id);

            if (removed == null) {
                throw new QuestNotFoundException(id);
            }
        }

        @Override
        public Quest assign(Long id, String heroName) {
            Quest current = findById(id)
                    .orElseThrow(() -> new QuestNotFoundException(id));

            if (current.status() != QuestStatus.OPEN) {
                throw new QuestAlreadyAssignedException(id);
            }

            Quest assigned = new Quest(
                    current.id(),
                    current.title(),
                    current.difficulty(),
                    current.reward(),
                    current.requiredClass(),
                    QuestStatus.ASSIGNED,
                    heroName
            );

            quests.put(id, assigned);
            return assigned;
        }
    }
}