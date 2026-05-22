package dev.cyberjar.web;

import dev.cyberjar.domain.Difficulty;
import dev.cyberjar.dto.AssignQuestRequest;
import dev.cyberjar.dto.CreateQuestRequest;
import dev.cyberjar.service.QuestService;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;

public final class QuestRoutes {

    private QuestRoutes() {}

    public static void mount(Router router, QuestService service) {
        var validator = new ValidationSupport();

        router.get("/quests/:id").handler(ctx -> {
            Long id = Long.valueOf(ctx.pathParam("id"));

            service.findById(id)
                    .onSuccess(ctx::json)
                    .onFailure(ctx::fail);
        });

        router.get("/quests").handler(ctx -> {
            Difficulty difficulty = null;
            String difficultyParam = ctx.queryParam("difficulty").stream().findFirst().orElse(null);

            if (difficultyParam != null) {
                difficulty = Difficulty.valueOf(difficultyParam);
            }

            String requiredClass = ctx.queryParam("requiredClass").stream().findFirst().orElse(null);

            service.search(difficulty, requiredClass)
                    .onSuccess(ctx::json)
                    .onFailure(ctx::fail);
        });

        router.post("/quests").handler(ctx -> {
            CreateQuestRequest request = Json.decodeValue(ctx.body().asString(), CreateQuestRequest.class);
            validator.validate(request);

            service.create(request)
                    .onSuccess(quest -> ctx.response()
                            .setStatusCode(201)
                            .putHeader("content-type", "application/json")
                            .end(Json.encode(quest)))
                    .onFailure(ctx::fail);
        });

        router.put("/quests/:id").handler(ctx -> {
            Long id = Long.valueOf(ctx.pathParam("id"));
            CreateQuestRequest request = Json.decodeValue(ctx.body().asString(), CreateQuestRequest.class);
            validator.validate(request);

            service.update(id, request)
                    .onSuccess(ctx::json)
                    .onFailure(ctx::fail);
        });

        router.delete("/quests/:id").handler(ctx -> {
            Long id = Long.valueOf(ctx.pathParam("id"));

            service.delete(id)
                    .onSuccess(ignored -> ctx.response().setStatusCode(204).end())
                    .onFailure(ctx::fail);
        });

        router.post("/quests/:id/assign").handler(ctx -> {
            Long id = Long.valueOf(ctx.pathParam("id"));
            AssignQuestRequest request = Json.decodeValue(ctx.body().asString(), AssignQuestRequest.class);
            validator.validate(request);

            service.assign(id, request)
                    .onSuccess(ctx::json)
                    .onFailure(ctx::fail);
        });
    }
}