# dev HTTP 요청 지표

Spring Boot Actuator의 `http.server.requests` 타이머를 OTLP로 내보낸다. dev 프로필에서만 1분마다 로컬 CloudWatch Agent(`127.0.0.1:4318`)로 전송하며, 다른 프로필에서는 OTLP 전송을 끈다. 기존 EC2 CPU 지표, `Setty/Dev` 메모리 지표, `/setty/dev/backend` 로그 수집은 별개로 유지한다.

OTLP에는 HTTP 타이머와 `outcome` 태그만 남긴다. 서비스/환경은 리소스 속성 `service.name=setty-backend`, `deployment.environment.name=dev`로 구분한다. 요청 수는 히스토그램의 count, 5xx는 `outcome=SERVER_ERROR`인 count, p95는 히스토그램의 95백분위수로 구한다. 경로·사용자·요청 ID는 전송하지 않는다. `/actuator/health` 같은 HTTP 요청도 합계에 포함된다. CloudWatch에서는 기존 **Classic metrics** 네임스페이스가 아니라 **Metrics → Query Studio**에서 OTLP 지표를 찾는다.

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

dev 애플리케이션을 배포해 HTTP 요청을 몇 번 발생시킨 뒤, CloudWatch **Metrics → Query Studio**에서 `http.server.requests`를 검색한다. 실제로 도착한 지표의 이름과 태그를 먼저 확인한다. 이름이 그대로라면 아래 PromQL로 최근 5분 요청 수·5xx 수·p95 지연시간(ms)을 볼 수 있다. p95는 근삿값이며 요청이 거의 없는 구간은 비어 있을 수 있다.

```promql
sum(histogram_count(increase({__name__="http.server.requests","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev"}[5m])))
sum(histogram_count(increase({__name__="http.server.requests",outcome="SERVER_ERROR","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev"}[5m])))
histogram_quantile(0.95, sum(rate({__name__="http.server.requests","@resource.service.name"="setty-backend","@resource.deployment.environment.name"="dev"}[5m])))
```

Agent의 로그·메모리 수집이 계속되는지, `Setty/Dev` 메모리 지표와 `/setty/dev/backend` 로그에도 새 데이터가 들어오는지 별도로 확인한다. 저장소의 빌드·테스트만으로 EC2 Agent 적용이나 CloudWatch 수신까지 검증되지는 않는다.

참고: [Spring Boot Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [CloudWatch Agent OTLP](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLPCloudWatchAgent.html), [CloudWatch 히스토그램](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/metrics-otel-histograms.html)
