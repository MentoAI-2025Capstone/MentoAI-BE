FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew gradle /app/
COPY gradle/wrapper /app/gradle/wrapper
RUN chmod +x gradlew

COPY . /app
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
