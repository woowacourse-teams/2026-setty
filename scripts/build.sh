#!/usr/bin/env bash
set -euo pipefail

APP_USER=ubuntu
SOURCE_DIR=/opt/setty/source/server
APP_DIR=/opt/setty/app
NEXT_JAR="$APP_DIR/setty.jar.next"
APP_HOME="$(getent passwd "$APP_USER" | cut -d: -f6)"

if [[ ! -x "$SOURCE_DIR/gradlew" ]]; then
    chmod 0755 "$SOURCE_DIR/gradlew"
fi

if [[ ! -f "$SOURCE_DIR/build.gradle" && ! -f "$SOURCE_DIR/build.gradle.kts" ]]; then
    echo "Gradle 프로젝트를 찾을 수 없습니다: $SOURCE_DIR"
    exit 1
fi

install -d -o "$APP_USER" -g "$APP_USER" -m 0755 "$APP_DIR"
chown -R "$APP_USER:$APP_USER" "$SOURCE_DIR"

echo "Spring Boot JAR 빌드 시작"
runuser -u "$APP_USER" -- env \
    HOME="$APP_HOME" \
    GRADLE_USER_HOME="$APP_HOME/.gradle" \
    GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx512m" \
    "$SOURCE_DIR/gradlew" \
    -p "$SOURCE_DIR" \
    --no-daemon \
    --max-workers=1 \
    clean bootJar \
    -x test

mapfile -d '' -t JAR_FILES < <(
    find "$SOURCE_DIR/build/libs" \
        -maxdepth 1 \
        -type f \
        -name '*.jar' \
        ! -name '*-plain.jar' \
        -print0
)

if [[ "${#JAR_FILES[@]}" -ne 1 ]]; then
    echo "배포할 Spring Boot JAR은 정확히 1개여야 합니다. 발견: ${#JAR_FILES[@]}"
    printf ' - %s\n' "${JAR_FILES[@]:-없음}"
    exit 1
fi

install -o "$APP_USER" -g "$APP_USER" -m 0644 "${JAR_FILES[0]}" "$NEXT_JAR"
echo "빌드 완료: $NEXT_JAR"
