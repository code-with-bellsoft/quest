FROM bellsoft/hardened-liberica-runtime-container:jdk-25-musl AS builder
WORKDIR /app
COPY . /app/quarkus
RUN cd quarkus && ./mvnw -Dmaven.test.skip=true clean package

FROM bellsoft/hardened-liberica-runtime-container:jre-25-musl
WORKDIR /app
COPY --from=builder /app/quarkus/target/quarkus-app/ /app/
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]