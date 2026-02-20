# payment-service/Dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 빌드된 jar 파일을 컨테이너 안으로 복사
COPY build/libs/*.jar app.jar
# 실행 명령
ENTRYPOINT ["java", "-jar", "app.jar"]