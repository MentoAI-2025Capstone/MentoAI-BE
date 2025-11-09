# --- Build stage ---
FROM eclipse-temurin:24-jdk AS build
WORKDIR /app

# 래퍼 먼저 (캐시)
COPY gradlew gradle /app/
COPY gradle/wrapper /app/gradle/wrapper

# 래퍼/플러그인만 먼저 다운로드
RUN chmod +x gradlew && ./gradlew --no-daemon --version

# 나머지 소스
COPY . /app

# 윈도우 개행 방지 + 실행권한 재부여 (중요!)
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 빌드
RUN ./gradlew clean bootJar -x test --no-daemon

# --- Run stage ---
FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
