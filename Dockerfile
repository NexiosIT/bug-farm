# Lunch app: builds the jar (backend + React screen) and runs it on a Java 21 JRE.
# All settings and secrets come from environment variables at run time (see .env.example).

# ---- build ----
FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
# The Node that Maven downloads for the frontend build needs libatomic.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src

# Dependencies first, so they stay cached while only source code changes.
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY contracts/pom.xml contracts/
COPY orders/pom.xml orders/
COPY admin/pom.xml admin/
COPY app/pom.xml app/
RUN sh mvnw -q -B dependency:go-offline -Dskip.npm -Dskip.installnodenpm

COPY contracts/ contracts/
COPY orders/ orders/
COPY admin/ admin/
COPY app/ app/
COPY frontend/ frontend/
# Maven downloads its own Node and builds the frontend into the jar (app/pom.xml).
RUN sh mvnw -q -B package -DskipTests

# ---- run ----
FROM eclipse-temurin:21.0.13_9-jre-noble
RUN useradd --system --create-home --home-dir /app --shell /usr/sbin/nologin lunch
WORKDIR /app
COPY --from=build --chown=lunch:lunch /src/app/target/lunch-app-0.0.1-SNAPSHOT.jar /app/app.jar
USER lunch
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
