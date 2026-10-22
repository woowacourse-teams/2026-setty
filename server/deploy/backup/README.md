# prod DB 백업

prod MySQL `setty` 스키마를 매일 04:00 KST에 덤프해 암호화한 뒤 S3에 올린다. 이 폴더의 파일은 CodeDeploy 배포 대상이 아니며, 서버에 한 번 손으로 설치한다.

| 파일 | 서버 위치 | 역할 |
| --- | --- | --- |
| `setty-backup.sh` | `/opt/setty/backup/` | 덤프 → gzip → 암호화 → S3 업로드 → 30일 지난 백업 삭제 |
| `setty-restore.sh` | `/opt/setty/backup/` | S3 객체 또는 로컬 파일을 복호화해 새 DB로 복원 |
| `setty-backup.service` | `/etc/systemd/system/` | 백업 스크립트를 1회 실행 |
| `setty-backup.timer` | `/etc/systemd/system/` | 매일 04:00 Asia/Seoul에 서비스 실행 |

## 백업 위치와 보관 기간

매일 한 부를 `s3://techcourse-project-2026/setty/backup/db/daily/`에 올리고 30일 동안 보관한다. 버킷 버전 관리가 켜져 있어, 스크립트는 30일 지난 객체의 이전 버전과 삭제 마커까지 지운다.

보관 기간을 30일로 둔 것은 탈퇴한 회원의 정보가 백업에 오래 남지 않게 하기 위해서다. 거래·결제 기록의 법정 보존은 백업이 아니라 운영 DB에서 다룬다.

## 서버 설치

root 셸(`sudo -i`)에서 진행한다.

1. 스크립트와 systemd 파일을 위 표의 위치에 복사하고 스크립트에 `chmod 700`을 준다.
2. 암호화 키를 만든다. 키 사본은 서버 밖 비밀번호 관리자에 따로 보관한다.

   ```bash
   openssl rand -base64 32 > /root/setty-backup.key && chmod 600 /root/setty-backup.key
   ```

3. 읽기 전용 백업 계정을 만들고 접속 정보를 `/root/.my.cnf`(600)에 둔다. prod MySQL에는 비밀번호 정책이 있어 대문자, 소문자, 숫자, 특수문자를 모두 넣어야 한다.

   ```bash
   PW="$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-20)"'Aa1_'
   printf '[client]\nuser=setty_backup\npassword="%s"\n' "$PW" > /root/.my.cnf && chmod 600 /root/.my.cnf
   printf "CREATE USER IF NOT EXISTS 'setty_backup'@'localhost' IDENTIFIED BY '%s';\nGRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES ON setty.* TO 'setty_backup'@'localhost';\nFLUSH PRIVILEGES;\n" "$PW" | mysql -uroot -p
   unset PW
   ```

4. 손으로 1회 실행해 S3에 파일이 올라가는지 확인한 뒤 timer를 켠다. 서버 시계가 UTC라 다음 실행은 19:00 UTC로 표시된다.

   ```bash
   systemctl daemon-reload && systemctl start setty-backup.service
   journalctl -u setty-backup.service --no-pager -n 20 -o cat
   systemctl enable --now setty-backup.timer
   systemctl show setty-backup.timer -p NextElapseUSecRealtime
   ```

## 실패 알림

백업이 끝까지 성공하면 스크립트가 CloudWatch `Setty/Prod` 이름공간에 `BackupSucceeded = 1`을 보낸다. CloudWatch 알람이 하루 동안 이 값의 합이 1보다 작으면 prod 알림 경로로 알린다. 백업이 중간에 실패한 경우뿐 아니라 timer가 멈추거나 서버가 꺼져 백업이 아예 돌지 않은 경우도 잡는다.

시험용으로 다른 경로에 백업을 돌릴 때는 `METRIC_NAMESPACE=` 를 비워 성공 지표를 보내지 않는다.

```bash
PREFIX=setty/backup/prune-test KEEP_DAYS=0 METRIC_NAMESPACE= /opt/setty/backup/setty-backup.sh
```

## 확인과 복구

- 백업이 돌았는지: `aws s3 ls s3://techcourse-project-2026/setty/backup/db/daily/`
- 실패 원인: `journalctl -u setty-backup.service --no-pager -n 30`
- 새 DB로 복원: `sudo /opt/setty/backup/setty-restore.sh setty/backup/db/daily/<파일 이름> <새 DB 이름>`
  - 실행하면 MySQL root 비밀번호를 묻는다. 백업 계정은 읽기 전용이라 복원에 쓸 수 없다.
  - 운영 DB(`setty`)와 이미 있는 DB에는 복원하지 않는다.
  - 키나 파일이 잘못되면 DB를 만들기 전에 멈춘다. 내려받은 파일은 끝나면 지운다.

prod DB 자체를 백업으로 덮어쓰는 절차는 아직 검증하지 않았다. 필요하면 혼자 진행하지 말고 팀과 먼저 상의한다.
