# dev HTTP 요청 지표

Spring Boot Actuator의 `http.server.requests` 타이머를 OTLP로 내보낸다. dev 프로필에서만 1분마다 로컬 CloudWatch Agent(`127.0.0.1:4318`)로 전송하며, 다른 프로필에서는 OTLP 전송을 끈다. 기존 EC2 CPU 지표, `Setty/Dev` 메모리 지표, `/setty/dev/backend` 로그 수집은 별개로 유지한다.

HTTP 지표에는 HTTP 타이머와 `uri`, `method`, `outcome` 태그를 남긴다. 서비스/환경은 리소스 속성 `service.name=setty-backend`, `deployment.environment.name=dev`로 구분한다. 요청 수는 히스토그램의 count, 5xx는 `outcome=SERVER_ERROR`인 count, p95는 히스토그램의 95백분위수로 구한다. `/actuator/health` 같은 HTTP 요청도 합계에 포함된다. CloudWatch에서는 기존 **Classic metrics** 네임스페이스가 아니라 **Metrics → Query Studio**에서 OTLP 지표를 찾는다.

JVM Heap과 DB 커넥션 풀의 최소 게이지도 같은 전송 경로를 사용한다. 허용 지표와 조회 방법은 [dev 자원 지표](dev-resource-metrics.md)를 참고한다.

## 경로별 관측 범위

처음에는 `outcome`만 전송해 서버 전체 상태만 확인할 수 있었다. 어느 API가 느리거나 실패하는지 구분하기 위해 Spring HTTP 관측이 제공하는 매핑 경로(`uri`)와 HTTP 메서드(`method`)를 유지한다.

- `/api/listings/123`과 `/api/listings/456`은 `/api/listings/{listingId}` 하나로 집계한다. 같은 경로도 GET과 POST는 구분한다.
- 원본 URL, 쿼리 문자열, 사용자 ID, 요청 ID는 지표 라벨로 보내지 않는다. 상태 코드와 예외 이름도 추가하지 않고 기존 `outcome` 구분을 유지한다.
- 매핑되지 않은 요청은 Spring이 제공하는 `/**`, `NOT_FOUND`, `UNKNOWN` 등의 값으로 묶인다. 원본 요청 경로를 대체값으로 사용하지 않는다. 인증 단계에서 종료되는 등 매핑 경로가 정해지지 않은 요청은 API별로 구분되지 않을 수 있다.
- 전체 지표는 경로·메서드를 구분하지 않는 `sum`으로 계속 조회할 수 있다. API별 p95를 평균 내지 않고 히스토그램을 합친 뒤 p95를 계산한다.

전송량은 경로·메서드·결과 조합 수에 따라 늘어난다. 실제 ID별 시계열은 만들지 않으며, 1분 전송 주기는 유지한다. 실제 비용은 수집 용량으로 확인한다.

사용자 행동·전환을 보는 제품 분석과 서버 API 성능 관측은 별개다. 특정 요청의 원인 추적은 서버 로그와 `requestId`의 역할이며, 이 변경은 사용자별 추적이나 GA4 연동을 추가하지 않는다.

## 애플리케이션 설정과 검증

Spring Boot 4.1의 OTLP 자동 설정에는 `micrometer-registry-otlp`뿐 아니라 `spring-boot-opentelemetry`도 필요하다. 후자가 제공하는 `OpenTelemetryProperties` 클래스가 없으면 `enabled: true`여도 OTLP 전송기가 생성되지 않는다. `histogram-flavor`의 유효한 지수 히스토그램 값은 `base2-exponential-bucket-histogram`이다. `exponential-histogram`은 자동 설정이 활성화되면 설정 바인딩 오류로 부팅에 실패한다.

전송 단위는 `milliseconds`, 집계 방식은 `cumulative`로 명시한다. 요청 수·5xx·p95에 사용하지 않는 별도 `.max` 게이지는 전송하지 않는다. `DevHttpMetricsAutoConfigurationTest`는 실제 프로필 설정으로 dev 전송기 생성과 기본/local/prod 비활성화를 검사한다. `DevHttpMetricsHttpIntegrationTest`는 실제 HTTP 200·404·503 응답을 발생시키고 로컬 OTLP HTTP 수신기로 도착한 protobuf에서 다음을 확인한다.

