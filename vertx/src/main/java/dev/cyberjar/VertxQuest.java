package dev.cyberjar;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class VertxQuest {

    public static void main(String[] args) {

        long startedAtNanos = System.nanoTime();

        Vertx vertx = Vertx.vertx();

        DeploymentOptions options = new DeploymentOptions()
                .setConfig(new JsonObject()
                        .put("startedAtNanos", startedAtNanos));

        vertx.deployVerticle(new MainVerticle(), options)
                .onFailure(error -> {
                    error.printStackTrace();
                    vertx.close();
                });
    }

}
