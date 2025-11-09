# --- Build stage ---
FROM eclipse-temurin:24-jdk AS build
WORKDIR /app

# Gradle 래퍼 먼저 복사 (캐시 최적화)
COPY gradlew gradle /app/
COPY gradle/wrapper /app/gradle/wrapper
RUN chmod +x gradlew

# 프로젝트 메타 먼저 복사 (캐시 최적화)
COPY settings.gradle settings.gradle.kts* build.gradle build.gradle.kts* gradle.properties* /app/

# 래퍼/플러그인 다운로드만 먼저 수행해 캐시
RUN ./gradlew --no-daemon --version

# 나머지 소스 복사 후 빌드
COPY . /app
RUN chmod +x gradlew
RUN ./gradlew clean bootJar -x test --no-daemon

# --- Run stage ---
FROM eclipse-temurin:24-jre
WORKDIR /app
# 생성된 jar 경로에 맞게 조정하세요
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