- 지표 이름 `http.server.requests`, 단위 `milliseconds`, 누적 지수 히스토그램
- 다른 ID의 요청은 같은 매핑 경로로 합산하고, 다른 경로·메서드·결과는 분리하며 버킷의 요청 수를 보존
- 서비스/환경 리소스 속성과 `uri`, `method`, `outcome`만 남는 데이터 포인트 라벨
- 서로 다른 미등록 경로의 404 요청도 하나의 정적 리소스 매핑으로 집계
- 허용한 HTTP·Heap 지표만 전송하고, HTTP 데이터에 원본 URL, 쿼리 문자열, 상태 코드, 사용자·요청 ID가 포함되지 않음

## EC2에서 기존 Agent 설정에 수신기 추가

현재 EC2의 `/opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json`이 원본이다. 아래 작업은 그 파일에 OTLP HTTP 수신기만 추가한다. 백업은 잘못 수정했을 때 기존 로그·메모리 수집 설정을 복원하기 위한 것으로, 수집을 중단하거나 CloudWatch의 과거 데이터를 변경하지 않는다. EC2에 `jq`가 있어야 한다.

1. 기존 설정을 보고 백업한다.

   ```sh
   sudo cat /opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json
   sudo cp -p /opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json "/opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json.bak.$(date +%Y%m%d%H%M%S)"
   ```

2. 기존 JSON에 수신기 필드만 추가하고 검증한다.

   ```sh
   sudo jq '.opentelemetry.collect.otlp.http_endpoint = "127.0.0.1:4318"' /opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json | sudo tee /opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json.new >/dev/null
   sudo jq -e . /opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json.new >/dev/null
   sudo cat /opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json.new
   ```

   출력에서 기존 `agent`, `metrics`, `logs` 항목이 그대로 있는지 확인한 뒤 적용한다.

   ```sh
   sudo mv /opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json.new /opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json
   sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -c file:/opt/aws/amazon-cloudwatch-agent/etc/setty-dev.json -s
   sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a status
   ss -ltn | grep ':4318'
   ```

   설정 검증이 실패하면 백업 파일을 원래 경로에 복사하고 같은 `fetch-config` 명령을 다시 실행한다. 수신기가 작동하려면 Agent 버전이 1.300070.0 이상이어야 하며, 인스턴스 역할에 OTLP CloudWatch 전송 권한이 필요하다.

## 배포 후 확인

1. EC2 배포 JAR에 설정 클래스와 두 라이브러리가 모두 있는지 확인한다. `jar` 명령이 없는 JRE 환경에서는 `unzip`을 사용한다.

   ```sh
   unzip -l /opt/setty/app/app.jar | grep -E 'DevMetricsConfig|micrometer-registry-otlp|spring-boot-opentelemetry'
   sudo journalctl -u setty-backend.service --since "1 hour ago" --no-pager \
     | grep -Ei 'profile|OtlpMeterRegistry|publishing metrics|failed to publish|4318'
   ```

   dev 프로필 로그와 `Publishing metrics for OtlpMeterRegistry`의 대상 URL·리소스 속성을 확인한다. dev 프로필 로그만으로 전송기 생성까지 확인된 것은 아니다. 전송기 시작 로그도 CloudWatch 수신을 보장하지 않는다.

2. dev HTTP 요청을 발생시키고 최소 두 번 이상의 1분 전송 주기를 기다린다. CloudWatch **Metrics → Query Studio → Editor**에서 아래 쿼리로 해당 서비스의 수신 여부를 먼저 확인한다.

   ```promql
   {"http.server.requests","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev"}
   ```

   로컬 전송 테스트에서 확인한 이름은 `http.server.requests`이다. 다른 서비스의 `http_server_requests_seconds`를 SETTY 데이터로 판단하지 않는다. CloudWatch는 지표 이름 없는 라벨 전용 selector를 지원하지 않는다. 쿼리 오류와 정상 실행 후의 빈 결과는 구분한다.

