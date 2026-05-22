FROM bellsoft/hardened-liberica-runtime-container:jdk-25-musl AS builder
WORKDIR /app
COPY . /app/vertx
RUN cd vertx && ./mvnw -Dmaven.test.skip=true clean package

FROM bellsoft/hardened-liberica-runtime-container:jre-25-musl
WORKDIR /app
COPY --from=builder /app/vertx/target/vertx-1.0-SNAPSHOT-fat.jar /app/app.jar
ENV APP_ENV=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]