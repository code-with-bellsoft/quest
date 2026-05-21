package dev.cyberjar;

import io.jooby.test.JoobyTest;
import io.restassured.http.ContentType;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;


@JoobyTest(value = JoobyQuest.class, port = 0)
class JoobyQuestTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("quest_board")
                    .withUsername("quest_user")
                    .withPassword("quest_pass");

    private static final Jdbi JDBI;

    static {
        POSTGRES.start();

        System.setProperty("db.url", POSTGRES.getJdbcUrl());
        System.setProperty("db.user", POSTGRES.getUsername());
        System.setProperty("db.password", POSTGRES.getPassword());

        System.setProperty("flyway.locations", "classpath:db/migration");
        System.setProperty("flyway.run", "migrate");

        JDBI = Jdbi.create(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();

        System.clearProperty("db.url");
        System.clearProperty("db.user");
        System.clearProperty("db.password");
        System.clearProperty("flyway.locations");
        System.clearProperty("flyway.run");
    }

    @BeforeEach
    void setUp() {
        JDBI.useHandle(handle -> {
            handle.execute("truncate table quests restart identity");

            handle.createUpdate("""
                    insert into quests (title, difficulty, reward, required_class)
                    values
                      ('Clear rats from the tavern cellar', 'EASY', 50, 'Any'),
                      ('Recover the cursed JVM artifact', 'HARD', 500, 'Wizard'),
                      ('Defeat the production incident dragon', 'BOSS', 1000, 'Senior Engineer')
                    """).execute();
        });
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
        long questId = persistQuest("Old title", "EASY", 10, "Intern");

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
        long questId = persistQuest("Temporary fetch quest", "EASY", 25, "Any");

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
        long questId = persistQuest(
                "Slay the flaky test dragon",
                "BOSS",
                1200,
                "QA Paladin"
        );

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


    private long persistQuest(String title, String difficulty, int reward, String requiredClass) {
        return JDBI.withHandle(handle -> handle
                .createQuery("""
                        insert into quests (title, difficulty, reward, required_class)
                        values (:title, :difficulty, :reward, :requiredClass)
                        returning id
                        """)
                .bind("title", title)
                .bind("difficulty", difficulty)
                .bind("reward", reward)
                .bind("requiredClass", requiredClass)
                .mapTo(Long.class)
                .one());
    }

}