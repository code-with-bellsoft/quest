package dev.cyberjar.exception;

import io.vertx.ext.web.Router;

public class ErrorHandler {

    private ErrorHandler() {}

    public static void install(Router router) {
        router.route().failureHandler(ctx -> {
            Throwable failure = ctx.failure();

            int status = switch (failure) {
                case QuestNotFoundException ignored -> 404;
                case QuestAlreadyAssignedException ignored -> 409;
                case IllegalArgumentException ignored -> 400;
                default -> 500;
            };

            String body = failure.getMessage() == null
                    ? "Internal server error"
                    : failure.getMessage();

            ctx.response()
                    .setStatusCode(status)
                    .putHeader("content-type", "text/plain")
                    .end(body);
        });
    }
}