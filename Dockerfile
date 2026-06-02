# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Install bash (required by Gradle wrapper)
RUN apk add --no-cache bash

# Copy Gradle wrapper + version catalog first → Docker layer cache hit on re-builds
COPY gradlew ./
COPY gradle/ gradle/
RUN sed -i 's/\r//' gradlew && chmod +x gradlew

# Minimal root build.gradle.kts — no Android/KMP plugins to resolve, only :server is built
RUN echo "" > build.gradle.kts

# Override settings.gradle.kts to include ONLY :server (avoids resolving Android SDK)
# Pin Kotlin version explicitly so kotlin("jvm") resolves without the root build.gradle.kts
RUN printf 'pluginManagement {\n\
    plugins {\n\
        kotlin("jvm") version "2.0.21"\n\
        kotlin("plugin.serialization") version "2.0.21"\n\
    }\n\
    repositories {\n\
        mavenCentral()\n\
        gradlePluginPortal()\n\
    }\n\
}\n\
dependencyResolutionManagement {\n\
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n\
    repositories { mavenCentral() }\n\
}\n\
rootProject.name = "vtempe-server"\n\
include(":server")\n' > settings.gradle.kts

# Copy server source
COPY server/ server/

# Build runnable distribution (bin/server script + all JARs under lib/)
RUN ./gradlew :server:installDist --no-daemon -q \
    && rm -rf server/build/tmp

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache tzdata

WORKDIR /app
COPY --from=builder /build/server/build/install/server .

# Cloud Run injects PORT env var; our Application.kt reads it (default 8080)
EXPOSE 8080
ENTRYPOINT ["bin/server"]
