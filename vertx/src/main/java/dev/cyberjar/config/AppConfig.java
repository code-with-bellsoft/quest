package dev.cyberjar.config;

import io.vertx.core.json.JsonObject;

public record AppConfig(
        int httpPort,
        String dbHost,
        int dbPort,
        String dbName,
        String dbUser,
        String dbPassword,
        int dbPoolSize,
        String flywayUrl,
        String flywayLocations
) {
    public static AppConfig from(JsonObject json) {
        JsonObject http = json.getJsonObject("http");
        JsonObject db = json.getJsonObject("db");
        JsonObject flyway = json.getJsonObject("flyway");

        return new AppConfig(
                http.getInteger("port", 8080),
                db.getString("host"),
                db.getInteger("port", 5432),
                db.getString("database"),
                db.getString("user"),
                db.getString("password"),
                db.getInteger("poolSize", 5),
                flyway.getString("url"),
                flyway.getString("locations", "classpath:db/migration")
        );
    }
}
