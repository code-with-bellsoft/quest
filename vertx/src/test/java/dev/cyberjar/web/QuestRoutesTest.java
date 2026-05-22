package dev.cyberjar.web;

import dev.cyberjar.exception.ErrorHandler;
import dev.cyberjar.repository.QuestRepository;
import dev.cyberjar.service.QuestService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.healthchecks.HealthCheckHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;


class QuestRoutesTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quest_board")
            .withUsername("quest_user")
            .withPassword("quest_pass");

    private static Vertx vertx;
    private static Pool pool;

    @BeforeAll
    static void startApplication() throws Exception {
        POSTGRES.start();

        waitForPostgres();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        vertx = Vertx.vertx();
        pool = PgBuilder.pool()
                .connectingTo(new PgConnectOptions()
                        .setHost(POSTGRES.getHost())
                        .setPort(POSTGRES.getMappedPort(5432))
                        .setDatabase(POSTGRES.getDatabaseName())
                        .setUser(POSTGRES.getUsername())
                        .setPassword(POSTGRES.getPassword()))
                .with(new PoolOptions().setMaxSize(5))
                .using(vertx)
                .build();

        QuestRepository repository = new QuestRepository(pool);
        QuestService service = new QuestService(repository, vertx.eventBus());

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        HealthChecks healthChecks = HealthChecks.create(vertx);
        healthChecks.register("postgres", promise -> pool.query("select 1").execute()
                .onSuccess(ok -> promise.complete())
                .onFailure(promise::fail));
        router.get("/health").handler(HealthCheckHandler.createWithHealthChecks(healthChecks));

        ErrorHandler.install(router);
        QuestRoutes.mount(router, service);

        int port = await(vertx.createHttpServer()
                .requestHandler(router)
                .listen(0))
                .actualPort();

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    private static void waitForPostgres() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        SQLException lastException = null;

        while (System.nanoTime() < deadline) {
            try (Connection ignored = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(),
                    POSTGRES.getPassword()
            )) {
                return;
            } catch (SQLException e) {
                lastException = e;
                Thread.sleep(250);
            }
        }

        throw new IllegalStateException(
                "PostgreSQL container did not become reachable at " + POSTGRES.getJdbcUrl(),
                lastException
        );
    }

    @AfterAll
    static void stopApplication() throws Exception {
        if (pool != null) {
            pool.close();
        }
        if (vertx != null) {
            await(vertx.close());
        }
        POSTGRES.stop();
        RestAssured.reset();
    }

    @BeforeEach
    void setUp() throws Exception {
        await(pool.query("truncate table quests restart identity").execute());
        await(pool.query("""
                insert into quests (title, difficulty, reward, required_class)
                values
                  ('Clear rats from the tavern cellar', 'EASY', 50, 'Any'),
                  ('Recover the cursed JVM artifact', 'HARD', 500, 'Wizard'),
                  ('Defeat the production incident dragon', 'BOSS', 1000, 'Senior Engineer')
                """).execute());
    }

    @Test
    void returnsHealth() {
        given()
                .when()
                .get("/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
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
                .body("[0].reward", equalTo(500))
                .body("[0].requiredClass", equalTo("Wizard"))
                .body("[0].status", equalTo("OPEN"));
    }


    @Test
    void createsQuest() {
        given()
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
    void updatesQuest() throws Exception {
        long questId = persistQuest("Old title", "EASY", 10, "Intern");

        given()
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
    void deletesQuest() throws Exception {
        long questId = persistQuest("Temporary fetch quest", "EASY", 25, "Any");

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
    void assignsOpenQuestAndRejectsSecondAssignment() throws Exception {
        long questId = persistQuest(
                "Slay the flaky test dragon",
                "BOSS",
                1200,
                "QA Paladin"
        );

        given()
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
    void returnsNotFoundForUnknownQuest() {
        given()
                .when()
                .get("/quests/{id}", 999_999L)
                .then()
                .statusCode(404)
                .body(equalTo("Quest not found: 999999"));
    }

    @Test
    void rejectsInvalidCreateQuestRequest() {
        given()
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

    private long persistQuest(String title, String difficulty, int reward, String requiredClass) throws Exception {
        return await(pool.preparedQuery("""
                        insert into quests (title, difficulty, reward, required_class)
                        values ($1, $2, $3, $4)
                        returning id
                        """)
                .execute(Tuple.of(title, difficulty, reward, requiredClass)))
                .iterator()
                .next()
                .getLong("id");
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage()
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS);
    }
}
