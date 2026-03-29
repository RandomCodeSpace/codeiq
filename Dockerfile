# Multi-stage build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B

# Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /build/target/code-iq-*.jar app.jar

# Training run for AOT cache
RUN java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar app.jar || true

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-XX:+UseZGC", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
