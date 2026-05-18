FROM bellsoft/hardened-liberica-runtime-container:jdk-25-musl as builder
WORKDIR /app
COPY . /app/spring
RUN cd spring && ./mvnw -Dmaven.test.skip=true clean package

FROM bellsoft/hardened-liberica-runtime-container:jre-25-musl as optimizer
WORKDIR /app
COPY --from=builder /app/spring/target/spring-*.jar app.jar
RUN java -Djarmode=tools -jar /app/app.jar extract --layers --destination extracted

FROM bellsoft/hardened-liberica-runtime-container:jre-25-musl
WORKDIR /app
COPY --from=optimizer /app/extracted/dependencies/ ./
COPY --from=optimizer /app/extracted/spring-boot-loader/ ./
COPY --from=optimizer /app/extracted/snapshot-dependencies/ ./
COPY --from=optimizer /app/extracted/application/ ./
ENTRYPOINT ["java", "-jar", "/app/app.jar"]