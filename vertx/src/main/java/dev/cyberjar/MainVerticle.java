package dev.cyberjar;

import dev.cyberjar.config.AppConfig;
import dev.cyberjar.repository.QuestRepository;
import dev.cyberjar.service.QuestService;
import dev.cyberjar.exception.ErrorHandler;
import dev.cyberjar.web.QuestRoutes;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.healthchecks.HealthCheckHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class MainVerticle extends VerticleBase {

    private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public Future<?> start() {
        return loadConfig()
                .map(AppConfig::from)
                .compose(config -> runFlyway(config)
                        .compose(ignored -> startHttpServer(config)));
    }

    private Future<JsonObject> loadConfig() {
        String env = System.getenv().getOrDefault("APP_ENV", "dev");
        String path = env.equals("prod") ? "application-prod.json" : "application.json";

        ConfigStoreOptions fileStore = new ConfigStoreOptions()
                .setType("file")
                .setConfig(new JsonObject().put("path", path));

        return ConfigRetriever
                .create(vertx, new ConfigRetrieverOptions().addStore(fileStore))
                .getConfig();
    }

    private Future<Void> runFlyway(AppConfig config) {
        return vertx.executeBlocking(() -> {
            Flyway.configure()
                    .dataSource(
                            config.flywayUrl(),
                            config.dbUser(),
                            config.dbPassword()
                    )
                    .locations(config.flywayLocations())
                    .load()
                    .migrate();
            return null;
        });
    }

    private Future<?> startHttpServer(AppConfig config) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(config.dbHost())
                .setPort(config.dbPort())
                .setDatabase(config.dbName())
                .setUser(config.dbUser())
                .setPassword(config.dbPassword());

        PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(config.dbPoolSize());

        Pool pool = PgBuilder.pool()
                .connectingTo(connectOptions)
                .with(poolOptions)
                .using(vertx)
                .build();

        QuestRepository repository = new QuestRepository(pool);
        QuestService service = new QuestService(repository, vertx.eventBus());

        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());

        HealthChecks healthChecks = HealthChecks.create(vertx);
        healthChecks.register("postgres", promise ->
                pool.query("select 1").execute()
                        .onSuccess(ok -> promise.complete())
                        .onFailure(promise::fail)
        );

        router.get("/health").handler(HealthCheckHandler.createWithHealthChecks(healthChecks));

        ErrorHandler.install(router);
        QuestRoutes.mount(router, service);

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(config.httpPort()).onSuccess(server -> {
                    long startedAtNanos = config()
                            .getLong("startedAtNanos", System.nanoTime());

                    long startupMillis = TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startedAtNanos
                    );

                    log.info("Vert.x quest service started on port {} in {} ms",
                            server.actualPort(),
                            startupMillis);
                });
    }
}