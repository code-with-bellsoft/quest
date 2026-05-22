package dev.cyberjar;


import dev.cyberjar.domain.Difficulty;
import dev.cyberjar.dto.AssignQuestRequest;
import dev.cyberjar.dto.CreateQuestRequest;
import dev.cyberjar.exception.QuestAlreadyAssignedException;
import dev.cyberjar.exception.QuestNotFoundException;
import dev.cyberjar.repository.QuestRepository;
import dev.cyberjar.service.QuestService;
import io.jooby.Jooby;
import io.jooby.MediaType;
import io.jooby.StatusCode;
import io.jooby.flyway.FlywayModule;
import io.jooby.hibernate.validator.HibernateValidatorModule;
import io.jooby.hikari.HikariModule;
import io.jooby.jackson3.Jackson3Module;
import io.jooby.jdbi.JdbiModule;
import io.jooby.netty.NettyServer;
import io.jooby.validation.BeanValidator;
import org.jdbi.v3.core.Jdbi;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class JoobyQuest extends Jooby {

    private static volatile long startedAtNanos;

    {
        install(new Jackson3Module());
        install(new HibernateValidatorModule().statusCode(StatusCode.BAD_REQUEST));
        install(new HikariModule());
        install(new FlywayModule());
        install(new JdbiModule());

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

        /*

        Can simply do

        errorCode(QuestNotFoundException.class, StatusCode.NOT_FOUND);
        errorCode(QuestAlreadyAssignedException.class, StatusCode.CONFLICT);

         */

        use(BeanValidator.validate());

        var jdbi = require(Jdbi.class);
        var repository = new QuestRepository(jdbi);
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
        onStarted(() -> {
            long startupMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAtNanos
            );

            getLog().info("Jooby quest service started in {} ms", startupMillis);
        });
    }

    public static void main(final String[] args) {

        startedAtNanos = System.nanoTime();

        runApp(args, new NettyServer(), JoobyQuest::new);

    }
}