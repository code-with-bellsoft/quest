FROM bellsoft/hardened-liberica-runtime-container:jdk-25-musl AS builder
WORKDIR /app
COPY . /app/jooby
RUN cd jooby && ./mvnw -Dmaven.test.skip=true clean package

FROM bellsoft/hardened-liberica-runtime-container:jre-25-musl
WORKDIR /app
COPY --from=builder /app/jooby/target/jooby-1.0-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar", "application.env=prod"]