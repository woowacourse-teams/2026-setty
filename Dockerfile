# 배포 전용 런타임 이미지.
# 빌드 컨텍스트 최상단에 CodeBuild가 만든 app.jar가 있어야 한다.
# 레포에는 app.jar가 없으므로 이 Dockerfile은 CodeDeploy 번들 안에서만 빌드된다.
FROM eclipse-temurin:21-jre

# 컨테이너 헬스체크에 필요하다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system setty && useradd --system --gid setty setty

WORKDIR /app
COPY app.jar app.jar
RUN chown -R setty:setty /app

USER setty
ENV TZ=Asia/Seoul
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
