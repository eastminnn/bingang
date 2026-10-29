# 1) 빌드: JDK로 jar를 만든다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# 의존성 정의만 먼저 복사해서, 코드만 바뀌었을 때는 의존성 다운로드를 캐시에서 재사용
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon > /dev/null
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# 2) 실행: JRE만 있는 작은 이미지에 jar만 옮긴다
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