3. 해당 데이터의 리소스 속성·단위를 확인한 뒤 아래 PromQL을 차례로 조회한다. 각각 최근 5분 요청 수·5xx 수·p95 지연시간(ms)이다. 이 쿼리의 AWS 실측 결과는 배포 후 별도로 검증해야 한다.

```promql
sum(histogram_count(increase({__name__="http.server.requests","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev"}[5m])))
sum(histogram_count(increase({__name__="http.server.requests",outcome="SERVER_ERROR","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev"}[5m])))
histogram_quantile(0.95, sum(rate({__name__="http.server.requests","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev"}[5m])))
```

`increase`는 샘플 사이의 증가량을 조회 구간에 맞춰 추정하므로 소수값이 나오거나 최초 샘플 이전 요청이 누락될 수 있다. 새 outcome 시계열도 최소 두 샘플이 필요하다. 5xx가 아직 발생하지 않아 `SERVER_ERROR` 시계열이 없으면 5xx 쿼리는 빈 결과를 낼 수 있다. 수집 장애를 숨기지 않도록 빈 결과를 무조건 0으로 바꾸지 않는다. p95는 히스토그램 근삿값이며 요청이 없는 구간은 비거나 `NaN`일 수 있다. 단위가 이미 ms이므로 1,000을 곱하지 않는다.

### API별 요청 수·5xx·p95

다음 쿼리는 각각 최근 5분 요청 수, 5xx 수, p95(ms)를 경로와 메서드별로 조회한다. `uri!=""`는 경로 태그가 없던 이전 배포의 시계열을 제외한다. 실제 CloudWatch 쿼리 결과는 배포 후 확인한다.

```promql
sum by (uri, method) (histogram_count(increase({"http.server.requests","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev",uri!=""}[5m])))
```

```promql
sum by (uri, method) (histogram_count(increase({"http.server.requests","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev",uri!="",outcome="SERVER_ERROR"}[5m])))
```

```promql
histogram_quantile(0.95, sum by (uri, method) (rate({"http.server.requests","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev",uri!=""}[5m])))
```

배포 직후에는 증가량 쿼리보다 아래 누적 count로 라벨과 수신을 먼저 확인한다. dev에서 서로 다른 매물 ID로 GET 요청을 보내고, `uri="/api/listings/{listingId}"`, `method="GET"`으로 묶이는지 확인한다. 요청 결과가 다르면 `outcome`은 별도로 나뉜다. `/error`의 테스트 500 응답은 `uri="/error"`, `method="GET"`, `outcome="SERVER_ERROR"`로 확인한다.

```promql
histogram_count({"http.server.requests","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev",uri!=""})
```

배포 전 지표에 경로를 소급해서 붙일 수는 없다. 배포 후 새 시계열이 만들어지고 누적 count가 새로 시작하므로 비교는 배포 이후 구간으로 제한한다. 위의 전체 집계 쿼리도 전환 구간에 이전·새 시계열이 섞일 수 있어 배포 이후 구간을 선택한다. 최근 5분 증가량·p95는 새 시계열이 충분히 수집된 뒤 확인한다.

데이터가 없을 때는 애플리케이션 전송 오류와 `/opt/aws/amazon-cloudwatch-agent/logs/amazon-cloudwatch-agent.log`의 OTLP 전송 오류를 확인한다. 애플리케이션에서 Agent로 보내는 구간과 Agent에서 AWS로 보내는 구간을 각각 확인해야 한다.

Agent의 로그·메모리 수집이 계속되는지, `Setty/Dev` 메모리 지표와 `/setty/dev/backend` 로그에도 새 데이터가 들어오는지 별도로 확인한다. 저장소의 빌드·테스트만으로 EC2 Agent 적용이나 CloudWatch 수신까지 검증되지는 않는다.

참고: [Spring Boot OTLP 자동 설정 조건](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/micrometer/metrics/autoconfigure/export/otlp/OtlpMetricsExportAutoConfiguration.html), [Spring Boot Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [CloudWatch Agent OTLP](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLPCloudWatchAgent.html), [CloudWatch 히스토그램](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/metrics-otel-histograms.html), [CloudWatch QueryMetrics 제한](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-PromQL-API-QueryMetrics.html)
